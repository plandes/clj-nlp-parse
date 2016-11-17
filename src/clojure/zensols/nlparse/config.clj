(ns ^{:doc "Configure the Stanford CoreNLP parser."
      :author "Paul Landes"}
    zensols.nlparse.config
  (:require [zensols.actioncli.dynamic :refer [defa- undef] :as dyn])
  (:require [zensols.nlparse.resource :as pres]))

(def ^{:dynamic true :private true}
  *parse-context* nil)

(defa- create-context-fns-inst)

(defa- default-context-inst)

(defn tokenize
  "Create annotator to split words per configured language."
  ([]
   (tokenize "en"))
  ([lang-code]
   {:component :tokenize
    :lang lang-code}))

(defn sentence
  "Create annotator to group tokens into sentences per configured language."
  []
  {:component :sents})

(defn stopword
  "Create annotator to annotate stop words (boolean)."
  []
  {:component :stopword})

(defn part-of-speech
  "Create annotator to do part of speech tagging."
  ([]
   (part-of-speech "english-left3words-distsim.tagger"))
  ([pos-model-resource]
   {:component :pos
    :pos-model-resource pos-model-resource}))

(defn named-entity-recognizer
  "Create annotator to do named entity recognition."
  ([]
   (named-entity-recognizer
    ["edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz"]))
  ([paths]
   {:component :ner
    :ner-model-paths paths}))

(defn token-regex
  "Create annotator to token regular expression."
  ([]
   (token-regex ["token-regex.txt"]))
  ([paths]
   {:component :tok-re
    :tok-re-resources paths}))

(defn parse-tree
  "Create annotator to create head and parse trees."
  []
  {:component :parse-tree})

(defn dependency-parse-tree
  "Create an annotator to create a dependency parse tree."
  []
  {:component :dependency-parse-tree})

(defn coreference
  "Create annotator to coreference tree structure."
  []
  {:component :coref})

(defn register-library [lib-name lib-cfg]
  (swap! create-context-fns-inst assoc lib-name lib-cfg))

(defn create-context
  ([]
   (create-context [(tokenize)
                    (sentence)
                    (stopword)
                    (part-of-speech)
                    (named-entity-recognizer)
                    (parse-tree)
                    (coreference)]))
  ([pipeline-config]
   (->> @create-context-fns-inst
        (map (fn [[k {:keys [create-fn]}]]
               {k (create-fn pipeline-config)}))
        (into {}))))

(defn reset []
  (->> @create-context-fns-inst
       vals
       (map #((:reset-fn %) *parse-context*))))

(defn context [lib-name]
  (-> (or *parse-context*
          (swap! default-context-inst #(or % (create-context))))
      (get lib-name)))

(defmacro with-context
  "Use the parser with a context created with [[create-context]].
  This context is optionally configured.  Without this macro the default
  context is created with [[create-context]]."
  {:style/indent 1}
  [exprs & forms]
  (let [[raw-context- & ckeys-] exprs]
    `(let [qkeys# (apply hash-map (quote ~ckeys-))
           context# (merge ~raw-context- qkeys#)]
       (binding [*parse-context* context#]
         ~@forms))))

(dyn/register-purge-fn reset)
(pres/initialize)
