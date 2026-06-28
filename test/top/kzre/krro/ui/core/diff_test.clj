(ns top.kzre.krro.ui.core.diff-test
  (:require [clojure.test :refer :all]
            [top.kzre.krro.ui.core.diff :as diff]
            [top.kzre.krro.ui.core.vnode :as vnode]))

;; ══════════════════════════════════════════════════════════
;; 辅助函数
;; ══════════════════════════════════════════════════════════

(defn- ops-contains?
  "检查操作序列中是否存在指定类型的操作"
  [ops op-type]
  (some #(= op-type (:type %)) ops))

(defn- ops-of-type
  "返回操作序列中所有指定类型的操作"
  [ops op-type]
  (filter #(= op-type (:type %)) ops))

;; ══════════════════════════════════════════════════════════
;; 基础场景（原测试保留）
;; ══════════════════════════════════════════════════════════

(deftest test-diff-same-nodes-no-ops
  (let [old (vnode/make-vnode :text :props {:content "A"})
        new (vnode/make-vnode :text :props {:content "A"})
        ops (diff/diff old new)]
    (is (empty? ops))))

(deftest test-diff-different-tag-replace
  (let [old (vnode/make-vnode :text :props {:content "A"})
        new (vnode/make-vnode :button :props {:content "A"})
        ops (diff/diff old new)]
    (is (= 1 (count ops)))
    (is (= :replace (:type (first ops))))))

(deftest test-diff-props-update
  (let [old (vnode/make-vnode :text :props {:content "A"})
        new (vnode/make-vnode :text :props {:content "B"})
        ops (diff/diff old new)]
    (is (= 1 (count ops)))
    (is (= :update-props (:type (first ops))))))

(deftest test-diff-create-child
  (let [old (vnode/make-vnode :block)
        new (vnode/make-vnode :block :children [(vnode/make-vnode :text :props {:content "New"})])
        ops (diff/diff old new)]
    (is (= 1 (count ops)))
    (is (= :create-child (:type (first ops))))))

(deftest test-diff-delete-child
  (let [old (vnode/make-vnode :block :children [(vnode/make-vnode :text :props {:content "Old"})])
        new (vnode/make-vnode :block)
        ops (diff/diff old new)]
    (is (= 1 (count ops)))
    (is (= :delete-child (:type (first ops))))))

(deftest test-diff-key-match-move
  (let [old (vnode/make-vnode :block :children
                              [(vnode/make-vnode :text :key :a :props {:content "A"})
                               (vnode/make-vnode :text :key :b :props {:content "B"})])
        new (vnode/make-vnode :block :children
                              [(vnode/make-vnode :text :key :b :props {:content "B"})
                               (vnode/make-vnode :text :key :a :props {:content "A"})])
        ops (diff/diff old new)]
    (is (some #(= :move-child (:type %)) ops))))

;; ══════════════════════════════════════════════════════════
;; 增强测试：深层嵌套、复杂更新、边界
;; ══════════════════════════════════════════════════════════

(deftest test-diff-deep-nested-update
  (let [old (vnode/make-vnode :block :children
                              [(vnode/make-vnode :block :children
                                                 [(vnode/make-vnode :text :props {:content "Deep"})])])
        new (vnode/make-vnode :block :children
                              [(vnode/make-vnode :block :children
                                                 [(vnode/make-vnode :text :props {:content "Deeper"})])])
        ops (diff/diff old new)]
    ;; 应只有一个 update-props 操作在叶子节点上，其余节点复用
    (is (= 1 (count ops)))
    (is (= :update-props (:type (first ops))))))

(deftest test-diff-intermediate-node-replace
  (let [old (vnode/make-vnode :scroll :children
                              [(vnode/make-vnode :block :key :container :children
                                                 [(vnode/make-vnode :text :props {:content "old"})])])
        new (vnode/make-vnode :scroll :children
                              [(vnode/make-vnode :grid :key :container :children
                                                 [(vnode/make-vnode :text :props {:content "new"})])])
        ops (diff/diff old new)]
    ;; :block 变为 :grid，标签不同，该子节点应被替换
    (is (ops-contains? ops :replace))
    (is (not-any? #(= :update-props (:type %)) ops))))

(deftest test-diff-property-added-and-removed
  (let [old (vnode/make-vnode :text :props {:content "A" :visible? true})
        new (vnode/make-vnode :text :props {:content "A" :disabled? true})
        ops (diff/diff old new)]
    ;; 属性变化，应产生 update-props
    (is (= 1 (count ops)))
    (is (= :update-props (:type (first ops))))))

(deftest test-diff-unkeyed-children-reorder
  (let [old (vnode/make-vnode :block :children
                              [(vnode/make-vnode :text :props {:content "A"})
                               (vnode/make-vnode :text :props {:content "B"})])
        new (vnode/make-vnode :block :children
                              [(vnode/make-vnode :text :props {:content "B"})
                               (vnode/make-vnode :text :props {:content "A"})])
        ops (diff/diff old new)]
    ;; 无 key 时，按顺序比较，会导致两个 update-props（内容交换）
    (is (= 2 (count (ops-of-type ops :update-props))))))

(deftest test-diff-root-replace
  (let [old (vnode/make-vnode :text :props {:content "A"})
        new (vnode/make-vnode :button :props {:content "A"})
        ops (diff/diff old new)]
    (is (= 1 (count ops)))
    (is (= :replace (:type (first ops))))))

(deftest test-diff-insert-multiple-children
  (let [old (vnode/make-vnode :block :children [])
        new (vnode/make-vnode :block :children
                              [(vnode/make-vnode :text :props {:content "1"})
                               (vnode/make-vnode :text :props {:content "2"})
                               (vnode/make-vnode :text :props {:content "3"})])
        ops (diff/diff old new)]
    (is (= 3 (count ops)))
    (is (every? #(= :create-child (:type %)) ops))))

(deftest test-diff-delete-multiple-children
  (let [old (vnode/make-vnode :block :children
                              [(vnode/make-vnode :text :key :a)
                               (vnode/make-vnode :text :key :b)
                               (vnode/make-vnode :text :key :c)])
        new (vnode/make-vnode :block :children [])
        ops (diff/diff old new)]
    (is (= 3 (count ops)))
    (is (every? #(= :delete-child (:type %)) ops))))

(deftest test-diff-complex-keyed-reorder
  (let [old (vnode/make-vnode :block :children
                              [(vnode/make-vnode :text :key :a :props {:content "A"})
                               (vnode/make-vnode :text :key :b :props {:content "B"})
                               (vnode/make-vnode :text :key :c :props {:content "C"})
                               (vnode/make-vnode :text :key :d :props {:content "D"})])
        new (vnode/make-vnode :block :children
                              [(vnode/make-vnode :text :key :c :props {:content "C"})
                               (vnode/make-vnode :text :key :a :props {:content "A"})
                               (vnode/make-vnode :text :key :d :props {:content "D"})
                               (vnode/make-vnode :text :key :b :props {:content "B"})])
        ops (diff/diff old new)]
    ;; 应只产生移动操作，数量取决于算法实现，至少应有移动操作，且不应有 create/delete/replace
    (is (some #(= :move-child (:type %)) ops))
    (is (not-any? #(#{:create-child :delete-child :replace} (:type %)) ops))))

(deftest test-diff-mixed-update-and-move
  (let [old (vnode/make-vnode :block :children
                              [(vnode/make-vnode :text :key :a :props {:content "A"})
                               (vnode/make-vnode :text :key :b :props {:content "B"})])
        new (vnode/make-vnode :block :children
                              [(vnode/make-vnode :text :key :b :props {:content "B-updated"})
                               (vnode/make-vnode :text :key :a :props {:content "A"})])
        ops (diff/diff old new)]
    ;; 应该有移动操作（b 移到前面）和属性更新（b 的内容变化）
    (is (ops-contains? ops :move-child))
    (is (ops-contains? ops :update-props))))

(deftest test-diff-null-new-vnode
  (let [old (vnode/make-vnode :text)
        ops (diff/diff old nil)]
    (is (= 1 (count ops)))
    (is (= :delete-child (:type (first ops))))))

(deftest test-diff-null-old-vnode
  (let [new (vnode/make-vnode :text)
        ops (diff/diff nil new)]
    (is (= 1 (count ops)))
    (is (= :create-child (:type (first ops))))))

(deftest test-diff-both-null
  (let [ops (diff/diff nil nil)]
    (is (empty? ops))))

(deftest test-diff-same-key-but-different-type
  (let [old (vnode/make-vnode :block :children
                              [(vnode/make-vnode :text :key :1)])
        new (vnode/make-vnode :block :children
                              [(vnode/make-vnode :button :key :1)])
        ops (diff/diff old new)]
    ;; key 相同但类型不同，应替换
    (is (ops-contains? ops :replace))))