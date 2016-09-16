(ns ^{:doc "Feature utility functions.  See [[zensols.nlparse.feature.lang]]."
      :author "Paul Landes"}
    zensols.nlparse.feature.word-count
  (:require [clojure.string :as s])
  (:require [zensols.nlparse.parse :as pt]))

;; word counts
(def ^:dynamic *word-count-config*
  "Configuration for word-count-* and calculate-word* functions."
  {;; number of word counts for each label
   :words-by-label-count 3
   ;; function maps a top level annotation to get its label
   :anon-to-label-fn nil
   :label-format-fn #(format "label-count-%s" %)
   :pos-tags #{"JJ" "JJR" "JJS"
               "MD"
               "NN" "NNS", "NNPS"
               "VB" "VBD" "VBG" "VBN" "VBP" "VBZ"}})

(defn- word-count-candidate?
  "Return whether a token should be considered a word candidate."
  [token]
  (let [word-count-tags (:pos-tags *word-count-config*)]
    (and (not (:stopword token))
         (contains? word-count-tags (:pos-tag token)))))

(defn- word-count-form
  "Conical string word count form of a token (i.e. Running -> run)."
  [token]
  (s/lower-case (:lemma token)))

(defn- calculate-word-count-dist
  "Get the top counts for each label using the top **words-by-label-count**
  number from each."
  [by-label label-keys]
  (let [lab-count (:words-by-label-count *word-count-config*)]
    (zipmap
     label-keys
     (->> label-keys
          (map (fn [label]
                 (take lab-count
                       (sort (fn [[ak av] [bk bv]]
                               (compare bv av))
                             (get by-label label)))))
          (map (fn [counts]
                 ;; normalize into a ratio to keep a bound on the P(hat) est
                 ;; size in the feature calculation
                 (let [total (reduce + 0 (vals counts))]
                   (apply merge (map (fn [[word count]]
                                       {word (/ count total)})
                                     counts)))))))))

(defn- calculate-words-by-label [anons]
  (let [{:keys [anon-to-label-fn]} *word-count-config*]
    (->> anons
         (map (fn [anon]
                (if-let [label (anon-to-label-fn anon)]
                  (->> (pt/tokens (:parse-anon anon))
                       (filter word-count-candidate?)
                       (map #(hash-map (word-count-form %) 1))
                       (apply merge-with +)
                       (hash-map label)))))
         (remove nil?)
         (apply merge-with (fn [& ms] (apply merge-with + ms))))))

(defn calculate-feature-stats
  "Calculate feature statistics during training.

  * **anons** a sequence of parsed annotations"
  [anons]
  (let [wba (calculate-words-by-label anons)
        label-keys (keys wba)]
    {:words-by-label wba
     :word-count-dist (calculate-word-count-dist wba label-keys)}))

(defn label-word-count-feature-key [label]
  (keyword ((:label-format-fn *word-count-config*) label)))

(defn- label-word-count-scores [tokens word-count-dist]
  (->> tokens
       (map (fn [token]
              (let [word (word-count-form token)]
                (map (fn [[label dist]]
                       (let [prob (get dist word)]
                         {label (or prob 0)}))
                     word-count-dist))))
       (apply concat)
       (apply merge-with +)))

(defn label-count-score-features
  "Generate count score features from trained statistics.

  * **panon** is the parsed annotation to generate features on
  * **feature-stats** is the trained stats from [[calculate-feature-stats]]."
  [panon feature-stats]
  (let [scores (label-word-count-scores
                (pt/tokens panon)
                (:word-count-dist feature-stats))]
    (into {}
          (map (fn [[label score]]
                 {(label-word-count-feature-key label)
                  (double score)})
               scores))))

(defn top-count-scores
  "Return the top **num-counts**.

  * **panon*** is the parsed annotation to generate features on
  * **feature-stats** is the trained stats from [[calculate-feature-stats]]."
  [num-counts panon features-stats]
  (->> features-stats
       :word-count-dist
       (label-word-count-scores (pt/tokens panon))
       (sort (fn [a b]
               (compare (second b) (second a))))
       (take num-counts)
       (filter #(> (second %) 0))
       (take num-counts)
       (map first)))
