(ns ^{:doc "Wraps the [Extended Java WordNet Library](http://extjwnl.sourceforge.net/javadocs/)."
      :author "Paul Landes"}
    zensols.nlparse.wordnet
  (:import net.sf.extjwnl.dictionary.Dictionary
           (net.sf.extjwnl.data POS))
  (:require [clojure.string :as str])
  (:require [zensols.actioncli.dynamic :refer (dyn-init-var)]))

(def ^:private word-pattern (re-pattern "^\\w+$"))

;;; wordnet
(def pos-tag-any "any")

(dyn-init-var *ns* 'wndict-inst (atom nil))
;(ns-unmap *ns* 'wndict-inst)

(defn wordnet-dictionary
  "Return the Wordnet dictionary instance.

  See the [Java
  example](https://github.com/extjwnl/extjwnl/blob/master/utilities/src/main/java/net/sf/extjwnl/utilities/Examples.java)"
  []
  (swap! wndict-inst #(or % (Dictionary/getDefaultResourceInstance))))

(defn lookup-word
  "Lookup a word (lemmatized) in wordnet.
Optionally supply *pos-tag* (type of POS/VERB)."
  ([lemma]
   (let [dict (wordnet-dictionary)
         words (.lookupAllIndexWords dict lemma)]
     (into [] (.getIndexWordCollection words))))
  ([lemma pos-tag]
   (let [dict (wordnet-dictionary)]
     (.lookupIndexWord dict pos-tag lemma))))

(defn looks-like-word? [lemma]
  (not (nil? (re-find word-pattern lemma))))

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
  "Return whether or not the lemmatized word has exists for a POS tag."
  [lemma pos-tag-name]
  (let [iws (lookup-word lemma)]
    (if (= pos-tag-any pos-tag-name)
      (not (empty? iws))
      (contains? (pos-tag-set iws) pos-tag-name))))

(defn wordnet-pos-labels
  "Return all word used POS tags."
  []
  (map #(.getLabel %) (POS/values)))

(defn lookup-verb
  "Return a (indexed) WordNet entry using a verb POS tags."
  [verb]
  (lookup-word verb POS/VERB))

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
