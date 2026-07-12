(ns top.kzre.krro.ui.core.diff
  "平台无关的虚拟 DOM 增量更新引擎。"
  (:require [top.kzre.krro.ui.core.protocol :as proto]
            [top.kzre.krro.ui.core.bind :as bind]))

;; ═══════════════════════════════════ 辅助函数（不变） ═══

(defn- effective-key [vnode]
  (or (proto/node-key vnode) (proto/node-id vnode)))

(defn- cleanup-element [element]
  (bind/unregister! element))

(defn- invoke-mounted [vnode]
  (when-let [on-mount (:on-mount (proto/node-hooks vnode))]
    (on-mount vnode)))

(defn- invoke-updated [element vnode old-vnode]
  (when-let [on-update (:on-update (proto/node-hooks vnode))]
    (on-update element old-vnode vnode)))

(defn- invoke-unmounted [vnode]
  (when-let [on-unmount (:on-unmount (proto/node-hooks vnode))]
    (on-unmount vnode)))

(defn- replace-child
  [renderer parent-el old-el new-el]
  (when (and old-el parent-el)
    (let [children (.getChildren parent-el)
          idx (.indexOf children old-el)]
      (if (>= idx 0)
        (do
          (cleanup-element old-el)
          (proto/remove-child renderer parent-el old-el)
          (proto/insert-child renderer parent-el new-el idx))
        (proto/append-child renderer parent-el new-el)))))

(defn- update-properties
  [factory element old-props new-props]
  (when (not= old-props new-props)
    (proto/update-properties factory element old-props new-props)
    (bind/refresh! element)))

;; ═══════════════════════════════════ 核心补丁 ═════════

(declare patch-children)

(defn- patch-internal
  [factory renderer frame parent-el old-node new-node]
  (let [old-type (proto/node-type old-node)
        new-type (proto/node-type new-node)
        old-key  (effective-key old-node)
        new-key  (effective-key new-node)
        same-key? (if (and (nil? old-key) (nil? new-key))
                    true
                    (= old-key new-key))]
    (if (or (not= old-type new-type)
            (not same-key?)
            (nil? (proto/node-element old-node)))
      ;; 替换
      (let [old-el (proto/node-element old-node)
            new-el (proto/create-element factory new-node frame)]
        ;; 直接使用协议替换，不再需要 replace-child 辅助函数
        (proto/replace-child renderer parent-el old-el new-el)
        (proto/destroy-element factory old-node frame)
        (let [new-node (assoc new-node :element new-el)]
          (invoke-mounted new-node)
          (let [updated-children (patch-children factory renderer frame new-el
                                                 [] (proto/node-children new-node))]
            (assoc new-node :children updated-children))))
      ;; 复用
      (let [element (proto/node-element old-node)
            new-node (assoc new-node
                       :id (proto/node-id old-node)
                       :element element)]
        (update-properties factory element old-node new-node)
        (invoke-updated element old-node new-node)
        (let [updated-children (patch-children factory renderer frame element
                                               (proto/node-children old-node)
                                               (proto/node-children new-node))]
          (assoc new-node :children updated-children))))))

;; ═══════════════════════════════════ patch-children 返回新子节点向量 ═════

(defn- patch-children
  [factory renderer frame parent-el old-children new-children]
  (let [old-children (vec (remove nil? old-children))
        new-children (vec (remove nil? new-children))
        old-key-map (reduce (fn [m c]
                              (if-let [k (effective-key c)]
                                (assoc m k c)
                                m))
                            {} old-children)
        old-index (atom 0)
        old-list (atom old-children)
        key-map (atom old-key-map)
        new-list (volatile! (transient []))]
    ;; 第一遍：创建/更新/删除，不移动
    (doseq [new-idx (range (count new-children))]
      (let [new-child (nth new-children new-idx)
            new-key (effective-key new-child)
            matched (when new-key (get @key-map new-key))]
        (if matched
          (let [current-pos (.indexOf @old-list matched)]
            (swap! old-list #(vec (concat (subvec % 0 current-pos) (subvec % (inc current-pos)))))
            (swap! key-map dissoc new-key)
            (when (< current-pos @old-index) (swap! old-index dec))
            (vswap! new-list conj! (patch-internal factory renderer frame parent-el matched new-child)))
          (if (< @old-index (count @old-list))
            (let [old-child (nth @old-list @old-index)]
              (swap! old-index inc)
              (when-let [k (effective-key old-child)]
                (swap! key-map dissoc k))
              (vswap! new-list conj! (patch-internal factory renderer frame parent-el old-child new-child)))
            (let [new-el (proto/create-element factory new-child frame)]
              ;; 追加新节点（后续排序）
              (proto/append-child renderer parent-el new-el)
              (let [new-child (assoc new-child :element new-el)]
                (invoke-mounted new-child)
                (let [updated (assoc new-child :children
                                               (patch-children factory renderer frame new-el
                                                               [] (proto/node-children new-child)))]
                  (vswap! new-list conj! updated))))))))
    ;; 删除剩余的旧节点
    (doseq [i (range @old-index (count @old-list))]
      (let [old-child (nth @old-list i)
            old-el (proto/node-element old-child)]
        (when old-el
          (invoke-unmounted old-child)
          (cleanup-element old-el)
          (proto/remove-child renderer parent-el old-el)
          (proto/destroy-element factory old-child frame))))
    ;; 第二遍：统一排序（根据 final-list 的顺序移动每个节点到正确位置）
    (let [final-list (persistent! @new-list)]
      (doseq [i (range (count final-list))]
        (when-let [el (proto/node-element (nth final-list i))]
          (proto/move-child renderer parent-el el i)))
      final-list)))

;; ═══════════════════════════════════ 入口 diff! ═════════

(defn diff!
  [factory renderer frame root-el old-vnode new-vnode]
  (if (nil? old-vnode)
    ;; 首次渲染
    (let [new-el (proto/create-element factory new-vnode frame)
          new-vnode (assoc new-vnode :element new-el)]
      (proto/append-child renderer root-el new-el)
      (invoke-mounted new-vnode)
      (let [children (patch-children factory renderer frame new-el [] (proto/node-children new-vnode))]
        (assoc new-vnode :children children)))
    ;; 增量更新
    (if (= (proto/node-type old-vnode) (proto/node-type new-vnode))
      (let [result (patch-internal factory renderer frame root-el old-vnode new-vnode)]
        result)
      ;; 根节点类型不同，完全替换
      (let [new-el (proto/create-element factory new-vnode frame)
            old-el (proto/node-element old-vnode)]
        (when old-el
          (cleanup-element old-el)
          (proto/remove-child renderer root-el old-el))
        (proto/append-child renderer root-el new-el)
        (let [new-vnode (assoc new-vnode :element new-el)]
          (invoke-mounted new-vnode)
          (let [children (patch-children factory renderer frame new-el [] (proto/node-children new-vnode))]
            (assoc new-vnode :children children)))))))