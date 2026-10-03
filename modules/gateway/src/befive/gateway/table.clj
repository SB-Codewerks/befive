(ns befive.gateway.table
  "Atomic route table. A failed compile leaves the current revision
  in place."
  (:require [befive.gateway.compile :as compile]
            [befive.gateway.targets :as targets]
            [clojure.tools.logging :as log]))

(set! *warn-on-reflection* true)

(defn current
  [table]
  @table)

(defn- truncate
  [text]
  (let [text (or text "compile failed")]
    (if (> (count text) 300)
      (subs text 0 300)
      text)))

(defn swap-in!
  "Compile `document` and publish it. On failure, keep the old table."
  [table document targets-atom]
  (try
    (let [compiled (compile/compile-snapshot document)]
      (reset! table compiled)
      (when targets-atom
        (targets/reconcile! targets-atom (:upstreams compiled)))
      {:applied (:revision compiled)})
    (catch Throwable thrown
      (let [message (truncate (.getMessage thrown))
            kept (:revision @table)]
        (log/error "route table compile failed"
                   {:revision (:revision document)
                    :kept kept
                    :error message})
        {:kept kept
         :error message}))))
