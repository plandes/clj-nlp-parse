(ns ^{:doc "Utility functions"
      :author "Paul Landes"}
    zensols.nlparse.util)

;; duplicated from zensols.tools.string since its bloated with excel deps
(def ^:dynamic *trunc-len*
  "Default truncation length for [[trunc]]."
  80)

(defn trunc
  "Truncate string `obj` at `len` characters adding ellipses if larger that a set
  length.  If `obj` isn't a string use `pr-str` to make it a string.

  See [[*trunc-len*]]."
  ([obj] (trunc obj *trunc-len*))
  ([obj len]
   (let [s (if (string? obj) obj (pr-str obj))
         slen (count s)
         trunc? (> slen len)
         maxlen (-> (if trunc? (min slen (- len 3))
                        (min slen len))
                    (max 0))]
     (str (subs s 0 maxlen) (if trunc? "...")))))

(defn has-some?
  "Return whether or not the data should be added to a data structure."
  [data]
  (and data
       (or (not (or (associative? data) (sequential? data)
                    (instance? java.util.Set data)))
           (not (empty? data)))))

(defn map-if-data
  "Return the key/value pair sequences as a map for those values that are data
  per `has-some?`."
  [entries]
  (->> entries
       (map (fn [[k elt]]
              (if (has-some? elt) (array-map k elt))))
       (remove nil?)
       (apply merge)))
