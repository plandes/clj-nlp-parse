(ns ^{:doc "Wraps the Stanford CoreNLP parser."
      :author "Paul Landes"}
    zensols.nlparse.stanford
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import (edu.stanford.nlp.pipeline Annotation Annotator)
           (edu.stanford.nlp.process CoreLabelTokenFactory)
           (edu.stanford.nlp.ling CoreLabel))
  (:require [zensols.actioncli.dynamic :as dyn]
            [zensols.actioncli.resource :refer (resource-path) :as res])
  (:require [zensols.nlparse.tok-re :as tre]))

(defn- initialize
  "Initialize model resource locations.

  This needs the system property `zensols.model` set to a directory that
  has the POS tagger model `english-left3words-distsim.tagger`(or whatever
  you configure in [[zensols.nlparse.stanford/create-context]]) in a directory
  called `pos`.

  See the [source documentation](https://github.com/plandes/zensols) for
  more information."
  []
  (log/debug "initializing")
  (res/register-resource :stanford-model
                         :pre-path :model :system-file "stanford")
  (res/register-resource :model :system-property "model"))

(def ^:dynamic pipeline-component-config
  "The configuration of each pipeline component defined
  in [[pipeline-components]].  This is used with [[create-context]] to create a
  pipeline object for NLP processing."
  {:tokenize {:lang "en"}
   :pos {:pos-model-resource "english-left3words-distsim.tagger"}
   :ner {:ner-model-paths ["edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz"]}
   :tok-re {:tok-re-resources ["token-regex.txt"]}})

(def ^:dynamic pipeline-components
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
  [:tokenize :sents :stopword :pos :ner :parse :coref])

(defn- compose-pipeline
  [components]
  (->> components
       (map (fn [comp]
              (merge {:component comp}
                     (get pipeline-component-config comp))))
       doall))

;; pipeline
(defn create-context
  "Create a default context that can (optionally) be configured.

  If **pipeline-compoennts** is given, create a pipeline with only the given
  components, which is a sequence of the following keywords:

  The output of this function depends on the bindings of:
  * [[pipeline-components]]
  * [[pipeline-component-config]]

  See [[with-context]]."
  []
  {:pipeline-config (compose-pipeline pipeline-components)
   :pipeline-inst (atom nil)
   :tagger-model (atom nil)
   :ner-annotator (atom nil)
   :tok-re-annotator (atom nil)
   :dependency-parse-annotator (atom nil)
   :coref-annotator (atom nil)})

(def ^{:dynamic true :private true}
  *parse-context* (create-context))

(defn reset
  "Reset all default cached pipeline components objects.  This can be invoked
  in the lexical context of [[with-context]] to reset a non-default contet."
  []
  (let [atoms [:pipeline-inst :tagger-model :ner-annotator :tok-re-annotator
               :dependency-parse-annotator :coref-annotator]]
    (doseq [atom atoms]
      (-> (get *parse-context* atom)
          (reset! nil)))))

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

(defn- create-tagger-model [pos-model-resource]
  (let [{:keys [tagger-model]} *parse-context*
        model-path (resource-path :stanford-model "pos")
        model-file (io/file model-path pos-model-resource)]
    (swap! tagger-model
           (fn [tagger]
             (log/infof "creating tagger model at %s" model-file)
             (or tagger (edu.stanford.nlp.tagger.maxent.MaxentTagger.
                         (.getAbsolutePath model-file)))))))

(defn- create-ner-annotator [ner-model-paths]
  (let [{:keys [ner-annotator]} *parse-context*]
    (swap! ner-annotator
           (fn [ann]
             (log/infof "creating ner annotators: %s" (pr-str ner-model-paths))
             (or ann (edu.stanford.nlp.pipeline.NERCombinerAnnotator.
                      (edu.stanford.nlp.ie.NERClassifierCombiner.
                       true true (into-array String ner-model-paths)) false))))))

(defn- create-tok-re-annotator [tok-re-resources]
  (let [{:keys [tok-re-annotator]} *parse-context*
        tok-re-path (resource-path :tok-re-resource)
        _ (log/infof "creating tok annotator from <%s> (%s)" tok-re-path
                     (type tok-re-path))
        tok-re-files (map #(io/file tok-re-path %) tok-re-resources)]
    (swap! tok-re-annotator
           (fn [ann]
             (let [res (map str tok-re-files)]
               (log/infof "creating token regular expression resources: %s"
                          (pr-str res))
               (or ann
                   (edu.stanford.nlp.pipeline.TokensRegexAnnotator.
                    (into-array res))))))))

(defn- create-dependency-parse-annotator []
  (let [{:keys [dependency-parse-annotator]} *parse-context*]
    (swap! dependency-parse-annotator
           #(or % (edu.stanford.nlp.pipeline.DependencyParseAnnotator.)))))

(defn- make-pipeline-component [{:keys [component] :as conf}]
  (log/debugf "creating component: %s" (pr-str component))
  (case component
    :tokenize
    {:name :tok
     :annotators [(edu.stanford.nlp.pipeline.TokenizerAnnotator.
                   false (:lang conf))]}

    :stopword
    {:name :stopword
     :annotators [(intoxicant.analytics.corenlp.StopwordAnnotator.)]}

    :sents
    {:name :sents
     :annotators [(edu.stanford.nlp.pipeline.WordsToSentencesAnnotator. false)]}

    :pos
    {:name :pos
     :annotators [(edu.stanford.nlp.pipeline.POSTaggerAnnotator.
                   (create-tagger-model (:pos-model-resource conf)))]}

    :ner
    {:name :ner
     :annotators [(edu.stanford.nlp.pipeline.MorphaAnnotator. false)
                  (create-ner-annotator (:ner-model-paths conf))
                  (edu.stanford.nlp.pipeline.EntityMentionsAnnotator.)]}

    :tok-re
    {:name :tok-re
     :annotators [(create-tok-re-annotator (:tok-re-resources conf))
                  (edu.stanford.nlp.pipeline.EntityMentionsAnnotator.)
                  (zensols.stanford.nlp.TokenRegexEntityMentionsAnnotator.)]}

    :parse
    {:name :parse
     :annotators [(edu.stanford.nlp.pipeline.ParserAnnotator. false -1)
                  (create-dependency-parse-annotator)]}

    :coref
    ;; hack to avoid NLE in RuleBasedCorefMentionFinder getting a class space
    ;; "parse" annotator starting in 3.6.0
    (let [props (doto (java.util.Properties.)
                  (.put "annotators" "tokenize,ssplit,parse"))]
      (edu.stanford.nlp.pipeline.StanfordCoreNLP. props)
      {:name :coref
       :annotators [(edu.stanford.nlp.pipeline.DeterministicCorefAnnotator.
                     (java.util.Properties.))]})))

(defn- pipeline []
  (let [{:keys [pipeline-inst pipeline-config]} *parse-context*]
    (log/debugf "creating pipeline with config <%s>" pipeline-config)
    (swap! pipeline-inst
           #(or % (map make-pipeline-component pipeline-config)))))



;; annotation getters
(def ^:private annotation-keys
  [:text :pos-tag :sent-index :token-range :token-index :index-range :char-range
   :lemma :entity-type :ner-tag :normalized-tag :stopword
   :tok-re-ner-tag :tok-re-ner-item-id :tok-re-ner-features])

(defn- get- [anon clazz]
  (if anon (.get anon clazz)))

(defn- sents- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$SentencesAnnotation))

;(defn- text- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$TextAnnotation))
(defn- text- [anon]
  (or (get- anon edu.stanford.nlp.ling.CoreAnnotations$OriginalTextAnnotation)
      (get- anon edu.stanford.nlp.ling.CoreAnnotations$TextAnnotation)))

(defn- pos-tag- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$PartOfSpeechAnnotation))

(defn- normalized-tag- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$NormalizedNamedEntityTagAnnotation))

(defn- ner-tag- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$NamedEntityTagAnnotation))

(defn- tok-re-ner-tag- [anon] (get- anon zensols.stanford.nlp.TokenRegexAnnotations$NERAnnotation))

(defn- tok-re-ner-features- [anon]
  (let [feat-str (get- anon zensols.stanford.nlp.TokenRegexAnnotations$NERFeatureCreateAnnotation)]
    (if feat-str (tre/parse-features feat-str))))

(defn- tok-re-ner-item-id- [anon]
  (let [id (get- anon zensols.stanford.nlp.TokenRegexAnnotations$NERItemIDAnnotation)]
    (if id (read-string id))))

(defn- tok-re-mentions- [anon] (get- anon zensols.stanford.nlp.TokenRegexAnnotations$MentionsAnnotation))

(defn- mentions- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$MentionsAnnotation))

(defn- entity-type- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$EntityTypeAnnotation))

(defn- lemma- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$LemmaAnnotation))

(defn- sent-index- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$SentenceIndexAnnotation))

(defn- token-index- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$IndexAnnotation))

(defn- tokens- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$TokensAnnotation))

(defn- stopword- [anon]
  (let [pair (get- anon intoxicant.analytics.corenlp.StopwordAnnotator)]
    (and pair (.first pair))))

(defn- char-range- [anon]
  (let [beg (get- anon edu.stanford.nlp.ling.CoreAnnotations$CharacterOffsetBeginAnnotation)
        end (get- anon edu.stanford.nlp.ling.CoreAnnotations$CharacterOffsetEndAnnotation)]
    (when beg end [beg end])))

(defn- token-range- [anon]
  (let [beg (get- anon edu.stanford.nlp.ling.CoreAnnotations$TokenBeginAnnotation)
        end (get- anon edu.stanford.nlp.ling.CoreAnnotations$TokenEndAnnotation)]
    (when beg end [beg end])))

(defn- index-range- [anon]
  (let [beg (get- anon edu.stanford.nlp.ling.CoreAnnotations$BeginIndexAnnotation)
        end (get- anon edu.stanford.nlp.ling.CoreAnnotations$EndIndexAnnotation)]
    (when beg end [beg end])))

(defn- dependency-parse-tree- [anon]
  (get- anon edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations$BasicDependenciesAnnotation))

(defn- parse-tree- [anon]
  (get- anon edu.stanford.nlp.trees.TreeCoreAnnotations$TreeAnnotation))

(defn- coref- [anon]
  (get- anon edu.stanford.nlp.hcoref.CorefCoreAnnotations$CorefChainAnnotation))

(defn- children- [graph node] (.getChildren graph node))

(def ^:private why-need-this-ns *ns*)

(defn- anon-word-map [anon]
  (letfn [(attr-fn-map [key]
            (let [sym (symbol (str (name key) "-"))
                  attr-fn (ns-resolve why-need-this-ns sym)]
              (if (nil? attr-fn)
                (throw (ex-info (format "No such func: <%s>" sym) {:symbol sym})))
              (attr-fn anon)))]
    (let [keys annotation-keys
          awmap (zipmap keys (map attr-fn-map keys))]
      (into {} (filter second awmap)))))

(defn- parse-tree-to-map [node]
  (when node
    (merge {:label (->> node .label .value)}
           (select-keys (->> node .label anon-word-map) [:token-index])
           (if-not (.isLeaf node)
             {:child (map parse-tree-to-map (.getChildrenAsList node))}))))

(defn- dep-parse-tree-to-map [graph]
  (when graph
    (letfn [(trav [node in-edge]
              (let [out-edges (.outgoingEdgeList graph node)]
                (java.util.Collections/sort out-edges)
                (merge (if in-edge
                         {:dep (-> in-edge .getRelation .getShortName)})
                       (select-keys (anon-word-map node) [:token-index :text])
                       (if (not (empty? out-edges))
                         {:child (map #(trav (.getTarget %) %) out-edges)}))))]
      (map #(trav % nil) (.getRoots graph)))))

(defn coref-tree-to-map [anon]
  (->> anon coref- vals       
       (map (fn [chain]
              (let [mentions (.getMentionsInTextualOrder chain)]
                {:id (.getChainID chain)
                 :mention (map (fn [cm]
                                 {:sent-index (-> cm .-sentNum)
                                  :token-range [(.-startIndex cm) (.-endIndex cm)]
                                  :head-index (-> cm .-headIndex)
                                  :gender (-> cm .-gender .toString)
                                  :animacy (-> cm .-animacy .toString)
                                        ;:span (-> cm .-mentionSpan)
                                  :type (-> cm .-mentionType .toString)
                                  :number (-> cm .-number .toString)})
                               mentions)})))
       doall))

(defn- anon-mention-map
  "Create a mention map from an annotation.  We clobber the `:text` entry since
  by default it uses the non-original annotation.  This non-original annotation
  will substitute things like parenthesis with `-LRB-` and `-RRB-`."
  [top-anon anon]
  (->> anon
       char-range-
       (apply subs (text- top-anon))
       (hash-map :text)
       (merge (anon-word-map anon))))

(defn- anon-map [anon]
  (log/debugf "tokens: %s" (tokens- anon))
  {:text (text- anon)
   :mentions (map #(anon-mention-map anon %) (mentions- anon))
   :tok-re-mentions (map #(anon-mention-map anon %) (tok-re-mentions- anon))
   :coref (coref-tree-to-map anon)
   :sents (map (fn [anon]
                 {:text (text- anon)
                  :sent-index (sent-index- anon)
                  ;; :dependency-parse-tree (dependency-parse-tree- anon)
                  ;; :parse-tree (parse-tree- anon)
                  :parse-tree (parse-tree-to-map (parse-tree- anon))
                  :dependency-parse-tree (dep-parse-tree-to-map (dependency-parse-tree- anon))
                  :tokens (map anon-word-map (tokens- anon))})
               (sents- anon))})

;; parse
(defn- invoke-annotator [context annotator anon]
  (log/debugf "invoking %s on %s" annotator anon)
  (.annotate annotator anon))

(defn- parse-with-pipeline [pipeline context anon]
  (log/debugf "parse: pipeline: %s, context: %s, anon: %s"
              (pr-str pipeline) (pr-str context) anon)
  (reduce (fn [anon-data annotator-data]
            (log/tracef "reduce: %s: anon: %s, annotator-data: %s"
                        (:name annotator-data)
                        anon annotator-data)
            (let [annotators (:annotators annotator-data)
                  func (:function annotator-data)
                  anon (if (sequential? anon-data)
                         (first anon-data)
                         anon-data)]
              (if annotators
                (do
                  (doseq [elt annotators]
                    (log/tracef "context: %s, ann: %s, anon: %s"
                                context elt anon)
                    (invoke-annotator context elt anon))
                  anon)
                (do
                  (log/debugf "invoking func %s <%s>" func anon)
                  (func context anon)))))
          anon pipeline))

(defn- parse-raw [utterance]
  (let [anon (Annotation. utterance)
        context (atom {})
        pipeline (pipeline)]
    (parse-with-pipeline pipeline context anon)))

(defn parse
  "Parse natural language **utterance**.

  See [[zensols.nlparse.parse/parse]] for a superset hierarchy of what this
  returns.

  See [[with-context]] and [[create-context]]."
  [utterance]
  (log/infof "parsing: <%s>" utterance)
  (->> (parse-raw utterance)
       anon-map))

(defn- tokens-equal [a b]
  (and (= (text- a) (text- b))
       (= (sent-index- a) (sent-index- b))
       (= (token-range- a) (token-range- b))))

(defn- pranon [anon & {:keys [full-class-name?] :or {full-class-name? false}}]
  (println (apply str (take 40 (repeat '-))))
  (dorun (map (fn [key]
                (println (format "%s => %s"
                                 (if full-class-name?
                                   (.getName key)
                                   (second (re-find #"^.*\$(.*)$"
                                                    (.getName key))))
                                 (.get anon key))))
              (.keySet anon))))

(defn- pranon-deep [anon]
  (println (apply str (repeat 70 \=)) "top level")
  (pranon anon :full-class-name? true)
  (println (apply str (repeat 70 \-)) "sents")
  (doall
   (map (fn [sent-anon]
          (pranon sent-anon)
          (doall (map pranon (tokens- sent-anon))))
        (sents- anon)))
  (println (apply str (repeat 70 \-)) "all"))

(defn parse-debug [utterance]
  (->> (parse-raw utterance)
       pranon-deep))

(dyn/register-purge-fn reset)
(initialize)
