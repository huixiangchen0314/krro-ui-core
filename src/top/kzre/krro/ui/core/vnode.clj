(ns top.kzre.krro.ui.core.vnode
  (:require [top.kzre.krro.ui.core.protocol :as proto]))

(defrecord VNode [id type key props children element hooks]
  proto/IVNode
  (node-id [_] id)
  (node-type [_] type)
  (node-key [_] (or key id))
  (node-props [_] props)
  (node-children [_] children)
  (node-element [_] element)
  (node-hooks [_] @hooks)
  (add-hook! [_ key f] (swap! hooks assoc key f)))

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

(defn edn->vnode [edn]
  (if (string? edn)
    edn
    (when (vector? edn)
      (let [[tag & tail] edn]
        (if (coll? tag)
          ;; 匿名组件：整个向量视为子节点列表，递归解析每一项
          (mapv edn->vnode edn)
          ;; 标准标签
          (let [attrs (when (map? (first tail)) (first tail))
                child-seq (if attrs (rest tail) tail)
                key (:key attrs)
                props (if attrs (dissoc attrs :key) {})
                child-nodes (mapv edn->vnode child-seq)]
            (make-vnode tag :key key :props props :children child-nodes)))))))