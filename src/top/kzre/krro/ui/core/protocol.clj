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
  (node-element [this] "返回关联的平台元素，若尚未挂载则为 nil")
  (node-hooks [this] "返回生命周期钩子 map，包含 :on-mount, :on-update, :on-unmount 等")
  (add-hook! [this key f] "动态添加生命周期钩子。key 为 :on-mount/:on-update/:on-unmount"))

(defprotocol IElementFactory
  "平台元素工厂，负责根据虚拟节点创建真实元素，以及更新其属性。
   这些操作是纯的，不产生副作用（属性更新除外）。"
  (create-element [this vnode frame]
    "根据 vnode 的标签和属性创建真实平台元素，返回该元素。")
  (update-properties [this element old-vnode new-vnode]
    "更新已存在元素的属性。返回更新后的元素（可能不变）。")
  (destroy-element [this vnode frame]
    "当虚拟节点被卸载时调用，用于释放平台资源、解除绑定等。"))

(defprotocol INodePatcher
  (append-child [this parent child])
  (insert-child [this parent child index])
  (remove-child [this parent child])
  (replace-child [this parent old new])   ;; 平台自己实现替换逻辑
  (move-child [this parent child target-index]))