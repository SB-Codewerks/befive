(ns befive.app.system
  "Integrant system for the gateway, the control plane, or both."
  (:require [befive.app.ops :as ops]
            [befive.core.db :as db]
            [befive.core.health :as health]
            [befive.cp.http :as admin]
            [befive.cp.migrate :as migrate]
            [befive.gateway.http :as gateway]
            [integrant.core :as ig]))

(defmethod ig/init-key :befive/settings
  [_ settings]
  settings)

(defmethod ig/halt-key! :befive/settings
  [_ _]
  nil)

(defmethod ig/init-key :befive/health
  [_ _]
  (health/new-health))

(defmethod ig/halt-key! :befive/health
  [_ _]
  nil)

(defmethod ig/init-key :befive/db
  [_ {:keys [settings]}]
  (db/start settings))

(defmethod ig/halt-key! :befive/db
  [_ component]
  (db/stop component))

(defmethod ig/init-key :befive/migrations
  [_ {:keys [db settings health]}]
  (if (:migrate-on-start settings)
    (let [result (migrate/run-migrations! db)]
      (health/mark! health :migrations-done? true)
      result)
    (do
      (health/mark! health :migrations-done? true)
      {:applied 0 :skipped true})))

(defmethod ig/halt-key! :befive/migrations
  [_ _]
  nil)

(defmethod ig/init-key :befive/gateway-http
  [_ {:keys [settings]}]
  (gateway/start settings))

(defmethod ig/halt-key! :befive/gateway-http
  [_ component]
  (gateway/stop component))

(defmethod ig/init-key :befive/admin-http
  [_ {:keys [settings]}]
  (admin/start settings))

(defmethod ig/halt-key! :befive/admin-http
  [_ component]
  (admin/stop component))

(defmethod ig/init-key :befive/ops
  [_ component]
  (ops/start component))

(defmethod ig/halt-key! :befive/ops
  [_ component]
  (ops/stop component))

(defn config
  "Integrant config for `settings`. Gateway and control-plane keys
  are present only for the selected role."
  [settings]
  (let [role (:role settings)
        gateway? (contains? #{:gateway :all} role)
        control-plane? (contains? #{:control-plane :all} role)
        ops (cond-> {:settings (ig/ref :befive/settings)
                     :db (ig/ref :befive/db)
                     :health (ig/ref :befive/health)}
              gateway? (assoc :gateway (ig/ref :befive/gateway-http))
              control-plane? (assoc :admin (ig/ref :befive/admin-http)
                                    :migrations (ig/ref :befive/migrations)))]
    (cond-> {:befive/settings settings
             :befive/health {}
             :befive/db {:settings (ig/ref :befive/settings)}
             :befive/ops ops}
      control-plane? (assoc :befive/migrations
                            {:db (ig/ref :befive/db)
                             :settings (ig/ref :befive/settings)
                             :health (ig/ref :befive/health)}
                            :befive/admin-http
                            {:settings (ig/ref :befive/settings)})
      gateway? (assoc :befive/gateway-http
                      {:settings (ig/ref :befive/settings)}))))
