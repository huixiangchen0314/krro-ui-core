(ns top.kzre.krro.ui.core.bind
  "平台无关的数据绑定管理器。
   监听项目原子路径变化，并调用渲染器提供的 apply-fn 更新控件。
   与 diff 执行器协作，保证 UI 与项目数据同步。"
  (:require [top.kzre.krro.core.project :as proj]
            [top.kzre.krro.core.command :as cmd]))

(defn- make-state []
  (atom {:bindings {}
         :watch-key (keyword (str "bind-manager-" (gensym)))
         :batch-queue (list)
         :batch-mode? false}))

(defn create-bind-manager [] (make-state))
(def ^:dynamic *default-bind-manager* (create-bind-manager))
(defn- transform? [val f] (if f (f val) val))

(defn- watch-callback [state]
  (fn [_ _ old new]
    (doseq [[element bindings] (:bindings @state)]
      (doseq [{:keys [path apply-fn transform]} bindings]
        (let [old-val (get-in old path)
              new-val (-> (get-in new path) (transform? transform))]
          (when (not= old-val new-val)
            (apply-fn element new-val)))))))


(defn register!
  "为 element 注册一个数据绑定。
   opts 可选 :transform (fn [val] new-val)"
  ([element bind-path apply-fn]
   (register! *default-bind-manager* element bind-path apply-fn nil))
  ([manager element bind-path apply-fn]
   (register! manager element bind-path apply-fn nil))
  ([manager element bind-path apply-fn opts]
   (when (empty? (:bindings @manager))
     (add-watch proj/project (:watch-key @manager) (watch-callback manager)))
   (swap! manager update-in [:bindings element]
          (fnil conj [])
          (merge {:path bind-path :apply-fn apply-fn} opts))
   (when-let [val (get-in @proj/project bind-path)]
     (let [v (if-let [f (:transform opts)] (f val) val)]
       (apply-fn element v)))))

(defn unregister!
  ([element] (unregister! *default-bind-manager* element))
  ([manager element]
   (swap! manager update :bindings dissoc element)
   (when (empty? (:bindings @manager))
     (remove-watch proj/project (:watch-key @manager)))))

(defn refresh!
  ([element] (refresh! *default-bind-manager* element))
  ([manager element]
   (when-let [bindings (get (:bindings @manager) element)]
     (doseq [{:keys [path apply-fn transform]} bindings]
       (when-let [val (get-in @proj/project path)]
         (let [v (if transform (transform val) val)]
           (apply-fn element v)))))))

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

(defn command-event-handler [cmd-id args-fn]
  (fn [& event]
    (let [args (args-fn)]
      (apply cmd/execute-command! cmd-id args))))