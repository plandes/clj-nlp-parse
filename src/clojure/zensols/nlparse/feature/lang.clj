(ns ^{:doc "Feature utility functions.  In this library, all references to
`panon` stand for *parsed annotation*, which is returned
from [[zensols.nlparse.parse/parse]]."
      :author "Paul Landes"}
    zensols.nlparse.feature.lang
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer (pprint)]
            [clojure.string :as s])
  (:require [zensols.nlparse.wordnet :as wn]
            [zensols.nlparse.wordlist :as wl]
            [zensols.nlparse.parse :as pt]))

(def none-label
  "Value used for missing features."
  "<none>")

(def beginning-of-sentence-label
  "Beginning of sentence marker."
  "<bos>")

(def end-of-sentence-label
  "End of sentence marker."
  "<eos>")

;; util
(defn upper-case? [text]
 (= (count text)
    (count (take-while #(Character/isUpperCase %) text))))

(defn lc-null
  "Return the lower case version of a string or nil if nil given."
  [str]
  (if str (s/lower-case str)))

(defn or-none
  "Return **str** if non-`nil` or otherwise the sepcial [[none-label]]."
  [str]
  (or str none-label))

(defn or-0
  "Call and return the value given by **val-fn** iff **check** is non-`nil`,
  otherwise return 0."
  ([val]
   (or val 0))
  ([check val-fn]
   (if check (val-fn check) 0)))

(defn ratio-true
  "Return the ratio of **items** whose evaluation of **true-fn** is `true`."
  [items true-fn]
  (/ (->> items (map true-fn)
          (filter true?) count)
     (count items)))

;; propbank
(defn first-sent-propbank-label
  "Find the first propbank label for a sentence."
  [anon]
  (let [toks (:tokens anon)]
    (first
     (filter #(not (nil? %))
             (map (fn [tok]
                    (let [srl (:srl tok)]
                      (if (:propbank srl)
                        (log/tracef "verbnet label: %s" (:verbnet-class srl)))
                      (:propbank srl)))
                  toks)))))

(defn first-propbank-label
  "Find the first propbank label across all ."
  [anon]
  (->> (:sents anon) (map first-sent-propbank-label) (drop-while nil?) first))

(defn verb-features
  "Find the most probable key head verb in sentence **sent**."
  [sent]
  (let [tree (:dependency-parse-tree sent)
        root-tok (pt/root-dependency sent)
        root-word (lc-null (:text root-tok))
        toks (:tokens sent)
        first-tok (first toks)
        first-word-verb? (= "VB" (:pos-tag first-tok))
        elected-verb-pair
        (cond first-word-verb? [(lc-null (:text (first toks))) 1]
              (and root-word (= "VB" (:pos-tag root-tok)))
              [root-word (first (:token-range root-tok))]
              ;; too dicey
              ;;root-word (morph/to-present-tense-verb root-word)
              true [none-label nil])
        elected-verb (first elected-verb-pair)
        elected-verb-id (if (and false (not (= none-label elected-verb)))
                          (let [iword (wn/lookup-word elected-verb wn/pos-verb)]
                            (if iword
                              (first (.getSynsetOffsets iword))
                              (do
                                (log/warnf "no wordnet offset for <%s>"
                                           elected-verb)
                                -1)))
                          (.hashCode elected-verb))]
    {:elected-verb-id elected-verb-id}))

(defn verb-feature-metas []
  [[:elected-verb-id 'numeric]])

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
(defn dictionary-features
  "Dictionary features include in/out-of-vocabulary ratio."
  [tokens]
  (let [lemmas (map :lemma tokens)]
    {:in-dict-ratio (ratio-true lemmas wn/in-dictionary?)
     :in-english-word-list-ratio (ratio-true lemmas #(wl/in-word-list? %))}))

(defn dictionary-feature-metas []
  [[:in-dict-ratio 'numeric]])

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


;; pos
(defn- pos-tag-ratio-keyword [lab]
  (-> (format "pos-tag-ratio-%s" lab) keyword))

(defn- pos-tag-count-keyword [lab]
  (-> (format "pos-tag-count-%s" lab) keyword))

(defn pos-tag-features [tokens]
  (let [pos-tag-types (pt/pos-tag-types)
        tc (count tokens)]
    (->> (map :pos-tag tokens)
         (map pt/pos-tag-type)
         (reduce (fn [ret ttype]
                   (merge ret {ttype (inc (or (get ret ttype) 0))}))
                 {})
         (merge (zipmap pos-tag-types (repeat (count pos-tag-types) 0)))
         (map (fn [[k v]]
                {(pos-tag-ratio-keyword k) (/ v tc)
                 (pos-tag-count-keyword k) v}))
         (apply merge)
         (merge {:pos-last-tag (->> tokens last :pos-tag)
                 :pos-first-tag (->> tokens first :pos-tag)}))))

(defn pos-tag-feature-metas []
  (vec (concat [[:pos-last-tag (into () (pt/pos-tags))]
                [:pos-first-tag (into () (pt/pos-tags))]]
               (map #(vector (pos-tag-ratio-keyword %) 'numeric)
                    (pt/pos-tag-types))
               (map #(vector (pos-tag-count-keyword %) 'numeric)
                    (pt/pos-tag-types)))))


;; tree
(defn- dependency-tree-id
  "Get a hash code for the dependency parse tree of sentence **sent**."
  [panon]
  (->> panon
       :sents
       (map :dependency-parse-tree)
       (map #(.hashCode %))
       (reduce +)))

(defn tree-feature-metas []
  [[:dep-tree-id 'numeric]])

(defn tree-features [panon]
  {:dep-tree-id (dependency-tree-id panon)})



;; SRL
(defn- srl-propbank-ids [toks]
  (->> toks
       (map (fn [tok]
              (-> tok :srl :propbank (#(if % (.hashCode %) 0)))))
       (reduce +)))

(defn- srl-argument-count [toks]
  (->> toks
       (map #(-> % :srl :heads first :dependency-label))
       (remove nil?)
       count))

(defn srl-feature-metas []
  [[:srl-propbank-id 'numeric]
   [:srl-argument-counts 'numeric]])

(defn srl-features [toks]
  {:srl-propbank-id (srl-propbank-ids toks)
   :srl-argument-counts (srl-argument-count toks)})
