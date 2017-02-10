(ns deferst.core
  (:require
   #?(:clj [clojure.tools.namespace.repl :as tn])
   [clojure.string :as str]
   #?(:clj [manifold.deferred :as d]
      :cljs [promesa.core :as p])
   #?(:clj [cats.labs.manifold :as dm]
      :cljs [cats.labs.promise :as pm])
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
           (reset! r (s/stop-system! sys))
           nil)))
      (if @r
        (s/system-map @r)
        #?(:clj (d/success-deferred nil)
           :cljs (p/promise nil)))))

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
       (def ~name (deferst/create-system
                       ~builder
                       ~default-conf
                       (symbol (name (ns-name *ns*)) (name '~name))))
       (defn ~start-name
         ([] (deferst/start! ~name))
         ([conf#] (deferst/start! ~name conf#)))
       (defn ~system-map-name [] (deferst/system-map ~name))
       (defn ~stop-name [] (deferst/stop! ~name))
       (defn ~reload-name []
         (deferst/stop! ~name)
         (tn/refresh
          :after
          (symbol
           (name (ns-name *ns*))
           (name '~start-name)))))))

(defmacro defsystem
  "macro which defs some vars around a system and provides
   easy tools-namespace reloading. you get

   <base-name>-sys - the Sys record
   <base-name>-start! - start the system, with optional config
   <base-name>-stop! - stop the system if running
   <base-name>-reload! - stop!, tools.namespace refresh, start!"
  ([builder default-conf]
   (defsystem* nil builder default-conf))
  ([base-name builder default-conf]
   (defsystem* base-name builder default-conf)))
