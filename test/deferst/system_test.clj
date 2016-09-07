(ns deferst.system-test
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [schema.test]
   [clojure.test :as test :refer [deftest is are testing use-fixtures]]
   [manifold.deferred :as d]
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
  (let [sb (s/system-builder [])
        sys (s/start-system! sb {:foo 10})
        _ (s/stop-system! sys)]

    (testing "null system is stopped"
      (is (= (-> sys deref last :config/destroyed deref)
             true)))))

(deftest single-item-system
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:a ff {:a-arg [:foo]}]])
        sys (s/start-system! sb {:foo 10})
        _ (s/stop-system! sys)]

    (testing "single item system has the single object"
      (is (= @(s/system-map sys)
             {:foo 10
              :a {:a-arg 10}})))

    (testing "single item was destroyed"
      (is (= @destructor-vals
             [{:a-arg 10}])))))

(deftest single-item-system-without-destructors
  (let [sb (s/system-builder [[:a identity {:a-arg [:foo]}]])
        sys (s/start-system! sb {:foo 10})
        _ (s/stop-system! sys)]

    (testing "single item system has the single object"
      (is (= @(s/system-map sys)
             {:foo 10
              :a {:a-arg 10}})))))

(deftest single-deferred-item-system
  (let [destructor-vals (atom [])
        ff (fn [v] (d/success-deferred
                    [v (fn [] (swap! destructor-vals conj v))]))
        sb (s/system-builder [[:a ff {:a-arg [:foo]}]])
        sys (s/start-system! sb {:foo 10})
        _ (s/stop-system! sys)]

    (testing "single item system has the single object"
      (is (= @(s/system-map sys)
             {:foo 10
              :a {:a-arg 10}})))))


(deftest dependent-item-system
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:a ff {:a-arg [:foo]}]
                              [:b ff {:b-arg [:a :a-arg]}]])
        sys (s/start-system! sb {:foo 10})
        _ (s/stop-system! sys)]

    (testing "dependent items are created"
      (is (= @(s/system-map sys)
             {:foo 10
              :a {:a-arg 10}
              :b {:b-arg 10}})))

    (testing "dependent items were destroyed"
      (is (= @destructor-vals
             [{:b-arg 10}
              {:a-arg 10}])))))

(deftest dependent-item-system-with-mixed-destructors
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:a identity {:a-arg [:foo]}]
                              [:b ff {:b-arg [:a :a-arg]}]])
        sys (s/start-system! sb {:foo 10})
        _ (s/stop-system! sys)]

    (testing "dependent items are created"
      (is (= @(s/system-map sys)
             {:foo 10
              :a {:a-arg 10}
              :b {:b-arg 10}})))

    (testing "dependent items were destroyed"
      (is (= @destructor-vals
             [{:b-arg 10}])))))

(deftest composed-builders
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:a ff {:a-arg [:foo]}]
                              [:b identity {:b-arg [:a :a-arg]}]])
        sb2 (s/system-builder sb [[:c ff {:c-a [:a :a-arg]
                                          :c-b [:b :b-arg]}]])
        sys (s/start-system! sb2 {:foo 10})
        _ (s/stop-system! sys)]

    (testing "items are created"
      (is (= @(s/system-map sys)
             {:foo 10
              :a {:a-arg 10}
              :b {:b-arg 10}
              :c {:c-a 10 :c-b 10}})))

    (testing "items were destroyed"
      (is (= @destructor-vals
             [{:c-a 10 :c-b 10}
              {:a-arg 10}])))))

(deftest unwind-on-builder-error
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        boom (fn [v] (throw (ex-info "boom" {:boom true})))
        sb (s/system-builder [[:a ff {:a-arg [:foo]}]
                              [:b boom {:b-arg [:a :a-arg]}]])
        sys (s/start-system! sb {:foo 10})]
    (testing "system is unwound"
      (is (= @destructor-vals [{:a-arg 10}]))
      (is (= (-> (d/error-value sys nil)
                 ex-data
                 :state
                 :config/destroyed
                 deref)
             true)))))
