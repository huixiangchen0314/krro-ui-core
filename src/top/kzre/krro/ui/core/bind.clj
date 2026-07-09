(ns top.kzre.krro.ui.core.bind
  "平台无关的数据绑定管理器。
   支持绑定到任意 atom（默认绑定到全局项目 atom）。
   监听源 atom 路径变化，并调用渲染器提供的 apply-fn 更新控件。
   与 diff 执行器协作，保证 UI 与数据同步。"
  (:require
   [top.kzre.krro.core.command :as cmd]
   [top.kzre.krro.core.project :as proj]))

;; ═══════════════════════════════════════════
;; 内部状态与默认实例
;; ═══════════════════════════════════════════

(defn- make-state
  "创建一个绑定管理器状态，关联到指定的源 atom。"
  [source-atom]
  (atom {:bindings {}                                          ;; element → [binding-spec]
         :watch-key (keyword (str "bind-manager-" (gensym)))   ;; 用于 add-watch 的唯一键
         :source-atom source-atom                              ;; 数据源 atom
         :batch-queue (list)
         :batch-mode? false}))

(defn create-bind-manager
  "创建一个绑定管理器，默认绑定到全局项目 atom。"
 ([]
  (make-state proj/project))
  ([source-atom]
   (make-state source-atom)))

(def ^:dynamic *default-bind-manager*
  "默认绑定管理器，绑定到全局项目 atom。"
  (create-bind-manager))

;; ═══════════════════════════════════════════
;; 内部辅助
;; ═══════════════════════════════════════════
(defn- transform? [val f] (if f (f val) val))

(defn- watch-callback
  "构建一个 watch 回调函数，用于监听源 atom 的变化。"
  [state]
  (fn [_ _ old new]
    (doseq [[element bindings] (:bindings @state)]
      (doseq [{:keys [path apply-fn transform]} bindings]
        (let [old-val (get-in old path)
              new-val (-> (get-in new path) (transform? transform))]
          (when (not= old-val new-val)
            (apply-fn element new-val)))))))

;; ═══════════════════════════════════════════
;; 公共 API
;; ═══════════════════════════════════════════

(defn register!
  "为 element 注册一个数据绑定。
   默认使用 *default-bind-manager*，也可显式传入 manager。
   opts 可选 :transform (fn [val] new-val)。"
  ([element bind-path apply-fn]
   (register! *default-bind-manager* element bind-path apply-fn nil))
  ([manager element bind-path apply-fn]
   (register! manager element bind-path apply-fn nil))
  ([manager element bind-path apply-fn opts]
   (let [state manager
         source-atom (:source-atom @state)]
     ;; 首次绑定时添加对源 atom 的监听
     (when (empty? (:bindings @state))
       (add-watch source-atom (:watch-key @state) (watch-callback state)))
     ;; 记录绑定信息
     (swap! state update-in [:bindings element]
            (fnil conj [])
            (merge {:path bind-path :apply-fn apply-fn} opts))
     ;; 立即用当前值同步控件
     (when-let [val (get-in @source-atom bind-path)]
       (let [v (if-let [f (:transform opts)] (f val) val)]
         (apply-fn element v))))))

(defn unregister!
  "移除 element 的所有绑定。"
  ([element] (unregister! *default-bind-manager* element))
  ([manager element]
   (swap! manager update :bindings dissoc element)
   ;; 如果没有其他绑定，移除对源 atom 的 watch
   (when (empty? (:bindings @manager))
     (remove-watch (:source-atom @manager) (:watch-key @manager)))))

(defn refresh!
  "强制刷新 element 的所有绑定。"
  ([element] (refresh! *default-bind-manager* element))
  ([manager element]
   (when-let [bindings (get (:bindings @manager) element)]
     (let [source-atom (:source-atom @manager)]
       (doseq [{:keys [path apply-fn transform]} bindings]
         (when-let [val (get-in @source-atom path)]
           (let [v (if transform (transform val) val)]
             (apply-fn element v))))))))

(defn- queue-refresh! [manager element]
  (swap! manager update :batch-queue conj element))

(defn flush-batch! [manager]
  (let [elements (distinct (:batch-queue @manager))]
    (swap! manager assoc :batch-queue (list))
    (doseq [elem elements]
      (refresh! manager elem))))

(defmacro batch-mode
  "在批量模式下执行 body，结束时统一刷新所有绑定。"
  [manager & body]
  `(do
     (swap! ~manager assoc :batch-mode? true :batch-queue (list))
     (try
       ~@body
       (flush-batch! ~manager)
       (finally
         (swap! ~manager assoc :batch-mode? false :batch-queue (list))))))

(defn command-event-handler
  "创建一个事件处理器，执行指定命令，args-fn 返回命令参数。"
  [cmd-id args-fn]
  (fn [& event]
    (let [args (args-fn)]
      (apply cmd/execute-command! cmd-id args))))