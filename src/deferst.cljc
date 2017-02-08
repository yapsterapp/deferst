(ns deferst
  (:require
   [clojure.tools.namespace.repl :as tn]
   [clojure.string :as str]
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

(defn- make-name
  [base-name suffix]
  (->> [base-name suffix]
       (filter identity)
       (str/join "-")
       symbol))

(defn- defsystem*
  [base-name builder default-conf]
  (let [name (make-name base-name "sys")
        start-name (make-name base-name "start!")
        system-map-name (make-name base-name "system-map")
        stop-name (make-name base-name "stop!")
        reload-name (make-name base-name "reload!")]
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

(defmacro defsystem
  "macro which defs some vars around a system and provides
   easy tools-namespace reloading. you get

   <name> - the Sys record
   <name>-start! - start the system, with optional config
   <name>-stop! - stop the system if running
   <name>-reload! - stop!, tools.namespace refresh, start!"
  ([builder default-conf]
   (defsystem* nil builder default-conf))
  ([name builder default-conf]
   (defsystem* name builder default-conf)))
