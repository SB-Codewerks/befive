(ns befive.main
  "Process entry. No arguments starts the node. `migrate` applies
  SQL and exits. `healthcheck` probes the ops port and exits."
  (:gen-class)
  (:require [befive.app.system :as system]
            [befive.core.db :as db]
            [befive.core.healthcheck :as healthcheck]
            [befive.core.logging :as logging]
            [befive.core.settings :as settings]
            [befive.cp.migrate :as migrate]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defn- exit!
  [code message]
  (when (seq message)
    (binding [*out* *err*]
      (println message)))
  (System/exit code))

(defn- block-until-shutdown!
  [system]
  (let [done (promise)]
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread.
      (reify Runnable
        (run [_]
          (try
            (ig/halt! system)
            (finally
              (deliver done true)))))))
    @done))

(defn start!
  []
  (let [prepared (settings/prepare)]
    (if-let [code (:exit prepared)]
      (exit! code (:message prepared))
      (let [running (ig/init (system/config (:settings prepared)))]
        (logging/log-start (:settings prepared))
        (log/info "listeners bound")
        (block-until-shutdown! running)))))

(defn migrate!
  []
  (let [prepared (settings/prepare)]
    (if-let [code (:exit prepared)]
      (exit! code (:message prepared))
      (let [database (db/start (:settings prepared) {:required true})]
        (try
          (let [result (migrate/run-migrations! database)]
            (println (str "applied " (:applied result) " migration(s)")))
          (finally
            (db/stop database)))
        (System/exit 0)))))

(defn healthcheck!
  [path]
  (let [port (or (parse-long (or (System/getenv "BEFIVE_OPS_PORT") ""))
                 9901)]
    (System/exit (healthcheck/exit-code port path))))

(defn -main
  [& args]
  (try
    (case (first args)
      nil (start!)
      "migrate" (migrate!)
      "healthcheck" (healthcheck! (or (second args) "/readyz"))
      (exit! 2 "usage: befive [migrate | healthcheck [path]]"))
    (catch Exception e
      (binding [*out* *err*]
        (println (str "startup failed: " (.getMessage e))))
      (System/exit 1))))
