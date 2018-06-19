(ns ^{:doc "Feature utility functions.  See [[zensols.nlparse.feature.lang]]."
      :author "Paul Landes"}
    zensols.nlparse.feature.word-count
  (:require [clojure.string :as s]
            [zensols.nlparse.parse :as pt]
            [zensols.nlparse.stopword :as st]))

;; word counts
(def ^:dynamic *word-count-config*
  "Configuration for `word-count-*` and `calculate-word*` functions."
  (merge st/*stopword-config*
         { ;; number of word counts for each label
          :words-by-label-count 3
          ;; function maps a top level annotation to get its label
          :anon-to-label-fn #(:class-label %)
          :anon-to-parse-fn #(:instance %)
          :label-format-fn #(format "word-count-%s" %)}))

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
  (binding [st/*stopword-config* *word-count-config*]
    (let [{:keys [anon-to-label-fn anon-to-parse-fn]} *word-count-config*]
      (->> anons
           (map (fn [anon]
                  (if-let [label (anon-to-label-fn anon)]
                    (->> (pt/tokens (anon-to-parse-fn anon))
                         (filter st/go-word?)
                         (map #(hash-map (st/go-word-form %) 1))
                         (apply merge-with +)
                         (hash-map label)))))
           (remove nil?)
           (apply merge-with (fn [& ms] (apply merge-with + ms)))))))

(defn calculate-feature-stats
  "Calculate feature statistics during training.

  * **anons** a sequence of parsed annotations"
  [anons]
  (let [wba (calculate-words-by-label anons)
        label-keys (keys wba)]
    {:words-by-label wba
     :word-count-dist (calculate-word-count-dist wba label-keys)}))

(defn- label-word-count-feature-key [label]
  (keyword ((:label-format-fn *word-count-config*) label)))

(defn- label-word-count-scores [tokens word-count-dist]
  (binding [st/*stopword-config* *word-count-config*]
    (->> tokens
         (map (fn [token]
                (let [word (st/go-word-form token)]
                  (map (fn [[label dist]]
                         (let [prob (get dist word)]
                           {label (or prob 0)}))
                       word-count-dist))))
         (apply concat)
         (apply merge-with +))))

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

(defn label-word-count-feature-metas
  "Return the feature metadatas for [[label-count-score-features]]."
  [thing]
  (->> (if (map? thing)
         (->> thing :words-by-label keys)
         thing)
       (map (fn [label]
              [(label-word-count-feature-key label) 'numeric]))))

(defn top-count-scores
  "Return the top **num-counts** as a list of strings.

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
