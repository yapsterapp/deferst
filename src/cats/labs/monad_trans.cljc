(ns cats.labs.monad-trans
  (:require
   [cats.context :as ctx]))

(defprotocol MonadTrans
  "A monad transformer abstraction."
  (-lift [m mv] "Lift a value from the parameterized monad to the transformer."))

(defn lift
  "Lift a value from the inner monad of a monad transformer
  into a value of the monad transformer."
  ([mv] (-lift ctx/*context* mv))
  ([m mv] (-lift m mv)))
