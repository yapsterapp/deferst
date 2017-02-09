(ns deferst-test
  (:require
   #?(:cljs [cljs.test :as t
             :refer [deftest is are testing use-fixtures]]
      :clj [clojure.test :as t
            :refer [deftest is are testing use-fixtures]])

   #?(:cljs [deferst.system-test])

   [clojure.set :as set]
   [schema.test]
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

    (testing "simple system stops and returns a promise of the config"
      (is (= @(d/stop! sys)
             {:foo 10})))

    (testing "start! returns new system map when restarted"
      (is (= @(d/start! sys {:foo 20})
             {:foo 20
              :a {:a-arg 20}})))))

;; --- Entry Point

#?(:cljs (enable-console-print!))
#?(:cljs (set! *main-cli-fn* #(t/run-tests
                               *ns*
                               'deferst.system-test)))
#?(:cljs
   (defmethod t/report [:cljs.test/default :end-run-tests]
     [m]
     (if (t/successful? m)
       (set! (.-exitCode js/process) 0)
       (set! (.-exitCode js/process) 1))))
