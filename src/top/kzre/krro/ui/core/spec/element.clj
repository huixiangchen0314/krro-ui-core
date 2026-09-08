(ns top.kzre.krro.ui.core.spec.element
  "平台无关的 UI 组件规范。仅包含真正通用、语义明确的组件。"
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.ui.core.spec.drag :as drag]))

;; ═══════════════════════════════════════════════════════
;; 基础类型
;; ═══════════════════════════════════════════════════════
(s/def ::key keyword?)
(s/def ::style (s/map-of keyword? string?))
(s/def ::class (s/or :single (s/or :kw keyword? :str string?)
                     :multiple (s/coll-of (s/or :kw keyword? :str string?) :kind vector?)))
(s/def ::stylesheet (s/or :single string?
                          :multiple (s/coll-of string? :kind vector?)))
(s/def ::visible boolean?)
(s/def ::disabled boolean?)
(s/def ::content string?)
(s/def ::checked boolean?)
(s/def ::value any?)
(s/def ::items (s/coll-of string? :kind vector?))
(s/def ::direction #{:vertical :horizontal})
(s/def ::src string?)
(s/def ::title string?)

;; 拖拽属性
(s/def ::drag-source ::drag/drag-source)
(s/def ::drag-target ::drag/drag-target)

;; ═══════════════════════════════════════════════════════
;; 通用事件（所有回调接收标准化事件 map，平台无关）
;; ═══════════════════════════════════════════════════════
(s/def ::on-click ifn?)
(s/def ::on-action ifn?)      ; 主操作，语义比 click 更通用（如按钮的默认动作）
(s/def ::on-change ifn?)      ; 值变化
(s/def ::on-focus ifn?)
(s/def ::on-blur ifn?)
(s/def ::on-key-down ifn?)
(s/def ::on-key-up ifn?)
(s/def ::on-mouse-enter ifn?)
(s/def ::on-mouse-leave ifn?)

;; 可复用的交互事件集合
(s/def ::eventful-attrs
  (s/keys :opt-un [::on-click ::on-action ::on-change
                   ::on-focus ::on-blur
                   ::on-key-down ::on-key-up
                   ::on-mouse-enter ::on-mouse-leave]))

;; ═══════════════════════════════════════════════════════
;; 公共属性（所有组件均可包含）
;; ═══════════════════════════════════════════════════════
(s/def ::common-attrs
  (s/keys :opt-un [::key ::style ::class ::visible ::disabled
                   ::drag-source ::drag-target ::stylesheet]))

;; ═══════════════════════════════════════════════════════
;; 布局容器
;; ═══════════════════════════════════════════════════════

(s/def ::alignment (s/or :keyword #{:top-left :top-center :top-right
                                    :center-left :center :center-right
                                    :bottom-left :bottom-center :bottom-right
                                    :baseline-left :baseline-center :baseline-right}
                         :string string?))

(s/def ::block-attrs (s/merge ::common-attrs
                              (s/keys :opt-un [::direction ::alignment])))
(s/def ::split-attrs (s/merge ::common-attrs
                              (s/keys :opt-un [::direction ::alignment])))
(s/def ::scroll-attrs ::common-attrs)
(s/def ::tool-bar-attrs ::common-attrs)

;; ═══════════════════════════════════════════════════════
;; 菜单系统
;; ═══════════════════════════════════════════════════════
(s/def ::menu-bar-attrs ::common-attrs)
(s/def ::menu-attrs (s/merge ::common-attrs
                             (s/keys :opt-un [::content])))  ;; 菜单标题
(s/def ::menu-item-attrs (s/merge ::common-attrs
                                  ::eventful-attrs
                                  (s/keys :opt-un [::content])))

;; ═══════════════════════════════════════════════════════
;; 基础控件（交互控件合并事件集合）
;; ═══════════════════════════════════════════════════════
(s/def ::text-attrs (s/merge ::common-attrs
                             (s/keys :opt-un [::content])))
(s/def ::button-attrs (s/merge ::common-attrs
                               ::eventful-attrs
                               (s/keys :opt-un [::content])))
(s/def ::input-attrs (s/merge ::common-attrs
                              ::eventful-attrs
                              (s/keys :opt-un [::content])))
(s/def ::text-area-attrs (s/merge ::common-attrs
                                  ::eventful-attrs
                                  (s/keys :opt-un [::content])))
(s/def ::check-box-attrs (s/merge ::common-attrs
                                  ::eventful-attrs
                                  (s/keys :opt-un [::content ::checked])))
(s/def ::radio-button-attrs (s/merge ::common-attrs
                                     ::eventful-attrs
                                     (s/keys :opt-un [::content ::checked])))
(s/def ::combo-box-attrs (s/merge ::common-attrs
                                  ::eventful-attrs
                                  (s/keys :opt-un [::items ::value])))
(s/def ::slider-attrs (s/merge ::common-attrs
                               ::eventful-attrs
                               (s/keys :opt-un [::min ::max ::value])))
(s/def ::progress-attrs (s/merge ::common-attrs
                                 (s/keys :opt-un [::value])))
(s/def ::separator-attrs ::common-attrs)
(s/def ::image-attrs (s/merge ::common-attrs
                              (s/keys :opt-un [::src])))
(s/def ::link-attrs (s/merge ::common-attrs
                             ::eventful-attrs
                             (s/keys :opt-un [::content])))

;; ═══════════════════════════════════════════════════════
;; 可选：完整的 VNode 多方法 Spec（用于验证 Hiccup 向量）
;; ═══════════════════════════════════════════════════════
(defmulti vnode-spec first)
(defmethod vnode-spec :block        [_] (s/cat :tag #(= :block %)
                                               :attrs (s/? ::block-attrs)
                                               :children (s/* (s/multi-spec vnode-spec first))))
(defmethod vnode-spec :text         [_] (s/cat :tag #(= :text %)
                                               :attrs (s/? ::text-attrs)
                                               :children (s/cat)))
(defmethod vnode-spec :button       [_] (s/cat :tag #(= :button %)
                                               :attrs (s/? ::button-attrs)
                                               :children (s/cat)))
(defmethod vnode-spec :input        [_] (s/cat :tag #(= :input %)
                                               :attrs (s/? ::input-attrs)
                                               :children (s/cat)))
(defmethod vnode-spec :text-area    [_] (s/cat :tag #(= :text-area %)
                                               :attrs (s/? ::text-area-attrs)
                                               :children (s/cat)))
(defmethod vnode-spec :check-box    [_] (s/cat :tag #(= :check-box %)
                                               :attrs (s/? ::check-box-attrs)
                                               :children (s/cat)))
(defmethod vnode-spec :radio-button [_] (s/cat :tag #(= :radio-button %)
                                               :attrs (s/? ::radio-button-attrs)
                                               :children (s/cat)))
(defmethod vnode-spec :combo-box    [_] (s/cat :tag #(= :combo-box %)
                                               :attrs (s/? ::combo-box-attrs)
                                               :children (s/cat)))
(defmethod vnode-spec :slider       [_] (s/cat :tag #(= :slider %)
                                               :attrs (s/? ::slider-attrs)
                                               :children (s/cat)))

(defmethod vnode-spec :image        [_] (s/cat :tag #(= :image %)
                                               :attrs (s/? ::image-attrs)
                                               :children (s/cat)))
(defmethod vnode-spec :link         [_] (s/cat :tag #(= :link %)
                                               :attrs (s/? ::link-attrs)
                                               :children (s/cat)))

(defmethod vnode-spec :scroll       [_] (s/cat :tag #(= :scroll %)
                                               :attrs (s/? ::scroll-attrs)
                                               :children (s/* (s/multi-spec vnode-spec first))))
(defmethod vnode-spec :split        [_] (s/cat :tag #(= :split %)
                                               :attrs (s/? ::split-attrs)
                                               :children (s/* (s/multi-spec vnode-spec first))))

;; 这些属于拓展元素，即大概不是所有原生gui都默认提供的，虽然可能提供，需要渲染器提供解释支持
(defmethod vnode-spec :progress     [_] (s/cat :tag #(= :progress %)
                                               :attrs (s/? ::progress-attrs)
                                               :children (s/cat)))
(defmethod vnode-spec :separator    [_] (s/cat :tag #(= :separator %)
                                               :attrs (s/? ::separator-attrs)
                                               :children (s/cat)))

(defmethod vnode-spec :menu-bar     [_] (s/cat :tag #(= :menu-bar %)
                                               :attrs (s/? ::menu-bar-attrs)
                                               :children (s/* (s/multi-spec vnode-spec first))))
(defmethod vnode-spec :menu         [_] (s/cat :tag #(= :menu %)
                                               :attrs (s/? ::menu-attrs)
                                               :children (s/* (s/multi-spec vnode-spec first))))
(defmethod vnode-spec :menu-item    [_] (s/cat :tag #(= :menu-item %)
                                               :attrs (s/? ::menu-item-attrs)
                                               :children (s/cat)))

(defmethod vnode-spec :tool-bar     [_] (s/cat :tag #(= :tool-bar %)
                                               :attrs (s/? ::tool-bar-attrs)
                                               :children (s/* (s/multi-spec vnode-spec first))))
(defmethod vnode-spec :default      [_] (s/cat :tag keyword? :attrs (s/? map?) :children (s/* (s/multi-spec vnode-spec first))))

(s/def ::vnode (s/multi-spec vnode-spec first))