(ns befive.gateway.http
  "Data-plane listener. Milestone 1 answers every request with the
  minimal not-found body. The proxy pipeline arrives in milestone 2."
  (:require [befive.core.response :as response]
            [befive.core.server :as server]))

(set! *warn-on-reflection* true)

(defn handler
  [_request]
  (response/not-found))

(defn start
  [{:keys [gateway-port]}]
  (server/start handler gateway-port))

(defn stop
  [component]
  (server/stop component))
