(ns zensols.nlparse.threadsafe-test
  (:require [clojure.test :refer :all])
  (:require [clojure.string :as s]
            [zensols.nlparse.parse :refer :all]))

(deftest in-test
  (testing "testing threadsafe"
    (let [agent-count 10;500
          ]
      (is (= agent-count
             (let [utterance "My name is Paul Landes"
                   correct-res (parse utterance)
                   agents (map (fn [_] (agent nil))
                               (range agent-count))]
               (doseq [a agents]
                 (send a (fn [_]
                           (parse utterance))))
               (apply await agents)
               (->> agents
                    (map #(->> % deref (= correct-res)))
                    (filter true?)
                    count)))))))
