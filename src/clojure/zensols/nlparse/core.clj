(ns zensols.nlparse.core
  (:require [zensols.actioncli.parse :as cli]
            [zensols.actioncli.log4j2 :as lu]
            [zensols.actioncli.resource :as res])
  (:require [parse.version])
  (:gen-class :main true))

(def ^:private version-info-command
  {:description "Get the version of the application."
   :options [["-g" "--gitref"]]
   :app (fn [{refp :gitref} & args]
          (println parse.version/version)
          (if refp (println parse.version/gitref)))})

(defn initialize
  "Initialize model resource locations.

  This needs the system property `clj.nlp.parse.model` set to a directory that
  has the POS tagger model `english-left3words-distsim.tagger`(or whatever
  you configure in [[zensols.nlparse.stanford/create-context]]) in a directory
  called `pos`.

  See the [source documentation](https://github.com/plandes/clj-nlp-parse) for
  more information."
  []
  (res/set-resource-property-format "clj.nlp.parse.%s")
  (res/register-resource :model :system-property "model")
  (res/register-resource :stanford-model :pre-path :model))

(defn- create-command-context []
  {:command-defs '((:repl zensols.actioncli repl repl-command)
                   (:parse zensols.nlparse parse parse-command))
   :single-commands {:version version-info-command}})

(defn -main [& args]
  (lu/configure "nlp-parse-log4j2.xml")
  (initialize)
  (let [command-context (create-command-context)]
    (apply cli/process-arguments command-context args)))
