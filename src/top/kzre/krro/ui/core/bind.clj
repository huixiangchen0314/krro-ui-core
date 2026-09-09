(ns top.kzre.krro.ui.core.bind
  "平台无关的数据绑定管理器。
   支持绑定到任意 atom（默认绑定到全局项目 atom）。
   监听源 atom 路径变化，并调用渲染器提供的 apply-fn 更新控件。
   与 diff 执行器协作，保证 UI 与数据同步。"
  (:require
    [top.kzre.krro.core.project :as proj]))

;; ═══════════════════════════════════════════
;; 内部状态与默认实例
;; ═══════════════════════════════════════════

(defn- make-bind-ctx [source-atom]
  (atom {:bindings {}                                          ;; element → [binding-spec]
         :watch-key (keyword (str "bind-manager-" (gensym)))   ;; 用于 add-watch 的唯一键
         :source-atom source-atom                              ;; 数据源 atom
         :batch-queue (list)
         :batch-mode? false}))

(defn create [source-atom] (make-bind-ctx source-atom))

(def ^:deprecated create-bind-manager create)

(def ^:dynamic *default-bind-manager* (create proj/project))

;; ═══════════════════════════════════════════
;; 内部辅助
;; ═══════════════════════════════════════════

(defn- resolve-value
  "根据 getter 或 path 从 source 中提取原始值。"
  [source getter path]
  (or (when getter (getter source))
      (when path (get-in source path))
      source))

(defn- apply-binding-spec!
  "根据绑定规范 spec 和 element，从 source 中取值并应用到 element。
   若原始值为 nil 则跳过，否则经过 transform 后调用 apply-fn。"
  [{:keys [path getter apply-fn ]} element source]
  (let [value (resolve-value source getter path)]
    (when (some? value)
      (apply-fn element value))))

(defn- watch-callback [manager]
  (fn [_ _ old new]
    (doseq [[element bindings] (:bindings @manager)]
      (doseq [spec bindings]
        (let [{:keys [getter path]} spec
              old-val (resolve-value old getter path)
              new-val (resolve-value new getter path)]
          (when (not= old-val new-val)
            (apply-binding-spec! spec element new)))))))

;; ═══════════════════════════════════════════
;; 公共 API
;; ═══════════════════════════════════════════

(defn register!
  [bind-ctx element apply-fn & {:keys [getter path]}]
  (let [{:keys [source-atom bindings watch-key]} @bind-ctx
        spec   (cond-> {:apply-fn apply-fn}
                       getter (assoc :getter getter)
                       path   (assoc :path path))]
    (when (empty? bindings)
      (add-watch source-atom watch-key (watch-callback bind-ctx)))
    (swap! bind-ctx update-in [:bindings element] (fnil conj []) spec)
    ;; 立即同步当前值
    (apply-binding-spec! spec element @source-atom)))

(defn unregister!
  ([element] (unregister! *default-bind-manager* element))
  ([bind-ctx element]
   (let [{:keys [source-atom watch-key]} @bind-ctx]
     (swap! bind-ctx update :bindings dissoc element)
     (when (empty? (:bindings @bind-ctx))
       (remove-watch source-atom watch-key)))))

(defn refresh!
  ([element] (refresh! *default-bind-manager* element))
  ([bind-ctx element]
   (let [{:keys [bindings source-atom]} @bind-ctx]
     (when-let [bindings (get bindings element)]
       (doseq [spec bindings]
         (apply-binding-spec! spec element @source-atom))))))
