(ns ^{:doc "Wraps the [Extended Java WordNet Library](http://extjwnl.sourceforge.net/javadocs/)."
      :author "Paul Landes"}
    zensols.nlparse.wordnet
  (:import net.sf.extjwnl.dictionary.Dictionary
           (net.sf.extjwnl.data POS))
  (:require [clojure.string :as str])
  (:require [zensols.actioncli.dynamic :refer (defa-)]))

(def ^:private word-pattern (re-pattern "^\\w+$"))

;;; wordnet
(def pos-tag-any
  "Special POS tag to indicate any POS tag (see [[has-pos-tag?]])"
  "any")

(def pos-adjective
  "The adjective wordnet API POS tag."
  POS/ADJECTIVE)

(def pos-adverb
  "The adverb wordnet API POS tag."
  POS/ADVERB)

(def pos-noun
  "The noun wordnet API POS tag."
  POS/NOUN)

(def pos-verb
  "The verb wordnet API POS tag."
  POS/VERB)

(def pos-tags
  "All wordnet tags, which
  include [[pos-verb]], [[pos-noun]], [[pos-adverb]], [[pos-adjective]]."
  (POS/getAllPOS))

(defa- wndict-inst)

(defn wordnet-dictionary
  "Return the Wordnet dictionary instance.

  See the [Java
  example](https://github.com/extjwnl/extjwnl/blob/master/utilities/src/main/java/net/sf/extjwnl/utilities/Examples.java)"
  []
  (swap! wndict-inst #(or % (Dictionary/getDefaultResourceInstance))))

(defn lookup-word
  "Lookup a word (lemmatized) in wordnet.

  * **pos-tag** type of POS/VERB and one of the pos-noun,verb etc"
  ([lemma]
   (let [dict (wordnet-dictionary)
         words (.lookupAllIndexWords dict lemma)]
     (into [] (.getIndexWordCollection words))))
  ([lemma pos-tag]
   (let [dict (wordnet-dictionary)]
     [(.lookupIndexWord dict pos-tag lemma)])))

(defn verb-frame-flags
  "If **synset** is a verb type synset return its verb frame flags.  Otherwise
  return nil."
  [synset]
  (if (and synset (instance? net.sf.extjwnl.data.VerbSynset synset))
    (.getVerbFrameFlags synset)))

(defn adjective-cluster?
  [synset]
  (and synset
       (instance? net.sf.extjwnl.data.AdjectiveSynset synset)
       (.isAdjectiveCluster synset)))

(defn looks-like-word? [lemma]
  (and lemma (not (nil? (re-find word-pattern lemma)))))

(defn in-dictionary?
  "Return whether or not a lemmatized word is in WordNet."
  [lemma]
  (and (looks-like-word? lemma)
       (not (empty? (lookup-word lemma)))))

(defn pos-tag-set
  "Return all the POS tags found for a lemmatized word."
  [lemma-or-indexed-word]
  (let [iws (if (string? lemma-or-indexed-word)
              (lookup-word lemma-or-indexed-word)
              lemma-or-indexed-word)]
    (into #{} (map #(-> % .getPOS .getLabel) iws))))

(defn has-pos-tag?
  "Return whether or not the lemmatized word has exists for a POS tag.

  * **lemma** the lemma (or word) to look up in wordnet
  * **pos-tag-name** the name of the pos tag or [[pos-tag-any]]"
  [lemma pos-tag-name]
  (let [iws (lookup-word lemma)]
    (if (= pos-tag-any pos-tag-name)
      (not (empty? iws))
      (contains? (pos-tag-set iws) pos-tag-name))))

(defn wordnet-pos-labels
  "Return all word used POS tags."
  []
  (map #(.getLabel %) (POS/values)))

(defn lookup-word-by-sense
  "Lookup a word by sense (i.e. `buy%2:40:00::`)."
  [sense-key]
  (let [dict (wordnet-dictionary)]
    ;; sense key is found in verbnet (get-13.5.1.xml)
    (.getWordBySenseKey dict sense-key)))

(defn present-tense-verb?
  "Return whether **word** looks like a present tense word."
  [word]
  (let [lword (str/lower-case word)]
    (let [dict (wordnet-dictionary)
          iword (.lookupIndexWord dict POS/VERB lword)]
      (if-not iword
        false
        (= lword (.getKey iword))))))

(defn to-present-tense-verb
  "Return the present tense of **verb**."
  [word]
  (let [dict (wordnet-dictionary)]
    (.lookupIndexWord dict POS/VERB word)))
