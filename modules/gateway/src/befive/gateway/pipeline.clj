(ns befive.gateway.pipeline
  "The data-plane phase order. Slots that belong to later milestones
  are present and do not enforce anything yet."
  (:require [befive.gateway.access-log :as access-log]
            [befive.gateway.client-ip :as client-ip]
            [befive.gateway.errors :as errors]
            [befive.gateway.executor :as executor]
            [befive.gateway.headers :as headers]
            [befive.gateway.limits :as limits]
            [befive.gateway.net :as net]
            [befive.gateway.proxy :as proxy]
            [befive.gateway.router :as router]
            [befive.gateway.targets :as targets]
            [clojure.string :as str]
            [manifold.deferred :as d])
  (:import (java.util UUID)
           (java.util.concurrent ThreadLocalRandom)))

(set! *warn-on-reflection* true)

(def phase-names
  [:befive/access-log
   :befive/request-id
   :befive/client-ip
   :befive/error-mapper
   :befive/strip-inbound
   :befive/ip-filter
   :befive/pre-auth-limit
   :befive/cors
   :befive/limits
   :befive/lifecycle
   :befive/pre-auth-plugins
   :befive/authn
   :befive/authz
   :befive/rate-limit
   :befive/post-auth-plugins
   :befive/validate
   :befive/cache
   :befive/request-transform
   :befive/pre-proxy-plugins
   :befive/response-plugins
   :befive/response-transform
   :befive/terminal])

(def ^:private uuid-re
  #"(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

(defn uuid7
  "A UUIDv7. The timestamp is millisecond Unix time."
  []
  (let [^ThreadLocalRandom random (ThreadLocalRandom/current)
        millis (System/currentTimeMillis)
        msb (bit-or (bit-shift-left (bit-and millis 0xffffffffffff) 16)
                    0x7000
                    (bit-and (.nextLong random) 0x0fff))
        lsb (bit-or (unchecked-long 0x8000000000000000)
                    (bit-and (.nextLong random) 0x3fffffffffffffff))]
    (UUID. msb lsb)))

(defn- slot
  [phase-name]
  {:name phase-name :enter (fn [ctx] ctx)})

(defn- traced
  [phase]
  (let [enter (:enter phase)]
    (assoc phase
           :enter
           (fn [ctx]
             (when-let [trace (:trace ctx)]
               (swap! trace conj (:name phase)))
             (if enter
               (enter ctx)
               ctx)))))

(defn- request-id
  [ctx]
  (let [request (:request ctx)
        supplied (headers/header (:headers request) "x-request-id")
        trusted? (net/trusted? (:trusted ctx) (:remote-addr request))]
    (assoc ctx
           :request-id
           (if (and trusted? supplied (re-matches uuid-re (str supplied)))
             (str supplied)
             (str (uuid7))))))

(defn- client-ip
  [ctx]
  (let [request (:request ctx)]
    (assoc ctx
           :client-ip
           (client-ip/client-ip (:headers request)
                                (:remote-addr request)
                                (:trusted ctx)))))

(defn- strip
  [ctx]
  (update-in ctx [:request :headers] headers/strip-identity))

(defn- on-error
  [ctx]
  (let [thrown (:error ctx)
        data (ex-data thrown)]
    (if (and (map? data) (:error data) (:reason data))
      (errors/fail ctx (:error data) (:reason data))
      (errors/fail ctx "bad_gateway" "upstream.protocol_error"))))

(defn- limits-enter
  [ctx]
  (let [request (:request ctx)
        table (:table ctx)
        path (or (:uri request) (:path request) "/")
        matched (router/match (:routers table)
                              {:host (headers/header (:headers request)
                                                     "host")
                               :method (:request-method request)
                               :path path})
        route (when (= :matched (:status matched))
                (:route matched))
        limits (limits/effective table route)
        problem (limits/request-problem (:headers request) limits)
        ctx (assoc ctx
                   :match matched
                   :route route
                   :path-params (if route
                                  (or (:path-params matched) {})
                                  {})
                   :limits limits
                   :bytes-in (limits/content-length (:headers request)))]
    (if problem
      (assoc (errors/fail ctx (first problem) (second problem))
             :limit-rejected true)
      ctx)))

(defn- allow-header
  [verbs]
  (str/join ", " (map #(str/upper-case (name %)) (sort verbs))))

(defn- lifecycle-enter
  [ctx]
  (if (:response ctx)
    ctx
    (let [matched (:match ctx)
          route (:route ctx)]
      (cond
        (= :not-found (:status matched))
        (errors/fail ctx "not_found" "route.not_found")

        (= :method-not-allowed (:status matched))
        (errors/fail ctx "method_not_allowed" "route.method_not_allowed"
                     {"allow" (allow-header (:allow matched))})

        (= :retired (:lifecycle route))
        (errors/fail ctx "gone" "version.retired")

        :else ctx))))

(defn- lifecycle-leave
  [ctx]
  (let [route (:route ctx)
        response (:response ctx)]
    (if (and response (= :deprecated (:lifecycle route)))
      (update-in ctx [:response :headers]
                 (fn [current]
                   (merge (or current {})
                          (cond-> {}
                            (:deprecation route)
                            (assoc "deprecation" (:deprecation route))
                            (:sunset route)
                            (assoc "sunset" (:sunset route))
                            (:link route)
                            (assoc "link" (:link route))))))
      ctx)))

(defn- request-transform
  [ctx]
  (if (:response ctx)
    ctx
    (assoc ctx :upstream-headers (proxy/forward-headers ctx))))

(defn- response-transform
  [ctx]
  (if (:headers (:response ctx))
    (update-in ctx [:response :headers] headers/strip-hop-by-hop)
    ctx))

(defn- terminal
  [ctx]
  (if (:response ctx)
    ctx
    (let [route (:route ctx)
          upstream (get-in ctx [:table :upstreams (:upstream route)])
          entry (targets/entry (:targets (:state ctx)) (:id upstream))]
      (proxy/dispatch (assoc ctx
                             :upstream upstream
                             :budget (:budget entry)
                             :limiter (:limiter entry))))))

(defn- log-leave
  [ctx]
  (let [elapsed (quot (- (System/nanoTime) (or (:started ctx) 0))
                      1000000)
        entry (access-log/record (assoc ctx :duration-ms elapsed))]
    (when-let [log (:access-log (:state ctx))]
      (access-log/offer! log entry))
    (assoc ctx :logged true :duration-ms elapsed)))

(defn phases
  "Interceptors in documented order."
  [_state]
  (mapv traced
        [{:name :befive/access-log :leave log-leave}
         {:name :befive/request-id :enter request-id}
         {:name :befive/client-ip :enter client-ip}
         {:name :befive/error-mapper :error on-error}
         {:name :befive/strip-inbound :enter strip}
         (slot :befive/ip-filter)
         (slot :befive/pre-auth-limit)
         (slot :befive/cors)
         {:name :befive/limits :enter limits-enter}
         {:name :befive/lifecycle
          :enter lifecycle-enter
          :leave lifecycle-leave}
         (slot :befive/pre-auth-plugins)
         (slot :befive/authn)
         (slot :befive/authz)
         (slot :befive/rate-limit)
         (slot :befive/post-auth-plugins)
         (slot :befive/validate)
         (slot :befive/cache)
         {:name :befive/request-transform :enter request-transform}
         (slot :befive/pre-proxy-plugins)
         (slot :befive/response-plugins)
         {:name :befive/response-transform :leave response-transform}
         {:name :befive/terminal :enter terminal}]))

(defn- publish-targets!
  [state table]
  (let [revision (:ready-revision state)]
    (when (not= @revision (:revision table))
      (locking revision
        (when (not= @revision (:revision table))
          (targets/reconcile! (:targets state) (:upstreams table))
          (reset! revision (:revision table)))))))

(defn- trusted
  [state table]
  (or (seq (:trusted-proxies table))
      (seq (:trusted-proxies (:settings state)))
      []))

(defn- initial
  [state table request trace]
  {:request request
   :table table
   :state state
   :started (System/nanoTime)
   :node-id (:node-id (:settings state))
   :environment (:environment (:settings state))
   :revision (:revision table)
   :trusted (trusted state table)
   :trace trace})

(defn handle
  "Run the pipeline. Returns a deferred of a Ring response, or of the
  WebSocket upgrade deferred."
  ([state request]
   (handle state request nil))
  ([state request trace]
   (let [table @(:table state)]
     (publish-targets! state table)
     (let [ctx (initial state table request trace)]
       (d/chain
        (d/catch (executor/execute (phases state) ctx)
                 (fn [_]
                   (errors/fail ctx "bad_gateway"
                                "upstream.protocol_error")))
        (fn [result]
          (when-not (:logged result)
            (when-let [log (:access-log state)]
              (access-log/offer! log (access-log/record result))))
          (or (:upgrade result)
              (:response result)
              (:response (errors/fail result "bad_gateway"
                                      "upstream.protocol_error")))))))))
