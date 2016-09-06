(ns deferst.system-test
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [schema.test]
   [clojure.test :as test :refer [deftest is are testing use-fixtures]]
   [deferst.system :as s]))

;; check schemas
(use-fixtures :once schema.test/validate-schemas)

(deftest build-nothing
  (let [ff (fn [v] [v (fn [] (prn "destroying" v))])
        sb (s/system-builder [])
        sys (s/start-system! sb {:foo 10})]

    (testing ""
      (is (= @(s/system-map sys)
             {:foo 10})))))
