(ns zensols.nlparse.tok-re)

(defn- format-features [features]
  (reduce-kv (fn [r k v]
               (str r (if r ",") (name k) "={" v "}"))
             nil features))

(defn parse-features [feature-string]
  (into {}
        (map (fn [[_ key val]]
               (if _ [(keyword key) val]))
             (re-seq #"(.+?)=\{(.+?)\},?" feature-string))))
