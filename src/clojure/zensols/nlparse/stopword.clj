(ns ^{:doc "This namesapce provides ways of filtering *stop word* tokens.

To avoid the double negative in function names, *go words* are defined to be
the compliment of a vocabulary with a stop word list.  Functions
like [[meaningful-word?]] tell whether or not a token is a stop word, which are
defined to be:

  * stopwords (predefined list)
  * punctuation
  * numbers
  * non-alphabetic characters
  * URLs"
      :author "Paul Landes"}
    zensols.nlparse.stopword
  (:require [clojure.string :as s]))

(def ^:dynamic *stopword-config*
  "Configuration for filtering stop words.

Keys
---
* **:post-tags** POS tags for *go words* (see namespace docs)
* **:word-form-fn** function run on the token in [[go-word-form]]; for example
  if `#(-> % :lemma s/lower-case)` then lemmatization is used (i.e. Running ->
  run)"
  {:pos-tags #{"RB", "JJ", "JJR", "JJS", "MD",
               "NN", "NNS", "NNP", "NNPS",
               "VB", "VBD", "VBG", "VBN", "VBP", "VBZ",
               "PRP", "PDT", "POS", "RP", "FW"}
   :word-form-fn #(-> % :text s/lower-case)})

(defn go-word?
  "Return whether a token is a *go* token."
  [token]
  (let [tags (:pos-tags *stopword-config*)]
    (and (not (:stopword token))
         (contains? tags (:pos-tag token)))))

(defn go-word-form
  "Conical string word count form of a token.  ."
  [token]
  ((:word-form-fn *stopword-config*) token))

(defn go-word-forms
  "Filter tokens per [[go-word?]] and return their *form*
  based on [[go-word-form]]."
  [tokens]
  (->> tokens
       (filter go-word?)
       (remove nil?)
       (map go-word-form)))
