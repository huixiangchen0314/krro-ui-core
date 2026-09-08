(ns top.kzre.krro.ui.core.core
  "krro-ui-core 公共入口。使用 :as 别名简化重导出，一次引入即可使用所有稳定符号。"
  (:require [top.kzre.krro.ui.core.bind :as bind]
            [top.kzre.krro.ui.core.diff :as diff]
            [top.kzre.krro.ui.core.protocol :as proto]
            [top.kzre.krro.ui.core.vnode :as vnode]))



;; ── 协议 ─────────────────────────────────────────────
(def IVNode proto/IVNode)
(def IElementFactory proto/IElementFactory)
(def IRenderer proto/INodePatcher)

;; ── 虚拟节点 ─────────────────────────────────────────
(def make-vnode vnode/make-vnode)
(def edn->vnode vnode/edn->vnode)

(def event vnode/event)

(def project-binding vnode/project-binding)
(def frame-param-binding vnode/frame-param-binding)


;; ── Diff 算法与执行器 ───────────────────────────────
(def diff! diff/diff!)


;; ── 数据绑定管理器 ─────────────────────────────────
(def create-bind-manager bind/create-bind-manager)
(def ^:dynamic *default-bind-manager* bind/*default-bind-manager*)
(def register-binding! bind/register!)
(def unregister-binding! bind/unregister!)
(def refresh-binding! bind/refresh!)
