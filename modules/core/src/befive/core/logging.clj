(ns befive.core.logging
  "Startup log lines. The line never includes a secret."
  (:require [clojure.tools.logging :as log]))

(set! *warn-on-reflection* true)

(defn log-start
  [{:keys [role node-id environment ops-port gateway-port admin-port]}]
  (log/info "starting"
            {:role role
             :node-id node-id
             :environment environment
             :ops-port ops-port
             :gateway-port gateway-port
             :admin-port admin-port}))
