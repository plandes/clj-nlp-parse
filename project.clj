(defproject com.zensols.nlp/parse "0.1.0-SNAPSHOT"
  :description "A library for parsing natural language feature creation."
  :url "https://github.com/plandes/clj-nlp-parse"
  :license {:name "Apache License version 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"
            :distribution :repo}
  :plugins [[lein-codox "0.9.5"]
            [org.clojars.cvillecsteele/lein-git-version "1.0.3"]]
  :codox {:metadata {:doc/format :markdown}
          :project {:name "NLP Parsing and Feature Creation"}
          :output-path "target/doc/codox"}
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :javac-options ["-Xlint:unchecked"]
  :exclusions [org.slf4j/slf4j-log4j12
               ch.qos.logback/logback-classic]
  :dependencies [[org.clojure/clojure "1.8.0"]

                 ;; logging
                 [org.apache.logging.log4j/log4j-core "2.3"]
                 [org.apache.logging.log4j/log4j-api "2.3"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.3"]
                 [org.apache.logging.log4j/log4j-jcl "2.3"]
                 [org.clojure/tools.logging "0.3.1"]

                 ;; command line
                 [com.zensols.tools/actioncli "0.0.8"]

                 ;; dev
                 [com.zensols.gui/tabres "0.0.5"]

                 ;; feature stats
                 [net.mikera/core.matrix.stats "0.7.0"]

                 ;;; NLP
                 ;; wordnet
                 [net.sf.extjwnl/extjwnl "1.9"]
                 [net.sf.extjwnl/extjwnl-data-wn31 "1.2"]

                 ;; Stanford CoreNLP
                 [edu.stanford.nlp/stanford-corenlp "3.6.0"]
                 [edu.stanford.nlp/stanford-corenlp "3.6.0" :classifier "models"]

                 ;; stopword
                 [com.zensols/stopword-annotator "2.1"]

                 ;; ClearNLP for semantic role labeling
                 [com.clearnlp/clearnlp "2.0.2"]
                 [com.clearnlp/clearnlp-dictionary "1.0"]
                 [com.clearnlp/clearnlp-general-en-dep "1.2"]
                 [com.clearnlp/clearnlp-general-en-pos "1.1"]
                 [com.clearnlp/clearnlp-general-en-srl "1.1"]]
  :pom-plugins [[org.codehaus.mojo/appassembler-maven-plugin "1.6"
                 {:configuration ([:programs
                                   [:program
                                    ([:mainClass "zensols.nlparse.core"]
                                     [:id "nlparse"])]]
                                  [:environmentSetupFileName "setupenv"])}]]
  :profiles {:uberjar {:aot :all}
             :appasem {:aot :all}
             :dev
             {:jvm-opts
              ["-Dlog4j.configurationFile=test-resources/log4j2.xml" "-Xms4g" "-Xmx12g" "-XX:+UseConcMarkSweepGC"]
              :dependencies [[edu.stanford.nlp/stanford-corenlp "3.6.0" :classifier "javadoc"]
                             [edu.stanford.nlp/stanford-corenlp "3.6.0" :classifier "sources"]
                             [com.zensols/clj-append "1.0.4"]]}}
  :main zensols.nlparse.core)
