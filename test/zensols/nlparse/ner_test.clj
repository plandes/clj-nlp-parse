(ns zensols.nlparse.ner-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io])
  (:require [zensols.actioncli.resource :refer :all]
            [zensols.actioncli.dynamic :refer :all]
            [zensols.nlparse.config :as conf :refer (with-context)]
            [zensols.nlparse.parse :as p]
            [zensols.nlparse.tok-re :as tr]))

(register-resource :tok-re-resource :constant "test-resources")

(defn- create-tok-ner-context []
  (->> (conf/create-parse-config
        :pipeline [(conf/tokenize)
                   (conf/sentence)
                   (conf/part-of-speech)
                   (conf/morphology)
                   (conf/named-entity-recognizer)
                   (conf/token-regex)])
       conf/create-context))

(defnc- tok-ner-context (create-tok-ner-context))

(defn- parse [utterance]
  (with-context tok-ner-context
    (p/parse utterance)))

(deftest test-parse-ner
  (testing "parse ner"
    (let [utterance "I like Teddy Grams on Tuesday"
          panon (parse utterance)
          [product dow] (->> panon :tok-re-mentions)
          mention-toks (->> panon :tok-re-mentions
                            first (p/tokens-for-mention panon))]
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
              :tok-re-ner-features {:food-type "snack"}
              :tok-re-ner-item-id 123}
             (-> mention-toks first
                 (select-keys [:tok-re-ner-tag :tok-re-ner-features
                               :tok-re-ner-item-id])))))))

(defn- create-tok-re-file []
  (let [out-dir "target/ner"]
    (->> [(tr/item "bad Words" "PROFANITY"
                   :lem-min-len 0)
          (tr/item "/^(?!000|666)[0-8][0-9]{2}-(?!00)[0-9]{2}-(?!0000)[0-9]{4}$/"
                   "PII"
                   :is-regexp? true
                   :features {:pii-type "ssn"})]
         ((fn [items]
            (map (fn [id item]
                   (assoc item :id id))
                 (range (count items)) items)))
         (tr/write-regex-files (io/file out-dir "token-regex.txt")
                               (io/file out-dir "token-feats.clj")))))

(deftest test-re-file-create
  (testing "regexp file create"
    (is (= {:pii-type #{"ssn"}} (create-tok-re-file)))))

(deftest test-re-file-use
  (testing "regexp file usage"
    (register-resource :tok-re-resource :constant "target/ner")
    (with-context (create-tok-ner-context)
      (let [ssn "667-16-9329"
            panon (p/parse (format "My social security number is %s.  Say some bad words." ssn))
            [ssn-ment prof-ment] (-> panon :tok-re-mentions)
            toks (p/tokens-for-mention panon ssn-ment)
            ftok (first toks)]
        (is (not (nil? panon)))
        (is (not (nil? ssn-ment)))
        (is (not (nil? prof-ment)))
        (is (not (nil? toks)))
        (is (= {:pii-type "ssn"} (->> ftok :tok-re-ner-features)))
        (is (= "NUMBER" (->> ftok :ner-tag)))
        (is (= "PII" (-> ftok :tok-re-ner-tag)))
        (is (= "PROFANITY" (:tok-re-ner-tag prof-ment)))))))
