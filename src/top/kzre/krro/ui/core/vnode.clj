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
  (map->VNode (merge {:id (str (gensym "vn"))
                      :type type
                      :key nil
                      :props {}
                      :children []
                      :element nil
                      :mounted? false}
                     opts)))