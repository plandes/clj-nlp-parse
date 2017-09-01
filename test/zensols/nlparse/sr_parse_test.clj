(ns zensols.nlparse.sr-parse-test
  (:require [clojure.test :refer :all])
  (:require [clojure.string :as s]
            [clojure.tools.logging :as log]
            [zensols.actioncli.resource :as res]
            [zensols.nlparse.parse :as p]
            [zensols.nlparse.config :as conf :refer (with-context)]))

(defonce ^:private context
  (->> (conf/create-parse-config
        :pipeline [(conf/tokenize)
                   (conf/sentence)
                   (conf/part-of-speech)
                   (conf/morphology)
                   (conf/named-entity-recognizer)
                   (conf/parse-tree {:use-shift-reduce? true})])
       conf/create-context))

(defn- sr-model-found? []
  (.exists (res/resource-path :stanford-sr-model)))

(defn- parse [utterance]
  (with-context context
    (p/parse utterance)))

(def ^:private first-sent-gold
  '{:child ({:child ({:child ({:child ({:label "I" :token-index 1})
                               :label "PRP"
                               :token-index 1})
                      :label "NP"}
                     {:child ({:child ({:label "am" :token-index 2})
                               :label "VBP"
                               :token-index 2}
                              {:child ({:child ({:label "Paul" :token-index 3})
                                        :label "NNP"
                                        :token-index 3})
                               :label "NP"})
                      :label "VP"})
             :label "S"})
    :label "ROOT"})

(deftest test-sr-parse []
  (testing "testing SR parser (if model is available)"
    ;; satisfy test runner
    (is (= 1 1))
    (if-not (sr-model-found?)
      (log/warn "No shift reduce model found--skipping test")
      (is (= first-sent-gold
             (->> (parse "I am Paul") :sents first :parse-tree))))))
