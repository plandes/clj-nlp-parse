(ns zensols.nlparse.range-test
  (:require [clojure.test :refer :all])
  (:require [clojure.string :as s]
            [zensols.nlparse.parse :refer :all]))

(deftest in-test
  (testing "encapulsate range test"
    (is (in-range? [1 5] [2 3]))
    (is (in-range? [1 5] [1 5]))
    (is (in-range? [1 5] [1 3]))
    (is (in-range? [1 5] [2 5]))
    (is (not (in-range? [1 5] [0 3])))
    (is (not (in-range? [1 5] [2 6])))))

(deftest overlap-test
  (testing "overlap range test"
    (is (overlap? [0 5] [3 9]))
    (is (overlap? [3 9] [0 5]))
    (is (overlap? [0 5] [5 9]))
    (is (overlap? [5 9] [0 5]))
    (is (overlap? [1 5] [1 5]))
    (is (not (overlap? [0 5] [6 9])))
    (is (not (overlap? [6 9] [0 5])))))
