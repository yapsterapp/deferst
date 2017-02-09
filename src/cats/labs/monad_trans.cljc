(ns cats.labs.monad-trans)

(defprotocol MonadTrans
  "A monad transformer abstraction."
  (-lift [m mv] "Lift a value from the parameterized monad to the transformer."))
