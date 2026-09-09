(ns top.kzre.krro.ui.core.spec.stylesheet
  "样式表 EDN 描述规范。"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str])
  (:import (java.io PushbackReader)))

;; ── 基础类型 ──────────────────────────────────
(s/def ::selector (s/or :str string? :kw keyword?))
(s/def ::style-value (s/or :str string?
                           :num number?
                           :kw keyword?))   ; 变量引用

;; e.g. "classpath://other.style.edn"
(s/def ::import
 string?)
(s/def ::styles (s/map-of keyword? ::style-value))
(s/def ::bindings (s/map-of keyword? any?))
;; ── 规则 ──────────────────────────────────
(s/def ::rules
  (s/cat :selector ::selector
         :styles ::styles
         :children (s/* ::rules)))

(s/def ::stylesheet-item (s/or :bindings ::bindings
                               :rules ::rules
                               :import ::import))

;; ── 样式表 ──────────────────────────────────
(s/def ::stylesheet (s/coll-of ::stylesheet-item :kind vector?))



(defn load-stylesheet-edn
  "加载 EDN 资源，支持 classpath:// 和 file:// 路径。"
  [path]
  (let [res (if (str/starts-with? path "classpath://")
              (io/resource (subs path 12))
              (io/file path))]
    (when-not res
      (throw (ex-info "Resource not found" {:path path})))
    (with-open [rdr (PushbackReader. (io/reader res))]
      (edn/read rdr))))



(defn expand-imports
  "递归展开样式表中的 :import 项，返回展开后的纯数据列表。"
  ([items] (expand-imports items #{}))
  ([items loaded]
   (if (empty? items)
     []
     (let [item (first items)]
       (if (string? item)
         (if (contains? loaded item)
           (expand-imports (rest items) loaded)   ; 跳过重复 import
           (let [loaded' (conj loaded item)
                 imported (load-stylesheet-edn item)
                 expanded (expand-imports imported loaded')]
             (concat expanded (expand-imports (rest items) loaded'))))
         (cons item (expand-imports (rest items) loaded)))))))


(defn- parse-rule
  "将 Hiccup 风格的规则向量（[selector styles & children]）解析为内部 map。"
  [[selector styles & children]]
  {:selector (name selector)
   :styles styles
   :children (mapv parse-rule children)})

(defn parse-stylesheet
  "解析展开后的样式表列表，返回 {:bindings, :rules}。"
  [items]
  (loop [bindings {}
         rules []
         remaining items]
    (if (empty? remaining)
      {:bindings bindings :rules rules}
      (let [item (first remaining)]
        (cond
          (map? item) (recur (merge bindings item) rules (rest remaining))
          (vector? item) (recur bindings (conj rules (parse-rule item)) (rest remaining))
          :else (throw (ex-info "Invalid stylesheet item" {:item item})))))))

(defn resolve-values
  "递归将样式值中的关键字变量替换为 bindings 中的实际值。"
  [bindings value]
  (cond
    (map? value) (into {} (map (fn [[k v]] [k (resolve-values bindings v)]) value))
    (vector? value) (mapv #(resolve-values bindings %) value)
    (keyword? value) (get bindings value value)
    :else value))