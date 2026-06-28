(ns top.kzre.krro.ui.core.diff
  "平台无关的虚拟 DOM diff 算法及执行器。
   与绑定系统集成，在属性更新后自动刷新绑定。"
  (:require [top.kzre.krro.ui.core.protocol :as proto]
            [top.kzre.krro.ui.core.bind :as bind]))

;; ── 有效匹配键 ──────────────────────────────
(defn- effective-key [vnode]
  (or (proto/node-key vnode) (proto/node-id vnode)))

;; ── 属性 diff ────────────────────────────────
(defn- diff-props [old-props new-props]
  (when (not= old-props new-props)
    {:type :update-props :old-props old-props :new-props new-props}))

;; ── 子节点 diff（生成操作序列）───────────────
(declare diff-node)
(defn- diff-children [old-children new-children parent-vnode]
  (let [old-children (vec (remove nil? old-children))
        new-children (vec (remove nil? new-children))
        old-key-map (reduce (fn [m c] (assoc m (effective-key c) c)) {} old-children)
        ;; reduce 遍历新子节点，累加器 [old-list key-map ops]
        [old-list _ ops]
        (reduce
          (fn [[old-list key-map ops] new-idx]
            (let [new-child (nth new-children new-idx)
                  new-key (effective-key new-child)
                  matched (when new-key (get key-map new-key))]
              (if matched
                ;; key 匹配：从 old-list 中移除匹配项，更新 key-map，生成可能移动操作，递归 diff
                (let [current-pos (.indexOf old-list matched)
                      old-list (vec (concat (subvec old-list 0 current-pos) (subvec old-list (inc current-pos))))
                      key-map (dissoc key-map new-key)
                      move-op (when (not= current-pos new-idx)
                                {:type :move-child :parent-vnode parent-vnode :child-vnode matched :target-index new-idx})
                      new-ops (if move-op (conj ops move-op) ops)
                      new-ops (into new-ops (diff-node matched new-child))]
                  [old-list key-map new-ops])
                ;; 无 key 匹配
                (if (seq old-list)
                  ;; 按顺序匹配：取第一个旧子节点
                  (let [old-child (first old-list)
                        old-list (subvec old-list 1)
                        new-ops (into ops (diff-node old-child new-child))]
                    [old-list key-map new-ops])
                  ;; 创建新节点
                  (let [new-ops (conj ops {:type :create-child :parent-vnode parent-vnode :child-vnode new-child :index new-idx})]
                    [old-list key-map new-ops])))))
          [old-children old-key-map []]
          (range (count new-children)))
        ;; 删除剩余旧节点
        delete-ops (mapv (fn [old-child] {:type :delete-child :parent-vnode parent-vnode :child-vnode old-child}) old-list)]
    (into ops delete-ops)))

(defn- diff-node [old-vnode new-vnode]
  (cond
    ;; 两者均为 nil，无操作
    (and (nil? old-vnode) (nil? new-vnode))
    []
    (nil? new-vnode)
    [{:type :delete-child :parent-vnode nil :child-vnode old-vnode}]
    (nil? old-vnode)
    [{:type :create-child :parent-vnode nil :child-vnode new-vnode :index nil}]
    (not= (proto/node-type old-vnode) (proto/node-type new-vnode))
    [{:type :replace :old-vnode old-vnode :new-vnode new-vnode}]
    :else
    (let [old-props (proto/node-props old-vnode)
          new-props (proto/node-props new-vnode)
          props-op (when-let [op (diff-props old-props new-props)]
                     [(assoc op :vnode old-vnode)])
          child-ops (diff-children (proto/node-children old-vnode)
                                   (proto/node-children new-vnode)
                                   old-vnode)]
      (concat props-op child-ops))))

(defn diff
  "纯函数：比较两个 VNode 树，返回操作向量。"
  [old-vnode new-vnode]
  (diff-node old-vnode new-vnode))

;; ── 操作执行器（集成绑定）─────────────────────
(defn execute-ops!
  "遍历操作序列，调用 factory 和 renderer 执行平台更新。
   在 :update-props 后自动刷新绑定，在删除/替换时自动解绑。"
  [factory renderer root-el ops]
  (doseq [{:keys [type] :as op} ops]
    (case type
      :create-child
      (let [{:keys [parent-vnode child-vnode index]} op
            parent-el (or (some-> parent-vnode proto/node-element) root-el)
            new-el (proto/create-element factory child-vnode)]
        (if index
          (proto/insert-child renderer parent-el new-el index)
          (proto/append-child renderer parent-el new-el))
        (let [child-vnode (assoc child-vnode :element new-el)]
          (when-let [child-ops (seq (diff nil child-vnode))]
            (execute-ops! factory renderer new-el child-ops))))

      :delete-child
      (let [{:keys [parent-vnode child-vnode]} op
            parent-el (or (some-> parent-vnode proto/node-element) root-el)
            child-el (proto/node-element child-vnode)]
        (when child-el
          (bind/unregister! child-el)   ;; 移除绑定
          (proto/remove-child renderer parent-el child-el)))

      :update-props
      (let [{:keys [vnode old-props new-props]} op
            el (proto/node-element vnode)]
        (when el
          (proto/update-properties factory el old-props new-props)
          ;; 刷新绑定，确保属性更新后 UI 反映项目数据
          (bind/refresh! el)))

      :replace
      (let [{:keys [old-vnode new-vnode]} op
            parent-el (or (some-> old-vnode proto/node-element) root-el)
            old-el (proto/node-element old-vnode)
            new-el (proto/create-element factory new-vnode)]
        (when old-el
          (bind/unregister! old-el))   ;; 移除旧元素绑定
        (if (and parent-el old-el)
          (proto/replace-child renderer parent-el old-el new-el)
          (when root-el
            (proto/append-child renderer root-el new-el)))
        (let [new-vnode (assoc new-vnode :element new-el)]
          (when-let [child-ops (seq (diff nil new-vnode))]
            (execute-ops! factory renderer new-el child-ops))))

      :move-child
      (let [{:keys [parent-vnode child-vnode target-index]} op
            parent-el (or (some-> parent-vnode proto/node-element) root-el)
            child-el (proto/node-element child-vnode)]
        (when (and parent-el child-el)
          (proto/move-child renderer parent-el child-el target-index)))
      nil)))

(defn patch!
  "比较新旧 VNode，生成操作序列并批量执行。
   批量模式确保绑定刷新延迟到所有操作完成后统一执行。"
  [factory renderer root-el old-vnode new-vnode]
  (let [ops (diff old-vnode new-vnode)]
    (bind/batch-mode bind/*default-bind-manager*
                     (execute-ops! factory renderer root-el ops))
    new-vnode))