(ns ^{:doc "Wraps the Stanford CoreNLP parser."
      :author "Paul Landes"}
    zensols.nlparse.stanford
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import (edu.stanford.nlp.pipeline Annotation Annotator)
           (edu.stanford.nlp.process CoreLabelTokenFactory)
           (edu.stanford.nlp.ling CoreLabel))
  (:require [zensols.actioncli.dynamic :refer (dyn-init-var) :as dyn]
            [zensols.actioncli.resource :refer (resource-path)]))

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
  (res/register-resource :model :system-property "model")
  (res/register-resource :stanford-model
                         :pre-path :model :system-file "stanford"))

;; pipeline
(defn create-context
  "Create a default context that can (optionally) be configured.

  See [[with-context]]."
  []
  {:pipeline-config
   [{:component :tokenize
     :lang "en"}
    {:component :sents}
    {:component :stopword}
    {:component :pos
     :pos-model-resource "english-left3words-distsim.tagger"}
    {:component :ner
     :ner-model-paths ["edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz"]}
    {:component :tree}]
   :pipeline-inst (atom nil)
   :tagger-model (atom nil)
   :ner-annotator (atom nil)
   :dependency-parse-annotator (atom nil)})

(def ^{:dynamic true :private true}
  *parse-context* (create-context))

(defn- reset []
  (let [atoms [:pipeline-inst :tagger-model :ner-annotator
               :dependency-parse-annotator]]
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

    :tree
    {:name :tree
     :annotators [(edu.stanford.nlp.pipeline.ParserAnnotator. false -1)
                  (create-dependency-parse-annotator)]}))

(defn- pipeline []
  (let [{:keys [pipeline-inst pipeline-config]} *parse-context*]
    (log/debugf "creating pipeline with config <%s>" pipeline-config)
    (swap! pipeline-inst
           #(or % (map make-pipeline-component pipeline-config)))))



;; annotation getters
(def ^:private annotation-keys
  [:text :pos-tag :sent-index :token-range :token-index :char-range
   :lemma :entity-type :ner-tag :normalized-tag :stopword])

(defn- get- [anon clazz]
  (if anon (.get anon clazz)))

(defn- sents- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$SentencesAnnotation))

(defn- text- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$TextAnnotation))

(defn- pos-tag- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$PartOfSpeechAnnotation))

(defn- normalized-tag- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$NormalizedNamedEntityTagAnnotation))

(defn- ner-tag- [anon] (get- anon edu.stanford.nlp.ling.CoreAnnotations$NamedEntityTagAnnotation))

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

(defn- dependency-parse-tree- [anon]
  (get- anon edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations$BasicDependenciesAnnotation))

(defn- parse-tree- [anon]
  (get- anon edu.stanford.nlp.trees.TreeCoreAnnotations$TreeAnnotation))

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
  (merge {:label (->> node .label .value)}
         (select-keys (->> node .label anon-word-map) [:token-index])
         (if-not (.isLeaf node)
           {:child (map parse-tree-to-map (.getChildrenAsList node))})))

(defn- dep-parse-tree-to-map [graph]
  (letfn [(trav [node in-edge]
            (let [out-edges (.outgoingEdgeList graph node)]
              (java.util.Collections/sort out-edges)
              (merge (if in-edge
                       {:dep (-> in-edge .getRelation .getShortName)})
                     (select-keys (anon-word-map node) [:token-index :text])
                     (if (not (empty? out-edges))
                       {:child (map #(trav (.getTarget %) %) out-edges)}))))]
    (map #(trav % nil) (.getRoots graph))))

(defn- anon-map [anon]
  (log/debugf "tokens: %s" (tokens- anon))
  {:text (text- anon)
   :mentions (map anon-word-map (mentions- anon))
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

(defn parse
  "Parse natural language **utterance**.

  See [[zensols.nlparse.parse/parse]] for a superset hierarchy of what this
  returns.

  See [[with-context]] and [[create-context]]."
  [utterance]
  (log/infof "parsing: <%s>" utterance)
  (let [anon (Annotation. utterance)
        context (atom {})
        pipeline (pipeline)]
    (->> (parse-with-pipeline pipeline context anon)
         anon-map)))

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

(defn- parse-raw [utterance]
  (let [anon (Annotation. utterance)
        context (atom {})
        pipeline (pipeline)]
    (parse-with-pipeline pipeline context anon)))

(defn parse-debug [utterance]
  (let [anon (Annotation. utterance)
        context (atom {})
        pipeline (pipeline)]
    (pranon-deep (parse-with-pipeline pipeline context anon))))

(dyn/register-purge-fn reset)
(initialize)
