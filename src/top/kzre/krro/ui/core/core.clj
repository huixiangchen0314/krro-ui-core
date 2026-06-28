(ns top.kzre.krro.ui.core.core
  "krro-ui-core 公共入口。使用 :as 别名简化重导出，一次引入即可使用所有稳定符号。"
  (:require [top.kzre.krro.ui.core.protocol :as proto]
            [top.kzre.krro.ui.core.vnode :as vnode]
            [top.kzre.krro.ui.core.diff :as diff]
            [top.kzre.krro.ui.core.spec :as spec]
            [top.kzre.krro.ui.core.bind :as bind]
            [top.kzre.krro.ui.core.css :as css]))

;; ── 协议 ─────────────────────────────────────────────
(def IVNode proto/IVNode)
(def IElementFactory proto/IElementFactory)
(def IRenderer proto/IRenderer)

;; ── 虚拟节点 ─────────────────────────────────────────
(def VNode vnode/VNode)
(def make-vnode vnode/make-vnode)
(def edn->vnode vnode/edn->vnode)

;; ── Diff 算法与执行器 ───────────────────────────────
(def diff diff/diff)
(def patch! diff/patch!)

;; ── UI 描述规范 ──────────────────────────────────────
(def effective-key spec/effective-key)
(def tag-of spec/tag-of)
(def attrs-of spec/attrs-of)
(def required-tags spec/required-tags)
(def required-events spec/required-events)

;; ── 数据绑定管理器 ─────────────────────────────────
(def create-bind-manager bind/create-bind-manager)
(def ^:dynamic *default-bind-manager* bind/*default-bind-manager*)
(def register-binding! bind/register!)
(def unregister-binding! bind/unregister!)
(def refresh-binding! bind/refresh!)

;; ── 样式转换辅助 ────────────────────────────────────
(def style->css css/style->css)
(def with-prefix css/with-prefix)