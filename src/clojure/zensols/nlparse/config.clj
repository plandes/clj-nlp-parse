(ns ^{:doc "Configure the Stanford CoreNLP parser.

This provides a plugin architecture for natural language processing tasks in a
pipeline.  A parser takes either an human language utterance or a previously
annotated data parsed from an utterance.


### Parser Libraries

Each parser provides a set of *components* that make up the pipeline.  Each
component (i.e. [[tokenize]]) is a function that returns a map including a map
containing keys:

* **component** a key that's the name of the component to create.
* **parser** a key that is the name of the parser it belongs to.

For example, the Stanford CoreNLP word tokenizer has the following return map:

* **:component** :tokenize
* **:lang**  *lang-code* (e.g. `en`)
* **:parser** :stanford

The map also has additional key/value pairs that represent remaining
configuration given to the parser library used to create it's pipeline
components.  All parse library names (keys) are given in [[all-parsers]].

Use [[register-library]] to add your library with the key name of your parser.


### Usage

You can either create your own custom parser configuration
with [[create-parse-config]] and then create it's respective context
with [[create-context]].  If you do this, then each parse call needs to be in
a [[with-context]] lexical context.  If you don't, a default context is created
and used for each parse invocation.

Once/if configured, use [[zensols.nlparse.parse/parse]] to invoke the parsing
pipeline."
      :author "Paul Landes"}
    zensols.nlparse.config
  (:require [clojure.tools.logging :as log]
            [clojure.test :as test]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.repl :as repl])
  (:require [zensols.actioncli.dynamic :refer [defa- undef] :as dyn])
  (:require [zensols.nlparse.resource :as pres]))

(def ^{:dynamic true :private true}
  *parse-context*
  "Parse context that's created with [[create-context]]."
  nil)

(defa- library-config-inst)

(defa- default-context-inst)

(def all-parsers
  "All parsers available in this package (jar)."
  #{:stanford :srl})

(defn tokenize
  "Create annotator to split words per configured language.  The tokenization
  langauge is set with the **lang-code** parameter, which is a two string
  language code and defaults to English."
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
  "Create annotator to do part of speech tagging.  You can set the model with a
  resource identified with the **pos-model-resource** string, which defaults to
  the [English WSJ trained
  corpus](http://www-nlp.stanford.edu/software/pos-tagger-faq.shtml)."
  ([]
   (part-of-speech "english-left3words-distsim.tagger"))
  ([pos-model-resource]
   {:component :pos
    :pos-model-resource pos-model-resource
    :parser :stanford}))

(defn morphology
  "Create a morphology annotator, which adds the lemmatization of a word.  This
  adds the `:lemma` keyword to each token.."
  []
  {:component :morph
   :parser :stanford})

(defn named-entity-recognizer
  "Create annotator to do named entity recognition.  All models in the
  **paths** sequence are loaded.  By default, the [English CoNLL 4
  class](http://www.cnts.ua.ac.be/conll2003/ner/) is used.  See the [Stanford
  NER](http://nlp.stanford.edu/software/CRF-NER.shtml) for more information."
  ([]
   (named-entity-recognizer
    ["edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz"]))
  ([paths]
   {:component :ner
    :ner-model-paths paths
    :parser :stanford}))

(defn token-regex
  "Create annotator to token regular expression.  You can configure an array of
  strings identifying either resources or files using the **paths** parameter,
  which defaults to `token-regex.txt`, which is included in the resources of
  this package as an example and used with the test cases."
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
  "Create a semantic role labeler annotator.  You can configure the language
  with the **lang-code**, which is a two string language code and defaults to
  English.

Keys
----
* **:lang** language used to create the SRL pipeline
* **:model-type** model type used to create the SRL pipeilne
* **:first-label-token-threshold** token minimum position that contains a
label to help decide the best SRL labeled sentence to choose."
  ([]
   (semantic-role-labeler "en"))
  ([lang-code]
   {:component :srl
    :lang lang-code
    :model-type (format "general-%s" lang-code)
    :first-label-token-threshold 3
    :parser :srl}))

(defn register-library
  "Register plugin library **lib-name** with **lib-cfg** a map containing:

* **:create-fn** a function taht takes a parse
  configuration (see [[create-parse-config]]) to create a context later
  returned with [[context]]
* **:reset-fn** a function that takes the parse context to `null` out any atoms
  or cached data structures; this is called by [[reset]
* **:parse-fn** a function that takes a signle human language utterance string
  or output of another parse library's output

* **:component-fns** all component creating functions from this library

  *Implementation note*: this forces re-creation of the default context (see
  the [usage section](#usage)) to allow create-context invoked on calling
  library at next invocation to `context` for newly registered libraries."
  [lib-name lib-cfg]
  (if-not (contains? @library-config-inst lib-name)
    (reset! default-context-inst nil))
  (swap! library-config-inst assoc lib-name lib-cfg))

(defn- components-by-parsers
  "Return all components given in set **parsers**."
  [parsers]
  (let [conf @library-config-inst]
    (->> parsers
         (map (fn [parser]
                (->> (get conf parser)
                     :component-fns
                     (map #(%)))))
         (apply concat))))

(defn create-parse-config
  "Create a parse configuration given as input to [[create-context]].

  If no keys are given all components are
  configured (see [[components-as-string]]).

Keys
----
* **:only-tokenize?** create a parse configuration that only utilizes the
  tokenization of the Stanford CoreNLP library.
* **:pipeline** a list of components created with one of the many component
  create functions (i.e. [[tokenize]]) or from a roll-your-own add-on library;
  this redners the `:parsers` key unsued
* **:parsers** a set of parser library names (keys) used to indicate which
  components to return (i.e. `:stanford`); see [[all-parsers]]"
  [& {:keys [parsers only-tokenize? pipeline]
      :or {parsers all-parsers}}]
  {:pipeline (cond pipeline pipeline
                   only-tokenize? [(tokenize) (sentence)]
                   :else (components-by-parsers parsers))})

(defn- registered-components
  "Return all registered component functions.  This is the `:component-fns` key
  in the registeration map provided by each parser."
  []
  (zipmap (->> @library-config-inst
               vals
               (map :component-fns)
               (apply concat)
               (map #(->> % pr-str (re-find #"\[[^/]+\/(.*)\]") second)))
          (->> @library-config-inst
               (map (fn [[k v]]
                      (map #(%) (->> v :component-fns))))
               (apply concat))))

(defn create-parse-config-by-string
  "See [[create-context]]."
  [components]
  (let [comps (registered-components)]
    (->> components
         (#(s/split % #","))
         (map (fn [comp-str]
                (or (get comps comp-str)
                    (throw (ex-info (format "No such component: %s" comp-str)
                                    {:component comp-str})))))
         (create-parse-config :pipeline))))

(defn create-context
  "Return a context used during parsing.  This calls all
  registered ([[register-library]]) parse libraries create functions and
  returns an object to be used with the parse
  function [[zensols.nlparse.parse/parse]]

  The parameter **parse-config** is either a parse configuration created
  with [[create-parse-config]] or a string.  If a string is used for the
  **parse-config** parameter create pipeline by component names separated by
  commas.  For example:

  `tokenize,sentence,part-of-speech,morphology`

  Creates a pipeline that tokenizes, adds POS and lemmas.  Using the output
  of [[components-of-string]] would create all components.  However, the easier
  way to utilize all components is to to call this function with no parameters.

  See the [usage section](#usage) section."
  ([]
   (create-context (create-parse-config)))
  ([parse-config]
   (if (string? parse-config)
     (create-context (create-parse-config-by-string parse-config))
     (do
       (log/debugf "creating context with <%s>" parse-config)
       (->> @library-config-inst
            (map (fn [[k {:keys [create-fn]}]]
                   {k (create-fn parse-config)}))
            (into {})
            (merge {:parse-config parse-config}))))))

(defn components-as-string
  "Return all available components as a string"
  []
  (->> (registered-components)
       keys
       (s/join ",")))

(defn reset
  "Reset the cached data structures and configuration in the default (or
  currently bound [[with-context]]) context.  This is also called
  by [[zensosls.actioncli.dynamic/purge]]."
  [& {:keys [hard?] :or {hard? true}}]
  (log/debugf "reseting nlparse config")
  (->> @library-config-inst
       vals
       (map #((:reset-fn %) *parse-context*)))
  (if hard?
    (reset! default-context-inst nil)))

(defn- derive-context []
  (or *parse-context*
      (swap! default-context-inst #(or % (create-context)))))

(defn context
  "Return context created with [[create-context]].

  See the [usage section](#usage) section."
  [lib-name]
  (-> (derive-context)
      (get lib-name)))

(defmacro with-context
  "Use the parser with a context created with [[create-context]].
  This context is optionally configured.  Without this macro the default
  context is used as described in the [usage section](#usage) section."
  {:style/indent 1}
  [context & forms]
  `(let [context# ~context]
     (binding [*parse-context* context#]
       ~@forms)))

(defn component-from-config
  "Return a component by **name** from parse **config**."
  [config name]
  (->> config
       :pipeline
       (filter #(= (:component %) name))
       first))

(defn- parse-fn [lib-name]
  (-> @library-config-inst
      (get lib-name)
      :parse-fn))

(defn parse-functions
  "Return all registered parse function in the order they are to be called.

  See the [usage section](#usage) section."
  []
  (->> (derive-context)
       :parse-config :pipeline
       (map (fn [{:keys [parser] :as comp}]
              (log/debugf "comp %s" comp)
              (if-not parser
                (log/warnf "no parser defined for component: %s" comp))
              parser))
       (remove nil?)
       distinct
       (map parse-fn)))

(defn- function-var
  "Return function metadata with a map of the `:fn-namespace`, `:fn-name`,
  and `:fn-var`."
  [fn-object]
  (let [[ns name] (->> fn-object pr-str
                       (re-find #"\[([^/]+)\/(.*)\]")
                       rest
                       (map symbol))]
    {:fn-namespace ns
     :fn-name name
     :fn-var (ns-resolve ns name)}))

(defn component-documentation
  "Return maps doc documentation with keys `:name` and `:doc`."
  []
  (->> @library-config-inst
       vals
       (map :component-fns)
       (apply concat)
       (map function-var)
       (map (fn [{:keys [fn-namespace fn-name fn-var]}]
              {:name (name fn-name)
               :doc (->> fn-var meta :doc)}))))

(defn print-component-documentation
  "Print the formatted component documentation see
  [[component-documentation]]."
  []
  (->> (component-documentation)
       (map (fn [{:keys [name doc]}]
              (with-out-str
                (println name)
                (->> (repeat (count name) \-) (apply str) println)
                (->> (io/reader (java.io.StringReader. doc))
                     line-seq
                     (map s/trim)
                     (s/join \newline)
                     print))))
       (map s/trim)
       (s/join (str \newline\newline))
       print))

(dyn/register-purge-fn reset)
(pres/initialize)
