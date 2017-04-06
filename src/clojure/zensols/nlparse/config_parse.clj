(ns ^{:doc "Parse a pipeline configruation.  This namespace supports a simple
DSL for parsing a pipeline configuration (see [[zensols.nlparse.config]]).  The
*configuration string* represents is a component separated by commas as a set
of *forms*.  For example the forms:
```
tokenize(\"en\"),sentence,part-of-speech(\"english.tagger\"),morphology
```
creates a pipeline that tokenizes, adds POS and lemmas when called
with [[parse]].  Note the double quotes in the `tokenize` and `part-of-speech`
mnemonics.  The [[parse]] function does this by calling in order:

* ([[zensols.nlparse.config/tokenize]] \"en\")
* ([[zensols.nlparse.config/sentence]])
* ([[zensols.nlparse.config/part-of-speech]] \"english.tagger\")
* ([[zensols.nlparse.config/morphology]])

Note that arguments are option (the parenthetical portion of the form) and so
is the namespace, which defaults to `zensols.nlparse.config`.  To use a
separate namespace for custom plug and play
components (see [[zensols.nlparse.config/register-library]]) you can specify
your own namespace with a `/`, for example:
```
example.namespace/myfunc(arg1,arg2)
```"
      :author "Paul Landes"}
    zensols.nlparse.config-parse
  (:require [clojure.string :as s]
            [clojure.tools.logging :as log])
  (:require [instaparse.core :as insta]))

(def ^:private conf-bnf-fn
  "Generated DSL parser."
  (insta/parser
   "forms = form (',' form)*
     form = (namespace '/')? func params?
     params = '(' param-list ')'
     param-list = arg+{',' arg}
     arg = #\"[^,) ]+\"
     func = #\"[a-zA-Z0-9-]+\"
     namespace = #\"[a-zA-Z0-9-.]+\""))

(def ^:private config-ns
  'zensols.nlparse.config)

(defn to-forms
  "Parse a configuration string into DSL forms."
  [config-str]
  (conf-bnf-fn config-str))

(defn- parse-to-map
  "Flatten vector argument DSL forms into something more Clojure 'friendly',
  which is a map of arguments or single argument."
  [formo & {:keys [seq?]}]
  (->> formo
       (filter sequential?)
       (map rest)
       (map #(filter sequential? %))
       (map (fn [form]
              (if seq?
                (map second form)
                (->> form
                     (map (fn [form]
                            (if (> (count form) 2)
                              (rest form)
                              (second form))))
                     (zipmap (map first form))))))))

(defn to-metadata
  "Create form metadata data structures from configuration string
  **config-str**."
  [config-str]
  (let [forms (to-forms config-str)]
    (if (insta/failure? forms)
      (throw (ex-info (str "Could not parse: '" config-str \')
                      {:failure forms
                       :config config-str})))
    (->> (parse-to-map forms)
         (map #(assoc % :params (first (parse-to-map (:params %) :seq? true)))))))

(defn- find-function [namespaces func-sym]
  (->> namespaces
       ;; ditch bad namespaces to create a better exception downstream
       (map find-ns)
       (remove nil?)
       (map ns-name)
       (map (fn [ns]
              (ns-resolve ns func-sym)))
       (drop-while nil?)
       first))

(defn parse
  "Parse configuration string **config-str** into a pipeline configuration.
  See the namespace ([[zensols.nlparse.config-parse]]) documentation for more
  information."
  ([config-str] (parse config-str nil))
  ([config-str namespaces]
   (letfn [(validate [cfn namespace func]
             (if (nil? cfn)
               (-> (str" No such component: "
                       (if namespace (str namespace "/"))
                       func)
                   (ex-info {:namespace namespace
                             :func func})
                   throw))
             cfn)]
     (->> (to-metadata config-str)
          (map (fn [{:keys [namespace func params] :as meta}]
                 (try
                   (let [args (map read-string params)
                         nssym (if namespace (symbol namespace))]
                     (if nssym (eval (list 'require `(quote [~nssym]))))
                     (-> (concat (if namespace
                                   (list nssym)
                                   namespaces))
                         (find-function (symbol func))
                         (validate namespace func)
                         (apply args)))
                   (catch Exception e
                     (-> (format "Cannot parse (%s/%s %s): %s"
                                 namespace func (pr-str params) (.toString e))
                         (ex-info {:meta meta} e)
                         throw)))))))))
