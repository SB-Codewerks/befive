(ns befive.gateway.errors
  "Minimal gateway error bodies. The reason stays on the access log."
  (:require [befive.core.json :as json]))

(set! *warn-on-reflection* true)

(def statuses
  {"bad_request" 400
   "unauthorized" 401
   "forbidden" 403
   "not_found" 404
   "method_not_allowed" 405
   "not_acceptable" 406
   "gone" 410
   "payload_too_large" 413
   "too_many_requests" 429
   "bad_gateway" 502
   "service_unavailable" 503
   "gateway_timeout" 504})

(defn response
  "JSON error. `request-id` is the gateway request id."
  ([error request-id reason]
   (response error request-id reason nil))
  ([error request-id reason headers]
   (let [status (get statuses error 500)]
     {:status status
      :headers (merge {"content-type" "application/json; charset=utf-8"
                       "cache-control" "no-store"}
                      headers)
      :body (json/write-str {:error error :request_id request-id})
      :error-code error
      :reason reason})))

(defn problem
  "problem+json for resource-state errors such as a retired version."
  [error request-id reason]
  (let [status (get statuses error 400)
        body {:type (str "/docs/errors/" error)
              :title error
              :status status
              :detail reason
              :request_id request-id}]
    {:status status
     :headers {"content-type" "application/problem+json; charset=utf-8"
               "cache-control" "no-store"}
     :body (json/write-str body)
     :error-code error
     :reason reason}))

(defn fail
  "Attach an error response to `ctx` and keep the reason for the log."
  ([ctx error reason]
   (fail ctx error reason nil))
  ([ctx error reason headers]
   (let [id (:request-id ctx)
         built (if (= error "gone")
                 (problem error id reason)
                 (response error id reason headers))]
     (assoc ctx
            :response (dissoc built :error-code :reason)
            :error-code (:error-code built)
            :reason reason))))
