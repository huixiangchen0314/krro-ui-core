(ns top.kzre.krro.ui.core.spec
  "UI 抽象层通用规范 —— 定义强制通用标签集、通用属性与事件。
   所有渲染器必须实现此规范，以保证 EDN 描述的可移植性。"
  (:require [clojure.spec.alpha :as s]))

;; ═══════════════════════════════════════════════════════════
;; 基础结构（所有元素都必须遵守）
;; ═══════════════════════════════════════════════════════════
(s/def ::tag keyword?)
(s/def ::attrs map?)
(s/def ::children (s/coll-of ::ui-element :kind vector?))

(s/def ::ui-element
  (s/or :string string?
        :vector (s/and vector?
                       (s/cat :tag ::tag
                              :attrs (s/? ::attrs)
                              :children (s/* ::ui-element)))))

;; ═══════════════════════════════════════════════════════════
;; 通用属性（所有渲染器必须支持，并保持相同语义）
;; ═══════════════════════════════════════════════════════════

;; 标识与复用
(s/def ::key (s/or :kw keyword? :str string?))

;; 样式
(s/def ::style map?)

;; 状态属性
(s/def ::visible? boolean?)
(s/def ::disabled? boolean?)

;; 数据绑定
(s/def ::path (s/coll-of (s/or :kw keyword? :idx int?) :kind vector? :min-count 1))
(s/def ::bind-path ::path)
(s/def ::bind (s/keys :req-un [::bind-path]))

;; 事件相关属性
(s/def ::event-type keyword?)
(s/def ::command-id keyword?)
(s/def ::command-args (s/coll-of any? :kind vector?))
(s/def ::on (s/map-of ::event-type ::command-id))

;; 布局与对齐（通用）
(s/def ::grow #{:never :always :auto})
(s/def ::h-grow ::grow)
(s/def ::v-grow ::grow)
(s/def ::align-x #{:left :center :right :stretch})
(s/def ::align-y #{:top :center :bottom :stretch})

;; ═══════════════════════════════════════════════════════════
;; 通用标签集（强制实现）
;; ═══════════════════════════════════════════════════════════

;; ── 布局容器 ────────────────────────────────
(s/def ::direction #{:vertical :horizontal})
(s/def ::block-props (s/keys :opt-un [::direction ::style ::key ::visible? ::disabled?]))

;; Grid 专用属性
(s/def ::columns int?)
(s/def ::rows int?)
(s/def ::row int?)
(s/def ::column int?)
(s/def ::row-span int?)
(s/def ::column-span int?)
(s/def ::grid-props (s/keys :opt-un [::columns ::rows ::style ::key]))
(s/def ::grid-child-props (s/keys :opt-un [::row ::column ::row-span ::column-span]))

;; ── 基础控件 ────────────────────────────────
(s/def ::content string?)
(s/def ::text-props (s/keys :opt-un [::content ::style ::key ::bind ::visible?]))

(s/def ::button-props (s/keys :opt-un [::content ::on ::style ::key ::bind ::disabled?]))

(s/def ::placeholder string?)
(s/def ::input-props (s/keys :opt-un [::content ::placeholder ::on ::style ::key ::bind ::disabled?]))

(s/def ::text-area-props (s/keys :opt-un [::content ::placeholder ::style ::key ::bind ::disabled?]))

(s/def ::checked? boolean?)
(s/def ::check-box-props (s/keys :opt-un [::content ::checked? ::on ::style ::key ::bind ::disabled?]))

(s/def ::radio-group string?)
(s/def ::radio-button-props (s/keys :opt-un [::content ::checked? ::radio-group ::on ::style ::key ::bind ::disabled?]))

(s/def ::min number?)
(s/def ::max number?)
(s/def ::value number?)
(s/def ::slider-props (s/keys :opt-un [::min ::max ::value ::on ::style ::key ::bind]))

(s/def ::progress-props (s/keys :opt-un [::value ::style ::key]))

(s/def ::items (s/coll-of any? :kind vector?))
(s/def ::combo-box-props (s/keys :opt-un [::items ::value ::on ::style ::key ::bind]))

(s/def ::list-view-props (s/keys :opt-un [::items ::on ::style ::key ::bind]))

(s/def ::tree-view-props (s/keys :opt-un [::style ::key ::bind]))

(s/def ::tab-panel-props (s/keys :opt-un [::style ::key]))

(s/def ::title string?)
(s/def ::tab-props (s/keys :opt-un [::title ::style ::key]))

(s/def ::src string?)
(s/def ::image-props (s/keys :opt-un [::src ::style ::key]))

(s/def ::separator-props (s/keys :opt-un [::style ::key]))

(s/def ::scroll-props (s/keys :opt-un [::style ::key]))

;; ── 强制标签集合 ──────────────────────────────
(def required-tags
  "所有符合规范的渲染器必须实现的标签集合。"
  #{:block :grid
    :text :button :input :text-area :check-box :radio-button
    :slider :progress :combo-box :list-view :tree-view
    :tab-panel :tab :image :separator :scroll})

;; ═══════════════════════════════════════════════════════════
;; 事件类型（必须映射）
;; ═══════════════════════════════════════════════════════════
(def required-events
  "渲染器必须支持的事件类型。"
  #{:click :input :change})

;; ═══════════════════════════════════════════════════════════
;; 工具函数
;; ═══════════════════════════════════════════════════════════
(defn effective-key
  "从 EDN 元素中提取 :key 属性值，若不存在返回 nil。"
  [edn]
  (when (and (vector? edn) (>= (count edn) 2))
    (let [attrs (second edn)]
      (when (map? attrs)
        (:key attrs)))))

(defn tag-of [edn] (when (vector? edn) (first edn)))

(defn attrs-of [edn]
  (when (and (vector? edn) (>= (count edn) 2) (map? (second edn)))
    (second edn)))