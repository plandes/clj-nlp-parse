(ns zensols.nlparse.wordlist
  (:require [clojure.java.io :as io]
            [clojure.string :as s]))

(def ^:private word-lists-inst (atom {}))

(def ^:private word-list-defs
  {:english "en"})

(defn word-lists []
  (keys word-list-defs))

(defn- load-word-list [lang-name]
  (let [lang-code (get word-list-defs lang-name)]
    (with-open [reader (->> (format "%s-words.txt" lang-code)
                            io/resource
                            io/reader)]
      (->> reader
           line-seq
           set))))

(defn- word-list [lang-name]
  (swap! word-lists-inst
         (fn [lists]
           (if (contains? lists lang-name)
             lists
             (assoc lists lang-name (load-word-list lang-name)))))
  (get @word-lists-inst lang-name))

(defn in-word-list?
  "Return whether **word** is in a word list.
  See [[word-lists]]"
  [lang word]
  (and word (contains? (word-list lang) (s/lower-case word))))
