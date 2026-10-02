(ns befive.core.response
  "Small JSON responses. Gateway errors stay minimal."
  (:require [befive.core.json :as json]))

(set! *warn-on-reflection* true)

(defn request-id
  []
  (str (random-uuid)))

(defn json
  [status body]
  {:status status
   :headers {"content-type" "application/json; charset=utf-8"
             "cache-control" "no-store"}
   :body (json/write-str body)})

(defn not-found
  []
  (json 404 {:error "not-found"
             :request-id (request-id)}))
