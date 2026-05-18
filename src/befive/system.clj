(ns befive.system
  (:require [befive.routes :as routes]
            [cheshire.core :as json]
            [com.brunobonacci.mulog :as u]
            [hato.client :as http]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [ring.adapter.jetty :as jetty])
  (:import (com.zaxxer.hikari HikariDataSource)
           (java.time Instant)
           (org.eclipse.jetty.server Server)))

(defmethod ig/init-key ::mulog [_ {:keys [publisher]}]
  (u/start-publisher! publisher))

(defmethod ig/halt-key! ::mulog [_ stop-fn]
  (stop-fn))

(defmethod ig/init-key ::db [_ {:keys [spec]}]
  (u/log ::db-starting :host (:host spec) :dbname (:dbname spec))
  (let [ds (connection/->pool HikariDataSource spec)]
    (jdbc/execute-one! ds ["select 1 as ok"])
    (u/log ::db-ready)
    ds))

(defmethod ig/halt-key! ::db [_ ^HikariDataSource ds]
  (.close ds))

(defmethod ig/init-key ::jwks [_ {:keys [url]}]
  (u/log ::jwks-fetching :url url)
  (let [resp (http/get url {:as :string :throw-exceptions? true})
        jwks (json/parse-string (:body resp) true)]
    (u/log ::jwks-ready :key-count (count (:keys jwks)))
    (atom {:fetched-at (Instant/now) :jwks jwks})))

(defmethod ig/halt-key! ::jwks [_ _] :ok)

(defmethod ig/init-key ::server [_ {:keys [port] :as deps}]
  (u/log ::server-starting :port port)
  (let [handler (routes/handler (dissoc deps :port))
        server  (jetty/run-jetty handler {:port port :join? false})]
    (u/log ::server-ready :port port)
    server))

(defmethod ig/halt-key! ::server [_ ^Server server]
  (.stop server))
