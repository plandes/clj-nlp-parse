(ns zensols.nlparse.stopword-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [zensols.nlparse.stopword :refer :all]
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

(deftest go-tokens-test []
  (testing "Filter semantically significant words"
    (is (= ["test" "filter" "semantically" "significant" "words"]
           (->> (parse "This is a test.  This will filter 5 semantically significant words.")
                p/tokens
                go-word-forms)))))

(deftest go-tokens-config-test []
  (testing "Filter semantically significant word via config changes"
    (binding [*stopword-config* (assoc *stopword-config*
                                       :pos-tags (p/pos-tags 'noun)
                                       :word-form-fn #(-> % :lemma s/lower-case))]
      (is (= ["test" "word"]
             (->> (parse "This is a test.  This cleans 5 semantically significant words.")
                  p/tokens
                  go-word-forms))))))
