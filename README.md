# deferst

a tiny clojure/script library for managing systems of objects

it uses a deferred-state monad transformer internally, hence *deferst*

## Usage

- systems are maps of objects identified by keyword keys
- `system-builder`s build systems by creating one object at a time and adding it to the system map against its key
- `system-builder`s create objects using `factory-ns` which are fed a map of arguments extracted from paths in the current system map
- `factory-fns` return either just an `object` or a pair of `[object destructor-fn]`, or a `promise`/`Deferred` of the same
- `factory-fns` can be asynchronous - just return a `Deferred`/`promise` of the `object` or `[object destructor-fn]` pair
- `system-builder`s are created with a list of `object-specs` which are `[key factory-fn arg-specs]` and specify all the information a `system-builder` needs to create an `object`
- a `system-builder`s `object-specs` must be ordered to respect the dependencies implied by the `arg-specs` - no automatic dependency resolution is yet attempted
- `system-builder`s iterate over their `object-specs` in order, extracting args from the current system-map according to `arg-specs`, building an `object` with `factory-fn` and storing the `object` in the system map against `key`
- system builders can be composed
- `system-start!` takes a `system-builder` and a map of `config` and returns a `promise`/`Deferred` of a system, making synchronisation trivial
- errors during a `system-start!` cause the operation to be unwound, calling `destructor-fns` for already constructed objects
- `system-stop!` calls a system's `destructor-fns` in reverse order of object construction
- there's a handy macro which defines `start!`, `stop!` functions, and `tools.namespace` based `reload!` for a single system


``` clojure
(defn build-client [{:keys [dir]}] (client/create {:dir dir}))
(defn build-server [{:keys [client port]}]
  (let [s (server/create port client)]
    [s (fn [] (server/stop s))]))

(require '[deferst.system :as s])
(require '[deferst :as d])

;; a builder for a system
(def base-builder (s/system-builder
                     [[:client build-client {:dir [:config :dir]}]
                      [:server build-server {:port [:config :port]
                                             :client [:client]}]]))
;; builders can be composed
(def builder (s/system-builder
                base-builder
                [[:thingy build-thingy {:count [:config :thingy-count]
                                        :client [:client]}]]))

;; give a config map to a system to start it
(def sys (s/start-system! builder {:config {:dir "/tmp/cache"
                                   :port 8080
                                   :thingy-count 5}}))

;; get the system-map from the running system
(s/system-map sys) ;; => Deferred< {:config ...
                   ;;               :client ...
                   ;;               :server ...
                   ;;               :thingy ...} >

;; stop the system, calling destructor functions in reverse order
(s/stop-system! sys)

;; the defsystem macro creates some convenience functions
(d/defsystem foo builder {:config {:dir "/tmp/cache"
                                   :port 8080
                                   :thingy-count 5}})

;; start up the system
(foo-start!) ;; => Deferred< {:config ...
             ;;               :client ...
             ;;               :server ...
             ;;               :thingy ...} >

;; shut it down
(foo-stop!)

;; stop, tools.namespace refresh and start again
(foo-reload!)

```

## License

Copyright © 2016 EMPLOYEE REPUBLIC LIMITED

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
