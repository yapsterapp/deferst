(ns deferst.core-test
  (:require
   #?(:cljs [cljs.test :as t
             :refer [deftest is are testing use-fixtures]]
      :clj [clojure.test :as t
            :refer [deftest is are testing use-fixtures]])

   #?(:cljs [deferst.system-test])

   [clojure.set :as set]
   [schema.test]
   [deferst.system :as s]
   [deferst.core :as d]))

(deftest simple-sys
  (let [sb (s/system-builder [[:a identity {:a-arg [:foo]}]])
        sys (d/create-system sb {:foo 10})]

    (testing "simple system starts"
      (let [sysmap (d/start! sys)
            x-sysmap {:foo 10 :a {:a-arg 10}}]
        #?(:clj
           (is (= @sysmap x-sysmap)))))

    (testing "simple system returns system map"
      (let [sysmap (d/system-map sys)
            x-sysmap {:foo 10 :a {:a-arg 10}}]
        #?(:clj
           (is (= @sysmap x-sysmap)))))

    (testing "start! returns same system map if already started"
      (let [sysmap (d/start! sys {:foo 20})
            x-sysmap {:foo 10 :a {:a-arg 10}}]
        #?(:clj
           (is (= @sysmap x-sysmap)))))

    (testing "simple system stops and returns a promise of the config"
      (let [stop-sys (d/stop! sys)
            x-stopmap {:foo 10}]
        #?(:clj
           (is (= @stop-sys x-stopmap)))))

    (testing "start! returns new system map when restarted"
      (let [sysmap (d/start! sys {:foo 20})
            x-sysmap {:foo 20 :a {:a-arg 20}}]
        #?(:clj
           (is (= @sysmap x-sysmap)))))))

;; --- Entry Point

#?(:cljs (enable-console-print!))
#?(:cljs (set! *main-cli-fn* #(t/run-tests
                               'deferst.core-test
                               'deferst.system-test)))
#?(:cljs
   (defmethod t/report [:cljs.test/default :end-run-tests]
     [m]
     (if (t/successful? m)
       (set! (.-exitCode js/process) 0)
       (set! (.-exitCode js/process) 1))))
