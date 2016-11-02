(ns zensols.nlparse.tok-re
  (:import (edu.stanford.nlp.process Morphology))
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.set :refer (union)])
  (:require [zensols.actioncli.resource :as res :refer (resource-path)]))

(defn- initialize []
  (res/register-resource :tok-re-template-resource
                         :constant "tok-re" :type :resource))

(defn- template-resource
  "Get the token regular expression template resource.  This is the top portion
  of the Stanford CoreNLP Token Regular expression annotator definition file."
  []
  (resource-path :tok-re-template-resource "template.txt"))

(def ^:private item-defaults
;; * **:min-len** item skip unless **content** is at least this length (default 3)
;; * **:max-len** item skip unless **content** is no longer this length (default 20)
;; * **:omit-single-stopword?** if there is only one token and it is a stop word
;;     then omit that pattern, defaults to `true`
  {:lem-min-len -1
   :case-min-tok -1
   ;; :min-len 3
   ;; :max-len 20
   :conj-regexp? true
   :is-regexp? false
   ;; :omit-single-stopword? true
   :first-det-chop? true})

(defn item
  "Create an item used to create a pattern/line in the Stanford CoreNLP regular
  expression definition file with a regex created from **content** and NER
  **label**.

  The **opts** parameter are keys with:

* **:lem-min-len** minimum item utterance length to turn on lemmatization
    for the last token (default -1), for example:
     * 2: if the string is or longer than 2 chars lemmatize the last token
     * 0: always lemmatize
     * -1: never lemmatize
* **:case-min-tok** must have at least N tokens to turn on case sensitivity
    (default to `-1`), for example:
     * 2: if there are 1 or 2 tokens make it case sensitive
     * 1: if there is only one token then make it case sensitive
     * 0: always case sensitive
     * -1: always case insensitive
* **:conj-regexp?** add and|& regex to match both symbols, defaults to `true`
* **:first-det-chop?** chop off 'the' at the beginning of the item utterance,
    defaults to `true`
* **:is-regexp?** if `true` write the regular expression verbatim instead of
    generating one from the utterance like form"
  [content label & opts]
  (merge item-defaults
         (apply hash-map opts)
         {:content content
          :label label}))

(defn- format-features [features]
  (reduce-kv (fn [r k v]
               (str r (if r ",") (name k) "={" v "}"))
             nil features))

(defn parse-features [feature-string]
  (into {}
        (map (fn [[_ key val]]
               (if _ [(keyword key) val]))
             (re-seq #"(.+?)=\{(.+?)\},?" feature-string))))

(defn- re-quote [s]
  (let [special (set ".?*+^$[]\\(){}|/")
        escfn #(if (special %) (str \\ %) %)]
    (apply str (map escfn s))))

(defn- lemma [word morph]
  (let [lemma (.lemma morph word "NNS")
        all-caps (re-find #"^[A-Z]+$" word)
        is-cap (Character/isUpperCase (first word))]
    (if (= 0 (.compareToIgnoreCase word lemma))
      {:lem? false :word word}
      {:lem? true
       :word (cond all-caps (str/upper-case lemma)
                   is-cap (str/capitalize lemma)
                   true lemma)})))

(defn- replace-regex-word-map [wmap repls case-sen?]
  (reduce (fn [{:keys [text] :as m} [re repl]]
            (let [reped (str/replace text re repl)
                  regex? (not (= text reped))]
              (merge m
                     (if regex? {:rep-regex true})
                     {:text reped})))
          (merge {:case-sen? case-sen?}
                 (if-not case-sen? {:regex true})
                 wmap)
          repls))

(defn- item-to-word-regexes [item]
  (let [morph (Morphology.)
        {:keys [content lem-min-len conj-regexp? delimiters
                case-min-tok first-det-chop? is-regexp?]} item
        repls (concat [[#"\"\"" "/ /"]]
                      ;; this will take more work given tok demarcation
                      (if (and false delimiters)
                        [[(re-pattern (str "(.+)[" delimiters "](.+)"))
                          (str "(?:$1[" delimiters "]?$2)|$1/ /$2")]])
                      (if conj-regexp?
                        [[#"^(?:&|and)$" "(?:&|and)"]]))
        toks (str/split content #"\s+")
        lem? (and (not (= -1 lem-min-len))
                  (>= (count content) lem-min-len))
        case-sen? (and (not (= -1 case-min-tok))
                       (>= (count toks) case-min-tok))]
    (letfn [(massage-word-map [wmap lem? first? last?]
              (log/debugf "massage wm: %s" (pr-str wmap))
              (when wmap
                (let [word (:text wmap)
                      {:keys [word]} (if lem?
                                       (lemma word morph)
                                       {:word word})
                      lower (str/lower-case word)]
                  (if-not (and (not is-regexp?) first?
                               first-det-chop? (= "the" lower))
                    (merge wmap
                           {:text (if (or case-sen? is-regexp?) word lower)
                            :lemmatize lem?})))))
            (massage-tokens [wmaps]
              (log/debugf "massage tokens with wmaps: %s" (pr-str wmaps))
              (let [middle-and-last (rest wmaps)
                    middle (butlast middle-and-last)]
                (filter
                 identity
                 (concat
                  (list (massage-word-map (first wmaps)
                                          (and lem? (= (count wmaps) 1))
                                          true (= (count wmaps) 1)))
                  (map #(massage-word-map % false false false) middle)
                  (list (massage-word-map (last middle-and-last)
                                          lem? true true))))))]
      (if is-regexp?
        (->> toks
             (map #(array-map :text % :regex true :is-regexp? true :case-sen? true)))
        (->> toks
            (map #(array-map :text %))
            massage-tokens
            (map #(replace-regex-word-map % repls case-sen?))
            (map (fn [itok]
                   (if (and (:regex itok) (not (:rep-regex itok)))
                     (assoc itok :text (re-quote (:text itok)))
                     itok))))))))

(defn- format-rules [item word-regexes]
  (letfn [(fmt-pattern [{:keys [case-sen? is-regexp? lemmatize regex tags text]}]
            (let [word (if case-sen? text (str"(?i)" text))
                  delim (if regex "/" "\"")
                  word-pat (str delim word delim)
                  word-pat (if is-regexp?
                             word-pat
                             (str (if lemmatize "lemma" "word") ":" word-pat))
                  tag-pat (if tags (format "tag:/%s/" (str/join "|" tags)))]
              (->> [word-pat tag-pat]
                   (map #(if % %))
                   (filter identity)
                   (str/join ";")
                   (#(if is-regexp? % (str "[{" % "}]"))))))]
    (when-not (empty? word-regexes)
      (let [pats (str/join " " (map fmt-pattern word-regexes))
            pat (:pattern item)
            pattern (if pat
                      (str/replace pat "~" pats)
                      (str "(" pats ")"))]
        (print "{pattern:")
        (print pattern)
        (print ",action:(")
        (print (format "Annotate($0,zstrner,\"%s\")" (:label item)))
        (if (:id item)
          (print (format ",Annotate($0,zstriid,\"%s\")" (:id item))))
        (when (:features item)
          (print ",Annotate($0,zstrfeat,\"")
          (print (format-features (:features item)))
          (print "\")"))
        (print ")}")))))

(defn write-regex-files
  "Write all **items** to the Stanford token regular expression files
  **regex-output-file** with all possible features in
  **features-output-file**."
  [regex-output-file features-output-file items]
  (->> regex-output-file
       .getParentFile
       .mkdirs)
  (let [feats (with-open [writer (io/writer regex-output-file)]
                (binding [*out* writer]
                  (print (slurp (template-resource)))
                  (->> items
                       (map (fn [item]
                              (->> item
                                   (item-to-word-regexes)
                                   (format-rules item))
                              (println)
                              (if (:features item)
                                (map (fn [[k v]]
                                       (if (not (nil? v))
                                         {k (hash-set v)}))
                                     (:features item)))))
                       (reduce (fn [a b]
                                 (apply merge-with union (cons a b)))
                               {})
                       (doall))))]
    (log/infof "write regex compiled file: %s" regex-output-file)
    (with-open [writer (io/writer features-output-file)]
      (binding [*out* writer]
        (println (format ";; all features found in %s"
                         (.getName regex-output-file)))
        (pr feats)
        (flush)))
    (log/infof "write regex compiled features: %s" features-output-file)
    feats))

(initialize)
