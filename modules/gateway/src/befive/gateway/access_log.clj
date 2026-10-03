(ns befive.gateway.access-log
  "Bounded access-log queue. Normal records are dropped when it is
  full. Summary records wait on a virtual thread instead of being
  dropped, and that wait is never on the Netty event loop."
  (:require [befive.core.json :as json]
            [befive.gateway.block :as block]
            [befive.gateway.headers :as headers]
            [befive.gateway.limits :as limits]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [manifold.deferred :as d]
            [manifold.stream :as s])
  (:import (java.io InputStream)
           (java.util.concurrent ArrayBlockingQueue TimeUnit)))

(set! *warn-on-reflection* true)

(defn- stream?
  [body]
  (or (instance? InputStream body)
      (s/stream? body)))

(defn- scheme-name
  [scheme]
  (cond
    (keyword? scheme) (name scheme)
    (string? scheme) scheme
    :else nil))

(defn record
  "One befive.access/1 map. Nil fields are omitted."
  [ctx]
  (let [request (:request ctx)
        response (:response ctx)
        route (:route ctx)
        upstream (:upstream ctx)
        body (:body response)
        values {:request_id (:request-id ctx)
                :method (some-> (:request-method request) name
                                str/upper-case)
                :scheme (scheme-name (:scheme request))
                :host (or (headers/header (:headers request) "host")
                          (:server-name request))
                :path (or (:uri request) (:path request))
                :query (:query-string request)
                :status (:status response)
                :route_id (:id route)
                :upstream_id (:id upstream)
                :upstream_kind (some-> (:kind upstream) name)
                :target (:target ctx)
                :error_code (:error-code ctx)
                :error (:reason ctx)
                :duration_ms (:duration-ms ctx)
                :bytes_in (:bytes-in ctx)
                :bytes_out (or (:bytes-out ctx)
                               (limits/content-length
                                (:headers response)))
                :client_ip (:client-ip ctx)
                :stream (stream? body)
                :ttfb_ms (:duration-ms ctx)
                :limit_rejected (boolean (:limit-rejected ctx))
                :lambda_request_id (:lambda-request-id ctx)
                :lambda_function_error (:lambda-function-error ctx)
                :node_id (:node-id ctx)
                :revision (:revision ctx)}]
    (into {} (remove (fn [[_ value]] (nil? value))) values)))

(defn- default-sink
  [entry]
  (log/info (json/write-str entry)))

(defn offer!
  "Queue `entry`. Summary lines are never dropped."
  ([log entry]
   (offer! log entry false))
  ([{:keys [^ArrayBlockingQueue queue
            ^ArrayBlockingQueue summaries
            dropped]}
    entry
    summary?]
   (let [target (if summary? summaries queue)]
     (if (.offer target entry)
       true
       (if summary?
         (do
           (d/future-with block/executor
             (.put target entry))
           true)
         (do
           (swap! dropped inc)
           false))))))

(defn start
  "Start the consumer. `sink` is called with each record."
  ([]
   (start {}))
  ([{:keys [sink capacity summary-capacity]
     :or {sink default-sink
          capacity 1024
          summary-capacity 10000}}]
   (let [^ArrayBlockingQueue queue (ArrayBlockingQueue. (int capacity))
         ^ArrayBlockingQueue summaries
         (ArrayBlockingQueue. (int summary-capacity))
         dropped (atom 0)
         stop? (atom false)
         ^Thread thread (-> (Thread/ofVirtual)
                            (.name "befive-access-log")
                            (.start
                             (fn []
                               (while (not @stop?)
                                 (try
                                   (let [entry (or (.poll summaries 0
                                                         TimeUnit/MILLISECONDS)
                                                   (.poll queue 200
                                                          TimeUnit/MILLISECONDS))]
                                     (when entry
                                       (sink entry)))
                                   (catch InterruptedException _
                                     (reset! stop? true))
                                   (catch Throwable t
                                     (log/error t "access log sink failed")))))))]
     {:queue queue
      :summaries summaries
      :dropped dropped
      :stop! (fn []
               (reset! stop? true)
               (.interrupt thread))})))

(defn stop!
  [log]
  (when-let [stop (:stop! log)]
    (stop)))
