(ns deferst.system-test
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [schema.test]
   [clojure.test :as test :refer [deftest is are testing use-fixtures]]
   [deferst.system :as s]))

;; check schemas
(use-fixtures :once schema.test/validate-schemas)

(deftest empty-system
  (let [sb (s/system-builder [])
        sys (s/start-system! sb {:foo 10})]

    (testing "null system contains config"
      (is (= @(s/system-map sys)
             {:foo 10})))))

(deftest empty-system-stops
  (let [ff (fn [v] [v (fn [] (prn "destroying" v))])
        sb (s/system-builder [])
        sys (s/start-system! sb {:foo 10})
        _ (s/stop-system! sys)]

    (testing "null system is stopped"
      (is (= (-> sys deref last :config/destroyed deref)
             true)))))

(deftest single-item-system
  (let [ff (fn [v] [v (fn [] (prn "destroying" v))])
        sb (s/system-builder [[:a ff {:a-arg [:foo]}]])
        sys (s/start-system! sb {:foo 10})]

    (testing "single item system has the single object"
      (is (= @(s/system-map sys)
             {:foo 10
              :a {:a-arg 10}})))))

(deftest dependent-item-system
  (let [ff (fn [v] [v (fn [] (prn "destroying" v))])
        sb (s/system-builder [[:a ff {:a-arg [:foo]}]
                              [:b ff {:b-arg [:a :a-arg]}]])
        sys (s/start-system! sb {:foo 10})]

    (testing "single item system has the single object"
      (is (= @(s/system-map sys)
             {:foo 10
              :a {:a-arg 10}
              :b {:b-arg 10}})))))

(deftest composed-builders
  (let [ff (fn [v] [v (fn [] (prn "destroying" v))])
        sb (s/system-builder [[:a ff {:a-arg [:foo]}]
                              [:b ff {:b-arg [:a :a-arg]}]])
        sb2 (s/system-builder sb [[:c ff {:c-a [:a :a-arg]
                                          :c-b [:b :b-arg]}]])
        sys (s/start-system! sb2 {:foo 10})]

    (testing "single item system has the single object"
      (is (= @(s/system-map sys)
             {:foo 10
              :a {:a-arg 10}
              :b {:b-arg 10}
              :c {:c-a 10 :c-b 10}})))))
