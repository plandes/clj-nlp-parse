(ns ^{:doc "Wrap ClearNLP SRL.

  Currently the propbank trained version is used.  The main classification
  function is [[label]]."
      :author "Paul Landes"}
    zensols.nlparse.srl
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:use [clojure.pprint :only (pprint)])
  (:import (java.io BufferedInputStream ObjectInputStream)
           (java.util.zip GZIPInputStream))
  (:import (com.clearnlp.component AbstractComponent)
           (com.clearnlp.nlp NLPGetter NLPMode)
           (com.clearnlp.reader AbstractReader)
           (com.clearnlp.propbank.verbnet PVMap)
           (com.clearnlp.component.pos AbstractPOSTagger EnglishPOSTagger)
           (com.clearnlp.dependency DEPNode DEPLib)
           (com.clearnlp.util UTInput))
  (:require [zensols.actioncli.util :refer (trunc)])
  (:require [zensols.nlparse.config :as conf]))

(def ^:dynamic first-label-token-threshold
  "Token minimum position that contains a label to help decide the best SRL
  labeled sentence to choose."
  3)

(def ^:private monitor (Object.))

(defn- create-context
  [parse-config]
  (log/debugf "create context with parse-config: %s" (pr-str parse-config))
  (let [srl-comp (conf/component-from-config parse-config :srl)]
    (log/debugf "component: %s" (pr-str srl-comp))
    {:config srl-comp
     :pipeline-inst (atom nil)}))

(defn- reset-context
  [parse-context]
  (when parse-context
    (reset! (:pipeline-inst parse-context) nil)))

(defn- nlp-object-input-stream [path mode]
  (ObjectInputStream.
   (BufferedInputStream.
    (GZIPInputStream.
     (UTInput/getInputStreamsFromClasspath (str path "/" mode))))))

(defn- create-pipeline [lang model-type]
  (log/infof "creating SRL pipeline lang: %s, model-type: %s" lang model-type)
  (let [tagger (EnglishPOSTagger. (nlp-object-input-stream model-type NLPMode/MODE_POS))
        parser (NLPGetter/getComponent model-type lang NLPMode/MODE_DEP)
        identifier (NLPGetter/getComponent model-type lang NLPMode/MODE_PRED)
        classifier (NLPGetter/getComponent model-type lang NLPMode/MODE_ROLE)
        labeler (NLPGetter/getComponent model-type lang NLPMode/MODE_SRL)
        ;; need this to avoid a NPE
        kluge (proxy [AbstractComponent] []
                (process [tree]
                  (doseq [node tree]
                    (if (nil? (.getXHeads node))
                      (.setXHeads node [])))))]
    {:parser parser
     :pre [tagger]
     :post [identifier classifier labeler kluge]}))

(defn- pipeline []
  (let [{:keys [pipeline-inst config]} (conf/context :srl)
        {:keys [lang model-type]} config]
    (locking monitor
      (swap! pipeline-inst
             (fn [pl]
               (if pl
                 (do (log/debugf "reusing cached pipeline: <%s>" (trunc pl))
                     pl)
                 (let [pl (create-pipeline lang model-type)]
                   (log/debugf "created new pipeline: <%s>" pl)
                   pl)))))))

(defn- parse-trees [pipeline tree]
  (let [parser (:parser pipeline)]
    (doseq [comp (:pre pipeline)]
      (.process comp tree))
    (let [trees (.getParsedTrees parser tree true)]
      (doseq [tree trees]
        (doseq [comp (:post pipeline)]
          (.process comp (.-o tree))))
      trees)))

(defn parse-sentences
  "Returns a list of hashs each having a list of sentence trees of the form:
  ({:sent, :score, :tree) ...}.  Each element's :tree value is a list of
  DEPTree instances.  Parameter is an object returned
  from [[parse-todo/parse]]."
  [sents]
  (let [pipeline (pipeline)]
    (map (fn [sent]
           (let [tree (NLPGetter/toDEPTree sent)
                 trees (parse-trees pipeline tree)]
             (map (fn [pair]
                    (let [ptree (.-o pair)]
                      {:sent-text (str/join " " sent)
                       :score (.-d pair)
                       :tree ptree}))
                  trees)))
         sents)))

(defn- propbank-feature [^DEPNode node]
  (let [feats (.getFeats node)]
    (if feats (.get feats DEPLib/FEAT_PB))))

(defn- sentence-sort-by-score [trees]
  (sort (fn [a b]
          (compare (:score b) (:score a)))
        trees))

(defn- first-with-label [sent-trees]
  (first
   (filter
    identity
    (map (fn [sent-tree]
           (if (first (filter (fn [tree-data]
                                (propbank-feature tree-data))
                              (:tree sent-tree)))
             sent-tree))
         sent-trees))))

(defn- short-sentence-best [sent-trees]
  ;; subtract one for the root node (not used a token)
  (let [tok-count (- (count (:tree (first sent-trees))) 1)]
    (if (<= tok-count first-label-token-threshold)
      (or (first-with-label sent-trees)
          (first sent-trees))
      (first sent-trees))))

(defn classify-sent-trees
  "Return the max likelihood (by score returned from [[parse-sentences]]) tree
  set.  The returned value is a list of sentences most likely to be correct."
  ([sent-trees]
   (classify-sent-trees sent-trees short-sentence-best))
  ([sent-trees classify-fn]
   (map #(classify-fn (sentence-sort-by-score %))
        sent-trees)))

(defn- mapify-parsed-sentences [sent-datas]
 (map (fn [sent-data]
        (map (fn [node]
               (let [heads (.getSHeads node)
                     head (.getHead node)
                     pb (propbank-feature node)]
                 {:id (.-id node)
                  ;:form (.-form node)
                  ;:lemma (.-lemma node)
                  ;:pos (.-pos node)
                  :propbank pb
                  :head-id (if head (.-id head))
                  :dependency-label (if head (.getLabel head))
                  :heads (if (not (.isEmpty heads))
                           (doall (map (fn [arc]
                                         {:function-tag (.getFunctionTag arc)
                                          :dependency-label (.getLabel arc)})
                                       heads)))}))
             (rest (:tree sent-data))))
      sent-datas))

(defn head-function-tags
  "Return function tags (under the head level)."
  []
  (set ["DIR" "GOL" "LOC" "PAG" "PPT" "PRD" "VSP" "CAU" "MNR" "TMP" "COM"
        "PRP" "EXT"]))

(defn head-dependency-labels
  "Return all argument labels for head dependencies."
  []
  (set ["A0" "A1" "A2" "A3" "A4" "AM-ADV" "AM-DIR" "AM-PRR" "AM-DIS" "AM-EXT"
        "AM-GOL" "AM-LOC" "AM-MNR" "AM-PRP" "AM-TMP" "C-V" "R-A1" "R-AM-TMP"
        "AM-MOD" "R-AM-GOL" "AM-NEG" "AM-COM" "R-AM-MNR" "C-A1" "AM-CAU"
        "R-A0" "R-AM-CAU" "AM-PRD" "AM-PNC" "R-A2" "R-AM-LOC" "R-AM-PRP"
        "AM-REC" "R-AM-DIR" "EXT" "R-A3" "R-AM-DIS" "A1-DSP" "R-AM-EXT"]))

(defn dependency-labels
  "Return dependency labels (under the head level and at the function tag
  level)."
  []
  (set ["advcl" "advmod" "amod" "appos" "attr" "auxpass" "conj" "dep" "dobj"
        "nn" "npadvmod" "nsubj" "partmod" "pcomp" "pobj" "poss" "prep"
        "root" "xcomp" "ccomp" "nmod" "prt" "punct" "rcmod" "iobj" "num"
        "mark" "infmod" "cc" "acomp" "oprd" "det" "agent" "nsubjpass" "hmod"
        "aux" "meta" "intj" "parataxis" "csubj" "preconj" "csubjpass" "neg"
        "number" "possessive" "expl" "predet"]))

(defn label
  "Label (classify) and return the tokenized sequence **tokens** of a sentence.

  * **tokens** are a sequence of strings that make up a sentence"
  [tokens]
  (-> tokens
      list
      parse-sentences
      classify-sent-trees
      mapify-parsed-sentences
      first))

(defn parse [panon]
  (->> panon
       :sents
       (map (fn [sent]
              (let [toks (:tokens sent)]
                (->> toks
                     (map :text)
                     (label)
                     (#(map (fn [tok srl]
                              (assoc tok :srl (dissoc srl [:form :lemma])))
                            toks %))
                     (assoc sent :tokens)))))
       (assoc panon :sents)))

(conf/register-library :srl {:create-fn create-context
                             :reset-fn reset-context
                             :parse-fn parse
                             :component-fns [(var conf/semantic-role-labeler)]})
