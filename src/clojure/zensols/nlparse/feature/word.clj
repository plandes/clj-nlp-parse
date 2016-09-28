(ns ^{:doc "Feature utility functions for tokens and words."
      :author "Paul Landes"}
    zensols.nlparse.feature.lang
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer (pprint)]
            [clojure.string :as s])
  (:require [zensols.nlparse.wordnet :as wn]
            [zensols.nlparse.wordlist :as wl]))

(defn wordnet-features
  "Get features generated from WordNet from **word**.

  * **word** the word to lookup
  * **pos-tag** a wordnet pos tag (see [[zensols.nlparse.wordnet/pos-tags]])"
  ([word]
   (wordnet-features word nil))
  ([word pos-tag]
   (let [iws (if pos-tag
               (wn/lookup-word word pos-tag)
               (wn/lookup-word word))
         iw (first iws)]
     (if iw (.sortSenses iw))
     (let [synset (if iw (->> iw .getSenses first))
           flags (wn/verb-frame-flags synset)]
       {:wn-offset (or-0 synset #(.getOffset %))
        :wn-word-set-count (count iws)
        :wn-is-adjective-cluster (not (nil? (wn/adjective-cluster? synset)))
        :wn-sense-word-count (or-0 synset #(-> % .getWords .size))
        :wn-sense-lex-file-num (or-0 synset #(.getLexFileNum %))
        :wn-verb-frame-flag-len (or-0 flags #(.length %))
        :wn-verb-frame-flag-size (or-0 flags #(.size %))
        :wn-verb-frame-flag-hash (or-0 flags #(.hashCode %))}))))

(defn wordnet-feature-metas []
  (->> [:wn-offset :wn-word-set-count :wn-sense-word-count
        :wn-sense-lex-file-num :wn-verb-frame-flag-len
        :wn-verb-frame-flag-size :wn-verb-frame-flag-hash]
       (map (fn [k] [k 'numeric]))
       (cons [:wn-is-adjective-cluster 'boolean])
       vec))


;; dict
(defn- dictionary-wl-key [lang-code]
  (->> lang-code (format "wl-%s-ratio") keyword))

(defn dictionary-features
  "Dictionary features include in/out-of-vocabulary ratio.  The **lang-codes**
  parameter is a hash set of two letter string language
  code (see [[zensols.nlparse.wordlist/in-word-list?]]) to look up, which
  defaults to `en` for English.

  See [[zensols.nlparse.wordlist/word-list-locales]]"
  ([tokens]
   (dictionary-features tokens #{"en"}))
  ([tokens lang-codes]
   (let [lemmas (map :lemma tokens)]
     (->> lang-codes
          (map (fn [lang-code]
                 {(dictionary-wl-key lang-code)
                  (ratio-true lemmas #(wl/in-word-list? lang-code %))}))
          (apply merge)
          (merge (if (contains? lang-codes "en")
                   {:wn-en-dict-ratio
                    (ratio-true lemmas wn/in-dictionary?)}))))))

(defn dictionary-feature-metas
  "See [[dictionary-features]]."
  [lang-codes]
  (->> lang-codes
       (map dictionary-wl-key)
       (concat (if (contains? (set lang-codes) "en")
                 [:wn-en-dict-ratio]))
       (map (fn [kw] [kw 'numeric]))))



;; token
(defn- token-average-length [tokens]
  (->> (map :text tokens)
       (map count)
       (apply +)
       (#(if (> (count tokens) 0)
           (/ % (count tokens))
           0))))

(defn token-features [panon tokens]
  {:utterance-length (count (:text panon))
   :mention-count (count (:mentions panon))
   :sent-count (count (:sents panon))
   :token-count (count tokens)
   :token-average-length (token-average-length tokens)
   :stopword-count (->> tokens (map #(if (:stopword %) 1 0)) (reduce +))
   :is-question (= "?" (-> tokens last :text))})

(defn token-feature-metas []
  [[:utterance-length 'numeric]
   [:mention-count 'numeric]
   [:sent-count 'numeric]
   [:token-count 'numeric]
   [:token-average-length 'numeric]
   [:stopword-count 'numeric]
   [:is-question 'boolean]])
