(ns zensols.nlparse.srl-test
  (:require [zensols.tabres.display-results :as dr])
  (:require zensols.nlparse.srl :refer :all))

(defn- display-srl-tree
  "Display the Semantic Role Labeling of *tree-sents*
  (an element from [[parse-sentences]]).

  **text** is the original text used for the title."
  [tree-sents]
  (letfn [(node-to-map [node]
            (let [heads (.getSHeads node)
                  head (.getHead node)]
              [(.-id node)
               (.-form node)
               (.-lemma node)
               (.-pos node)
               (.getFeats node)
               (if head (.-id head))
               (if head (.getLabel head))
               (if (not (.isEmpty heads))
                 (str/join "," heads))]))]
    (dr/display-results
     (apply concat
            (map (fn [tree]
                   (map node-to-map (rest (:tree tree))))
                 tree-sents))
     :column-names ["ID" "Form" "Lemma" "POS" "features"
                    "Head ID" "Dep Label" "Semantic Heads"])))

(defn display-srl
  "Graphically display the Semantic Role Labeling of the tokenized sequence
  **tokens** of a sentence.

  * **tokens** are a sequence of strings that make up a sentence"
  [tokens]
  (->> tokens
       list
       parse-sentences
       classify-sent-trees
       display-srl-tree))
