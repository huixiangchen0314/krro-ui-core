(ns top.kzre.krro.ui.core.bind-test
  (:require [clojure.test :refer :all]
            [top.kzre.krro.core.project :as proj]
            [top.kzre.krro.ui.core.bind :as bind]))

;; ── Fixture ────────────────────────────────────────
(use-fixtures :each
              (fn [f]
                (proj/init-project!)
                (swap! proj/project assoc :test/data {:value "initial"})
                (f)))

(deftest test-register-binding-applies-current-value
  (let [mgr (bind/create-bind-manager)
        element (Object.)
        called (atom nil)]
    (bind/register! mgr element [:test/data :value]
                    (fn [_ v] (reset! called v)))
    (is (= "initial" @called))))

(deftest test-binding-fires-on-project-change
  (let [mgr (bind/create-bind-manager)
        element (Object.)
        called (atom nil)]
    (bind/register! mgr element [:test/data :value]
                    (fn [_ v] (reset! called v)))
    (swap! proj/project assoc-in [:test/data :value] "updated")
    (is (= "updated" @called))))

(deftest test-unregister-stops-binding
  (let [mgr (bind/create-bind-manager)
        element (Object.)
        called (atom nil)]
    (bind/register! mgr element [:test/data :value]
                    (fn [_ v] (reset! called v)))
    (bind/unregister! mgr element)
    (reset! called nil)
    (swap! proj/project assoc-in [:test/data :value] "should not trigger")
    (is (nil? @called))))

(deftest test-refresh-binding
  (let [mgr (bind/create-bind-manager)
        element (Object.)
        called (atom nil)]
    (bind/register! mgr element [:test/data :value]
                    (fn [_ v] (reset! called v)))
    ;; 先确保 watch 已触发，然后重置 called，再用 refresh 手动刷新
    (swap! proj/project assoc-in [:test/data :value] "updated")
    (is (= "updated" @called))
    (reset! called nil)
    (bind/refresh! mgr element)
    (is (= "updated" @called))))

(deftest test-default-bind-manager
  (let [element (Object.)
        called (atom nil)]
    (bind/register! element [:test/data :value]
                    (fn [_ v] (reset! called v)))
    (is (= "initial" @called))
    (swap! proj/project assoc-in [:test/data :value] "default")
    (is (= "default" @called))))

(deftest test-watch-removed-functionality
  ;; 验证：解绑后修改项目数据不会触发已解绑的绑定
  (let [mgr (bind/create-bind-manager)
        element (Object.)
        called (atom nil)]
    (bind/register! mgr element [:test/data :value]
                    (fn [_ v] (reset! called v)))
    (bind/unregister! mgr element)
    (reset! called nil)
    (swap! proj/project assoc-in [:test/data :value] "should-not-fire")
    (is (nil? @called) "Binding was not unregistered correctly")))