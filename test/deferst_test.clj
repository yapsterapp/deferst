(ns deferst.system-test
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [schema.test]
   [clojure.test :as test :refer [deftest is are testing use-fixtures]]
   [deferst.system :as s]
   [deferst :as d]))

(deftest simple-sys
  (let [sb (s/system-builder [[:a identity {:a-arg [:foo]}]])
        sys (d/create-system sb {:foo 10})]

    (testing "simple system starts"
      (is (= @(d/start! sys)
             {:foo 10
              :a {:a-arg 10}})))

    (testing "simple system returns system map"
      (is (= @(d/system-map sys)
             {:foo 10
              :a {:a-arg 10}})))

    (testing "start! returns same system map if already started"
      (is (= @(d/start! sys {:foo 20})
             {:foo 10
              :a {:a-arg 10}})))

    (testing "simple system stops"
      (is (= @(d/stop! sys)
             true)))

    (testing "start! returns new system map when restarted"
      (is (= @(d/start! sys {:foo 20})
             {:foo 20
              :a {:a-arg 20}})))))
