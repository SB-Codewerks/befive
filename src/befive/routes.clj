(ns befive.routes
  (:require [reitit.ring :as ring]))

(defn- healthcheck [_req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "OK"})

(def routes
  [["/healthcheck" {:get healthcheck}]])

(defn handler
  "Build the top-level Ring handler. `deps` is the map of system
  dependencies (datasource, JWKS atom, etc.) that interceptors will
  close over. None are needed yet."
  [_deps]
  (ring/ring-handler
   (ring/router routes)
   (ring/create-default-handler)))
