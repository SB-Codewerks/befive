(ns befive.gateway.proxy
  "Stream an HTTP upstream through an Aleph pool, or splice a
  WebSocket. Retries run only for idempotent methods, and only
  while the retry budget allows it."
  (:require [aleph.http :as http]
            [befive.gateway.balance :as balance]
            [befive.gateway.block :as block]
            [befive.gateway.client-ip :as client-ip]
            [befive.gateway.errors :as errors]
            [befive.gateway.headers :as headers]
            [befive.gateway.lambda :as lambda]
            [befive.gateway.lambda.aws :as aws]
            [befive.gateway.limits :as limits]
            [befive.gateway.targets :as targets]
            [clojure.string :as str]
            [manifold.deferred :as d]
            [manifold.stream :as s])
  (:import (aleph.utils ConnectionTimeoutException PoolTimeoutException
                        ProxyConnectionTimeoutException
                        ReadTimeoutException RequestTimeoutException)
           (io.netty.channel ConnectTimeoutException)
           (java.io Closeable)
           (java.net ConnectException URI)
           (java.util.concurrent RejectedExecutionException)
           (javax.net.ssl SSLException)))

(set! *warn-on-reflection* true)

(defn- pool-spec
  [upstream]
  {:max (:max-connections upstream 64)
   :connect (:connect-timeout-ms upstream 3000)
   :idle (:idle-timeout-ms upstream 60000)})

(defn pool-for
  "One HTTP/1.1 pool per upstream. Settings changes open a new pool."
  [pools upstream]
  (let [spec (pool-spec upstream)
        id (:id upstream)]
    (locking pools
      (let [current (get @pools id)]
        (if (and current (= spec (:spec current)))
          (:pool current)
          (let [pool (http/connection-pool
                      {:connections-per-host (:max spec)
                       :total-connections (:max spec)
                       :max-queue-size (:max spec)
                       :connection-options
                       {:keep-alive? true
                        :idle-timeout (:idle spec)
                        :connect-timeout (:connect spec)
                        :http-versions [:http1]
                        :response-buffer-size 1}})]
            (swap! pools assoc id {:spec spec :pool pool})
            pool))))))

(defn- scheme-name
  [scheme]
  (cond
    (keyword? scheme) (name scheme)
    (string? scheme) scheme
    :else "http"))

(defn- host-header
  [^String url]
  (let [uri (URI. url)
        port (.getPort uri)]
    (if (neg? port)
      (.getHost uri)
      (str (.getHost uri) ":" port))))

(defn join-url
  [base path query]
  (let [base (str/replace (str base) #"/+$" "")
        path (if (str/blank? path) "/" path)
        path (if (str/starts-with? path "/") path (str "/" path))]
    (str base path (when (and query (not (str/blank? query)))
                     (str "?" query)))))

(defn forward-headers
  "Hop-by-hop and inbound identity headers are not forwarded."
  [ctx]
  (let [request (:request ctx)
        headers (-> (:headers request)
                    headers/strip-hop-by-hop
                    headers/strip-identity)
        client (:client-ip ctx)]
    (assoc headers
           "x-forwarded-for" (client-ip/forwarded-for
                              (:headers request)
                              client)
           "x-forwarded-proto" (scheme-name (:scheme request))
           "x-forwarded-host" (or (headers/header (:headers request) "host")
                                  ""))))

(defn- close-body!
  [body]
  (cond
    (instance? Closeable body) (.close ^Closeable body)
    (s/stream? body) (s/close! body)
    :else nil))

(defn- failure
  [^Throwable thrown]
  (cond
    (instance? PoolTimeoutException thrown)
    ["service_unavailable" "upstream.pool_exhausted"]

    (instance? RejectedExecutionException thrown)
    ["service_unavailable" "upstream.pool_exhausted"]

    (or (instance? ConnectionTimeoutException thrown)
        (instance? ConnectTimeoutException thrown)
        (instance? ProxyConnectionTimeoutException thrown))
    ["gateway_timeout" "upstream.connect_timeout"]

    (or (instance? RequestTimeoutException thrown)
        (instance? ReadTimeoutException thrown))
    ["gateway_timeout" "upstream.read_timeout"]

    (instance? SSLException thrown)
    ["bad_gateway" "upstream.tls_failed"]

    (instance? ConnectException thrown)
    ["bad_gateway" "upstream.connect_failed"]

    :else
    ["bad_gateway" "upstream.connect_failed"]))

(defn- retryable-reason?
  [reason]
  (contains? #{"upstream.connect_timeout"
               "upstream.connect_failed"
               "upstream.tls_failed"}
             reason))

(defn- send-once
  [pool request upstream target headers]
  (let [url (join-url (:url target)
                      (or (:uri request) (:path request))
                      (:query-string request))
        method (:request-method request)]
    (http/request
     {:request-method method
      :url url
      :headers (assoc headers "host" (host-header (:url target)))
      :body (when-not (contains? #{:get :head} method)
              (:body request))
      :pool pool
      :pool-timeout (:connect-timeout-ms upstream 3000)
      :connection-timeout (:connect-timeout-ms upstream 3000)
      :request-timeout (:read-timeout-ms upstream 30000)
      :follow-redirects false
      :throw-exceptions false})))

(defn- from-upstream
  [ctx response]
  (let [header-map (headers/strip-hop-by-hop (:headers response))
        limits (:limits ctx)]
    (if (limits/response-too-large? header-map limits)
      (do
        (close-body! (:body response))
        (errors/fail ctx "bad_gateway" "limits.response_too_large"))
      (assoc ctx
             :response {:status (:status response)
                        :headers header-map
                        :body (:body response)}
             :target (:target ctx)))))

(defn- release
  [state upstream-id target-id]
  (when (and state target-id)
    (targets/release! (:targets state) upstream-id target-id)))

(defn- http-dispatch
  [ctx]
  (let [state (:state ctx)
        upstream (:upstream ctx)
        budget (:budget ctx)
        method (:request-method (:request ctx))
        max-retries (:max-retries upstream 1)
        _ (when budget (balance/reserve-request! budget))]
    (letfn [(attempt [n]
              (let [chosen (targets/pick! (:targets state)
                                          (:id upstream)
                                          (balance/hash-key (:request ctx)))]
                (if-not chosen
                  (errors/fail ctx "service_unavailable"
                               "upstream.no_healthy_targets")
                  (d/catch
                   (d/chain
                    (send-once (pool-for (:pools state) upstream)
                               (:request ctx)
                               upstream
                               chosen
                               (or (:upstream-headers ctx)
                                   (forward-headers ctx)))
                    (fn [response]
                      (targets/note-success! (:targets state)
                                             (:id upstream)
                                             (:id chosen))
                      (release state (:id upstream) (:id chosen))
                      (from-upstream (assoc ctx :target (:url chosen))
                                     response)))
                   (fn [thrown]
                     (let [[error reason] (failure thrown)
                           after (:unhealthy-after (:health chosen) 2)]
                       (targets/note-failure! (:targets state)
                                              (:id upstream)
                                              (:id chosen)
                                              after)
                       (release state (:id upstream) (:id chosen))
                       (if (and (retryable-reason? reason)
                                (< n max-retries)
                                (balance/retryable-method? upstream method)
                                budget
                                (balance/reserve-retry! budget))
                         (d/chain (block/sleep (long (+ 25 (* 25 n))))
                                  (fn [_] (attempt (inc n))))
                         (errors/fail ctx error reason))))))))]
      (attempt 0))))

(defn websocket?
  [request]
  (and (= :get (:request-method request))
       (let [upgrade (headers/header (:headers request) "upgrade")]
         (and upgrade
              (= "websocket" (str/lower-case (str/trim (str upgrade))))))))

(defn- ws-url
  [target request]
  (let [base (str/replace (:url target) #"^http" "ws")]
    (join-url base (or (:uri request) "/") (:query-string request))))

(defn- websocket-dispatch
  [ctx]
  (let [state (:state ctx)
        upstream (:upstream ctx)
        chosen (targets/pick! (:targets state)
                              (:id upstream)
                              (balance/hash-key (:request ctx)))]
    (if-not chosen
      (errors/fail ctx "service_unavailable" "upstream.no_healthy_targets")
      (let [upgrade
            (d/catch
             (d/chain
              (http/websocket-connection (:request ctx))
              (fn [client]
                (d/catch
                 (d/chain
                  (http/websocket-client
                   (ws-url chosen (:request ctx))
                   {:headers (or (:upstream-headers ctx)
                                 (forward-headers ctx))})
                  (fn [remote]
                    (s/connect client remote)
                    (s/connect remote client)
                    (targets/release! (:targets state)
                                      (:id upstream)
                                      (:id chosen))
                    nil))
                 (fn [thrown]
                   (s/close! client)
                   (targets/note-failure! (:targets state)
                                          (:id upstream)
                                          (:id chosen)
                                          (:unhealthy-after (:health chosen) 2))
                   (targets/release! (:targets state)
                                     (:id upstream)
                                     (:id chosen))
                   (throw thrown)))))
             (fn [_] nil))]
        (assoc ctx
               :target (:url chosen)
               :upgrade upgrade
               :response {:status 101 :headers {} :body nil})))))

(defn dispatch
  "Proxy the matched upstream. Returns a context or a deferred of one."
  [ctx]
  (let [upstream (:upstream ctx)]
    (cond
      (nil? upstream)
      (errors/fail ctx "bad_gateway" "upstream.connect_failed")

      (websocket? (:request ctx))
      (websocket-dispatch ctx)

      (= :lambda (:kind upstream))
      (if-let [transport (or (:lambda-transport (:state ctx))
                             (when (:lambda-clients (:state ctx))
                               (aws/cached-transport
                                (:lambda-clients (:state ctx))
                                (:lambda upstream))))]
        (lambda/invoke (assoc ctx
                              :upstream (assoc upstream
                                               :limiter (:limiter ctx)))
                       transport
                       (:budget ctx))
        (errors/fail ctx "bad_gateway" "lambda.invoke_failed"))

      :else
      (http-dispatch ctx))))
