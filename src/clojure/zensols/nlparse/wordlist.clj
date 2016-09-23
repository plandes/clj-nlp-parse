(ns zensols.nlparse.wordlist
  (:require [clojure.java.io :as io]
            [clojure.string :as s])
  (:require [zensols.nlparse.locale :as loc]))

(def ^:private word-lists-inst (atom {}))

(def ^:private word-list-lang-codes
  #{"en" "es" "fr"})

(defn word-list-locales
  "Return a list of locales that belong to a language with a supported word list.

  See [[zensols.nlparse.locale/locale-to-lang-code]] to map to a language code
  for use with `in-word-list?`."
  []
  (->> word-list-lang-codes
       (map loc/lang-code-to-locale)))

(defn- load-word-list [lang-code]
  (let [res (->> (format "word-lists/%s" lang-code) io/resource)]
    (if (nil? res)
      (throw (ex-info (format "No supported language: %s" lang-code)
                      {:lang-code lang-code})))
    (with-open [reader (io/reader res)]
      (->> reader
           line-seq
           (map s/lower-case)
           set))))

(defn- word-list [lang-code]
  (swap! word-lists-inst
         (fn [lists]
           (if (contains? lists lang-code)
             lists
             (assoc lists lang-code (load-word-list lang-code)))))
  (get @word-lists-inst lang-code))

(defn in-word-list?
  "Return whether **word** is in a word list.

  See [[word-list-locales]]"
  [lang-code word]
  (and word (contains? (word-list lang-code) (s/lower-case word))))
