(ns befive.gateway.lambda-test
  (:require [befive.core.json :as json]
            [befive.gateway.balance :as balance]
            [befive.gateway.lambda :as lambda]
            [clojure.test :refer [deftest is]]
            [manifold.deferred :as d])
  (:import (java.nio.charset StandardCharsets)))

(defn- bytes-of
  [^String text]
  (.getBytes text StandardCharsets/UTF_8))

(defn- ok-payload
  []
  (bytes-of (json/write-str {:statusCode 201
                             :headers {:content-type "text/plain"}
                             :body "created"})))

(defn- ctx
  [upstream method]
  {:request {:request-method method
             :uri "/orders/1"
             :query-string "q=a%20b"
             :scheme :http
             :headers {"host" "api.example.com"
                       "content-type" "application/json"
                       "cookie" "a=1; b=2"
                       "x-befive-subject" "forged"}
             :body "{\"n\":1}"}
   :request-id "req-1"
   :client-ip "203.0.113.9"
   :route {:id "orders" :path "/orders/:id" :methods #{method}}
   :path-params {:id "1"}
   :upstream upstream})

(defn- upstream
  [& {:keys [retries limiter retry-methods]
      :or {retries 1}}]
  (cond-> {:id "fn"
           :kind :lambda
           :max-retries retries
           :lambda {:function "echo"
                    :region "us-east-1"
                    :timeout-ms 1000}}
    limiter (assoc :limiter limiter)
    retry-methods (assoc :retry-methods retry-methods)))

(defn- await-ctx
  [deferred]
  (let [value (deref deferred 2000 ::timeout)]
    (is (not= ::timeout value))
    value))

(defn- capturing
  [calls result]
  (fn [call]
    (swap! calls conj call)
    (if (fn? result)
      (result call)
      (d/success-deferred result))))

(deftest event-uses-payload-format-2
  (let [calls (atom [])
        transport (capturing calls {:payload (ok-payload) :request-id "aws-1"})
        result (await-ctx (lambda/invoke (ctx (upstream) :post) transport nil))
        event (json/read-str (String. ^bytes (:payload (first @calls))
                                      StandardCharsets/UTF_8))]
    (is (= 201 (get-in result [:response :status])))
    (is (= "aws-1" (:lambda-request-id result)))
    (is (= "2.0" (:version event)))
    (is (= "POST /orders/{id}" (:routeKey event)))
    (is (= "/orders/1" (:rawPath event)))
    (is (= {:q "a b"} (:queryStringParameters event)))
    (is (= ["a=1" "b=2"] (:cookies event)))
    (is (nil? (get-in event [:headers :x-befive-subject])))))

(deftest binary-bodies-are-base64
  (let [calls (atom [])
        request (assoc-in (ctx (upstream) :post)
                          [:request :headers "content-type"]
                          "image/png")
        request (assoc-in request [:request :body] (byte-array [(byte 0) (byte 1)]))
        request (assoc-in request [:upstream :lambda :binary-media-types]
                          #{"image/*"})
        transport (capturing calls {:payload (ok-payload) :request-id "b"})]
    (await-ctx (lambda/invoke request transport nil))
    (let [event (json/read-str (String. ^bytes (:payload (first @calls))
                                        StandardCharsets/UTF_8))]
      (is (true? (:isBase64Encoded event)))
      (is (string? (:body event))))))

(deftest function-error-is-a-bad-gateway-and-is-not-retried
  (let [calls (atom 0)
        transport (fn [_]
                    (swap! calls inc)
                    (d/success-deferred
                     {:payload (bytes-of "nope")
                      :function-error "Unhandled"
                      :request-id "aws-err"}))
        result (await-ctx (lambda/invoke (ctx (upstream) :get)
                                         transport
                                         (balance/budget)))]
    (is (= 1 @calls))
    (is (= 502 (get-in result [:response :status])))
    (is (= "lambda.function_error" (:reason result)))
    (is (= "Unhandled" (:lambda-function-error result)))))

(deftest invalid-and-oversized-payloads-are-bad-responses
  (let [invalid (lambda/interpret-payload (bytes-of "not-json") nil "r")
        huge (lambda/interpret-payload
              (byte-array (inc lambda/max-event-bytes))
              nil
              "r")]
    (is (= "lambda.bad_response" (:reason invalid)))
    (is (= "lambda.bad_response" (:reason huge)))))

(deftest a-payload-without-status-code-is-json-200
  (let [text "{\"hello\":1}"
        parsed (lambda/interpret-payload (bytes-of text) nil "r")]
    (is (= 200 (:status parsed)))
    (is (= text (:body parsed)))))

(deftest throttling-retries-an-idempotent-call-only
  (let [calls (atom 0)
        transport (fn [_]
                    (let [n (swap! calls inc)]
                      (d/success-deferred
                       (if (= n 1)
                         {:reason "lambda.throttled" :retryable true}
                         {:payload (ok-payload) :request-id "ok"}))))
        budget (balance/budget)
        retried (await-ctx (lambda/invoke (ctx (upstream) :get)
                                          transport
                                          budget))
        once (atom 0)
        post (await-ctx
              (lambda/invoke (ctx (upstream) :post)
                             (fn [_]
                               (swap! once inc)
                               (d/success-deferred
                                {:reason "lambda.throttled" :retryable true}))
                             (balance/budget)))]
    (is (= 2 @calls))
    (is (= 201 (get-in retried [:response :status])))
    (is (= 1 @once))
    (is (= 503 (get-in post [:response :status])))
    (is (= "1" (get-in post [:response :headers "retry-after"])))))

(deftest timeouts-are-not-retried
  (let [calls (atom 0)
        result (await-ctx
                (lambda/invoke
                 (ctx (upstream :retries 2) :get)
                 (fn [_]
                   (swap! calls inc)
                   (d/success-deferred
                    {:reason "lambda.timeout" :retryable false}))
                 (balance/budget)))]
    (is (= 1 @calls))
    (is (= 504 (get-in result [:response :status])))
    (is (= "lambda.timeout" (:reason result)))))

(deftest invoke-failed-without-the-retry-flag-stays-a-502
  (let [calls (atom 0)
        result (await-ctx
                (lambda/invoke
                 (ctx (upstream) :get)
                 (fn [_]
                   (swap! calls inc)
                   (d/success-deferred
                    {:reason "lambda.invoke_failed" :retryable false}))
                 (balance/budget)))]
    (is (= 1 @calls))
    (is (= 502 (get-in result [:response :status])))))

(deftest an-oversized-body-is-413
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"payload too large"
       @(lambda/read-body (byte-array 11) 10))))

(deftest the-limiter-queues-one-waiter-and-rejects-the-rest
  (let [gate (lambda/limiter 1 1)
        first-permit (lambda/acquire! gate)
        waiting (lambda/acquire! gate)
        rejected (lambda/acquire! gate)]
    (is (true? @first-permit))
    (is (not (d/realized? waiting)))
    (is (= "lambda.concurrency_limited"
           (:reason (ex-data (try @rejected
                                  (catch clojure.lang.ExceptionInfo ex
                                    ex))))))
    (lambda/release! gate)
    (is (true? (deref waiting 500 ::timeout)))
    (lambda/release! gate)))

(deftest a-full-limiter-fails-the-invoke
  (let [gate (lambda/limiter 1 0)
        up (upstream :limiter gate)
        release (d/deferred)
        started (promise)
        transport (fn [_]
                    (deliver started true)
                    release)
        held (lambda/invoke (ctx up :get) transport nil)]
    (is (deref started 1000 false))
    (let [blocked (await-ctx (lambda/invoke (ctx up :get) transport nil))]
      (is (= 503 (get-in blocked [:response :status])))
      (is (= "lambda.concurrency_limited" (:reason blocked))))
    (d/success! release {:payload (ok-payload) :request-id "held"})
    (is (= 201 (get-in (await-ctx held) [:response :status])))))
