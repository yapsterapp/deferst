(ns deferst
  (:require
   [clojure.tools.namespace.repl :as tn]
   [deferst.system :as s]))

(defprotocol ISys
  (start! [_] [_ conf])
  (stop! [_])
  (system-map [_]))

(defrecord Sys [builder sys-atom default-conf label]
  ISys
  (start! [this]
    (start! this default-conf))

  (start! [_ conf]
    (swap!
     sys-atom
     (fn [sys]
       (if sys
         sys
         (do
           (when label (println "starting: " label))
           (s/start-system! builder conf)))))
    (s/system-map @sys-atom))

  (stop! [_]
    (let [r (atom nil)]
      (swap!
       sys-atom
       (fn [sys]
         (when sys
           (when label (println "stopping: " label))
           (reset! r (s/stop-system! @sys-atom))
           nil)))
      @r))

  (system-map [_]
    (s/system-map @sys-atom)))

(defn create-system
  ([builder]
   (create-system builder nil nil))
  ([builder default-conf]
   (create-system builder default-conf nil))
  ([builder default-conf label]
   (map->Sys {:builder builder
              :sys-atom (atom nil)
              :default-conf default-conf
              :label label})))

(defmacro defsystem
  "macro which defs some vars around a system and provides
   easy tools-namespace reloading. you get

   <name> - the Sys record
   <name>-start! - start the system, with optional config
   <name>-stop! - stop the system if running
   <name>-reload! - stop!, tools.namespace refresh, start!"
  [name builder default-conf]
  (let [start-name (symbol (str name "-start!"))
        system-map-name (symbol (str name "-system-map"))
        stop-name (symbol (str name "-stop!"))
        reload-name (symbol (str name "-reload!"))]
    `(do
       (def ~name (create-system
                   ~builder
                   ~default-conf
                   (symbol (name (ns-name *ns*)) (name '~name))))
       (defn ~start-name
         ([] (start! ~name))
         ([conf#] (start! ~name conf#)))
       (defn ~system-map-name [] (system-map ~name))
       (defn ~stop-name [] (stop! ~name))
       (defn ~reload-name []
         (stop! ~name)
         (tn/refresh :after (symbol (name (ns-name *ns*)) (name '~start-name)))))))
