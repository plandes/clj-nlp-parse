(ns zensols.nlparse.timeout-test
  (:require [clojure.test :refer :all])
  (:require [zensols.nlparse.parse :as p]
            [zensols.nlparse.config :as conf :refer (with-context)]))

(defonce ^:private context
  (-> (conf/create-parse-config)
      (conf/create-context :timeout-millis 1)))

(defn- parse [utterance]
  (with-context context
    (p/parse utterance)))

(deftest test-timeout-parse []
  (testing "testing timeout exception happens"
    (is (thrown? java.util.concurrent.TimeoutException
                 (parse "This is a test")))))
