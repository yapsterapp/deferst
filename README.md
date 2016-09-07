# deferst

a tiny clojure/script library for managing systems of objects

it uses a deferred-state monad transformer internally, hence *deferst*

## Usage

- objects are created by factory functions
- factory functions return either just an object or a pair of `[object destructor-fn]`, or a promise/Deferred of the same
- factory functions are given an argument map gathered from the state according to arg-specs
- factory functions can be asynchronous - just return a Deferred/promise of the object or `[object destructor-fn]` pair
- system builders are created with a list of object specs
- object specs are `[key factory-fn arg-specs]`
- objects will be built with the `factory-fn` and placed in the system with the `key`
- system builders can be composed
- `system-start!` takes a system builder and a map of config and returns a promise of a system, making synchronisation trivial
- errors during a `system-start!` cause the operation to be unwound, calling destructor functions on already constructed objects
- `system-stop!` calls a system's destructor functions in reverse order of object construction
- there's a handy macro which defines start!, stop! functions, and tools.namespace based reload! for a single system


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
