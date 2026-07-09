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
  "创建 VNode。hooks 参数可传普通 map，内部会包装为 atom。"
  [type & {:as opts}]
  (let [initial-hooks (or (:hooks opts) {})
        hooks-atom (atom (if (map? initial-hooks) initial-hooks {}))]
    (map->VNode (merge {:id (str (gensym "vnode"))
                        :type type
                        :key nil
                        :props {}
                        :children []
                        :element nil
                        :hooks hooks-atom}
                       (dissoc opts :hooks)))))


(defn event
  [props event]
  (let [shot-key (keyword (str "on-" (name event)))]
    (or (shot-key props)
        (get-in props [:on event]))))

(defn edn->vnode [edn]
  (if (string? edn)
    edn
    (when (vector? edn)
      (let [[tag & tail] edn]
        (if (vector? tag)
          ;; 匿名组件：递归解析这个向量，直接得到 VNode
          (edn->vnode tag)
          ;; 标准标签
          (let [attrs (when (map? (first tail)) (first tail))
                child-seq (if attrs (rest tail) tail)
                key (:key attrs)
                props (if attrs (dissoc attrs :key) {})
                child-nodes (mapv edn->vnode child-seq)]
            (make-vnode tag :key key :props props :children child-nodes)))))))