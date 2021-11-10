(ns megalan2021.core-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [megalan2021.core :as core]))

(deftest fake-test
  (testing "fake description"
    (is (= 1 2))))
