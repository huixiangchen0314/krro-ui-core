(ns top.kzre.krro.ui.core.spec.style
  "UI 样式内容规范。以 JavaFX CSS 属性为准，定义可用的样式属性及其合法值。"
  (:require [clojure.spec.alpha :as s]))

;; ── 基础值类型 ──────────────────────────────────
(s/def ::size (s/or :number number?    ; 数值默认为 px
                    :string string?))  ; 显式带单位如 "10px", "1em"
(s/def ::color (s/or :keyword #{:transparent :white :black :gray :red :green :blue :yellow :cyan :magenta}
                     :string #(re-matches #"^#[0-9a-fA-F]{3,8}$" %)
                     :string #(re-matches #"^rgba?\(.*\)$" %)))
(s/def ::position (s/or :keyword #{:top :bottom :left :right :center}
                        :string string?))
(s/def ::font-weight #{:normal :bold :bolder :lighter 100 200 300 400 500 600 700 800 900})
(s/def ::font-style #{:normal :italic :oblique})
(s/def ::text-align #{:left :center :right :justify})
(s/def ::text-decoration #{:none :underline :line-through :overline})
(s/def ::cursor (s/or :keyword #{:default :hand :text :wait :crosshair :move :not-allowed :resize}
                      :string string?))

;; ── 单值属性 spec ──────────────────────────────
(s/def ::background-color ::color)
(s/def ::background-image string?)
(s/def ::border-color ::color)
(s/def ::border-style #{:none :solid :dashed :dotted :double :groove :ridge :inset :outset})
(s/def ::border-width ::size)
(s/def ::border-radius ::size)
(s/def ::padding ::size)
(s/def ::padding-top ::size)
(s/def ::padding-right ::size)
(s/def ::padding-bottom ::size)
(s/def ::padding-left ::size)
(s/def ::margin ::size)
(s/def ::margin-top ::size)
(s/def ::margin-right ::size)
(s/def ::margin-bottom ::size)
(s/def ::margin-left ::size)
(s/def ::font-family string?)
(s/def ::font-size ::size)
(s/def ::font-weight ::font-weight)
(s/def ::font-style ::font-style)
(s/def ::text-fill ::color)           ; JavaFX 特有
(s/def ::text-align ::text-align)
(s/def ::text-decoration ::text-decoration)
(s/def ::line-height ::size)
(s/def ::opacity (s/and number? #(<= 0 % 1)))
(s/def ::visibility #{:visible :hidden})
(s/def ::cursor ::cursor)
(s/def ::overflow #{:visible :hidden :scroll :auto})
(s/def ::background-position (s/or :single ::position :pair (s/cat :x ::position :y ::position)))
(s/def ::background-repeat #{:repeat :no-repeat :repeat-x :repeat-y})
(s/def ::border (s/keys :opt-un [::border-color ::border-style ::border-width]))
(s/def ::border-radius (s/or :all ::size :corners (s/tuple ::size ::size ::size ::size)))
(s/def ::box-shadow string?)

;; ── 完整的 style map spec ──────────────────────────
(s/def ::style
  (s/keys :opt-un [::background-color ::background-image ::background-position ::background-repeat
                   ::border ::border-color ::border-style ::border-width ::border-radius
                   ::padding ::padding-top ::padding-right ::padding-bottom ::padding-left
                   ::margin ::margin-top ::margin-right ::margin-bottom ::margin-left
                   ::font-family ::font-size ::font-weight ::font-style ::text-fill
                   ::text-align ::text-decoration ::line-height
                   ::opacity ::visibility ::cursor ::overflow
                   ::box-shadow]))