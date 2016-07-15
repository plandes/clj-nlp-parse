(ns ^{:doc "Parse an utterance using the Stanford CoreNLP and the ClearNLP SRL."
      :author "Paul Landes"}
    zensols.nlparse.parse
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.set :as set])
  (:require [zensols.nlparse.stanford :as sp]
            [zensols.nlparse.srl :as srl]))

(def penn-treebank-pos-tags
  "Alphabetical list of part-of-speech tags used in the [Penn Treebank
  Project](https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html)."
  {"CC" "Coordinating conjunction"
   "CD" "Cardinal number"
   "DT" "Determiner"
   "EX" "Existential there"
   "FW" "Foreign word"
   "IN" "Preposition or subordinating conjunction"
   "JJ" "Adjective"
   "JJR" "Adjective, comparative"
   "JJS" "Adjective, superlative"
   "LS" "List item marker"
   "MD" "Modal"
   "NN" "Noun, singular or mass"
   "NNS" "Noun, plural"
   "NNP" "Proper noun, singular"
   "NNPS" "Proper noun, plural"
   "PDT" "Predeterminer"
   "POS" "Possessive ending"
   "PRP" "Personal pronoun"
   "PRP$" "Possessive pronoun"
   "RB" "Adverb"
   "RBR" "Adverb, comparative"
   "RBS" "Adverb, superlative"
   "RP" "Particle"
   "SYM" "Symbol"
   "TO" "to"
   "UH" "Interjection"
   "VB" "Verb, base form"
   "VBD" "Verb, past tense"
   "VBG" "Verb, gerund or present participle"
   "VBN" "Verb, past participle"
   "VBP" "Verb, non-3rd person singular present"
   "VBZ" "Verb, 3rd person singular present"
   "WDT" "Wh-determiner"
   "WP" "Wh-pronoun"
   "WP$" "Possessive wh-pronoun"
   "WRB" "Wh-adverb"})

(defn pos-description
  "Get a human readable description of *pos-tag*."
  [pos-tag]
  (get penn-treebank-pos-tags pos-tag))

;; POS tag adjust
(def ^:private noun-tags
  #{"NN" "NNS" "NNP" "NNPS"})

(def ^:private verb-tags
  #{"VB" "VBD" "VBG" "VBN" "VBP" "VBZ"})

(def ^:private adjective-tags
  #{"JJ" "JJR" "JJS"})

(def ^:private adverb-tags
  #{"RB" "RBR" "RBS" "WRB"})

(def ^:private pos-tag-list
  #{"$" "#" "''" "," "-LRB-" "-RRB-" "." ":" "CC" "CD" "DT" "IN" "JJ" "MD"
    "NN" "NNP" "NNPS" "NNS" "PDT" "POS" "PRP" "RB" "RP" "SYM"
    "VB" "VBD" "VBG" "VBN" "VBP" "VBZ" "LS"})

(def ^:private wh-tags
  #{"WDT" "WRB" "WP" "WP$"})

(defn pos-tags
  "Get a sequence of POS tags based on **type** (if given) and all otherwise.

  **type** is a symbol and one of `noun`, `verb`, `adjective`, `adverb`, and
  `wh`.

  See [[pos-tag-types]]."
  ([type]
   (case type
     noun noun-tags
     verb verb-tags
     adjective adjective-tags
     adverb adverb-tags
     wh wh-tags))
  ([]
   (set/union (set (keys penn-treebank-pos-tags)) pos-tag-list)))

(defn pos-tag-types
  "Return types of POS tags (i.e. `noun`, `verb`, etc).

  See [[pos-tags]]."
  []
  #{"noun" "verb" "adjective" "adverb" "wh"})

(defn pos-tag-type
  "Return the POS tag type (ie. `verb` yeilds `VB`).

  The return (function range) is the input to [[pos-tags]]."
  [tag]
  (->> (map (fn [type]
             (if (contains? (pos-tags type) tag) type))
           '(noun verb adjective adverb wh))
       (remove nil?)
       first))

(defn parse
  "Parse natural language **utterance** returning a symbol expression tree of
  it's meaning.

  This returns a symbolic expression (map of maps) with the following hierarchy:

- text
- mentions
    - tokens (mirrors tokens at the sents level)
- dependency-parse-tree (dependency head)
- parse-tree (constituency)
- sents (sentences)
    - tokens
        - text
        - token-range (utterance inclusive tuple)
        - token-index (1-based sentence index)
        - ner-tag (named entity tag)
        - pos-tag (part of speech)
        - lemma (lemmatization)
        - char-range (utterance inclusive tuple)
        - srl (semantic role label)
            - propbank (propbank verb entry)
            - head-id (id of the head in the tree)
            - dependency-label (the dependency relation)
"
  [utterance]
  (let [anon (-> utterance (str/replace #"%[a-zA-Z.]+\s*" "") sp/parse)]
    (->> anon :sents
         (map (fn [sent]
                (let [toks (:tokens sent)]
                  (->> toks
                       (map :text)
                       (srl/label)
                       (#(map (fn [tok srl]
                                (assoc tok :srl (dissoc srl [:form :lemma])))
                              toks %))
                       (assoc sent :tokens)))))
         (assoc anon :sents))))

(defn tokens
  "Get all tokens across all sentences."
  [panon]
  (->> panon :sents (map :tokens) (apply concat)))

(defn token
  "Get the 0-based **index**th token."
  [panon index]
  (nth (tokens panon) index))

(defn sent
  "Get the 0-based **index**th sentence."
  [panon index]
  (nth (:sents panon) index))

(defn root-dependency
  "Get the text of the root node of the dependency tree."
  [sent]
  (->> sent
       :dependency-parse-tree first :token-index dec (nth (:tokens sent))))

(defn tok-in-range?
  "Return whether token **tok** is in tuple **range**."
  [tok range]
  (let [[st et] (:token-range tok)
        [sr er] range]
    (and (>= st sr) (<= et er))))

(defn mention-for-token
  "Return a mention for token **tok** (if any)."
  [panon tok]
  (->> (:mentions panon)
       (filter #(->> % :token-range (tok-in-range? tok)))))

(def parse-command
  "CLI command to parse an utterance"
  {:description "parse an English utterance"
   :options
   [(zensols.actioncli.log4j2/log-level-set-option)
    ["-u" "--utterance" "The utterance to parse"
     :required "TEXT"
     :validate [#(> (count %) 0) "No utterance given"]]]
   :app (fn [{:keys [utterance] :as opts} & args]
          (clojure.pprint/pprint (parse utterance)))})
