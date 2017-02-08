(ns deferst.system
  (:require
   [schema.core :as s]
   [clojure.set :as set]
   [cats.context :refer [with-context]]
   [cats.data :as data]
   [cats.labs.state
    :refer [state state-t get-state put-state swap-state run-state]]
   [deferst.kahn :refer [kahn-sort]]

   ;; has to come after state, because of the re-aninated nature
   ;; of monad transformers
   [cats.core :refer [mlet return bind lift join]]
   #?(:clj [cats.labs.manifold :as dm]
      :cljs [cats.labs.promise :as pm])
   #?(:clj [manifold.deferred :as d]))
  (:import
   [cats.labs.state State]))

(def ^:private config-ctx (state-t cats.labs.manifold/deferred-context))

(def ^:private SystemStateSchema
  {;; for each key, a map of constructor, destructor config-ctx
   ;; value fns and its dependencies
   ::system {s/Keyword {:constructor (s/pred #(instance? State %))
                        :destructor (s/pred #(instance? State %))
                        :deps {s/Keyword #{s/Keyword}}}}

   ;; the managed objects
   s/Keyword s/Any})

(defn- valid-system-state?
  [st]
  (nil? (s/check SystemStateSchema st)))

(defn- new-system-state
  "initial system state"
  []
  {::system {}})

(def ^:private system-keys
  (-> (new-system-state) keys set))

(defn- filter-system-map
  "remove the internal-use keys from the state"
  [st]
  (apply dissoc st system-keys))

(defn- all-deps
  [st]
  (->> st ::system (map (fn [[k {deps :deps}]] [k deps])) (into {})))

(defn- dependents
  "return a topo-sorted list of all keys which
   are dependent on the keys in key-set"
  [st key-set]
  (let [key-set (set key-set)
        ads (all-deps st)
        d-set (->> ads
                   (filter
                    (fn [[k deps]]
                      (not-empty
                       (set/intersection key-set deps))))
                   (map first)
                   set)
        tsks (kahn-sort ads)]
    (filter d-set tsks)))

(defn- new-system
  "construct a new system"
  []
  (with-context config-ctx
    (mlet [ist (get-state)
           :let [_ (when (valid-system-state? ist)
                     (throw (ex-info "can't create a new system with a system seed"
                                     {:state ist})))
                 st (merge ist (new-system-state))]
           _ (put-state st)]
      (return (filter-system-map st)))))

(defn- factory-args
  "given args-specs of form
   {k key-or-key-path} or [key-path] extract
   args and dependencies from state and return
   [args deps] where args is a map of {key val}
   and deps is a set #{dep-key}"
  [st arg-specs]
  (cond
    (map? arg-specs)
    (reduce
     (fn [[args deps] [k arg-spec]]
       (let [arg-spec (if (vector? arg-spec)
                        arg-spec
                        [arg-spec])]
         [(assoc args
                 k
                 (get-in st arg-spec))
          (conj deps (first arg-spec))]))
     [{} #{}]
     arg-specs)

    (vector? arg-specs)
    [(get-in st arg-specs)
     (first arg-specs)]

    :else
    (throw
     (ex-info
      "args-specs must be a state path vector or a map of state path vectors"
      {:state st
       :arg-specs arg-specs}))))

(defn- is-promise?
  [obj]
  (d/deferred? obj))

(defn construct-maybe-lift
  "construct an object given a factory-fn and args. if the
   object is already Deferred lift it into the config-ctx,
   otherwise wrap it"
  [st factory-fn args]
  (with-context config-ctx
    (try
      (let [obj (factory-fn args)]
        (if (is-promise? obj)
          (lift config-ctx obj)
          (return obj)))
      (catch Exception e
        (throw (ex-info "factory-fn threw"
                        {:state st
                         :error e}))))))

(defn construct-obj
  [factory-fn arg-specs]
  (with-context config-ctx
    (mlet
      [st (get-state)
       :let [[args deps] (factory-args st arg-specs)]
       obj-and-maybe-destructor-fn (construct-maybe-lift st factory-fn args)
       :let [[obj destructor-fn] (if (sequential? obj-and-maybe-destructor-fn)
                                   obj-and-maybe-destructor-fn
                                   [obj-and-maybe-destructor-fn])]]
      (return [obj destructor-fn deps]))))

(defn destroy-maybe-lift
  [st destructor-fn]
  (with-context config-ctx
    (try
      (let [r (destructor-fn)]
        (if (is-promise? r)
          (lift config-ctx r)
          (return r)))
      (catch Exception e
        (throw (ex-info "destructor-fn threw"
                        {:state st
                         :error e}))))))

(defn m-destructor
  "returns a monadic value which destroys key
   k and removes it from the system state"
  [k destructor-fn]
  (with-context config-ctx
    (mlet
      [st (get-state)

       :let [dpdts (dependents st [k])
             _ (when (not-empty dpdts)
                 (throw (ex-info
                         (str "can't destroy "
                              k
                              " it still has dependents")
                         {:state st
                          :k k
                    :dependents dpdts})))

             _ (destroy-maybe-lift st destructor-fn)

             new-st (-> st
                        (dissoc k)
                        (update-in [::system] dissoc k))]

       _ (put-state new-st)]
      (return
       (filter-system-map new-st)))))

(defn m-constructor
  "returns a monadic value which creates key k,
   and adds it to the system state"
  [k factory-fn arg-specs]
  (with-context config-ctx
    (mlet
      [st (get-state)

       :let [_ (when (contains? st k)
                 (throw (ex-info
                         (str "key already exists: " k)
                         {:state st
                          :k k})))]

       [obj destructor-fn deps] (construct-obj factory-fn arg-specs)

       :let [constructor (m-constructor k factory-fn arg-specs)
             destructor (m-destructor k destructor-fn)
             new-st (-> st
                        (assoc k obj)
                        (assoc-in
                         [::system k]
                         {:constructor constructor
                          :destructor destructor
                          :deps deps}))]
       _ (put-state new-st)]
      (return
       (filter-system-map new-st)))))

(defn system-builder
  "return a system-builder in the Deferred<State> monad which
   will build objects in the state map with build-obj according
   to the key-factory-fn-argspecs-list.

   when run with run-state and a seed (using start-system!)
   the system-builder will return a Deferred<[system-map, system]>"
  ([key-factoryfn-argspecs-list]
   (system-builder nil key-factoryfn-argspecs-list))

  ([base-system-builder key-factoryfn-argspecs-list]
   (reduce (fn [mv [k factory-fn arg-specs]]
             (with-context config-ctx
               (bind mv (fn [_]
                          (m-constructor k factory-fn arg-specs)))) )
           (or base-system-builder (new-system))
           key-factoryfn-argspecs-list)))

(defn system-destructor
  "given a system (including the ::system key) create a
   system-destructor which will apply the individual object
   destructors for all the objects in the system in
   reverse dependency order"
  [system]
  (let [ads (all-deps system)
        objks (-> system ::system keys set)
        dks (->> ads kahn-sort (filter objks))]
    (reduce (fn [mv k]
              (with-context config-ctx
                (bind mv (fn [_]
                           (get-in system [::system k :destructor])))))
            (return config-ctx system)
            dks)))

(defn stop-system!
  "given a Deferred<[_, system]> stop the system, running
   destructor functions in reverse order to object construction"
  [dsys]
  (with-context dm/deferred-context
    (bind dsys
          (fn [[_ system]]
            (let [sd (system-destructor system)]
              (run-state sd system))))))

(defn start-system!
  "given a system-builder, start a system with the seed config
   returns a Deferred<[system-map, system]>. if an Exception
   is thrown somewhere, unwind the system construction and
   call destructor functions for the objects which have so
   far been constructed"
  [system-builder seed]
  (-> (run-state system-builder seed)
      (d/catch
          Exception
          (fn [e]
            (let [{st :state :as xd} (ex-data e)
                  sd (system-destructor st)]
              (d/chain
               (d/catch
                   (run-state sd sd)
                   Exception
                   (fn [ue]
                     (throw
                      (ex-info "unwind failed after start-system! failed"
                               {:state st
                                :error e
                                :unwind-error ue}))))
               (fn [_]
                 (throw
                  (ex-info "start-system! failed and unwound"
                           {:state st
                            :error e})))))))))

(defn system-map
  "given a Deferred<[system-map, system]> extract the system-map
  to a Deferred<system-map>"
  [sys]
  (with-context dm/deferred-context
    (bind sys
          (fn [[sm _]]
            (return sm)))))

(comment
  (require '[deferst.system :as dfs])

  ;; simple object factory which returns a [obj destructor] pair
  (def ff (fn [v] [v (fn [] (prn "destroying" v))]))

  ;; a new context builder... uses the ff factory to create
  ;; a couple of objects, with :a depending on seed state and
   ;; :b on :a
  (def sb (dfs/system-builder [[:a ff {:a-foo [:foo]}]
                               [:b ff {:b-foo :a}]]))

  ;; supply seed state to start the system, then stop it
  (def s (dfs/start-system! sb {:foo 10}))
  (dfs/stop-system! s)

  ;; a system-builder building on another system-builder
  ;; which creates a new object :c depending on :a and :b
  (def sb2 (dfs/system-builder sb
                              [[:c ff {:c-a :a
                                       :c-b :b}]]))

  (def s2 (dfs/start-system! sb2 {:foo 10}))
  (dfs/stop-system! s2)

  )
