(ns zensols.nlparse.config-parse-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [instaparse.core :as insta]
            [zensols.nlparse.config-parse :refer :all]))

(deftest test-to-forms []
  (is (= [:forms [:form [:func "tokenize"]]]
         (to-forms "tokenize")))
  (is (= [:forms [:form [:func "tokenize"] [:params "(" [:param-list [:arg "arg1"] "," [:arg "arg2"]] ")"]]]
         (to-forms "tokenize(arg1,arg2)")))
  (is (= [:forms [:form [:namespace "zensols.nlparse"] "/" [:func "tokenize"] [:params "(" [:param-list [:arg "arg1"] "," [:arg "arg2"]] ")"]]]
         (to-forms "zensols.nlparse/tokenize(arg1,arg2)")))
  (is (= [:forms [:form [:namespace "zensols.nlparse"] "/" [:func "tokenize"] [:params "(" [:param-list [:arg "arg1"] "," [:arg "arg2"]] ")"]]
          ","
          [:form [:func "sentence"]]]
         (to-forms "zensols.nlparse/tokenize(arg1,arg2),sentence")))
  (is (= [:forms [:form [:func "sentence"]]
          ","
          [:form [:namespace "zensols.nlparse"] "/" [:func "tokenize"] [:params "(" [:param-list [:arg "arg1"] "," [:arg "arg2"]] ")"]]]
         (to-forms "sentence,zensols.nlparse/tokenize(arg1,arg2)")))
  (is (= [:forms [:form [:namespace "zensols.nlparse"] "/" [:func "tokenize"] [:params "(" [:param-list [:arg "arg1"] "," [:arg "arg2"]] ")"]]
          ","
          [:form [:namespace "zensols.nlparse"] "/" [:func "sentence"]]]
         (to-forms "zensols.nlparse/tokenize(arg1,arg2),zensols.nlparse/sentence")))
  (is (= [:forms [:form [:func "sentence"]]
          ","
          [:form [:namespace "zensols.nlparse"] "/" [:func "tokenize"] [:params "(" [:param-list [:arg "arg1"] "," [:arg "arg2"]] ")"]]]
         (to-forms "sentence,zensols.nlparse/tokenize(arg1,arg2)")))
  (is (= [:forms [:form [:func "sentence"] [:params "(" [:param-list [:arg "arg1"] "," [:arg "arg2"]] ")"]]
          ","
          [:form [:namespace "zensols.nlparse"] "/" [:func "tokenize"] [:params "(" [:param-list [:arg "arg1"] "," [:arg "arg2"]] ")"]]]
         (to-forms "sentence(arg1,arg2),zensols.nlparse/tokenize(arg1,arg2)")))
  (is (= [:forms [:form [:namespace "zensols.nlparse"] "/" [:func "sentence-stuff"] [:params "(" [:param-list [:arg "arg1"] "," [:arg "arg2"]] ")"]]
          ","
          [:form [:namespace "zensols.nlparse"] "/" [:func "tokenize"] [:params "(" [:param-list [:arg "arg1"] "," [:arg "arg2"]] ")"]]]
         (to-forms "zensols.nlparse/sentence-stuff(arg1,arg2),zensols.nlparse/tokenize(arg1,arg2)")))
  (is (.startsWith (pr-str (insta/get-failure (to-forms "a(,a)")))
                   "Parse error at line 1, column 3:
a(,a)
  ^
Expected:"))
  (is (.startsWith (pr-str (insta/get-failure (to-forms "a(a,)")))
                   "Parse error at line 1, column 5:
a(a,)
    ^
Expected:")))

(deftest test-to-metadata []
  (is (= '({:func "tokenize", :params nil})
         (to-metadata "tokenize")))
  (is (= '({:func "tokenize", :params ("arg1" "arg2")})
         (to-metadata "tokenize(arg1,arg2)")))
  (is (= '({:namespace "zensols.nlparse.config", :func "tokenize", :params ("arg1" "arg2")})
         (to-metadata "zensols.nlparse.config/tokenize(arg1,arg2)")))
  (is (= '({:namespace "zensols.nlparse.config", :func "tokenize", :params ("arg1" "arg2")} {:func "sentence", :params nil})
         (to-metadata "zensols.nlparse.config/tokenize(arg1,arg2),sentence")))
  (is (= '({:func "sentence", :params nil} {:namespace "zensols.nlparse.config", :func "tokenize", :params ("arg1" "arg2")})
         (to-metadata "sentence,zensols.nlparse.config/tokenize(arg1,arg2)")))
  (is (= '({:namespace "zensols.nlparse.config", :func "tokenize", :params ("arg1" "arg2")} {:namespace "zensols.nlparse.config", :func "sentence", :params nil})
         (to-metadata "zensols.nlparse.config/tokenize(arg1,arg2),zensols.nlparse.config/sentence")))
  (is (= '({:func "sentence", :params nil} {:namespace "zensols.nlparse.config", :func "tokenize", :params ("arg1" "arg2")})
         (to-metadata "sentence,zensols.nlparse.config/tokenize(arg1,arg2)")))
  (is (= '({:func "sentence", :params ("arg1" "arg2")} {:namespace "zensols.nlparse.config", :func "tokenize", :params ("arg1" "arg2")})
         (to-metadata "sentence(arg1,arg2),zensols.nlparse.config/tokenize(arg1,arg2)")))
  (is (= '({:func "tokenize", :params nil} {:func "sentence", :params nil} {:func "part-of-speech", :params nil} {:func "morphology", :params nil})
         (to-metadata "tokenize,sentence,part-of-speech,morphology"))))

(defn parse-with-ns [config-str]
  (parse config-str '(zensols.nlparse.config)))

(deftest test-to-parse []
  (is (= '({:component :tokenize, :lang "en", :parser :stanford})
         (parse-with-ns "tokenize")))
  (is (= '({:component :tokenize, :lang arg1, :parser :stanford})
         (parse-with-ns "tokenize(arg1)")))
  (is (= '({:component :tokenize, :lang "arg1" :parser :stanford})
         (parse-with-ns "tokenize(\"arg1\")")))
  (is (= '({:component :tokenize, :lang "LANG1", :parser :stanford})
         (parse-with-ns "zensols.nlparse.config/tokenize(\"LANG1\")")))
  (is (= '({:component :tokenize, :lang "arg1", :parser :stanford}
           {:component :sents, :parser :stanford})
         (parse-with-ns "zensols.nlparse.config/tokenize(\"arg1\"),sentence")))
  (is (= '({:component :sents, :parser :stanford}
           {:component :tokenize, :lang "arg1", :parser :stanford})
         (parse-with-ns "sentence,zensols.nlparse.config/tokenize(\"arg1\")")))
  (is (= '({:component :tokenize, :lang "arg1", :parser :stanford}
           {:component :sents, :parser :stanford})
         (parse-with-ns "zensols.nlparse.config/tokenize(\"arg1\"),zensols.nlparse.config/sentence")))
  (is (= '({:component :sents, :parser :stanford}
           {:component :tokenize, :lang "arg1", :parser :stanford})
         (parse-with-ns "sentence,zensols.nlparse.config/tokenize(\"arg1\")")))
  (is (= '({:component :pos, :pos-model-resource "arg2", :parser :stanford}
           {:component :tokenize, :lang "arg1", :parser :stanford})
         (parse-with-ns "part-of-speech(\"arg2\"),zensols.nlparse.config/tokenize(\"arg1\")")))
  (is (= '({:component :tokenize, :lang "en", :parser :stanford}
           {:component :sents, :parser :stanford}
           {:component :pos, :pos-model-resource "english.tagger", :parser :stanford}
           {:component :morph, :parser :stanford})
         (parse-with-ns "tokenize(\"en\"),sentence,part-of-speech(\"english.tagger\"),morphology")))
  (is (= '({:component :sentiment, :parser :stanford, :aggregate? true})
         (parse-with-ns "sentiment")))
  (is (= '({:component :sentiment, :parser :stanford, :aggregate? true})
         (parse-with-ns "sentiment(true)")))
  (is (= '({:component :sentiment, :parser :stanford, :aggregate? false})
         (parse-with-ns "sentiment(false)"))))
