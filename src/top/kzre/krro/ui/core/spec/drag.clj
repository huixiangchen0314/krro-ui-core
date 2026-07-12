(ns top.kzre.krro.ui.core.spec.drag
  "拖拽交互的抽象属性声明。
   为 :drag-source 和 :drag-target 提供 spec 定义，
   隔离平台细节，仅依赖纯数据描述。"
  (:require [clojure.spec.alpha :as s]))

;; ═══════════════════════════════════════════════════════
;; 基础类型
;; ═══════════════════════════════════════════════════════
(s/def ::x number?)
(s/def ::y number?)
(s/def ::data string?)
(s/def ::accepted? boolean?)
;; transfer-mode 限定为已知关键字
(s/def ::transfer-mode #{:move :copy :link})
(s/def ::transfer-modes (s/coll-of ::transfer-mode :kind vector? :min-count 1))

;; ═══════════════════════════════════════════════════════
;; 标准化拖拽事件 map（用户回调中接收的对象）
;; ═══════════════════════════════════════════════════════
(s/def ::drag-event
  (s/keys :req-un [::x ::y ::data ::accepted?]
          :opt-un [::transfer-mode]))

;; ═══════════════════════════════════════════════════════
;; :drag-source 属性
;; ═══════════════════════════════════════════════════════
(s/def ::content-fn ifn?)          ;; (fn [node] -> string)
(s/def ::on-drag-start ifn?)       ;; (fn [drag-event] -> ?)
(s/def ::on-drag-end ifn?)         ;; (fn [drag-event] -> ?)
(s/def ::drag-source
  (s/keys :req-un [::content-fn]
          :opt-un [::transfer-modes ::on-drag-start ::on-drag-end]))

;; ═══════════════════════════════════════════════════════
;; :drag-target 属性
;; ═══════════════════════════════════════════════════════
(s/def ::accept-fn ifn?)           ;; (fn [node drag-event] -> boolean)
(s/def ::on-drag-over ifn?)        ;; (fn [node drag-event] -> ?)
(s/def ::on-drag-dropped ifn?)     ;; (fn [node drag-event] -> ?)
(s/def ::drag-target
  (s/keys :req-un [::accept-fn ::on-drag-dropped]
          :opt-un [::on-drag-over]))

;; ═══════════════════════════════════════════════════════
;; 辅助函数：构造常用属性
;; ═══════════════════════════════════════════════════════
(defn drag-source
  "创建一个 :drag-source 属性 map。
   content-fn - (fn [node] -> string) 返回拖拽携带的字符串数据。
   可选参数：
     :modes       - transfer 模式向量，默认 [:move]
     :on-start    - 拖拽开始回调 (fn [event-map])
     :on-end      - 拖拽结束回调 (fn [event-map])"
  [content-fn & {:keys [modes on-start on-end]
                 :or   {modes [:move]}}]
  (cond-> {:content-fn content-fn
           :transfer-modes (vec modes)}
          on-start (assoc :on-drag-start on-start)
          on-end   (assoc :on-drag-end on-end)))

(defn drag-target
  "创建一个 :drag-target 属性 map。
   accept-fn    - (fn [node event-map] -> boolean) 是否接受拖拽。
   on-dropped   - (fn [node event-map]) 放置时的回调。
   可选：
     :on-over    - 拖拽悬停时的回调。"
  [accept-fn on-dropped & {:keys [on-over]}]
  (cond-> {:accept-fn accept-fn
           :on-drag-dropped on-dropped}
          on-over (assoc :on-drag-over on-over)))