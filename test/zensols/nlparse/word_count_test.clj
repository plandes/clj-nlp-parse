(ns zensols.nlparse.word-count-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [zensols.nlparse.feature.word-count :refer :all]
            [zensols.nlparse.parse :as p]
            [zensols.nlparse.config :as conf :refer (with-context)]))

(defonce ^:private context
  (->> (conf/create-parse-config
        :pipeline [(conf/tokenize)
                   (conf/sentence)
                   (conf/part-of-speech)
                   (conf/morphology)
                   (conf/stopword)])
       conf/create-context))
;(ns-unmap *ns* 'context)

(defn- parse [utterance]
  (with-context context
    (p/parse utterance)))

(defn- feature-stats [res-name]
  (with-open [reader (io/reader (format "test-resources/%s.txt" res-name))]
    (->> (line-seq reader)
         (s/join " ")
         parse
         (hash-map :class-label res-name :instance)
         list
         calculate-feature-stats)))

(deftest feature-word-count-distribution []
  (testing "Feature count distribution statistics"
    (is (= {"get" 3/17 "i" 9/17 "me" 5/17}
           (-> (feature-stats "moby") :word-count-dist vals first)))))

(deftest feature-word-count-distribution-config []
  (testing "Feature count distribution statistics with config"
    (binding [*word-count-config* (assoc *word-count-config*
                                         :words-by-label-count 10
                                         :pos-tags (p/pos-tags 'noun)
                                         :word-form-fn #(-> % :lemma s/lower-case))]
      (is (= {"pier-head" 1/18
              "desk" 1/18
              "thousand" 1/9
              "city" 1/9
              "ocean" 1/9
              "man" 1/9
              "nothing" 1/9
              "time" 1/9
              "street" 1/9
              "ship" 1/9}
             (-> (feature-stats "moby") :word-count-dist vals first))))))

(deftest feature-word-count-score []
  (testing "Feature word count score"
    (let [stats (feature-stats "moby")
          feats (-> "I account it high time to get to sea as soon as I can."
                    parse
                    (label-count-score-features stats))]
      (is (contains? feats :word-count-moby))
      (is (= 123.0
             (-> feats :word-count-moby (* 100) Math/floor))))))
