(ns top.kzre.krro.ui.core.diff
  "平台无关的虚拟 DOM 增量更新引擎。
   提供一个 diff! 函数，直接在新旧 VNode 树之间执行增量更新（副作用）。
   完全参照 C# VDomDiff 逻辑：PatchInternal + PatchChildren。
   不再生成操作序列。"
  (:require [top.kzre.krro.ui.core.protocol :as proto]
            [top.kzre.krro.ui.core.bind :as bind]))

;; ═══════════════════════════════════════════════════════════
;; 内部辅助
;; ═══════════════════════════════════════════════════════════

(defn- effective-key [vnode]
  (or (proto/node-key vnode) (proto/node-id vnode)))

(defn- cleanup-element [element]
  (bind/unregister! element))

(defn- invoke-mounted [vnode element]
  ;; 生命周期扩展点
  nil)

(defn- invoke-updated [vnode element]
  nil)

(defn- invoke-unmounted [vnode element]
  nil)

(defn- replace-child
  "在父容器中用新元素替换旧元素，保持原索引并清理旧元素。"
  [renderer parent-el old-el new-el]
  (when (and old-el parent-el)
    (let [children (.getChildren ^javafx.scene.Parent parent-el)
          idx (.indexOf children old-el)]
      (if (>= idx 0)
        (do
          (cleanup-element old-el)
          (proto/remove-child renderer parent-el old-el)
          (proto/insert-child renderer parent-el new-el idx))
        (proto/append-child renderer parent-el new-el)))))

(defn- update-properties
  "更新元素属性，包括样式、文本、事件等。委托给工厂的 update-properties。"
  [factory element old-props new-props]
  (when (not= old-props new-props)
    (proto/update-properties factory element old-props new-props)
    (bind/refresh! element)))

;; ═══════════════════════════════════════════════════════════
;; 核心递归 patch
;; ═══════════════════════════════════════════════════════════

(declare patch-children)

(defn- patch-internal
  "对单个节点进行 diff 并执行更新，不检查父节点。"
  [factory renderer parent-el old-node new-node]
  (if (or (not= (proto/node-type old-node) (proto/node-type new-node))
          (nil? (proto/node-element old-node)))
    ;; 类型不同或旧节点无真实元素 -> 创建新元素并替换
    (let [new-el (proto/create-element factory new-node)]
      (replace-child renderer parent-el (proto/node-element old-node) new-el)
      (let [new-node (assoc new-node :element new-el)]
        (invoke-mounted new-node new-el)
        (patch-children factory renderer new-el [] (proto/node-children new-node))
        new-node))
    ;; 类型相同，复用真实元素
    (do
      ;; 继承身份
      (let [new-node (assoc new-node
                       :id (proto/node-id old-node)
                       :element (proto/node-element old-node))]
        (update-properties factory (proto/node-element old-node)
                           (proto/node-props old-node)
                           (proto/node-props new-node))
        (invoke-updated new-node (proto/node-element old-node))
        (patch-children factory renderer (proto/node-element old-node)
                        (proto/node-children old-node)
                        (proto/node-children new-node))
        new-node))))

(defn- patch-children
  "递归比较并更新子节点列表。基于 key 匹配，支持移动、创建、删除。
   完全移植 C# PatchChildren。"
  [factory renderer parent-el old-children new-children]
  (let [old-children (vec (remove nil? old-children))
        new-children (vec (remove nil? new-children))
        old-key-map (reduce (fn [m c] (assoc m (effective-key c) c)) {} old-children)
        old-index (atom 0)
        old-list (atom old-children)
        key-map (atom old-key-map)]
    (doseq [new-idx (range (count new-children))]
      (let [new-child (nth new-children new-idx)
            new-key (effective-key new-child)
            matched (when new-key (get @key-map new-key))]
        (if matched
          (let [current-pos (.indexOf @old-list matched)]
            (swap! old-list #(vec (concat (subvec % 0 current-pos) (subvec % (inc current-pos)))))
            (swap! key-map dissoc new-key)
            (when (< current-pos @old-index) (swap! old-index dec))
            (when (not= current-pos new-idx)
              (when-let [el (proto/node-element matched)]
                (proto/move-child renderer parent-el el new-idx)))
            (patch-internal factory renderer parent-el matched new-child))
          (if (< @old-index (count @old-list))
            (let [old-child (nth @old-list @old-index)]
              (swap! old-index inc)
              (patch-internal factory renderer parent-el old-child new-child))
            (let [new-el (proto/create-element factory new-child)]
              (proto/insert-child renderer parent-el new-el new-idx)
              (let [new-child (assoc new-child :element new-el)]
                (invoke-mounted new-child new-el)
                (patch-children factory renderer new-el [] (proto/node-children new-child))))))))
    (doseq [i (range @old-index (count @old-list))]
      (let [old-child (nth @old-list i)
            old-el (proto/node-element old-child)]
        (when old-el
          (invoke-unmounted old-child old-el)
          (cleanup-element old-el)
          (proto/remove-child renderer parent-el old-el))))))

;; ═══════════════════════════════════════════════════════════
;; 公共入口：diff!
;; ═══════════════════════════════════════════════════════════

(defn diff!
  "比较新旧 VNode 树并直接执行增量更新，返回新的根 VNode。
   root-el 为平台根容器元素，首次渲染时 old-vnode 应为 nil。"
  [factory renderer root-el old-vnode new-vnode]
  (if (nil? old-vnode)
    ;; 首次渲染：创建整棵树并挂载到根容器
    (let [new-el (proto/create-element factory new-vnode)
          new-vnode (assoc new-vnode :element new-el)]
      (proto/append-child renderer root-el new-el)
      (invoke-mounted new-vnode new-el)
      (patch-children factory renderer new-el [] (proto/node-children new-vnode))
      new-vnode)
    ;; 增量更新
    (if (= (proto/node-type old-vnode) (proto/node-type new-vnode))
      (let [result (patch-internal factory renderer root-el old-vnode new-vnode)]
        ;; 如果根节点被替换，patch-internal 内部会处理，但根容器 root-el 是固定的。
        ;; 需要确保新根元素挂载在 root-el 下。
        (when (not= (proto/node-element old-vnode) (proto/node-element result))
          ;; 根节点已替换，旧根元素已被移除，新元素已插入，直接返回
          )
        result)
      ;; 根节点类型不同，完全替换根节点
      (let [new-el (proto/create-element factory new-vnode)
            old-el (proto/node-element old-vnode)]
        (when old-el
          (cleanup-element old-el)
          (proto/remove-child renderer root-el old-el))
        (proto/append-child renderer root-el new-el)
        (let [new-vnode (assoc new-vnode :element new-el)]
          (invoke-mounted new-vnode new-el)
          (patch-children factory renderer new-el [] (proto/node-children new-vnode))
          new-vnode)))))