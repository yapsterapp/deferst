# deferst

[![Build Status](https://travis-ci.org/employeerepublic/deferst.svg?branch=master)](https://travis-ci.org/employeerepublic/deferst)

a tiny clojure (soon clojurescript too) library for managing systems of interdependent objects. it's async friendly, and it won't infect your codebase

## Install

[![Clojars Project](https://img.shields.io/clojars/v/employeerepublic/deferst.svg)](https://clojars.org/employeerepublic/deferst)

## Usage

``` clojure
;; some example object factory fns - factory fns take a single map
;; argument and return an object, or an
;; [object, 0-args-destructor-fn], or a promise thereof

(defn create-db-connection [{:keys [host port]}]
  ;; this might take a while, so let's do it async
  (manifold.deferred/future
    (let [conn (db/create-connection host port)]
      [conn (fn [] (db/close conn))])))

(defn create-client [{:keys [dir db]}] (client/create {:dir dir :db db}))

;; and here's how to build a system of objects and definte their
;; interdependencies

(require '[deferst.system :as s])
(require '[deferst :as d])

;; a system builder requires a tuple for each
;; object to be created of [key factory-fn arg-specs].
;; args-specs is either a path into the system or a
;; map of paths into the system. system builders are
;; fns of config which build a system of objects
(def builder (s/system-builder
               [[:db create-db-connection [:config :db]]
                [:client create-client {:dir [:config :dir]
                                        :db :db}]]))

;; defsystem creates some convenient control fns and
;; associates default config with a system builder fn
(d/defsystem
  builder
  {:config {:db {:host "localhost" :port 9042}
            :dir "/tmp/cache"
            :port 8080}})

;; (start!) returns a promise of the constructed system-map
@(start!) ;; => Deferred< {:config ...
          ;;               :db     ...
          ;;               :client ...} >

;; (stop!) returns a promise of the emptied system-map
@(stop!) ;; destructor fns have been called

;; (reload!) does a tools.namespace reload followed by (start!)
@(reload!) ;; => Deferred< {:config ...
           ;;               :db     ...
           ;;               :client ...} >

```

### More details

- systems are maps of objects identified by keyword keys
- `system-builder`s build systems by creating one object at a time and adding it to the system map against its key
- `system-builder`s create objects using `factory-fns` which are fed a map of arguments extracted from paths in the current system map
- `factory-fns` return either just an `object` or a pair of `[object destructor-fn]`, or a `promise`/`Deferred` of the same
- `factory-fns` can be asynchronous - just return a `Deferred`/`promise` of the `object` or `[object destructor-fn]` pair
- `system-builder`s are created with a list of `object-specs` which are `[key factory-fn arg-specs]` and specify all the information a `system-builder` needs to create an `object`
- a `system-builder`s `object-specs` will be sorted to respect the dependencies implied by the `arg-specs` - but circular dependencies will cause an error
- `system-builder`s iterate over their `object-specs` in dependency (partial-)order, extracting args from the current system-map according to `arg-specs`, building an `object` with `factory-fn` and storing the `object` in the system map against `key`
- `system builders` can be composed
- `start-system!` takes a `system-builder` and a map of `config` and returns a `promise`/`Deferred` of a system, making synchronisation trivial
- errors during a `start-system!` cause the operation to be unwound, calling `destructor-fns` for already constructed objects
- `stop-system!` calls a system's `destructor-fns` in reverse order of object dependencies
- there's some macro sugar which defines `start!`, `stop!` functions, and `tools.namespace` based `reload!` for a single system


``` clojure
;; another factory-fn
(defn create-server [{:keys [client port db]}]
  (let [s (server/create port db client)]
    [s (fn [] (server/stop s))]))

;; builders can be composed
(def server-builder (s/system-builder
                      builder
                      [[:server create-server {:port [:config :port]
                                               :db [:db]
                                               :client :client}]))

;; the defsystem sugar is optional
(def sys (s/start-system!
            server-builder
            {:config {:db {:host "localhost" :port 9042}
                      :dir "/tmp/cache"
                      :port 8080}}))

;; get the system-map from the running system
@(s/system-map sys) ;; => Deferred< {:config ...
                    ;;               :db     ...
                    ;;               :client ...
                    ;;               :server ...} >

;; stop the system, calling destructor functions in reverse order
@(s/stop-system! sys)

```

## Name

internally it uses a deferred-state monad transformer, hence *deferst*

## License

Copyright © 2016 EMPLOYEE REPUBLIC LIMITED

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
