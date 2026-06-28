(ns top.kzre.krro.ui.core.diff
  "平台无关的虚拟 DOM diff 算法及执行器。
   操作序列由构造器生成，执行器通过统一方式解构。"
  (:require [top.kzre.krro.ui.core.protocol :as proto]))

;; ═══════════════════════════════════════════════════════════
;; 操作构造器
;; ═══════════════════════════════════════════════════════════

(defn op-create-child
  "创建新子节点：将其挂载到 parent-vnode 下，位置 index。"
  [parent-vnode child-vnode index]
  {:type :create-child
   :parent-vnode parent-vnode
   :child-vnode child-vnode
   :index index})

(defn op-delete-child
  "删除子节点：从 parent-vnode 下移除 child-vnode。"
  [parent-vnode child-vnode]
  {:type :delete-child
   :parent-vnode parent-vnode
   :child-vnode child-vnode})

(defn op-update-props
  "更新节点属性。"
  [vnode old-props new-props]
  {:type :update-props
   :vnode vnode
   :old-props old-props
   :new-props new-props})

(defn op-replace
  "替换节点：用 new-vnode 替代 old-vnode。"
  [old-vnode new-vnode]
  {:type :replace
   :old-vnode old-vnode
   :new-vnode new-vnode})

(defn op-move-child
  "移动子节点到新位置。"
  [parent-vnode child-vnode target-index]
  {:type :move-child
   :parent-vnode parent-vnode
   :child-vnode child-vnode
   :target-index target-index})

;; ═══════════════════════════════════════════════════════════
;; Diff 核心（使用构造器）
;; ═══════════════════════════════════════════════════════════

(defn- effective-key [vnode]
  (or (proto/node-key vnode) (proto/node-id vnode)))

(defn- diff-props [old-props new-props]
  (when (not= old-props new-props)
    (op-update-props nil old-props new-props))) ;; vnode 稍后填充
(declare diff-children)
(defn- diff-node [old-vnode new-vnode]
  (cond
    (nil? new-vnode)
    [(op-delete-child nil old-vnode)]

    (nil? old-vnode)
    [(op-create-child nil new-vnode nil)]

    (not= (proto/node-type old-vnode) (proto/node-type new-vnode))
    [(op-replace old-vnode new-vnode)]

    :else
    (let [old-props (proto/node-props old-vnode)
          new-props (proto/node-props new-vnode)
          props-ops (when-let [op (diff-props old-props new-props)]
                      [(assoc op :vnode old-vnode)]) ;; 填充 vnode
          child-ops (diff-children (proto/node-children old-vnode)
                                   (proto/node-children new-vnode)
                                   old-vnode)]
      (concat props-ops child-ops))))

(defn- diff-children [old-children new-children parent-vnode]
  (let [old-children (vec (remove nil? old-children))
        new-children (vec (remove nil? new-children))
        old-key-map (reduce (fn [m c] (assoc m (effective-key c) c)) {} old-children)
        [old-list _ ops]
        (reduce
          (fn [[old-list key-map ops] new-idx]
            (let [new-child (nth new-children new-idx)
                  new-key (effective-key new-child)
                  matched (when new-key (get key-map new-key))]
              (if matched
                (let [current-pos (.indexOf old-list matched)
                      old-list (vec (concat (subvec old-list 0 current-pos) (subvec old-list (inc current-pos))))
                      key-map (dissoc key-map new-key)
                      ops (cond-> ops
                                  (not= current-pos new-idx)
                                  (conj (op-move-child parent-vnode matched new-idx))
                                  :always
                                  (into (diff-node matched new-child)))]
                  [old-list key-map ops])
                (if (seq old-list)
                  (let [old-child (first old-list)
                        old-list (subvec old-list 1)
                        ops (into ops (diff-node old-child new-child))]
                    [old-list key-map ops])
                  (let [ops (conj ops (op-create-child parent-vnode new-child new-idx))]
                    [old-list key-map ops])))))
          [old-children old-key-map []]
          (range (count new-children)))
        delete-ops (mapv (fn [old-child] (op-delete-child parent-vnode old-child)) old-list)]
    (into ops delete-ops)))

(defn diff
  "纯函数：比较两个 VNode 树，返回由构造器生成的操作向量。"
  [old-vnode new-vnode]
  (diff-node old-vnode new-vnode))

;; ═══════════════════════════════════════════════════════════
;; 通用操作执行器
;; ═══════════════════════════════════════════════════════════

(defn execute-ops!
  "遍历操作序列，调用 factory 和 renderer 执行平台更新。
   操作中的字段通过构造器识别。"
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
          (proto/remove-child renderer parent-el child-el)))

      :update-props
      (let [{:keys [vnode old-props new-props]} op
            el (proto/node-element vnode)]
        (when el
          (proto/update-properties factory el old-props new-props)))

      :replace
      (let [{:keys [old-vnode new-vnode]} op
            parent-el (or (some-> old-vnode proto/node-element) root-el) ;; 简化：父元素为 root-el
            old-el (proto/node-element old-vnode)
            new-el (proto/create-element factory new-vnode)]
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
  "比较新旧 VNode，生成操作序列并执行。返回更新后的根 VNode。"
  [factory renderer root-el old-vnode new-vnode]
  (let [ops (diff old-vnode new-vnode)]
    (execute-ops! factory renderer root-el ops)
    new-vnode))