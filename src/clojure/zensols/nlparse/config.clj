(ns ^{:doc "Configure the Stanford CoreNLP parser."
      :author "Paul Landes"}
    zensols.nlparse.config)

(defn tokenize
  ([]
   (tokenize "en"))
  ([lang-code]
   {:component :tokenize
    :lang lang-code}))

(defn part-of-speech
  ([]
   (part-of-speech "english-left3words-distsim.tagger"))
  ([pos-model-resource]
   {:component :pos
    :pos-model-resource pos-model-resource}))

(defn sentence []
  {:component :sents})

(defn stopword []
  {:component :stopword})

(defn named-entity-recognizer
  ([]
   (named-entity-recognizer
    ["edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz"]))
  ([paths]
   {:component :ner
    :ner-model-paths paths}))

(defn token-regex
  ([]
   (token-regex ["token-regex.txt"]))
  ([paths]
   {:component :tok-re
    :tok-re-resources paths}))

(defn parse-tree []
  {:component :parse-tree})

(defn dependency-parse-tree []
  {:component :dependency-parse-tree})

(defn coreference []
  {:component :coref})

(def ^:dynamic *pipeline-config*
  "A sequence of keys with each representing an annotator to add to the NLP
  pipeline.  This is used with [[create-context]] to create a pipeline object
  for NLP processing.

Keys
----
* **:tokenize** split words per configured language
* **:sents** group tokens into sentences per configured language
* **:stopword** annotate stop words (boolean)
* **:pos** do part of speech tagging
* **:ner** do named entity recognition
* **:tok-re** token regular expression
* **:parse** create head and parse trees
* **:coref** coreference tree structure"
  [(tokenize)
   (sentence)
   (stopword)
   (part-of-speech)
   (named-entity-recognizer)
   (parse-tree)
   (coreference)])
