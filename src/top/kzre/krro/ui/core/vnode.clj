(ns top.kzre.krro.ui.core.vnode
  (:require [top.kzre.krro.ui.core.protocol :as proto]))

(defrecord VNode [id type key props children
                  element hooks
                  ;; 组件渲染函数
                  render]
  proto/IVNode
  (node-id [_] id)
  (node-type [_] type)
  (node-key [_] (or key id))
  (node-props [_] props)
  (node-children [_] children)
  (node-element [_] element)
  (node-hooks [_] @hooks)
  (add-hook! [this k f] (swap! hooks assoc k f) this))

(defn make-vnode
  "创建 VNode。hooks 参数可传普通 map，内部会包装为 atom。
   其他参数 :key, :props, :children 等会覆盖默认值。"
  [type & {:as opts}]
  (let [initial-hooks (or (:hooks opts) {})
        hooks-atom (atom initial-hooks)
        ;; 移除 :hooks 避免重复，其余参数直接覆盖默认值
        opts' (dissoc opts :hooks)]
    (map->VNode (merge {:id nil
                        :type type
                        :key nil
                        :props {}
                        :children []
                        :element nil
                        :hooks hooks-atom}
                       opts'))))

(defn project-binding
  "项目绑定"
  [props]
  (:bind props))

(defn frame-param-binding
  "Frame param 绑定"
  [props]
  (:bindf props))

(defn event
  [props event]
  (let [shot-key (keyword (str "on-" (name event)))]
    (or (get props shot-key)
        (get-in props [:on event]))))
(defn has-event?
  "判断 props 中是否包含事件属性（以 :on- 开头的键）。"
  [props]
  (some #(-> % name (.startsWith "on-")) (keys props)))

;; 组件解析
(defn edn->vnode
  "将 EDN 描述解析为 VNode 树，自动分配身份路径 (id)。
   身份路径用于 diff 时复用节点（保留闭包状态）。

   身份路径规则：
   - 根节点：id = []
   - 有显式 :key 的子节点：id = (conj parent-id key-value)
   - 无 :key 的子节点：id = (conj parent-id 索引)

   参数：
     - edn:      EDN 描述
     - identity: 当前节点的身份路径（由父级计算传入），根节点为 []"
  ([edn] (edn->vnode edn []))
  ([edn identity]
   (cond
     (string? edn) edn
     (not (vector? edn)) edn
     :else
     (let [[tag & tail] edn]
       (cond
         ;; 匿名组件：整个向量视为子节点列表
         (coll? tag)
         (mapv (fn [child idx]
                 (edn->vnode child (conj identity idx)))
               edn (range))

         ;; 标准标签
         :else
         (let [attrs        (when (map? (first tail)) (first tail))
               child-seq    (if attrs (rest tail) tail)
               explicit-key (:key attrs)
               props        (if attrs (dissoc attrs :key) {})
               ;; 为每个子节点计算身份：有 :key 用 key，否则用索引
               child-nodes  (mapv (fn [child idx]
                                    (let [child-attrs (when (and (vector? child)
                                                                 (map? (second child)))
                                                        (second child))
                                          child-key   (:key child-attrs)
                                          child-id    (conj identity
                                                            (or child-key
                                                                (:id child-attrs)
                                                                idx))]
                                      (edn->vnode child child-id)))
                                  child-seq (range))]
           (make-vnode tag
                       :id       identity
                       :key      explicit-key
                       :props    props
                       :children child-nodes)))))))