(ns top.kzre.krro.ui.core.bind
  "平台无关的数据绑定管理器。
   支持单向/双向绑定、路径表达式转换、批量刷新。
   与 diff 执行器集成，在属性更新后自动刷新绑定。"
  (:require [top.kzre.krro.core.project :as proj]
            [top.kzre.krro.core.command :as cmd]))

;; ── 内部数据结构 ──────────────────────────────
(defn- make-state []
  (atom {:bindings {}              ;; element -> [{:path :apply-fn ...}]
         :watch-key (keyword (str "bind-manager-" (gensym)))
         :batch-queue (list)       ;; 待刷新的元素集合
         :batch-mode? false}))

(defn create-bind-manager
  "创建一个新的绑定管理器实例。"
  []
  (make-state))

(def ^:dynamic *default-bind-manager* (create-bind-manager))
(defn- transform? [val f] (if f (f val) val))
(defn- watch-callback [state]
  (fn [key ref old new]
    (doseq [[element bindings] (:bindings @state)]
      (doseq [{:keys [path apply-fn transform]} bindings]
        (let [old-val (get-in old path)
              new-val (-> (get-in new path) (transform? transform))]
          (when (not= old-val new-val)
            (apply-fn element new-val)))))))


(defn register!
  "为 element 注册一个数据绑定。
   element      - 平台元素
   bind-path    - 项目原子路径向量
   apply-fn     - (fn [element new-value]) 或返回命令描述的函数以实现双向绑定
   opts         - 可选 :transform (fn [val] new-val)"
  ([element bind-path apply-fn & {:as opts}]
   (register! *default-bind-manager* element bind-path apply-fn opts))
  ([manager element bind-path apply-fn & {:as opts}]
   (let [state manager]
     (when (empty? (:bindings @state))
       (add-watch proj/project (:watch-key @state) (watch-callback state)))
     (swap! state update-in [:bindings element]
            (fnil conj [])
            (merge {:path bind-path :apply-fn apply-fn} opts))
     ;; 立即应用当前值
     (when-let [val (get-in @proj/project bind-path)]
       (let [v (if (:transform opts) ((:transform opts) val) val)]
         (apply-fn element v))))))

(defn unregister!
  ([element] (unregister! *default-bind-manager* element))
  ([manager element]
   (swap! manager update :bindings dissoc element)
   (when (empty? (:bindings @manager))
     (remove-watch proj/project (:watch-key @manager)))))

(defn refresh!
  "立即刷新 element 的所有绑定值。"
  ([element] (refresh! *default-bind-manager* element))
  ([manager element]
   (when-let [bindings (get (:bindings @manager) element)]
     (doseq [{:keys [path apply-fn transform]} bindings]
       (when-let [val (get-in @proj/project path)]
         (let [v (if transform (transform val) val)]
           (apply-fn element v)))))))

(defn- queue-refresh!
  "将元素加入批量刷新队列。"
  [manager element]
  (swap! manager update :batch-queue conj element))

(defn- flush-batch!
  "刷新批量队列中的所有元素。"
  [manager]
  (let [elements (distinct (:batch-queue @manager))]
    (swap! manager assoc :batch-queue (list))
    (doseq [elem elements]
      (refresh! manager elem))))

(defmacro batch-mode
  "在批处理模式下执行 body，在退出时统一刷新所有受影响的绑定。
   用于 diff 操作序列执行时包裹。"
  [manager & body]
  `(do
     (swap! ~manager assoc :batch-mode? true :batch-queue (list))
     (try
       ~@body
       (flush-batch! ~manager)
       (finally
         (swap! ~manager assoc :batch-mode? false :batch-queue (list))))))

;; ── 双向绑定辅助 ─────────────────────────────────
(defn command-event-handler
  "返回一个可用于控件事件处理器的函数，触发命令更新项目。
   cmd-id    - 命令关键字
   args-fn   - (fn [element] ...) 返回命令所需参数
   通常由 apply-fn 返回，绑定系统自动处理。"
  [cmd-id args-fn]
  (fn [& event]
    (let [args (args-fn)]
      (apply cmd/execute-command! cmd-id args))))
