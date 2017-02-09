(ns deferst.system-test
  (:require

   #?(:cljs [cljs.test :as t
             :refer [deftest is are testing use-fixtures]]
      :clj [clojure.test :as t
            :refer [deftest is are testing use-fixtures]])

   [clojure.set :as set]
   [schema.test]

   #?(:clj [manifold.deferred :as d]
      :cljs [promesa.core :as p])

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

    (testing "null system has no managed objects"
      (is (= (-> sys deref last :s/system empty?)
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

(deftest single-item-system-with-vector-arg-specs
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:a ff [:foo]]])
        sys (s/start-system! sb {:foo 10})]

    (testing "single item system has the single object"
      (is (= @(s/system-map sys)
             {:foo 10
              :a 10})))

    (testing "single item was destroyed"
      @(s/stop-system! sys)
      (is (= @destructor-vals
             [10])))))

(deftest bad-arg-specs-throw
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])]

    (testing "an error is thrown describing the bad arg specs"
      (is (thrown? clojure.lang.ExceptionInfo
                   (s/system-builder [[:a ff :foo]])
                   ;; arg-specs throws on toposort now
                   ;;@(s/start-system! sb {:foo 10})
                   )))))

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

(deftest dependent-item-system-specified-out-of-order
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:b ff {:b-arg [:a :a-arg]}]
                              [:a ff {:a-arg [:foo]}]])
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

(deftest dependent-item-system-with-circular-deps
  (let [ff (fn [v] v)]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"circular dependency"
         (s/system-builder [[:a ff {:a-arg [:b :b-arg]}]
                            [:b ff {:b-arg [:a :a-arg]}]])))))

(deftest dependent-item-system-with-vector-arg-specs
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:a ff [:foo]]
                              [:b ff {:b-arg [:a]}]])
        sys (s/start-system! sb {:foo 10})
        _ (s/stop-system! sys)]

    (testing "dependent items are created"
      (is (= @(s/system-map sys)
             {:foo 10
              :a 10
              :b {:b-arg 10}})))

    (testing "dependent items were destroyed"
      (is (= @destructor-vals
             [{:b-arg 10}
              10])))))

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
        ]

    (testing "items are created"
      (is (= @(s/system-map sys)
             {:foo 10
              :a {:a-arg 10}
              :b {:b-arg 10}
              :c {:c-a 10 :c-b 10}})))

    (testing "items were destroyed"
      @(s/stop-system! sys)
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
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"start-system! failed and unwound"
           @sys)))))
