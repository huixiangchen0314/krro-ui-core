(ns top.kzre.krro.ui.core.vnode-test
  (:require [clojure.test :refer :all]
            [top.kzre.krro.ui.core.vnode :as vnode]))

;; ── make-vnode 基础测试 ──────────────────────────────
(deftest test-make-vnode-defaults
  (let [v (vnode/make-vnode :text)]
    (is (some? (:id v)))
    (is (= :text (:type v)))
    (is (nil? (:key v)))
    (is (empty? (:props v)))
    (is (empty? (:children v)))
    (is (nil? (:element v)))))

(deftest test-make-vnode-with-opts
  (let [v (vnode/make-vnode :text :key :hello :props {:content "Hi"} :children [(vnode/make-vnode :text)])]
    (is (= :hello (:key v)))
    (is (= {:content "Hi"} (:props v)))
    (is (= 1 (count (:children v))))))

;; ── edn->vnode 转换测试 ──────────────────────────────
(deftest test-edn->vnode-simple-element
  (let [v (vnode/edn->vnode [:text {:content "Hello" :key :greeting}])]
    (is (= :text (:type v)))
    (is (= "Hello" (get-in v [:props :content])))
    (is (= :greeting (:key v)))
    (is (empty? (:children v)))))

(deftest test-edn->vnode-element-without-attrs
  (let [v (vnode/edn->vnode [:block])]
    (is (= :block (:type v)))
    (is (empty? (:props v)))
    (is (empty? (:children v)))))

(deftest test-edn->vnode-nested
  (let [v (vnode/edn->vnode [:block {:direction :vertical}
                             [:text {:content "Child"}]])]
    (is (= :block (:type v)))
    (is (= 1 (count (:children v))))
    (let [child (first (:children v))]
      (is (= :text (:type child)))
      (is (= "Child" (get-in child [:props :content]))))))

(deftest test-edn->vnode-deep-nesting
  (let [v (vnode/edn->vnode [:scroll
                             [:block
                              [:text {:content "Nested"}]
                              [:button {:content "Click"}]]])]
    (is (= :scroll (:type v)))
    (let [block (first (:children v))]
      (is (= :block (:type block)))
      (is (= 2 (count (:children block)))))))

(deftest test-edn->vnode-string-leaf
  (let [v (vnode/edn->vnode "Just text")]
    (is (string? v))
    (is (= "Just text" v))))

(deftest test-edn->vnode-empty-children
  (let [v (vnode/edn->vnode [:block])]
    (is (= :block (:type v)))
    (is (empty? (:children v)))))

(deftest test-edn->vnode-with-key-prop
  (let [v (vnode/edn->vnode [:text {:key :my-key :content "Keyed"}])]
    (is (= :my-key (:key v)))
    (is (= "Keyed" (get-in v [:props :content])))))