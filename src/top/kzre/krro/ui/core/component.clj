
(ns top.kzre.krro.ui.core.component
  "组件化")

(defonce ^:private component-registry (atom {}))

(defn make-component
  "factory: init-props->((props, frame)->{:keys [on-unmount, on-update, layout]})"
  [tag factory]
  {:tag tag
   :factory factory
   })


(defn get-component [tag]
  (get @component-registry tag))

(defn reg-component [tag factory]
  (swap! component-registry assoc
    tag
    (make-component tag factory)))