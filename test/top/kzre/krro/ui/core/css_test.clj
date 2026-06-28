(ns top.kzre.krro.ui.core.css-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [top.kzre.krro.ui.core.css :as css]))

(deftest test-style->css-empty
  (is (nil? (css/style->css {})))
  (is (nil? (css/style->css nil))))

(deftest test-style->css-single-property
  (is (= "font-size: 14;"
         (css/style->css {:font-size 14}))))

(deftest test-style->css-multiple-properties
  (let [css-str (css/style->css {:font-size 14 :color "red"})]
    (is (str/includes? css-str "font-size: 14"))
    (is (str/includes? css-str "color: red"))
    (is (str/ends-with? css-str ";"))))

(deftest test-with-prefix
  (let [prefixed (css/with-prefix {:font-size 14 :color "blue"} "-fx-")]
    ;; 键为关键字，不是字符串
    (is (= {:-fx-font-size 14, :-fx-color "blue"} prefixed))
    (is (= 14 (get prefixed :-fx-font-size)))
    (is (= "blue" (get prefixed :-fx-color)))))

(deftest test-with-prefix-empty
  (is (empty? (css/with-prefix {} "-fx-")))
  (is (empty? (css/with-prefix {} ""))))

(deftest test-style->css-with-numeric-and-string-values
  (let [css-str (css/style->css {:width "100px" :opacity 0.5})]
    (is (str/includes? css-str "width: 100px"))
    (is (str/includes? css-str "opacity: 0.5"))))