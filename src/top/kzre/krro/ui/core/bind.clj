(ns top.kzre.krro.ui.core.bind
  "平台无关的数据绑定管理器。
   支持绑定到任意 atom（默认绑定到全局项目 atom）。
   监听源 atom 路径变化，并调用渲染器提供的 apply-fn 更新控件。
   与 diff 执行器协作，保证 UI 与数据同步。"
  (:require
    [top.kzre.krro.core.command :as cmd]
    [top.kzre.krro.core.project :as proj]
    [top.kzre.krro.ui.core.vnode :as vnode]))

;; ═══════════════════════════════════════════
;; 内部状态与默认实例
;; ═══════════════════════════════════════════

(defn- make-state [source-atom]
  (atom {:bindings {}                                          ;; element → [binding-spec]
         :watch-key (keyword (str "bind-manager-" (gensym)))   ;; 用于 add-watch 的唯一键
         :source-atom source-atom                              ;; 数据源 atom
         :batch-queue (list)
         :batch-mode? false}))

(defn create-bind-manager
  ([] (make-state proj/project))
  ([source-atom] (make-state source-atom)))

(def ^:dynamic *default-bind-manager*
  (create-bind-manager))

;; ═══════════════════════════════════════════
;; 内部辅助
;; ═══════════════════════════════════════════

(defn- transform? [val f] (if f (f val) val))

(defn- resolve-value
  "根据 getter 或 path 从 source 中提取原始值。"
  [source getter path]
  (if getter (getter source) (get-in source path)))

(defn- apply-binding-spec!
  "根据绑定规范 spec 和 element，从 source 中取值并应用到 element。
   若原始值为 nil 则跳过，否则经过 transform 后调用 apply-fn。"
  [{:keys [path getter apply-fn transform]} element source]
  (let [raw (resolve-value source getter path)]
    (when (some? raw)
      (let [v (if transform (transform raw) raw)]
        (apply-fn element v)))))

(defn- watch-callback [state]
  (fn [_ _ old new]
    (doseq [[element bindings] (:bindings @state)]
      (doseq [spec bindings]
        (let [{:keys [getter path transform]} spec
              old-val (resolve-value old getter path)
              new-val (transform? (resolve-value new getter path) transform)]
          (when (not= old-val new-val)
            (apply-binding-spec! spec element new)))))))

;; ═══════════════════════════════════════════
;; 公共 API
;; ═══════════════════════════════════════════

(defn register!
  ([manager element bind-path apply-fn] (register! manager element bind-path apply-fn nil))
  ([manager element bind-path apply-fn opts]
   (let [state manager
         source-atom (:source-atom @state)
         getter (:getter opts)
         path   (when-not getter bind-path)
         spec   (cond-> {:apply-fn apply-fn}
                        getter (assoc :getter getter)
                        path   (assoc :path path)
                        (:transform opts) (assoc :transform (:transform opts)))]
     (when (empty? (:bindings @state))
       (add-watch source-atom (:watch-key @state) (watch-callback state)))
     (swap! state update-in [:bindings element] (fnil conj []) spec)
     ;; 立即同步当前值
     (apply-binding-spec! spec element @source-atom))))

(defn unregister!
  ([element] (unregister! *default-bind-manager* element))
  ([manager element]
   (swap! manager update :bindings dissoc element)
   (when (empty? (:bindings @manager))
     (remove-watch (:source-atom @manager) (:watch-key @manager)))))

(defn refresh!
  ([element] (refresh! *default-bind-manager* element))
  ([manager element]
   (when-let [bindings (get (:bindings @manager) element)]
     (let [source-atom (:source-atom @manager)]
       (doseq [spec bindings]
         (apply-binding-spec! spec element @source-atom))))))

(defn- queue-refresh! [manager element]
  (swap! manager update :batch-queue conj element))

(defn flush-batch! [manager]
  (let [elements (distinct (:batch-queue @manager))]
    (swap! manager assoc :batch-queue (list))
    (doseq [elem elements]
      (refresh! manager elem))))

(defmacro batch-mode
  [manager & body]
  `(do
     (swap! ~manager assoc :batch-mode? true :batch-queue (list))
     (try
       ~@body
       (flush-batch! ~manager)
       (finally
         (swap! ~manager assoc :batch-mode? false :batch-queue (list))))))

(defn command-event-handler
  [cmd-id args-fn]
  (fn [& event]
    (let [args (args-fn)]
      (apply cmd/execute-command! cmd-id args))))