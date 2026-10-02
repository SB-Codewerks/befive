(ns befive.cp.http
  "Admin listener. Milestone 1 binds the port and returns the
  minimal not-found body. `/admin/v1` arrives in milestone 6."
  (:require [befive.core.response :as response]
            [befive.core.server :as server]))

(set! *warn-on-reflection* true)

(defn handler
  [_request]
  (response/not-found))

(defn start
  [{:keys [admin-port]}]
  (server/start handler admin-port))

(defn stop
  [component]
  (server/stop component))
