(ns top.kzre.krro.ui.core.css
  "平台无关的样式转换辅助。
   将 EDN 描述的样式 map 转换为 CSS 字符串。
   渲染器可在此基础上扩展平台特有前缀（如 JavaFX 的 -fx-）。"
  (:require
   [clojure.string :as str]))

(defn style->css
  "将样式 map 转换为 CSS 字符串。
   键为 kebab-case 关键字（如 :font-size），值为字符串或数字。
   例如 {:font-size 14 :color \"red\"} => \"font-size: 14; color: red;\""
  [style-map]
  (when (seq style-map)
    (->> (for [[k v] style-map]
           (str (name k) ": " v ";"))
         (str/join " "))))

(defn with-prefix
  "在样式 map 的每个键前添加前缀，返回新的样式 map。
   用于 JavaFX 渲染器添加 -fx- 前缀。
   例如 (with-prefix {:font-size 14} \"-fx-\") => {\"-fx-font-size\" 14}"
  [style-map prefix]
  (reduce-kv (fn [m k v]
               (assoc m (keyword (str prefix (name k))) v))
             {} style-map))