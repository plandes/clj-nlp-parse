(ns ^{:doc "Wraps the Stanford CoreNLP parser."
      :author "Paul Landes"}
    zensols.nlparse.stanford
  (:import [java.util Properties]
           [edu.stanford.nlp.pipeline Annotation])
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.string :as s])
  (:require [zensols.actioncli.dynamic :as dyn]
            [zensols.actioncli.resource :as res])
  (:require [zensols.nlparse.util :as util]
            [zensols.nlparse.tok-re :as tre]
            [zensols.nlparse.config :as conf]))

(def ^:private annotator-prop-name (->> *ns* ns-name name))

(def ^:private all-components
  '(tokenize
    sentence
    part-of-speech
    morphology
    stopword
    named-entity-recognizer
    parse-tree
    dependency-parse-tree
    sentiment
    coreference))

;; pipeline
(defn- create-context
  [parse-config]
  {:pipeline-config (:pipeline parse-config)
   :pipeline-inst (atom nil)
   :tagger-model (atom nil)
   :ner-annotator (atom nil)
   :tok-re-annotator (atom nil)
   :dependency-parse-annotator (atom nil)
   :coref-annotator (atom nil)})

(defn- reset-context
  "Reset all default cached pipeline components objects.  This can be invoked
  in the lexical context of [[with-context]] to reset a non-default contet."
  [parse-context]
  (log/infof "resting <%s>" (pr-str parse-context))
  (when parse-context
    (let [atoms [:pipeline-inst :tagger-model :ner-annotator :tok-re-annotator
                 :dependency-parse-annotator :coref-annotator]]
      (doseq [atom atoms]
        (-> (get parse-context atom)
            (reset! nil))))))

(defn- context []
  (conf/context :stanford))

(defn- into-properties
  ([] (into-properties {}))
  ([map]
   (doto (Properties.)
     (.putAll map))))

(defn- create-tagger-model [pos-model-resource]
  (let [{:keys [tagger-model]} (context)
        model-res (res/resource-path :stanford-pos-tagger pos-model-resource)
        model-res (if (instance? java.io.File model-res)
                    (.getAbsolutePath model-res)
                    model-res)]
    (swap! tagger-model
           (fn [tagger]
             (if-not tagger
               (do (log/infof "creating tagger model with %s" model-res)
                   (edu.stanford.nlp.tagger.maxent.MaxentTagger. model-res))
               tagger)))))

(defn- ner-classifier-combiner [ner-model-paths lang]
  (let [lang (edu.stanford.nlp.ie.NERClassifierCombiner$Language/valueOf lang)]
    (->> (into-array String ner-model-paths)
         (edu.stanford.nlp.ie.NERClassifierCombiner.
          true lang true true (into-properties)))))

(defn- create-ner-annotator [ner-model-paths lang]
  (let [{:keys [ner-annotator]} (context)]
    (swap! ner-annotator
           (fn [ann]
             (if-not ann
               (do (log/infof "creating ner annotators: %s"
                              (pr-str ner-model-paths))
                   (edu.stanford.nlp.pipeline.NERCombinerAnnotator.
                    (ner-classifier-combiner ner-model-paths lang) false)))))))

(defn- create-tok-re-annotator [tok-re-resources]
  (let [{:keys [tok-re-annotator]} (context)
        tok-re-path (res/resource-path :tok-re-resource)
        _ (log/infof "creating tok annotator from <%s> (%s)" tok-re-path
                     (type tok-re-path))
        tok-re-resources (if (sequential? (first tok-re-resources))
                           tok-re-resources
                           (list tok-re-resources))]
    (swap! tok-re-annotator
           (fn [ann]
             (->> tok-re-resources
                  (map (fn [paths]
                         (let [paths (map #(->> % (io/file tok-re-path) str)
                                          paths)]
                           (edu.stanford.nlp.pipeline.TokensRegexAnnotator.
                            (into-array paths))))))))))

(defn- create-dependency-parse-annotator []
  (let [{:keys [dependency-parse-annotator]} (context)]
    (swap! dependency-parse-annotator
           #(or % (edu.stanford.nlp.pipeline.DependencyParseAnnotator.)))))

(defn- create-tok-re-mentions-annotator []
  (->> ["nerCoreAnnotation" "nerNormalizedCoreAnnotation" "mentionsCoreAnnotation"]
       (map #(str annotator-prop-name "." %))
       (#(zipmap % (map (fn [c] (.getName c))
                        [zensols.stanford.nlp.TokenRegexAnnotations$NERAnnotation
                         zensols.stanford.nlp.TokenRegexAnnotations$NERNormalizedAnnotation
                         zensols.stanford.nlp.TokenRegexAnnotations$MentionsAnnotation])))
       into-properties
       (edu.stanford.nlp.pipeline.EntityMentionsAnnotator. annotator-prop-name)))

(defn- make-pipeline-component [{:keys [component] :as conf}]
  (log/debugf "creating component: %s" (pr-str component))
  (let [props (->> {(str annotator-prop-name "." "binaryTrees") "true"}
                   into-properties)]
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

     :morph
     {:name :morph
      :annotators [(edu.stanford.nlp.pipeline.MorphaAnnotator. false)]}

     :ner
     {:name :ner
      :annotators [(create-ner-annotator (:ner-model-paths conf)
                                         (:language conf))
                   (edu.stanford.nlp.pipeline.EntityMentionsAnnotator.)]}

     :tok-re
     {:name :tok-re
      :annotators (->> [(create-tok-re-annotator (:tok-re-resources conf))
                        (edu.stanford.nlp.pipeline.EntityMentionsAnnotator.)
                        (create-tok-re-mentions-annotator)]
                       flatten vec)}

     :parse-tree
     {:name :parse-tree
      :annotators [(edu.stanford.nlp.pipeline.ParserAnnotator.
                    annotator-prop-name props)]}

     :sentiment
     {:name :sentiment
      :annotators (->> [(edu.stanford.nlp.pipeline.SentimentAnnotator.
                         annotator-prop-name props)])}

     :dependency-parse-tree
     {:name :dependency-parse-tree
      :annotators [(create-dependency-parse-annotator)]}

     :coref
     ;; hack to avoid NLE in RuleBasedCorefMentionFinder getting a class space
     ;; "parse" annotator starting in 3.6.0
     (let [props (into-properties {"annotators" "tokenize,ssplit,parse"})]
       (edu.stanford.nlp.pipeline.StanfordCoreNLP. props)
       {:name :coref
        :annotators [(edu.stanford.nlp.pipeline.DeterministicCorefAnnotator.
                      props)]})
     (log/debugf "skipping non-library compponent: %s" (pr-str conf)))))

(defn- pipeline []
  (let [{:keys [pipeline-inst pipeline-config]} (context)]
    (log/debugf "creating pipeline with config <%s>" (pr-str pipeline-config))
    (swap! pipeline-inst
           #(or % (->> (map make-pipeline-component pipeline-config)
                       (remove nil?))))))



;; annotation getters
(def ^:private annotation-keys
  [:text :pos-tag :sent-index :token-range :token-index :index-range :char-range
   :lemma :entity-type :ner-tag :normalized-tag :stopword :stoplemma
   :sentiment :tok-re-ner-tag :tok-re-ner-item-id :tok-re-ner-features])

(defn- get- [anon clazz]
  (.get anon clazz))

(defn- sents- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$SentencesAnnotation))

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

(defn- stoplemma- [anon]
  (let [pair (get- anon intoxicant.analytics.corenlp.StopwordAnnotator)]
    (and pair (.second pair))))

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

(defn- sentiment- [anon]
  (let [stag (get- anon edu.stanford.nlp.sentiment.SentimentCoreAnnotations$SentimentClass)
        stag (if stag (s/lower-case stag))]
    (case stag
      "positive" 1
      "very positive" 2
      "negative" -1
      "very negative" -2
      "neutral" 0
      nil)))

(defn- sentiment-tree- [anon]
  (get- anon edu.stanford.nlp.sentiment.SentimentCoreAnnotations$SentimentAnnotatedTree))

(defn- rnn-predicted-class [anon]
  (get- anon edu.stanford.nlp.neural.rnn.RNNCoreAnnotations$PredictedClass))

(defn- coref- [anon]
  (get- anon edu.stanford.nlp.dcoref.CorefCoreAnnotations$CorefChainAnnotation))

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
    (let [label (.label node)]
     (merge {:label (->> label .value)}
            (select-keys (->> label anon-word-map)
                         [:token-index :index-range :sentiment])
            (let [score (.score node)]
              (if-not (Double/isNaN score)
                {:score score}))
            (if-not (.isLeaf node)
              {:child (map parse-tree-to-map (.getChildrenAsList node))})))))

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

(defn- anon-sent-map [anon]
  (->> [[:text (text- anon)]
        [:sent-index (sent-index- anon)]
        [:parse-tree (parse-tree-to-map (parse-tree- anon))]
        [:dependency-parse-tree
         (dep-parse-tree-to-map (dependency-parse-tree- anon))]
        [:sentiment (sentiment- anon)]
        ;[:sentiment-tree (parse-tree-to-map (sentiment-tree- anon))]
        [:tokens (map anon-word-map (tokens- anon))]]
       util/map-if-data))

(defn- anon-map [anon]
  (log/debugf "tokens: %s" (tokens- anon))
  (let [agg? (->> (conf/component-from-context (context) :sentiment)
                  :aggregate?)]
   (->> [[:text (text- anon)]
         [:mentions (map #(anon-mention-map anon %) (mentions- anon))]
         [:tok-re-mentions (map #(anon-mention-map anon %)
                                (tok-re-mentions- anon))]
         [:sentiment (if agg? (->> anon sents- (map sentiment-) (reduce +)))]
         [:coref (coref-tree-to-map anon)]
         [:sents (map anon-sent-map (sents- anon))]]
        util/map-if-data)))



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

(defn- parse
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

(let [comps (map #(ns-resolve 'zensols.nlparse.config %)
                 all-components)]
  (conf/register-library :stanford {:create-fn create-context
                                    :reset-fn reset-context
                                    :parse-fn parse
                                    :component-fns comps}))
