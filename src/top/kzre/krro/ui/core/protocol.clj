(ns top.kzre.krro.ui.core.protocol
  "平台无关的 UI 核心协议。
   遵循函数式设计：虚拟节点是纯数据，渲染器执行所有副作用。
   Diff 引擎基于这些协议操作虚拟树，完全与平台解耦。")

(defprotocol IVNode
  "虚拟 DOM 节点，纯数据描述。实现者通常是 defrecord。"
  (node-id [this] "返回唯一实例 ID，自动生成，用于 diff 复用")
  (node-type [this] "返回标签关键字，如 :box, :button")
  (node-key [this] "返回可选的稳定标识，来自 :key 属性，用于跨渲染周期匹配")
  (node-props [this] "返回属性 map")
  (node-children [this] "返回子节点向量，元素为 IVNode")
  (node-element [this] "返回关联的平台元素，若尚未挂载则为 nil"))

(defprotocol IElementFactory
  "平台元素工厂，负责根据虚拟节点创建真实元素，以及更新其属性。
   这些操作是纯的，不产生副作用（属性更新除外）。"
  (create-element [this vnode]
    "根据 vnode 的标签和属性创建真实平台元素，返回该元素。")
  (update-properties [this element old-props new-props]
    "更新已存在元素的属性。返回更新后的元素（可能不变）。"))

(defprotocol IRenderer
  "平台渲染器，负责将虚拟 DOM 树挂载到平台视图，并执行所有变更。
   方法接受父平台元素和虚拟节点，执行相应的副作用（添加、删除、移动等）。"
  (append-child [this parent-element vnode]
    "为 vnode 创建平台元素，追加到 parent-element 下，返回更新后的 vnode（含 element）。")
  (insert-child [this parent-element vnode index]
    "在指定索引插入子节点。")
  (remove-child [this parent-element vnode]
    "移除 vnode 对应的平台元素。")
  (replace-child [this parent-element old-vnode new-vnode]
    "用新虚拟节点替换旧虚拟节点对应的平台元素。")
  (move-child [this parent-element vnode target-index]
    "将 vnode 的平台元素移动到 target-index 位置。"))