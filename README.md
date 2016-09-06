# deferst

a tiny clojure/script library for managing systems of objects

it uses a deferred-state monad transformer internally, hence *deferst*

## Usage

- objects are created by factory functions
- factory functions return either just an object or a pair of `[object destructor-fn]`
- factory functions are given an argument map gathered from the state
- builders are created with a list of object specs
- object specs are `[key factory-fn arg-specs]`
- builders can be composed
- there's a handy macro to provide start!, stop! and reload! functions


``` clojure
(defn build-client [{:keys [dir]}] (client/create {:dir dir}))
(defn build-server [{:keys [client port]}]
  (let [s (server/create port client)]
    [s (fn [] (server/stop s))]))

(require '[deferst.system :as s])
(require '[deferst :as d])

;; a composable builder for a system
(def builder (s/system-builder [[:client build-client {:dir [:config :dir]}]
                                [:server build-server {:port [:config :port]
                                                       :client [:client]}]]))

;; a simple way of starting, stopping and reloading
(d/defsystem foo builder {:config {:dir "/tmp/cache" :port 8080}})

;; start up the system
(foo-start!) ;; => Deferred< {:client *client* :server *server* :config *config*} >

;; shut it down
(foo-stop!)

;; stop, tools.namespace refresh and start
(foo-reload!)

```

## License

Copyright © 2016 EMPLOYEE REPUBLIC LIMITED

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
