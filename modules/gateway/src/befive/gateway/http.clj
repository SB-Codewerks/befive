(ns befive.gateway.http
  "Data-plane listener. The handler dereferences the route table
  once per request and returns a deferred."
  (:require [befive.core.server :as server]
            [befive.gateway.access-log :as access-log]
            [befive.gateway.compile :as compile]
            [befive.gateway.health :as health]
            [befive.gateway.pipeline :as pipeline]
            [befive.gateway.sync :as sync])
  (:import (javax.sql DataSource)))

(set! *warn-on-reflection* true)

(defn state
  "Gateway state for `settings`. `datasource` may be nil."
  ([settings]
   (state settings nil))
  ([settings datasource]
   (let [table (atom (compile/empty-table))]
     {:settings settings
      :table table
      :targets (atom {})
      :pools (atom {})
      :lambda-clients (atom {})
      :ready-revision (atom nil)
      :access-log (access-log/start {})
      :datasource datasource})))

(defn- datasource
  [db]
  (when-let [source (:datasource db)]
    ^DataSource source))

(defn start
  "Bind the gateway. Sync loads config on a virtual thread, so
  `/healthz` does not wait for PostgreSQL."
  [{:keys [settings db] :as component}]
  (let [settings (if (:gateway-port settings) settings component)
        db (when (map? db) db)
        gateway (state settings (datasource db))
        handler (fn [request]
                  (pipeline/handle gateway request))
        server (server/start handler
                             (:gateway-port settings)
                             {:executor :none
                              :allow-duplicate-content-lengths true})
        probes (health/start (:targets gateway))
        syncing (sync/start {:table (:table gateway)
                             :targets (:targets gateway)
                             :settings settings
                             :datasource (:datasource gateway)})]
    (assoc server
           :gateway gateway
           :probes probes
           :sync syncing)))

(defn stop
  [component]
  (when-let [halt (:stop! (:sync component))]
    (halt))
  (when-let [halt (:stop! (:probes component))]
    (halt))
  (when-let [log (:access-log (:gateway component))]
    (access-log/stop! log))
  (server/stop component))
