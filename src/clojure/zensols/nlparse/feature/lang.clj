(ns ^{:doc "Feature utility functions.  In this library, all references to
`panon` stand for *parsed annotation*, which is returned
from [[zensols.nlparse.parse/parse]]."
      :author "Paul Landes"}
    zensols.nlparse.feature.lang
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer (pprint)]
            [clojure.string :as s])
  (:require [zensols.nlparse.feature.util :refer :all]
            [zensols.nlparse.wordnet :as wn]
            [zensols.nlparse.parse :as pt]))


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


;; pos
(defn- pos-tag-ratio-keyword [lab]
  (-> (format "pos-tag-ratio-%s" lab) keyword))

(defn- pos-tag-count-keyword [lab]
  (-> (format "pos-tag-count-%s" lab) keyword))

(defn- pos-tag-or-none [tag]
  (if (contains? (pt/pos-tags) tag)
    tag
    none-label))

(defn pos-tag-features
  "Return POS tag features computed all **tokens**, which include:

  * **:pos-first-tag** The first POS tag.
  * **:pos-last-tag** The last POS tag.
  * **:pos-tag-count-adjective*: The number of adjectives found.
  * **:pos-tag-ratio-adjective*: The ratio of adjectives to other POS tags.
  * **:pos-tag-count-adverb*: The number of adverbs found.
  * **:pos-tag-ratio-adverb*: The ratio of adverbs to other POS tags.
  * **:pos-tag-count-noun*: The number of nouns found.
  * **:pos-tag-ratio-noun*: The ratio of nouns to other POS tags.
  * **:pos-tag-count-verb*: The number of verbs found.
  * **:pos-tag-ratio-verb*: The ratio of verbs to other POS tags.
  * **:pos-tag-count-wh*: The number of what/when/where type questions POS
  tags.
  * **:pos-tag-ratio-wh*: The ratio of what/when/where type question POS tags
  to other POS tags.
  * **:pos-tag-count-adjective*: The number of adjectives found.
  * **:pos-tag-ratio-adjective*: The ratio of adjectives to other POS tags."
  [tokens]
  (let [pos-tag-types (pt/pos-tag-types)
        tc (count tokens)]
    (->> (map :pos-tag tokens)
         (map pt/pos-tag-type)
         (remove nil?)
         (reduce (fn [ret ttype]
                   (merge ret {ttype (inc (or (get ret ttype) 0))}))
                 {})
         (merge (zipmap pos-tag-types (repeat (count pos-tag-types) 0)))
         (map (fn [[k v]]
                {(pos-tag-ratio-keyword k) (/ v tc)
                 (pos-tag-count-keyword k) v}))
         (apply merge)
         (merge {:pos-last-tag (->> tokens last :pos-tag pos-tag-or-none)
                 :pos-first-tag (->> tokens first :pos-tag pos-tag-or-none)}))))

(defn pos-tag-feature-metas []
  (vec (concat [[:pos-last-tag (cons none-label (into () (pt/pos-tags)))]
                [:pos-first-tag (cons none-label (into () (pt/pos-tags)))]]
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
       (remove nil?)
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
