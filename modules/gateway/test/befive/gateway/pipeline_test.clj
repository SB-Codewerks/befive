(ns befive.gateway.pipeline-test
  (:require [befive.core.json :as json]
            [befive.gateway.access-log :as access-log]
            [befive.gateway.compile :as compile]
            [befive.gateway.pipeline :as pipeline]
            [befive.gateway.targets :as targets]
            [befive.schema.access-log :as catalog]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [manifold.deferred :as d])
  (:import (java.nio.charset StandardCharsets)
           (java.util UUID)))

(def phase-order
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

(defn- open-gateway
  [document logs]
  (let [compiled (compile/compile-snapshot document)]
    {:settings {:node-id "node-1"
                :environment "dev"
                :trusted-proxies ["10.0.0.0/8"]}
     :table (atom compiled)
     :targets (doto (atom {})
                (targets/reconcile! (:upstreams compiled)))
     :pools (atom {})
     :lambda-clients (atom {})
     :ready-revision (atom (:revision compiled))
     :access-log (access-log/start {:sink #(swap! logs conj %)})
     :datasource nil}))

(defn- stop!
  [gateway]
  (access-log/stop! (:access-log gateway)))

(defn- call
  [gateway request trace]
  (let [value (deref (pipeline/handle gateway request trace) 2000 ::timeout)]
    (is (map? value))
    value))

(defn- request
  [method path headers remote]
  {:request-method method
   :uri path
   :scheme :http
   :remote-addr remote
   :headers headers})

(defn- wait-log
  [logs]
  (loop [left 30]
    (when (and (empty? @logs) (pos? left))
      (Thread/sleep 20)
      (recur (dec left))))
  (first @logs))

(deftest phase-order-matches-the-documented-pipeline
  (is (= phase-order pipeline/phase-names))
  (let [pre (.indexOf pipeline/phase-names :befive/pre-auth-limit)
        ip (.indexOf pipeline/phase-names :befive/ip-filter)
        cors (.indexOf pipeline/phase-names :befive/cors)]
    (is (= (inc ip) pre))
    (is (= (inc pre) cors))))

(deftest unknown-route-is-a-minimal-404
  (let [logs (atom [])
        gateway (open-gateway {:revision 1 :routes [] :upstreams []} logs)
        trace (atom [])
        response (call gateway
                       (request :get "/missing"
                                {"host" "api.example.com"
                                 "x-befive-subject" "forged"}
                                "203.0.113.8")
                       trace)
        entry (wait-log logs)]
    (try
      (is (= (vec (take 10 pipeline/phase-names)) @trace))
      (is (= 404 (:status response)))
      (is (= {:error "not_found" :request_id (:request_id (json/read-str (:body response)))}
             (json/read-str (:body response))))
      (is (not (str/includes? (:body response) "route.not_found")))
      (is (catalog/known? entry))
      (is (= "not_found" (:error_code entry)))
      (is (= "route.not_found" (:error entry)))
      (is (false? (:limit_rejected entry)))
      (is (= "node-1" (:node_id entry)))
      (finally
        (stop! gateway)))))

(deftest method-not-allowed-sends-allow-and-retired-is-problem-json
  (let [logs (atom [])
        gateway (open-gateway
                 {:revision 2
                  :routes [{:id "orders"
                            :methods #{:get :post}
                            :path "/orders"
                            :upstream "up"}
                           {:id "old"
                            :methods #{:get}
                            :path "/old"
                            :upstream "up"
                            :lifecycle :retired}]
                  :upstreams [{:id "up"
                               :kind :http
                               :targets [{:url "http://127.0.0.1:9"}]}]}
                 logs)
        denied (call gateway
                     (request :delete "/orders" {"host" "api.example.com"}
                              "203.0.113.8")
                     nil)
        gone (call gateway
                   (request :get "/old" {"host" "api.example.com"}
                            "203.0.113.8")
                   nil)]
    (try
      (is (= 405 (:status denied)))
      (is (= "GET, POST" (get (:headers denied) "allow")))
      (is (= "method_not_allowed"
             (:error (json/read-str (:body denied)))))
      (is (= 410 (:status gone)))
      (is (= "application/problem+json; charset=utf-8"
             (get (:headers gone) "content-type")))
      (is (= "/docs/errors/gone"
             (:type (json/read-str (:body gone)))))
      (finally
        (stop! gateway)))))

(deftest oversize-and-smuggling-fail-before-routing
  (let [logs (atom [])
        gateway (open-gateway
                 {:revision 3
                  :limits {:max-header-bytes 32
                           :max-request-bytes 10}
                  :routes []
                  :upstreams []}
                 logs)
        huge (call gateway
                   (request :get "/nope"
                            {"host" "api.example.com"
                             "x-padding" (apply str (repeat 80 "a"))}
                            "203.0.113.8")
                   nil)
        smuggled (call gateway
                       (request :post "/nope"
                                {"host" "api.example.com"
                                 "content-length" "1, 2"}
                                "203.0.113.8")
                       nil)
        too-big (call gateway
                     (request :post "/nope"
                              {"host" "api.example.com"
                               "content-length" "50"}
                              "203.0.113.8")
                     nil)]
    (try
      (is (= 413 (:status huge)))
      (is (= "payload_too_large" (:error (json/read-str (:body huge)))))
      (is (= 400 (:status smuggled)))
      (is (not (str/includes? (:body smuggled) "request.malformed")))
      (is (= 413 (:status too-big)))
      (let [entry (wait-log logs)]
        (is (catalog/known? entry))
        (is (true? (:limit_rejected entry))))
      (finally
        (stop! gateway)))))

(deftest a-trusted-proxy-may-supply-the-request-id
  (let [logs (atom [])
        gateway (open-gateway {:revision 1
                          :trusted-proxies ["10.0.0.0/8"]
                          :routes []
                          :upstreams []}
                         logs)
        id (str (UUID/randomUUID))
        trusted (call gateway
                      (request :get "/"
                               {"host" "api.example.com"
                                "x-request-id" id}
                               "10.1.2.3")
                      nil)
        forged (call gateway
                     (request :get "/"
                              {"host" "api.example.com"
                               "x-request-id" id}
                              "203.0.113.8")
                     nil)]
    (try
      (is (= id (:request_id (json/read-str (:body trusted)))))
      (is (not= id (:request_id (json/read-str (:body forged)))))
      (finally
        (stop! gateway)))))

(deftest lambda-event-includes-path-parameters
  (let [logs (atom [])
        seen (promise)
        base (open-gateway {:revision 1
                       :routes [{:id "echo"
                                 :methods #{:get}
                                 :path "/echo/:name"
                                 :upstream "fn"}]
                       :upstreams [{:id "fn"
                                    :kind :lambda
                                    :lambda {:function "echo"
                                             :region "us-east-1"}}]}
                      logs)
        state (assoc base
                     :lambda-transport
                     (fn [invocation]
                       (deliver seen
                                (json/read-str
                                 (String. ^bytes (:payload invocation)
                                          StandardCharsets/UTF_8)))
                       (d/success-deferred
                        {:payload (.getBytes "{\"statusCode\":200,\"body\":\"ok\"}"
                                             StandardCharsets/UTF_8)
                         :request-id "aws-1"})))]
    (try
      (let [response (call state
                           (request :get "/echo/widget"
                                    {"host" "api.example.com"
                                     "x-befive-subject" "forged"}
                                    "203.0.113.8")
                           nil)
            event (deref seen 1000 nil)]
        (is (= 200 (:status response)))
        (is (= "2.0" (:version event)))
        (is (= "/echo/widget" (:rawPath event)))
        (is (= {:name "widget"} (:pathParameters event)))
        (is (nil? (get (:headers event) :x-befive-subject))))
      (finally
        (stop! base)))))
