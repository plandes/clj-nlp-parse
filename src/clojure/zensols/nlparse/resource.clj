(ns ^{:doc "Configure environment for the NLP pipeline."
      :author "Paul Landes"}
    zensols.nlparse.resource
  (:require [clojure.tools.logging :as log])
  (:require [zensols.actioncli.resource :refer (resource-path) :as res]))

(defn initialize
  "Initialize model resource locations.

  This needs the system property `zensols.model` set to a directory that
  has the POS tagger model `english-left3words-distsim.tagger`(or whatever
  you configure in [[zensols.nlparse.stanford/create-context]]) in a directory
  called `pos`.

  See the [source documentation](https://github.com/plandes/clj-nlp-parse) for
  more information."
  []
  (log/debug "initializing")
  (res/register-resource :stanford-pos-tagger
                         :pre-path :stanford-model :system-file "pos")
  (res/register-resource :stanford-sr-model
                         :pre-path :stanford-model
                         :system-file "sr")
  (res/register-resource :stanford-model
                         :pre-path :model :system-file "stanford")
  (res/register-resource :model :system-property "model"))
