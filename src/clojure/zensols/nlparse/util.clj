(ns ^{:doc "Utility functions"
      :author "Paul Landes"}
    zensols.nlparse.util)

(defn has-some?
  "Return whether or not the data should be added to a data structure."
  [data]
  (and data
       (or (not (or (associative? data) (sequential? data)
                    (instance? java.util.Set data )))
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
