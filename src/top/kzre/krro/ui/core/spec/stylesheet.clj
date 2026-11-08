(ns top.kzre.krro.ui.core.spec.stylesheet
  "样式表 EDN 描述规范。"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str])
  (:import (java.io PushbackReader)))

;; ── 基础类型 ──────────────────────────────────
(s/def ::selector (s/or :str string? :kw keyword?))

;; 值可以是字符串、数字、关键字（变量或字面量），
;; 或由这些值组成的 vector（用于多值属性）。
(s/def ::style-value
  (s/or :str string?
        :num number?
        :kw  keyword?                                   ; 变量引用 / 字面量
        :vec (s/coll-of ::style-value :kind vector?)))

;; e.g. "classpath://other.style.edn" / "file:///tmp/base.edn" / "base.edn"
(s/def ::import string?)
(s/def ::styles (s/map-of keyword? ::style-value))
(s/def ::bindings (s/map-of keyword? any?))

;; ── 规则 ──────────────────────────────────
;; 用 vector? 收窄类型，避免 ::rules 与 ::import 在 s/or 中产生歧义
;; （原先依赖 "字符串不是 string? 元素" 的偶然性质）。
(s/def ::rules
  (s/and vector?
         (s/cat :selector ::selector
                :styles   ::styles
                :children (s/* ::rules))))

(s/def ::stylesheet-item
  (s/or :bindings ::bindings
        :rules    ::rules
        :import   ::import))

;; ── 样式表 ──────────────────────────────────
(s/def ::stylesheet (s/coll-of ::stylesheet-item :kind vector?))

;; ── 资源加载 ──────────────────────────────────

(def ^:private classpath-prefix "classpath://")
(def ^:private file-prefix "file://")

(defn- resolve-resource
  "将路径解析为 io/resource 或 java.io.File。"
  [path]
  (cond
    (str/starts-with? path classpath-prefix)
    (io/resource (subs path (count classpath-prefix)))

    (str/starts-with? path file-prefix)
    (io/file (subs path (count file-prefix)))

    :else
    (io/file path)))

(defn load-stylesheet-edn
  "加载 EDN 资源，支持 classpath://、file:// 和普通文件路径。

   一个文件通常包含一个样式表 vector；若包含多个顶层 form，
   要求它们全部是 vector，并拼接为一个 vector 返回。
   其他多 form 组合会抛出 ex-info（带 :path）。"
  [path]
  (let [res (resolve-resource path)]
    (when-not res
      (throw (ex-info "Resource not found" {:path path})))
    (let [forms (with-open [rdr (PushbackReader. (io/reader res))]
                  (let [eof  (Object.)
                        step (fn step [acc]
                               (let [form (edn/read {:eof eof} rdr)]
                                 (if (identical? form eof)
                                   acc
                                   (recur (conj acc form)))))]
                    (step [])))]
      (case (count forms)
        0 (throw (ex-info "Empty stylesheet file" {:path path}))
        1 (first forms)
        (do
          (when-not (every? vector? forms)
            (throw (ex-info "Multiple forms in stylesheet must all be vectors"
                            {:path path :form-count (count forms)})))
          (vec (mapcat identity forms)))))))

;; ── import 展开 ──────────────────────────────────

(defn- expand-imports*
  "内部实现：返回 [expanded-items loaded-set]。

   与旧实现相比，loaded 会在兄弟分支间正确传播，
   因此同一路径在整棵展开树中只会被加载一次。"
  [items loaded]
  (reduce (fn [[acc loaded] item]
            (if (string? item)
              (if (contains? loaded item)
                [acc loaded]
                (let [imported (load-stylesheet-edn item)
                      _ (when-not (vector? imported)
                          (throw (ex-info "Imported stylesheet must be a vector"
                                          {:path item :value imported})))
                      [expanded loaded'] (expand-imports* imported (conj loaded item))]
                  [(into acc expanded) loaded']))
              [(conj acc item) loaded]))
          [[] loaded]
          items))

(defn expand-imports
  "递归展开样式表中的 import 项，返回展开后的纯数据 vector。

   - import 只允许出现在顶层；同一路径只会被加载一次。
   - 展开结果保证不含字符串，可直接交给 parse-stylesheet。"
  ([items] (first (expand-imports* items #{})))
  ([items loaded] (first (expand-imports* items loaded))))

;; ── 解析 ──────────────────────────────────

(defn- parse-rule
  "将 Hiccup 风格的规则向量（[selector styles & children]）解析为内部 map。"
  [[selector styles & children]]
  {:selector (name selector)
   :styles   styles
   :children (mapv parse-rule children)})

(defn parse-stylesheet
  "解析展开后的样式表列表，返回 {:bindings, :rules}。

   入口处用 ::stylesheet spec 校验整个输入；非法输入抛出带
   :explain（s/explain-data 结果）的 ex-info。
   若输入中仍残留 import 字符串，则提示先调用 expand-imports。"
  [items]
  (when-not (sequential? items)
    (throw (ex-info "Stylesheet must be a sequential collection"
                    {:value items})))
  (let [items (vec items)]
    (when-not (s/valid? ::stylesheet items)
      (throw (ex-info "Invalid stylesheet"
                      {:explain (s/explain-data ::stylesheet items)})))
    (loop [bindings  {}
           rules     []
           idx       0
           remaining items]
      (if (empty? remaining)
        {:bindings bindings :rules rules}
        (let [item (first remaining)]
          (cond
            (map? item)
            (recur (merge bindings item) rules (inc idx) (rest remaining))

            (vector? item)
            (recur bindings (conj rules (parse-rule item)) (inc idx) (rest remaining))

            (string? item)
            (throw (ex-info "Unexpanded import; call expand-imports first"
                            {:index idx :import item}))

            :else
            (throw (ex-info "Invalid stylesheet item"
                            {:index idx :item item}))))))))

;; ── 变量解析 ──────────────────────────────────

(defn resolve-values
  "递归将样式值中的关键字变量替换为 bindings 中的实际值。

   默认宽松：未绑定的 keyword 原样保留（视为字面量）。
   传入 {:strict? true} 时，未绑定的 keyword 会抛出 ex-info。
   显式绑定为 nil 的关键字在严格模式下不会报错（因为它已被绑定）。"
  ([bindings value] (resolve-values bindings value {}))
  ([bindings value {:keys [strict?] :as opts}]
   (cond
     (map? value)
     (into {} (map (fn [[k v]] [k (resolve-values bindings v opts)]) value))

     (vector? value)
     (mapv #(resolve-values bindings % opts) value)

     (set? value)
     (into #{} (map #(resolve-values bindings % opts)) value)

     (keyword? value)
     (if (contains? bindings value)
       (get bindings value)
       (if strict?
         (throw (ex-info "Unresolved variable"
                         {:var value :known (set (keys bindings))}))
         value))

     :else value)))