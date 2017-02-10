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
#?(:clj
   (use-fixtures :once schema.test/validate-schemas))

(deftest empty-system
  (let [sb (s/system-builder [])
        sys (s/start-system! sb {:foo 10})
        sysmap (s/system-map sys)
        x-sysmap {:foo 10}]

    (testing "null system contains config"
      #?(:clj (is (= @sysmap x-sysmap))

         :cljs
         (t/async
          done
          (p/then
           sysmap
           (fn [v]
             (t/is (= v x-sysmap))
             (done))))))))

(deftest empty-system-stops
  (let [sb (s/system-builder [])
        sys (s/start-system! sb {:foo 10})
        stop-sys (s/stop-system! sys)]

    (testing "null system has no managed objects"
      #?(:clj
         (do
           (is (contains? (-> sys deref last) :deferst.system/system))
           (is (empty? (-> sys deref last :deferst.system/system))))

         :cljs
         (t/async
          done
          (p/then
           sys
           (fn [v]
             (is (contains? (-> v last) :deferst.system/system))
             (is (empty? (-> v last :deferst.system/system)))
             (done))))))))

(deftest single-item-system
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:a ff {:a-arg [:foo]}]])
        sys (s/start-system! sb {:foo 10})
        sysmap (s/system-map sys)
        stop-sys (s/stop-system! sys)

        x-sysmap {:foo 10 :a {:a-arg 10}}
        x-dvals [{:a-arg 10}]]

    (testing "single item system has the single object"
      #?(:clj
         (do
           @stop-sys
           (is (= @sysmap
                  x-sysmap)))

         :cljs
         (t/async
          done
          (p/then
           sysmap
           (fn [v] (is (= v x-sysmap))
             (done))))))

    (testing "single item was destroyed"
      #?(:clj
         (is (= @destructor-vals
                x-dvals))
         :cljs
         (t/async
          done
          (p/then
           stop-sys
           (fn [_] (is (= @destructor-vals x-dvals))
             (done))))))))

(deftest single-item-system-with-vector-arg-specs
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:a ff [:foo]]])
        sys (s/start-system! sb {:foo 10})
        smap (s/system-map sys)
        stop-sys (s/stop-system! sys)
        x-sysmap {:foo 10 :a 10}
        x-dvals [10]]

    (testing "single item system has the single object"
      #?(:clj
         (is (= @smap
                x-sysmap))

         :cljs
         (t/async
          done
          (p/then
           smap
           (fn [v] (= v x-sysmap)
             (done))))))

    (testing "single item was destroyed"
      #?(:clj
         (do
           @stop-sys
           (is (= @destructor-vals
                  x-dvals)))

         :cljs
         (t/async
          done
          (p/then
           stop-sys
           (fn [_] (is (= @destructor-vals x-dvals))
             (done))))))))

(deftest bad-arg-specs-throw
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])]

    (testing "an error is thrown describing the bad arg specs"
      (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs :default)
                   (s/system-builder [[:a ff :foo]]))))))

(deftest single-item-system-without-destructors
  (let [sb (s/system-builder [[:a identity {:a-arg [:foo]}]])
        sys (s/start-system! sb {:foo 10})
        sysmap (s/system-map sys)
        stop-sys (s/stop-system! sys)
        x-sysmap {:foo 10
                  :a {:a-arg 10}}]

    (testing "single item system has the single object"
      #?(:clj
         (is (= @sysmap x-sysmap))

         :cljs
         (t/async
          done
          (p/then
           sysmap
           (fn [v] (is (= v x-sysmap))
             (done))))))))

(deftest single-deferred-item-system
  (let [destructor-vals (atom [])

        ff (fn [v]
             (let [obj-destr [v (fn [] (swap! destructor-vals conj v))]]
               #?(:clj (d/success-deferred obj-destr)
                  :cljs (p/promise obj-destr))))
        sb (s/system-builder [[:a ff {:a-arg [:foo]}]])
        sys (s/start-system! sb {:foo 10})
        sysmap (s/system-map sys)
        stop-sys (s/stop-system! sys)
        x-sysmap {:foo 10 :a {:a-arg 10}}]

    (testing "single item system has the single object"
      #?(:clj
         (is (= @sysmap x-sysmap))

         :cljs
         (t/async
          done
          (p/then
           sysmap
           (fn [v] (is (= v x-sysmap))
             (done))))
         ))))


(deftest dependent-item-system
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:a ff {:a-arg [:foo]}]
                              [:b ff {:b-arg [:a :a-arg]}]])
        sys (s/start-system! sb {:foo 10})
        sysmap (s/system-map sys)
        stop-sys (s/stop-system! sys)
        x-sysmap {:foo 10 :a {:a-arg 10} :b {:b-arg 10}}
        x-dvals [{:b-arg 10} {:a-arg 10}]]

    (testing "dependent items are created"
      #?(:clj
         (is (= @sysmap x-sysmap))

         :cljs
         (t/async
          done
          (p/then
           sysmap
           (fn [v] (is (= v x-sysmap))
             (done))))))

    (testing "dependent items were destroyed"
      #?(:clj
         (is (= @destructor-vals x-dvals))

         :cljs
         (t/async
          done
          (p/then
           stop-sys
           (fn [v] (is (= @destructor-vals x-dvals))
             (done))))))))

(deftest dependent-item-system-specified-out-of-order
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:b ff {:b-arg [:a :a-arg]}]
                              [:a ff {:a-arg [:foo]}]])
        sys (s/start-system! sb {:foo 10})
        sysmap (s/system-map sys)
        stop-sys (s/stop-system! sys)
        x-sysmap {:foo 10 :a {:a-arg 10} :b {:b-arg 10}}
        x-dvals [{:b-arg 10} {:a-arg 10}]]

    (testing "dependent items are created"
      #?(:clj
         (is (= @sysmap x-sysmap))

         :cljs
         (t/async
          done
          (p/then
           sysmap
           (fn [v] (is (= v x-sysmap))
             (done))))))

    (testing "dependent items were destroyed"
      #?(:clj
         (is (= @destructor-vals x-dvals))

         :cljs
         (t/async
          done
          (p/then
           stop-sys
           (fn [_] (is (= @destructor-vals x-dvals))
             (done))))))))

(deftest dependent-item-system-with-circular-deps
  (let [ff (fn [v] v)]
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :cljs :default)
         #"circular dependency"
         (s/system-builder [[:a ff {:a-arg [:b :b-arg]}]
                            [:b ff {:b-arg [:a :a-arg]}]])))))

(deftest dependent-item-system-with-vector-arg-specs
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:a ff [:foo]]
                              [:b ff {:b-arg [:a]}]])
        sys (s/start-system! sb {:foo 10})
        sysmap (s/system-map sys)
        stop-sys (s/stop-system! sys)
        x-sysmap {:foo 10 :a 10 :b {:b-arg 10}}
        x-dvals [{:b-arg 10} 10]]

    (testing "dependent items are created"
      #?(:clj
         (is (= @sysmap
                x-sysmap))

         :cljs
         (t/async
          done
          (p/then
           sysmap
           (fn [v] (is (= v x-sysmap))
             (done))))))

    (testing "dependent items were destroyed"
      #?(:clj
         (is (= @destructor-vals x-dvals))

         :cljs
         (t/async
          done
          (p/then
           stop-sys
           (fn [_] (is (= @destructor-vals x-dvals))
             (done))))))))

(deftest dependent-item-system-with-mixed-destructors
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb (s/system-builder [[:a identity {:a-arg [:foo]}]
                              [:b ff {:b-arg [:a :a-arg]}]])
        sys (s/start-system! sb {:foo 10})
        sysmap (s/system-map sys)
        stop-sys (s/stop-system! sys)
        x-sysmap {:foo 10 :a {:a-arg 10} :b {:b-arg 10}}
        x-dvals [{:b-arg 10}]]

    (testing "dependent items are created"
      #?(:clj
         (is (= @sysmap x-sysmap))

         :cljs
         (t/async
          done
          (p/then
           sysmap
           (fn [v] (is (= v x-sysmap))
             (done))))))

    (testing "dependent items were destroyed"
      #?(:clj
         (is (= @destructor-vals x-dvals))

         :cljs
         (t/async
          done
          (p/then
           stop-sys
           (fn [_] (is (= @destructor-vals x-dvals))
             (done))))))))

(deftest composed-builders-creation
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        sb-obj-specs [[:a ff {:a-arg [:foo]}]
                      [:b identity {:b-arg [:a :a-arg]}]]
        sb2-obj-specs [[:c ff {:c-a [:a :a-arg]
                               :c-b [:b :b-arg]}]]
        x-sysmap {:foo 10 :a {:a-arg 10} :b {:b-arg 10} :c {:c-a 10 :c-b 10}}
        x-dvals [{:c-a 10 :c-b 10} {:a-arg 10}]

        sb (s/system-builder sb-obj-specs)
        sb2 (s/system-builder sb sb2-obj-specs)
        sys (s/start-system! sb2 {:foo 10})
        sysmap (s/system-map sys)]

    (testing "items are created"
      #?(:clj
         (is (= @sysmap x-sysmap))

         :cljs
         (t/async
          done
          (p/then
           sysmap
           (fn [v] (is (= v x-sysmap))
             (done))))))))

(deftest composed-builders-destruction
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])

        sb (s/system-builder [[:a ff {:a-arg [:foo]}]
                              [:b identity {:b-arg [:a :a-arg]}]])
        sb2 (s/system-builder sb [[:c ff {:c-a [:a :a-arg]
                                          :c-b [:b :b-arg]}]])
        sys (s/start-system! sb2 {:foo 10})
        stop-sys (s/stop-system! sys)

        x-sysmap {:foo 10 :a {:a-arg 10} :b {:b-arg 10} :c {:c-a 10 :c-b 10}}
        x-dvals [{:c-a 10 :c-b 10} {:a-arg 10}]]

    (testing "items were destroyed"
      #?(:clj
         (do
           @stop-sys
           (is (= @destructor-vals x-dvals)))

         :cljs
         (t/async
          done
          (p/then
           stop-sys
           (fn [_] (is (= @destructor-vals x-dvals))
             (done))))))))

(deftest unwind-on-builder-error
  (let [destructor-vals (atom [])
        ff (fn [v] [v (fn [] (swap! destructor-vals conj v))])
        boom (fn [v] (throw (ex-info "boom" {:boom true})))
        sb (s/system-builder [[:a ff {:a-arg [:foo]}]
                              [:b boom {:b-arg [:a :a-arg]}]])
        sys (s/start-system! sb {:foo 10})
        x-dvals [{:a-arg 10}]]
    (testing "system is unwound"
      #?(:clj
         (do
           (is (= @destructor-vals x-dvals))
           (is (thrown-with-msg?
                clojure.lang.ExceptionInfo
                #"start-system! failed and unwound"
                @sys)))

         :cljs
         (do
           (t/async
            done
            (p/catch
                sys
                (fn [e]
                  (is (= @destructor-vals x-dvals))
                  (done)))))))))
