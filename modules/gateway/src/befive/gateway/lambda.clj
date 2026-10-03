(ns befive.gateway.lambda
  "API Gateway payload format 2.0 mapping, the concurrency limiter,
  and the Lambda error table. The AWS client lives in
  `befive.gateway.lambda.aws`."
  (:require [befive.core.json :as json]
            [befive.gateway.balance :as balance]
            [befive.gateway.block :as block]
            [befive.gateway.errors :as errors]
            [befive.gateway.headers :as headers]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [manifold.deferred :as d])
  (:import (java.nio.charset CharsetDecoder CodingErrorAction
                             StandardCharsets)
           (java.nio ByteBuffer)
           (java.util Base64)
           (java.util.concurrent ArrayBlockingQueue Semaphore)))

(set! *warn-on-reflection* true)

(def max-event-bytes
  "Lambda synchronous invoke limit."
  (* 6 1024 1024))

(deftype Limiter [^Semaphore sem
                  ^ArrayBlockingQueue queue
                  ^long max-pending
                  ^Object lock])

(defn limiter
  "A fair semaphore plus a waiter queue. The lock is the Limiter field."
  [max-concurrency max-pending]
  (->Limiter (Semaphore. (int max-concurrency) true)
             (ArrayBlockingQueue. (int (max 1 max-pending)))
             (long max-pending)
             (Object.)))

(defn acquire!
  "A deferred that succeeds when a permit is held."
  [^Limiter gate]
  (let [^Semaphore sem (.-sem gate)]
    ;; The monitor is a field of the shared Limiter, not a fresh object.
    #_{:clj-kondo/ignore [:locking-suspicious-lock]}
    (locking (.-lock gate)
      (if (.tryAcquire sem)
        (d/success-deferred true nil)
        (if (zero? (.-max-pending gate))
          (d/error-deferred
           (ex-info "lambda concurrency limited"
                    {:error "service_unavailable"
                     :reason "lambda.concurrency_limited"}))
          (let [slot (d/deferred)]
            (if (.offer ^ArrayBlockingQueue (.-queue gate) slot)
              slot
              (d/error-deferred
               (ex-info "lambda concurrency limited"
                        {:error "service_unavailable"
                         :reason "lambda.concurrency_limited"})))))))))

(defn release!
  "Return the permit, or hand it to the oldest waiter."
  [^Limiter gate]
  (let [waiter #_{:clj-kondo/ignore [:locking-suspicious-lock]}
        (locking (.-lock gate)
                 (if-let [waiting (.poll ^ArrayBlockingQueue
                                         (.-queue gate))]
                   waiting
                   (do
                     (.release ^Semaphore (.-sem gate))
                     nil)))]
    (when waiter
      (d/success! waiter true))
    true))

(defn- finally-d
  [deferred f]
  (d/chain
   (d/catch deferred
            (fn [thrown]
              (f)
              (d/error-deferred thrown)))
   (fn [value]
     (f)
     value)))

(defn route-key
  [method template]
  (str (str/upper-case (name method))
       " "
       (str/replace template #":([^/]+)" "{$1}")))

(defn- joined-headers
  [headers]
  (reduce (fn [out [header value]]
            (let [lowered (headers/header-name header)
                  text (if (sequential? value)
                         (str/join "," (map str value))
                         (str value))]
              (update out lowered (fn [current]
                                (if current
                                  (str current "," text)
                                  text)))))
          {}
          headers))

(defn- cookie-list
  [headers]
  (when-let [cookie (get (joined-headers headers) "cookie")]
    (let [cookies (->> (str/split cookie #";")
                       (map str/trim)
                       (remove str/blank?)
                       vec)]
      (when (seq cookies)
        cookies))))

(defn query-parameters
  [query]
  (when-not (str/blank? query)
    (into {}
          (keep (fn [pair]
                  (when-not (str/blank? pair)
                    (let [[^String k ^String v] (str/split pair #"=" 2)]
                      [(java.net.URLDecoder/decode k "UTF-8")
                       (java.net.URLDecoder/decode (or v "") "UTF-8")]))))
          (str/split query #"&"))))

(defn utf8?
  [^bytes data]
  (try
    (let [^CharsetDecoder decoder
          (doto (.newDecoder StandardCharsets/UTF_8)
            (.onMalformedInput CodingErrorAction/REPORT)
            (.onUnmappableCharacter CodingErrorAction/REPORT))]
      (.decode decoder (ByteBuffer/wrap data))
      true)
    (catch java.nio.charset.CharacterCodingException _
      false)))

(defn- media-name
  [content-type]
  (some-> content-type str/lower-case (str/replace #";.*$" "") str/trim))

(defn binary-media?
  [content-type patterns]
  (let [media (media-name content-type)]
    (boolean
     (and media
          (some (fn [pattern]
                  (let [pattern (str/lower-case pattern)]
                    (if (str/ends-with? pattern "/*")
                      (str/starts-with? media
                                        (subs pattern 0
                                              (dec (count pattern))))
                      (= media pattern))))
                patterns)))))

(defn- encode-body
  [^bytes data content-type patterns]
  (if (zero? (alength data))
    {:body nil :encoded false}
    (if (or (binary-media? content-type patterns)
            (not (utf8? data)))
      {:body (.encodeToString (Base64/getEncoder) data)
       :encoded true}
      {:body (String. data StandardCharsets/UTF_8)
       :encoded false})))

(defn event
  "Build a payload-format 2.0 event from the gateway context."
  [ctx ^bytes data]
  (let [request (:request ctx)
        route (:route ctx)
        headers (joined-headers
                 (headers/strip-identity (:headers request)))
        method (:request-method request)
        path (or (:uri request) "/")
        query (or (:query-string request) "")
        encoded (encode-body data
                              (get headers "content-type")
                              (:binary-media-types (:lambda (:upstream ctx))
                                                   #{}))
        host (or (get headers "host") "")
        principal (:identity ctx)
        context {:accountId ""
                 :apiId "befive"
                 :domainName (str/replace host #":\d+$" "")
                 :http {:method (str/upper-case (name method))
                        :path path
                        :protocol "HTTP/1.1"
                        :sourceIp (:client-ip ctx)
                        :userAgent (get headers "user-agent")}
                 :requestId (:request-id ctx)
                 :routeKey (route-key method (:path route path))
                 :stage (:environment ctx "dev")
                 :timeEpoch (System/currentTimeMillis)}
        built {:version "2.0"
               :routeKey (:routeKey context)
               :rawPath path
               :rawQueryString query
               :headers headers
               :queryStringParameters (query-parameters query)
               :pathParameters (or (:path-params ctx) {})
               :cookies (cookie-list (:headers request))
               :isBase64Encoded (:encoded encoded)
               :body (:body encoded)
               :requestContext context}]
    (cond-> built
      principal (assoc :requestContext
                       (assoc context
                              :authorizer
                              {:jwt {:claims (or (:claims principal) {})
                                     :scopes (or (:scopes principal) [])}
                               :befive principal})))))

(defn- omit-nils
  [value]
  (cond
    (map? value) (into {}
                       (keep (fn [[k v]]
                               (when (some? v)
                                 [k (omit-nils v)])))
                       value)
    (sequential? value) (mapv omit-nils value)
    :else value))

(defn event-bytes
  [ctx data]
  (let [^String text (json/write-str (omit-nils (event ctx data)))]
    (.getBytes text StandardCharsets/UTF_8)))

(defn- header-out
  [headers]
  (reduce-kv (fn [out header value]
               (assoc out (headers/header-name header)
                      (if (sequential? value)
                        (vec value)
                        (str value))))
             {}
             headers))

(defn- decode-function-body
  [parsed]
  (let [body (:body parsed)]
    (cond
      (nil? body) nil
      (:isBase64Encoded parsed)
      (.decode (Base64/getDecoder) (str body))
      :else (str body))))

(defn interpret-payload
  "Map a function payload to a Ring response, or an error tag."
  [^bytes payload function-error request-id]
  (cond
    function-error
    {:error "bad_gateway"
     :reason "lambda.function_error"
     :lambda-function-error (str function-error)
     :lambda-request-id request-id}

    (> (alength payload) max-event-bytes)
    {:error "bad_gateway"
     :reason "lambda.bad_response"
     :lambda-request-id request-id}

    :else
    (let [text (String. payload StandardCharsets/UTF_8)
          parsed (try
                   (json/read-str text)
                   (catch Exception _
                     ::invalid))]
      (cond
        (= parsed ::invalid)
        {:error "bad_gateway"
         :reason "lambda.bad_response"
         :lambda-request-id request-id}

        (and (map? parsed) (contains? parsed :statusCode))
        (let [headers (header-out (or (:headers parsed) {}))
              cookies (:cookies parsed)
              headers (if (seq cookies)
                        (update headers "set-cookie"
                                (fn [current]
                                  (into (if (sequential? current)
                                          (vec current)
                                          (if current [current] []))
                                        (map str cookies))))
                        headers)]
          {:status (int (:statusCode parsed))
           :headers headers
           :body (decode-function-body parsed)
           :lambda-request-id request-id})

        :else
        {:status 200
         :headers {"content-type" "application/json; charset=utf-8"}
         :body text
         :lambda-request-id request-id}))))

(defn read-body
  "Buffer a Lambda body up to `max-bytes`. Larger bodies error."
  [body max-bytes]
  (block/off-loop
   (fn []
     (cond
       (nil? body) (byte-array 0)
       (bytes? body)
       (if (> (alength ^bytes body) max-bytes)
         (throw (ex-info "lambda payload too large"
                         {:error "payload_too_large"
                          :reason "lambda.payload_too_large"}))
         body)
       (string? body)
       (let [data (.getBytes ^String body "UTF-8")]
         (if (> (alength data) max-bytes)
           (throw (ex-info "lambda payload too large"
                           {:error "payload_too_large"
                            :reason "lambda.payload_too_large"}))
           data))
       :else
       (with-open [input (io/input-stream body)
                   output (java.io.ByteArrayOutputStream.)]
         (let [buf (byte-array 8192)]
           (loop [total 0]
             (let [n (.read input buf)]
               (if (neg? n)
                 (.toByteArray output)
                 (let [total' (+ total n)]
                   (if (> total' max-bytes)
                     (throw (ex-info "lambda payload too large"
                                     {:error "payload_too_large"
                                      :reason "lambda.payload_too_large"}))
                     (do
                       (.write output buf 0 n)
                       (recur total')))))))))))))

(defn- fail
  [ctx error reason extra]
  (merge (errors/fail ctx error reason
                      (when (= reason "lambda.throttled")
                        {"retry-after" "1"}))
         extra))

(defn- response-from
  [ctx interpreted]
  (if (:error interpreted)
    (fail ctx (:error interpreted) (:reason interpreted)
          (select-keys interpreted [:lambda-request-id
                                    :lambda-function-error]))
    (assoc ctx
           :response (dissoc interpreted :lambda-request-id)
           :lambda-request-id (:lambda-request-id interpreted))))

(defn- retryable-result?
  [result]
  (true? (:retryable result)))

(defn invoke
  "Call `transport` with the 2.0 event. `transport` returns a deferred
  of `{:payload :function-error :request-id :reason}`."
  [ctx transport budget]
  (let [upstream (:upstream ctx)
        config (:lambda upstream)
        max-retries (:max-retries upstream 1)
        method (:request-method (:request ctx))
        _ (when budget
            (balance/reserve-request! budget))]
    (d/catch
     (d/chain
      (read-body (:body (:request ctx)) max-event-bytes)
      (fn [data]
        (let [^bytes payload (event-bytes ctx data)]
          (if (> (alength payload) max-event-bytes)
            (fail ctx "payload_too_large" "lambda.payload_too_large" {})
            (let [call {:function (:function config)
                        :qualifier (:qualifier config)
                        :payload payload
                        :timeout-ms (:timeout-ms config 29000)}
                  gate (:limiter upstream)]
              (letfn [(once []
                        (if gate
                          (d/chain
                           (acquire! gate)
                           (fn [_]
                             (finally-d (transport call)
                                        #(release! gate))))
                          (transport call)))
                      (attempt [n]
                        (d/catch
                         (d/chain
                          (once)
                          (fn [result]
                            (if (:reason result)
                              (if (and (retryable-result? result)
                                       (< n max-retries)
                                       (balance/retryable-method?
                                        upstream method)
                                       budget
                                       (balance/reserve-retry! budget))
                                (d/chain
                                 (block/sleep (long (* 25 (Math/pow 2 n))))
                                 (fn [_] (attempt (inc n))))
                                (fail ctx
                                      (case (:reason result)
                                        "lambda.throttled"
                                        "service_unavailable"
                                        "lambda.timeout"
                                        "gateway_timeout"
                                        "bad_gateway")
                                      (:reason result)
                                      result))
                              (response-from
                               ctx
                               (interpret-payload
                                (:payload result)
                                (:function-error result)
                                (:request-id result))))))
                         (fn [thrown]
                           (let [data (ex-data thrown)]
                             (if (:error data)
                               (fail ctx (:error data) (:reason data) {})
                               (fail ctx "bad_gateway"
                                     "lambda.invoke_failed" {}))))))]
                (attempt 0)))))))
     (fn [thrown]
       (let [data (ex-data thrown)]
         (if (:error data)
           (fail ctx (:error data) (:reason data) {})
           (fail ctx "bad_gateway" "lambda.invoke_failed" {})))))))
