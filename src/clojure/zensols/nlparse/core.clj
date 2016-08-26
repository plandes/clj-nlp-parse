(ns zensols.nlparse.core
  (:require [zensols.actioncli.parse :as cli]
            [zensols.actioncli.log4j2 :as lu])
  (:require [parse.version])
  (:gen-class :main true))

(def ^:private version-info-command
  {:description "Get the version of the application."
   :options [["-g" "--gitref"]]
   :app (fn [{refp :gitref} & args]
          (println parse.version/version)
          (if refp (println parse.version/gitref)))})

(defn- create-command-context []
  {:command-defs '((:repl zensols.actioncli repl repl-command)
                   (:parse zensols.nlparse parse parse-command))
   :single-commands {:version version-info-command}})

(defn -main [& args]
  (lu/configure "nlp-parse-log4j2.xml")
  (let [command-context (create-command-context)]
    (apply cli/process-arguments command-context args)))
