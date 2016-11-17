(ns ^{:doc "Configure the Stanford CoreNLP parser."
      :author "Paul Landes"}
    zensols.nlparse.config
  (:require [clojure.tools.logging :as log])
  (:require [zensols.actioncli.dynamic :refer [defa- undef] :as dyn])
  (:require [zensols.nlparse.resource :as pres]))

(def ^{:dynamic true :private true}
  *parse-context* nil)

(defa- library-config-inst)

(defa- default-context-inst)

(def ^:private all-components
  '(tokenize
    sentence
    stopword
    part-of-speech
    named-entity-recognizer
    parse-tree
    coreference
    semantic-role-labeler))

(def ^:private all-parsers #{:stanford :srl})

(defn tokenize
  "Create annotator to split words per configured language."
  ([]
   (tokenize "en"))
  ([lang-code]
   {:component :tokenize
    :lang lang-code
    :parser :stanford}))

(defn sentence
  "Create annotator to group tokens into sentences per configured language."
  []
  {:component :sents
   :parser :stanford})

(defn stopword
  "Create annotator to annotate stop words (boolean)."
  []
  {:component :stopword
   :parser :stanford})

(defn part-of-speech
  "Create annotator to do part of speech tagging."
  ([]
   (part-of-speech "english-left3words-distsim.tagger"))
  ([pos-model-resource]
   {:component :pos
    :pos-model-resource pos-model-resource
    :parser :stanford}))

(defn named-entity-recognizer
  "Create annotator to do named entity recognition."
  ([]
   (named-entity-recognizer
    ["edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz"]))
  ([paths]
   {:component :ner
    :ner-model-paths paths
    :parser :stanford}))

(defn token-regex
  "Create annotator to token regular expression."
  ([]
   (token-regex ["token-regex.txt"]))
  ([paths]
   {:component :tok-re
    :tok-re-resources paths
    :parser :stanford}))

(defn parse-tree
  "Create annotator to create head and parse trees."
  []
  {:component :parse-tree
   :parser :stanford})

(defn dependency-parse-tree
  "Create an annotator to create a dependency parse tree."
  []
  {:component :dependency-parse-tree
   :parser :stanford})

(defn coreference
  "Create annotator to coreference tree structure."
  []
  {:component :coref
   :parser :stanford})

(defn semantic-role-labeler
  "Create a semantic role labeler annotator.

  Keys
  ----
  * **:lang** language used to create the SRL pipeline

  * **:model-type** model type used to create the SRL pipeilne

  * **first-label-token-threshold** token minimum position that contains a
  label to help decide the best SRL labeled sentence to choose."
  ([]
   (semantic-role-labeler "en"))
  ([lang-code]
   {:component :srl
    :lang lang-code
    :model-type (format "general-%s" lang-code)
    :first-label-token-threshold 3
    :parser :srl}))

(defn register-library [lib-name lib-cfg]
  ;; force recreation of default context to allow create-context invoked on
  ;; calling library at next invocation to `context` for newly registered
  ;; libraries
  (if-not (contains? @library-config-inst lib-name)
    (reset! default-context-inst nil))
  (swap! library-config-inst assoc lib-name lib-cfg))

(def ^:private conf-ns *ns*)

(defn- components-by-parsers [parsers]
  (->> all-components
       (map #((ns-resolve conf-ns %)))
       (filter #(contains? parsers (:parser %)))))

(defn create-parse-config
  [& {:keys [parsers only-tokenize? pipeline]
      :or {parsers all-parsers}}]
  {:pipeline (cond pipeline pipeline
                   only-tokenize? [(tokenize) (sentence)]
                   :else (components-by-parsers parsers))})

(defn create-context
  ([]
   (create-context (create-parse-config)))
  ([parse-config]
   (log/debugf "creating context with <%s>" parse-config)
   (->> @library-config-inst
        (map (fn [[k {:keys [create-fn]}]]
               {k (create-fn parse-config)}))
        (into {})
        (merge {:parse-config parse-config}))))

(defn reset [& {:keys [hard?]
                :or {hard? true}}]
  (log/debugf "reseting nlparse config")
  (->> @library-config-inst
       vals
       (map #((:reset-fn %) *parse-context*)))
  (if hard?
    (reset! default-context-inst nil)))

(defn- derive-context []
  (or *parse-context*
      (swap! default-context-inst #(or % (create-context)))))

(defn context [lib-name]
  (-> (derive-context)
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

(defn component-from-config [config name]
  (->> config
       :pipeline
       (filter #(= (:component %) name))
       first))

(defn- parse-fn [lib-name]
  (-> @library-config-inst
      (get lib-name)
      :parse-fn))

(defn parse-functions []
  (->> (derive-context)
       :parse-config :pipeline
       (map :parser)
       (remove nil?)
       distinct
       (map parse-fn)))

(dyn/register-purge-fn reset)
(pres/initialize)
