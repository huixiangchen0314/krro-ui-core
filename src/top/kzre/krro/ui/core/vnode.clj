(ns top.kzre.krro.ui.core.vnode
  (:require [top.kzre.krro.ui.core.protocol :as proto]))

(defrecord VNode [id type key props children element]
  proto/IVNode
  (node-id [_] id)
  (node-type [_] type)
  (node-key [_] (or key id))
  (node-props [_] props)
  (node-children [_] children)
  (node-element [_] element))

(defn make-vnode
  "创建一个纯数据的虚拟节点。可接受 :key, :props, :children。"
  [type & {:as opts}]
  (map->VNode (merge {:id (str (gensym "vnode"))
                      :type type
                      :key nil
                      :props {}
                      :children []
                      :element nil}
                     opts)))


(defn edn->vnode
  "将 EDN 元素递归转换为 VNode 树。"
  [edn]
  (if (string? edn)
    edn
    (when (vector? edn)
      (let [[tag & tail] edn
            attrs (when (map? (first tail)) (first tail))
            child-seq (if attrs (rest tail) tail)   ;; 使用 clojure.core/rest 处理 tail
            key (:key attrs)
            props (if attrs (dissoc attrs :key) {})
            child-nodes (mapv edn->vnode child-seq)]
        (make-vnode tag :key key :props props :children child-nodes)))))