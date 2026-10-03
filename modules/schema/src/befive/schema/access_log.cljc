(ns befive.schema.access-log
  "Field catalog for the befive.access/1 record.
  A record may omit fields. It may not invent them."
  (:require [clojure.spec.alpha :as s]))

(def schema-name "befive.access/1")

(def fields
  #{:request_id :method :scheme :host :path :query :status
    :route_id :upstream_id :upstream_kind :target
    :error_code :error :auth_reason :authz_reason
    :duration_ms :bytes_in :bytes_out :client_ip
    :stream :stream_duration_ms :ttfb_ms :limit_rejected
    :lambda_request_id :lambda_function_error
    :node_id :revision})

(defn known?
  "True when every key of `record` is in the catalog."
  [record]
  (and (map? record)
       (every? fields (keys record))))

(s/def ::record known?)
