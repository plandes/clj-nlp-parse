(ns zensols.nlparse.ner-test
  (:require [clojure.test :refer :all])
  (:require [zensols.actioncli.resource :refer :all]
            [zensols.actioncli.dynamic :refer :all]
            [zensols.nlparse.stanford :as s :refer (with-context)]
            [zensols.nlparse.parse :as p]))

(register-resource :tok-re-resource :constant "test-resources")

(def ^:private utterance "I like Teddy Grams on Tuesday")

(defnc- tok-ner-context
  (s/create-context [:tokenize :sents :pos :ner :tok-re]))

(defn- parse [utterance]
  (with-context [tok-ner-context]
    (s/parse utterance)))

(deftest test-parse-ner
  (testing "parse ner"
    (let [panon (parse utterance)
          [product dow] (->> panon :tok-re-mentions)
          mention-toks (->> panon :tok-re-mentions first (p/tokens-for-mention panon))]
      (is (not (nil? product)))
      (is (not (nil? dow)))
      (is (not (nil? panon)))
      (is (= "PRODUCT" (:tok-re-ner-tag product)))
      (is (= "Teddy Grams" (:text product)))
      (is (= "DAY_OF_WEEK" (:tok-re-ner-tag dow)))
      (is (= "Tuesday" (:text dow)))
      (is (not (nil? mention-toks)))
      (is (= 2 (count mention-toks)))
      (is (= {:tok-re-ner-tag "PRODUCT"
              :tok-re-ner-features {:food-type "dessert"}
              :tok-re-ner-item-id 497}
             (-> mention-toks first
                 (select-keys [:tok-re-ner-tag :tok-re-ner-features
                               :tok-re-ner-item-id])))))))
