(ns top.kzre.krro.ui.core.diff
  "平台无关的虚拟 DOM 增量更新引擎。"
  (:require
    [top.kzre.krro.ui.core.bind :as bind]
    [top.kzre.krro.ui.core.component :as component]
    [top.kzre.krro.ui.core.protocol :as proto]
    [top.kzre.krro.ui.core.vnode :as vnode]))

;; ═══════════════════════════════════ 辅助函数（不变） ═══

(defn- effective-key [vnode]
  (or (proto/node-key vnode) (proto/node-id vnode)))






;; TODO 由渲染器清理，我们不要动
(defn- ^:deprecated cleanup-element [element]
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

(defn- update-properties
  [factory element old-props new-props]
  (when (not= old-props new-props)
    (proto/update-properties factory element old-props new-props)))

;; ═══════════════════════════════════ 核心补丁 ═════════

(declare patch-children)


(defn- resolve-component [old-vnode new-vnode frame]
  (if-let [{:keys [factory]} (component/get-component (proto/node-type new-vnode))]
    (if (and old-vnode new-vnode
             ;; 存在旧的渲染
             (:render old-vnode)
             ;; 节点类型可能因为组件展开而不同, 我们只比较key
             (= (effective-key old-vnode) (effective-key new-vnode)))
      ;; ── 复用：闭包状态、钩子、element 全部继承自旧 vnode ──
      (let [render  (:render old-vnode)
            props   (proto/node-props new-vnode)]
        (merge (render old-vnode props)
               (select-keys old-vnode [:element])))
      ;; ── 新建：调用工厂，创建状态和钩子 ──
      (let [node-id    (proto/node-id new-vnode)
            init-props (proto/node-props new-vnode)
            {:keys [render on-mount on-update on-unmount]} (factory init-props)
            render-fn
            (fn [old-node props]
              (let [new-node (->  (render props frame)
                              (vnode/edn->vnode node-id))]
                (when  on-mount  (proto/add-hook! new-node :on-mount on-mount))
                (when  on-update  (proto/add-hook! new-node :on-update on-update))
                (let [node' (resolve-component old-node new-node frame)]
                  (when  on-unmount  (proto/add-hook! node' :on-unmount on-unmount))
                  node')))
            vnode (render-fn old-vnode init-props)]
        (assoc vnode :render render-fn)))
    ;; 不是组件
    new-vnode))


(defn- patch-internal
  [factory renderer frame parent-el old-vnode new-vnode]
  (let [new-vnode (resolve-component old-vnode new-vnode frame)
        old-type (proto/node-type old-vnode)
        new-type (proto/node-type new-vnode)
        old-key  (effective-key old-vnode)
        new-key  (effective-key new-vnode)
        same-key? (if (and (nil? old-key) (nil? new-key))
                    true
                    (= old-key new-key))]
    (if (or (not= old-type new-type)
            (not same-key?)
            (nil? (proto/node-element old-vnode)))
      ;; 替换
      (let [old-el (proto/node-element old-vnode)
            new-el (proto/create-element factory new-vnode frame)]
        ;; 直接使用协议替换，不再需要 replace-child 辅助函数
        (proto/replace-child renderer parent-el old-el new-el)
        (proto/destroy-element factory old-vnode frame)
        (let [new-node (assoc new-vnode :element new-el)]
          (invoke-mounted new-node)
          (let [updated-children (patch-children factory renderer frame new-el
                                                 [] (proto/node-children new-node))]
            (assoc new-node :children updated-children))))
      ;; 复用
      (let [element (proto/node-element old-vnode)
            new-node (assoc new-vnode
                       :id (proto/node-id old-vnode)
                       :element element)]
        (update-properties factory element old-vnode new-node)
        (invoke-updated element old-vnode new-node)
        (let [updated-children (patch-children factory renderer frame element
                                               (proto/node-children old-vnode)
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
  (let [new-vnode (resolve-component old-vnode new-vnode frame)]
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
              (assoc new-vnode :children children))))))))