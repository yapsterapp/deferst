(ns deferst.system
  (:require
   [schema.core :as s]
   [cats.context :refer [with-context]]
   [cats.labs.state
    :refer [state-t get-state put-state swap-state run-state]]

   ;; has to come after state, because of the re-aninated nature
   ;; of monad transformers
   [cats.core :refer [mlet return bind lift]]
   #?(:clj [cats.labs.manifold :as dm]
      :cljs [cats.labs.promise :as pm])
   #?(:clj [manifold.deferred :as d])))

(def ^:private config-ctx (state-t cats.labs.manifold/deferred-context))

;; destructors are added by put-obj and
;; called in reverse order during stop!
(def ^:private SystemStateSchema
  {:config/destroyed (s/pred (fn [v]
                               (and (instance? clojure.lang.Atom v)
                                    (or (nil? @v)
                                        (= true @v)
                                        (= false @v)))))
   :config/destructors [[(s/one s/Keyword :destructor-key)
                         (s/one (s/maybe (s/pred fn?)) :destructor-fn)]]
   s/Keyword s/Any})

(defn- valid-system-state?
  [st]
  (nil? (s/check SystemStateSchema st)))

(defn- new-system-state
  "initial system state"
  []
  {:config/destroyed (atom false)
   :config/destructors []})

(defn- filter-system-map
  "get the filter-system-map given the system-state"
  [st]
  (dissoc st
          :config/destroyed
          :config/destructors))

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

(defn- put-obj
  "put an object with an optional destructor fn into the system map
   at key k

  throws if an object already exists with the given key"
  ([k obj] (put-obj k obj nil))
  ([k obj destructor-fn]
   (with-context config-ctx
     (mlet [st (get-state)
            :let [o (get st k)
                  _ (when o (throw (ex-info (str "key already exists: " k)
                                            {:state st})))
                  des (:config/destructors st)]
            _ (put-state (assoc
                          st
                          :config/destructors (conj des [k destructor-fn])
                          k obj))
            st (get-state)]
       (when-not (valid-system-state? st)
         (throw (ex-info "invalid system state" {:state st})))
       (return (filter-system-map st))))))

(defn- factory-args
  "given args-specs of form
   {k key-or-key-path} or [key-path] extract
   args from state"
  [st arg-specs]
  (cond
    (map? arg-specs)
    (reduce (fn [args [k arg-spec]]
              (assoc args
                     k
                     (get-in st (if (sequential? arg-spec)
                                  arg-spec
                                  [arg-spec]))))
            {}
            arg-specs)

    (vector? arg-specs)
    (get-in st arg-specs)

    :else
    (throw
     (ex-info
      "args-specs must be a state path vector or a map of state path vectors"
      {:state st
       :arg-specs arg-specs}))))

(defn- is-promise?
  [obj]
  (d/deferred? obj))

(defn- build-obj
  "build a single object and put in the system at key k given
  a factory-fn and arg-specs

  a map of args for the factory-fn will be taken from the state
  according  to the arg-specs

  the factory-fn can return either a plain object or
  [obj destructor-fn] in which case when stop! is
  called on a system, the destructor-fns for all the constructed
  objects will be called in reverse order"
  [k obj-factory-fn arg-specs]
  (with-context config-ctx
    (mlet [st (get-state)
           :let [args (factory-args st arg-specs)]
           obj-and-destructor-fn (try
                                   (let [obj (obj-factory-fn args)]
                                     (if (is-promise? obj)
                                       (lift config-ctx obj)
                                       (return obj)))
                                   (catch Exception e
                                     (throw (ex-info "factory-fn threw"
                                                     {:state st
                                                      :error e}))))
           :let [[obj destructor-fn] (if (sequential? obj-and-destructor-fn)
                                       obj-and-destructor-fn
                                       [obj-and-destructor-fn])]]
      (put-obj k obj destructor-fn))))

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
                          (build-obj k factory-fn arg-specs)))) )
           (or base-system-builder (new-system))
           key-factoryfn-argspecs-list)))

(defn- call-destructor-fns
  "calls destructor fns in reverse order,
   collecting a list of Exceptions along the way"
  [destructor-fns]
  (doall
   (filter
    identity
    (for [[_ df] (reverse destructor-fns)]
      (when df
        (try
          (df)
          nil
          (catch Exception e
            e)))))))

(defn stop-system!
  "given a Deferred<[_, system]> stop the system, running
   destructor functions in reverse order to object construction"
  [sys]
  (with-context dm/deferred-context
    (bind sys
          (fn [[_ st]]
            (let [da (:config/destroyed st)
                  d? @da
                  destr (:config/destructors st)]
              (reset! da true)
              (if-not d?
                (let [d-errors (call-destructor-fns destr)]
                  (if (empty? d-errors)
                    true
                    d-errors))
                false))))))

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
                  da (:config/destroyed st)
                  destr (:config/destructors st)]
              (reset! da true)
              (let [d-errors (call-destructor-fns destr)]
                (throw
                 (ex-info "start-system! failed and unwound"
                          {:state st
                           :destructor-errors d-errors
                           :error e}))))))))

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
