(ns ^{:doc "Parse an utterance using the Stanford CoreNLP and the ClearNLP SRL.

This is the main client entry point to the package.  A default out of the box
parser works that comes with components listed
in [[zensols.nlparse.config/all-components]].

If you want to customzie or add your own parser plug in, see
the [[zensols.nlparse.config]] namespace."
      :author "Paul Landes"}
    zensols.nlparse.parse
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.set :as set])
  (:require [zensols.nlparse.config :as conf]
            [zensols.nlparse.util :refer (trunc)]
            [zensols.nlparse.stanford :as sp]
            [zensols.nlparse.srl :as srl]))

(def sentimnet-labels
  "All labels returned by [[sentiment-score-to-label]] in order of positive to
  negative."
  ["very positive" "positive" "very negative" "negative" "neutral"])

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

  This returns a symbolic expression (map of maps).  A definition of these
  annotations can be [seen here](doc/annotation-definitions.md).

  See [test
  token-regex](https://github.com/plandes/clj-nlp-parse/blob/v0.0.11/test-resources/token-regex.txt#L3)
  for example of `entity-tag`."
  [utterance]
  (log/infof "parsing: <%s>" (trunc utterance))
  (->> (conf/parse-functions)
       (reduce (fn [last-res parse-fn]
                 (log/debugf "next parser: %s" parse-fn)
                 (parse-fn last-res))
               utterance)))

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

(defn overlap?
  "Return whether two ranges overlap inclusively.  Both parameters have the
  form: `[start end]`."
  [a b]
  (let [[sa ea] a
        [sb eb] b]
    (and (<= sa eb) (<= sb ea))))

(defn in-range?
  "Return whether **inner** range sits in inclusive range **outter**.  Both
  parameters have the form: `[start end]`."
  [outter inner]
  (let [[so eo] outter
        [si ei] inner]
    (and (>= si so) (<= ei eo))))

(defn token-in-range?
  "Return whether token **tok** is in tuple **range**."
  [token range]
  (in-range? range (:token-range token)))

(defn mentions
  "Get all mentions from parse annotation **panon**.

Keys
----
* **:mention-keys** top level keys of mentions to use and defaults to
    `[:mentions :tok-re-mentions]`"
  [panon & {:keys [mention-keys]
            :or {mention-keys [:mentions :tok-re-mentions]}}]
  (->> mention-keys
       (map #(get panon %))
       (apply concat)))

(defn mention-for-token
  "Return a mention for token **tok** (if any)."
  [panon token]
  (->> (:mentions panon)
       (filter #(->> % :token-range (token-in-range? token)))))

(defn tokens-by-sentence
  "Return the tokens for **sent-index** in the **token-range**."
  [panon sent-index token-range]
  (let [sent (-> panon :sents (nth sent-index) :tokens)]
    (->> sent
         (drop (first token-range))
         (take (- (second token-range) (first token-range))))))

(defn tokens-for-mention
  "Return tokens for **mention**."
  [panon mention]
  (let [{:keys [token-range sent-index]} mention
        sents (->> panon :sents)]
    (when (and mention (< sent-index (count sents)))
     (->> sents (#(nth % sent-index)) :tokens
          (filter #(token-in-range? % (:token-range mention)))))))

(defn token-mentions
  "Return mentions with a `:tokens` key that includes token maps from the
  sentence level."
  ([panon]
   (token-mentions panon (mentions panon)))
  ([panon mentions]
   (->> mentions
        (map (fn [mention]
               (->> mention
                    (tokens-for-mention panon)
                    (assoc mention :tokens)))))))

(defn sentiment-score-to-label
  "Create a human readable tag from the sentiment score.

  See [[sentimnet-labels]]."
  [sentiment-score]
  (cond (nil? sentiment-score) nil
        (> sentiment-score 1) "very positive"
        (= sentiment-score 1) "positive"
        (< sentiment-score -1) "very negative"
        (= sentiment-score -1) "negative"
        (= 0 sentiment-score) "neutral"))
