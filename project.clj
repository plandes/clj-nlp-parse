(defproject com.zensols.nlp/parse "0.1.0-SNAPSHOT"
  :description "A library for parsing natural language feature creation."
  :url "https://github.com/plandes/clj-nlp-parse"
  :license {:name "Apache License version 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"
            :distribution :repo}
  :plugins [[lein-codox "0.10.1"]
            [org.clojars.cvillecsteele/lein-git-version "1.2.7"]]
  :codox {:metadata {:doc/format :markdown}
          :project {:name "NLP Parsing and Feature Creation"}
          :output-path "target/doc/codox"
          :source-uri "https://github.com/plandes/clj-nlp-parse/blob/v{version}/{filepath}#L{line}"}
  :git-version {:root-ns "zensols.nlparse"
                :path "src/clojure/zensols/nlparse"
                :version-cmd "git describe --match v*.* --abbrev=4 --dirty=-dirty"}
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :javac-options ["-Xlint:unchecked"]
  :jar-exclusions [#".gitignore"]
  :dependencies [[org.clojure/clojure "1.8.0"]

                 ;; feature creation
                 [com.zensols.nlp/feature "0.0.4"]

                 ;;; NLP
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
  :profiles {:appassem {:aot :all}
             :dev
             {:jvm-opts ["-Dlog4j.configurationFile=test-resources/log4j2.xml" "-Xms4g" "-Xmx12g" "-XX:+UseConcMarkSweepGC"]
              :exclusions [org.slf4j/slf4j-log4j12
                           ch.qos.logback/logback-classic]
              :dependencies [[edu.stanford.nlp/stanford-corenlp "3.6.0" :classifier "javadoc"]
                             [edu.stanford.nlp/stanford-corenlp "3.6.0" :classifier "sources"]
                             [org.apache.logging.log4j/log4j-core "2.7"]
                             [org.apache.logging.log4j/log4j-slf4j-impl "2.7"]
                             [org.apache.logging.log4j/log4j-1.2-api "2.7"]
                             [org.apache.logging.log4j/log4j-jcl "2.7"]
                             [com.zensols.gui/tabres "0.0.6"]
                             [com.zensols/clj-append "1.0.5"]]}})
