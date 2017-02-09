(ns cats.labs.lift
  (:require
   [cats.context :as ctx]
   [cats.labs.monad-trans :as mt]))

(defn lift
  "Lift a value from the inner monad of a monad transformer
  into a value of the monad transformer."
  ([mv] (mt/-lift ctx/*context* mv))
  ([m mv] (mt/-lift m mv)))
