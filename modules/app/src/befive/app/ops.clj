(ns befive.app.ops
  "Ops listener on port 9901: /healthz, /readyz and /internal/info."
  (:require [befive.core.db :as db]
            [befive.core.health :as health]
            [befive.core.response :as response]
            [befive.core.server :as server]
            [befive.core.version :as version]))

(defn handle
  "Pure handler. `snapshot` is the health value, `info` is safe to show."
  [{:keys [snapshot info]} request]
  (case (:uri request)
    "/healthz" (response/json 200 {:status "ok"})
    "/readyz" (if (health/ready? snapshot)
                (response/json 200 {:status "ready"})
                (response/json
                 503
                 {:status "not-ready"
                  :checks {:database (:database snapshot)
                           :config (:config snapshot)
                           :listeners (:listeners snapshot)}}))
    "/internal/info" (response/json 200 info)
    (response/not-found)))

(defn- refresh!
  [health db settings]
  (let [up? (db/ping db)
        done? (health/migrations-done? health (:role settings))]
    (swap! health
           (fn [current]
             (health/next-snapshot
              current
              {:role (:role settings)
               :database-up? up?
               :migrations-done? done?
               :listeners-bound? true})))))

(defn start
  [{:keys [settings health db]}]
  (let [info (merge (version/info)
                    {:role (:role settings)
                     :node-id (:node-id settings)
                     :environment (:environment settings)})
        handler (fn [request]
                  (let [uri (:uri request)
                        snapshot (if (= uri "/readyz")
                                   (refresh! health db settings)
                                   @health)]
                    (handle {:snapshot snapshot :info info} request)))]
    (server/start handler (:ops-port settings))))

(defn stop
  [component]
  (server/stop component))
