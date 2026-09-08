(ns top.kzre.krro.ui.core.spec.event
  "平台无关的 UI 事件规范。定义各种事件 map 的结构，
   供回调函数使用。所有字段均不依赖特定平台。"
  (:require [clojure.spec.alpha :as s]))

;; ═══════════════════════════════════════════════════════
;; 基础事件
;; ═══════════════════════════════════════════════════════
;; 事件类型，如 :click, :change, :keydown
(s/def ::type #{:move :hover :click :change :press :drag :release :wheel})

(s/def ::target any?)            ;; 事件源组件标识（可能是 VNode 的 :key 或 :id）
(s/def ::timestamp int?)         ;; 时间戳（毫秒）

(s/def ::event
  (s/keys :req-un [::type ::target ::timestamp]))

;; ── 修饰键 ──────────────────────────────────────────
(s/def ::ctrl? boolean?)
(s/def ::shift? boolean?)
(s/def ::alt? boolean?)
(s/def ::meta? boolean?)

(s/def ::modifiers
  (s/keys :opt-un [::ctrl? ::shift? ::alt? ::meta?]))

;; ═══════════════════════════════════════════════════════
;; 鼠标事件
;; ═══════════════════════════════════════════════════════
(s/def ::x number?)             ;; 相对组件坐标
(s/def ::y number?)
(s/def ::screen-x number?)      ;; 屏幕坐标（可选）
(s/def ::screen-y number?)
(s/def ::button #{:left :middle :right})  ;; 鼠标按键

;; 基础鼠标事件（如移动、进入、离开）
(s/def ::mouse-event
  (s/merge ::event
           (s/keys :opt-un [::x ::y ::screen-x ::screen-y ::modifiers])))

;; 点击事件（带按键信息）
(s/def ::click-event
  (s/merge ::mouse-event
           (s/keys :opt-un [::button])))

;; 滚轮事件
(s/def ::delta-x number?)
(s/def ::delta-y number?)
(s/def ::wheel-event
  (s/merge ::mouse-event
           (s/keys :opt-un [::delta-x ::delta-y])))

;; ═══════════════════════════════════════════════════════
;; 键盘事件
;; ═══════════════════════════════════════════════════════
(s/def ::key string?)           ;; 可读键名，如 "a", "Enter"
(s/def ::key-code int?)         ;; 键码（平台无关时可用标准 Unicode 值）
(s/def ::repeat? boolean?)      ;; 是否为重复按键

(s/def ::key-event
  (s/merge ::event
           (s/keys :opt-un [::key ::key-code ::repeat? ::modifiers])))

;; ═══════════════════════════════════════════════════════
;; 焦点事件
;; ═══════════════════════════════════════════════════════
(s/def ::focus-event
  (s/merge ::event))

;; ═══════════════════════════════════════════════════════
;; 值变化事件
;; ═══════════════════════════════════════════════════════
(s/def ::old-value any?)
(s/def ::new-value any?)
(s/def ::change-event
  (s/merge ::event
           (s/keys :req-un [::old-value ::new-value])))

;; ═══════════════════════════════════════════════════════
;; 动作事件（如按钮按下、菜单项选择）
;; ═══════════════════════════════════════════════════════
(s/def ::action-event
  (s/merge ::event))

;; ═══════════════════════════════════════════════════════
;; 具体回调函数规范（用于文档说明，实际运行时仍为 ifn?）
;; ═══════════════════════════════════════════════════════
;; 以下定义仅用于指导实现，不强制在 spec 中约束函数参数。
(comment
  ;; 点击回调应接受 ::click-event 或其子类型
  (s/def ::on-click (s/fspec :args (s/cat :e ::click-event)))

  ;; 值变化回调应接受 ::change-event
  (s/def ::on-change (s/fspec :args (s/cat :e ::change-event)))

  ;; 键盘事件回调
  (s/def ::on-key-down (s/fspec :args (s/cat :e ::key-event)))
  (s/def ::on-key-up   (s/fspec :args (s/cat :e ::key-event)))

  ;; 鼠标进入/离开
  (s/def ::on-mouse-enter (s/fspec :args (s/cat :e ::mouse-event)))
  (s/def ::on-mouse-leave (s/fspec :args (s/cat :e ::mouse-event)))

  ;; 焦点事件
  (s/def ::on-focus (s/fspec :args (s/cat :e ::focus-event)))
  (s/def ::on-blur  (s/fspec :args (s/cat :e ::focus-event)))

  ;; 动作事件（如按钮主操作）
  (s/def ::on-action (s/fspec :args (s/cat :e ::action-event)))
  )