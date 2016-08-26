(ns zensols.nlparse.wordlist
  (:require [clojure.java.io :as io]
            [clojure.string :as s]))

(def ^:private word-lists-inst (atom {}))

(defn word-lists []
  #{:english})

(defn- load-word-list [lang-name]
  (with-open [reader (->> (format "%s-words.txt" lang-name)
                          io/resource
                          io/reader)]
    (->> reader
         line-seq
         set)))

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
  (contains? (word-list (name lang)) (s/lower-case word)))
