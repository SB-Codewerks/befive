(ns befive.gateway.proxy-test
  (:require [aleph.http :as http]
            [aleph.netty :as netty]
            [befive.core.json :as json]
            [befive.core.server :as server]
            [befive.gateway.access-log :as access-log]
            [befive.gateway.compile :as compile]
            [befive.gateway.pipeline :as pipeline]
            [befive.gateway.table :as table]
            [befive.gateway.targets :as targets]
            [clj-commons.byte-streams :as bs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [manifold.deferred :as d]
            [manifold.stream :as s])
  (:import (io.netty.util ResourceLeakDetector ResourceLeakDetector$Level)
           (java.io Closeable)
           (java.net ServerSocket Socket)))

(ResourceLeakDetector/setLevel ResourceLeakDetector$Level/PARANOID)

(defn- listen
  [handler]
  (http/start-server handler {:port 0 :join? false}))

(defn- port-of
  [server]
  (netty/port server))

(defn- close!
  [server]
  (when server
    (.close ^Closeable server)))

(defn- open-gateway
  [document]
  (let [compiled (compile/compile-snapshot document)
        targets (atom {})
        logs (atom [])]
    (targets/reconcile! targets (:upstreams compiled))
    {:settings {:node-id "node-1" :environment "dev"}
     :table (atom compiled)
     :targets targets
     :pools (atom {})
     :lambda-clients (atom {})
     :ready-revision (atom (:revision compiled))
     :logs logs
     :access-log (access-log/start {:sink #(swap! logs conj %)})
     :datasource nil}))

(defn- logged-reason
  [gateway reason]
  (loop [left 30]
    (let [hit (some #(when (= reason (:error %)) %) @(:logs gateway))]
      (cond
        hit hit
        (zero? left) nil
        :else (do
                (Thread/sleep 20)
                (recur (dec left)))))))

(defn- stop-gateway!
  [gateway]
  (access-log/stop! (:access-log gateway)))

(defn- await-response
  [gateway request]
  (let [value (deref (pipeline/handle gateway request) 3000 ::timeout)]
    (is (not= ::timeout value) "pipeline timed out")
    value))

(defn- http-upstream
  [url & {:keys [retries lifecycle]
          :or {retries 1}}]
  {:id "up"
   :kind :http
   :balance :round-robin
   :panic-threshold 0.0
   :max-retries retries
   :connect-timeout-ms 400
   :read-timeout-ms 400
   :idle-timeout-ms 1000
   :max-connections 4
   :targets [{:url url}]
   :lifecycle lifecycle})

(defn- document
  [upstream path extra]
  {:revision 1
   :routes [(merge {:id "route"
                    :methods (:methods extra #{:get :post})
                    :path path
                    :upstream "up"}
                   (dissoc extra :methods))]
   :upstreams [(dissoc upstream :lifecycle)]})

(defn- ring
  [method path headers body]
  (cond-> {:request-method method
           :uri path
           :scheme :http
           :remote-addr "203.0.113.8"
           :headers (merge {"host" "api.example.com"} headers)}
    body (assoc :body body)))

(deftest streamed-bodies-sse-and-header-forwarding
  (let [chunks (s/stream)
        echo (listen
              (fn [request]
                (if (= "/events" (:uri request))
                  {:status 200
                   :headers {"content-type" "text/event-stream"}
                   :body chunks}
                  (let [payload (when (:body request)
                                  (bs/to-string (:body request)))]
                    {:status 200
                     :headers {"content-type" "application/json"
                               "connection" "close"
                               "keep-alive" "timeout=5"
                               "x-upstream" "echo"}
                     :body (json/write-str
                            {:uri (:uri request)
                             :headers (:headers request)
                             :body payload})}))))
        base (str "http://127.0.0.1:" (port-of echo))
        gateway (open-gateway (document (http-upstream base) "/*path" {}))]
    (try
      (let [response (await-response
                      gateway
                      (ring :post "/orders"
                            {"x-befive-subject" "forged"
                             "content-type" "text/plain"}
                            "hello"))
            seen (json/read-str (bs/to-string (:body response)))]
        (is (= 200 (:status response)))
        (is (= "echo" (get (:headers response) "x-upstream")))
        (is (nil? (get (:headers response) "connection")))
        (is (nil? (get (:headers response) "keep-alive")))
        (is (= "hello" (:body seen)))
        (is (nil? (get-in seen [:headers :x-befive-subject])))
        (is (str/includes? (str (get-in seen [:headers :x-forwarded-for]))
                           "203.0.113.8")))
      (d/future
        (s/put! chunks "data: one\n\n")
        (s/put! chunks "data: two\n\n")
        (s/close! chunks))
      (let [response (await-response gateway
                                     (ring :get "/events" {} nil))
            text (bs/to-string (:body response))]
        (is (= 200 (:status response)))
        (is (str/includes? text "data: one"))
        (is (str/includes? text "data: two")))
      (finally
        (s/close! chunks)
        (stop-gateway! gateway)
        (close! echo)))))

(deftest a-megabyte-body-is-a-stream
  (let [payload (byte-array (* 1024 1024) (byte 7))
        body (s/stream)
        server (listen (fn [_]
                         {:status 200
                          :headers {"content-type" "application/octet-stream"}
                          :body body}))
        gateway (open-gateway
                 (document (http-upstream (str "http://127.0.0.1:"
                                               (port-of server)))
                           "/*path"
                           {}))]
    (try
      (d/future
        (s/put! body payload)
        (s/close! body))
      (let [response (await-response gateway (ring :get "/blob" {} nil))]
        (is (or (s/stream? (:body response))
                (instance? java.io.InputStream (:body response))))
        (is (= (alength payload)
               (alength ^bytes (bs/to-byte-array (:body response))))))
      (finally
        (s/close! body)
        (stop-gateway! gateway)
        (close! server)))))

(deftest response-and-request-caps-cannot-be-raised-by-a-route
  (let [server (listen (fn [_]
                         {:status 200
                          :headers {"content-type" "text/plain"
                                    "content-length" "40"}
                          :body (apply str (repeat 40 "x"))}))
        url (str "http://127.0.0.1:" (port-of server))
        gateway (open-gateway
                 {:revision 1
                  :limits {:max-response-bytes 10
                           :max-request-bytes 100}
                  :routes [{:id "route"
                            :methods #{:get :post}
                            :path "/limited"
                            :upstream "up"
                            :limits {:max-response-bytes 100000
                                     :max-request-bytes 100000}}]
                  :upstreams [(http-upstream url)]})]
    (try
      (let [too-big (await-response gateway
                                    (ring :get "/limited" {} nil))
            rejected (await-response
                      gateway
                      (ring :post "/limited"
                            {"content-length" "150"}
                            "nope"))]
        (is (= 502 (:status too-big)))
        (is (= "limits.response_too_large"
               (:error (logged-reason gateway "limits.response_too_large"))))
        (is (= 413 (:status rejected))))
      (finally
        (stop-gateway! gateway)
        (close! server)))))

(deftest deprecation-headers-are-added-on-the-way-out
  (let [server (listen (fn [_] {:status 200 :body "ok" :headers {}}))
        url (str "http://127.0.0.1:" (port-of server))
        gateway (open-gateway
                 {:revision 1
                  :routes [{:id "old"
                            :methods #{:get}
                            :path "/old"
                            :upstream "up"
                            :lifecycle :deprecated
                            :deprecation "@1"
                            :sunset "Wed, 01 Jan 2031 00:00:00 GMT"
                            :link "</docs>; rel=\"deprecation\""}]
                  :upstreams [(http-upstream url)]})]
    (try
      (let [response (await-response gateway (ring :get "/old" {} nil))]
        (is (= 200 (:status response)))
        (is (= "@1" (get (:headers response) "deprecation")))
        (is (= "Wed, 01 Jan 2031 00:00:00 GMT"
               (get (:headers response) "sunset")))
        (is (str/includes? (get (:headers response) "link") "deprecation")))
      (finally
        (stop-gateway! gateway)
        (close! server)))))

(defn- accept-loop
  [^ServerSocket socket on-accept]
  (doto (Thread.
         ^Runnable
         (fn []
           (try
             (while (not (.isClosed socket))
               (let [^Socket accepted (.accept socket)]
                 (on-accept accepted)))
             (catch Exception _))))
    (.setDaemon true)
    (.start)))

(deftest idempotent-requests-retry-connection-failures
  (let [socket (ServerSocket. 0)
        accepted (atom 0)
        _ (accept-loop socket (fn [^Socket accepted-socket]
                                (swap! accepted inc)
                                (.setSoLinger accepted-socket true 0)
                                (.close accepted-socket)))
        url (str "http://127.0.0.1:" (.getLocalPort socket))
        gateway (open-gateway
                 (-> (document (assoc (http-upstream url :retries 1)
                                      :health {:unhealthy-after 100})
                                "/retry"
                                {})
                     (assoc-in [:routes 0 :methods] #{:get :post})))]
    (try
      (let [get-response (await-response gateway (ring :get "/retry" {} nil))
            gets @accepted
            post-response (await-response gateway
                                          (ring :post "/retry" {} "x"))]
        (is (= 502 (:status get-response)))
        (is (= 2 gets))
        (is (= 502 (:status post-response)))
        (is (= 3 @accepted)))
      (finally
        (stop-gateway! gateway)
        (.close socket)))))

(deftest a-header-timeout-is-not-retried
  (let [socket (ServerSocket. 0)
        accepted (atom [])
        _ (accept-loop socket (fn [^Socket accepted-socket]
                                (swap! accepted conj accepted-socket)))
        url (str "http://127.0.0.1:" (.getLocalPort socket))
        gateway (open-gateway (document (http-upstream url :retries 1)
                                   "/slow"
                                   {:methods #{:get}}))]
    (try
      (let [response (await-response gateway (ring :get "/slow" {} nil))]
        (is (= 504 (:status response)))
        (is (= "upstream.read_timeout"
               (:error (logged-reason gateway "upstream.read_timeout"))))
        (is (= 1 (count @accepted))))
      (finally
        (doseq [^Socket open @accepted]
          (.close open))
        (stop-gateway! gateway)
        (.close socket)))))

(deftest an-in-flight-request-keeps-the-revision-it-started-with
  (let [go (promise)
        hit (promise)
        first-server (listen (fn [_]
                               (deliver hit true)
                               (deref go 2000 false)
                               {:status 200
                                :headers {"x-who" "a"}
                                :body "a"}))
        second-server (listen (fn [_]
                                {:status 200
                                 :headers {"x-who" "b"}
                                 :body "b"}))
        first-url (str "http://127.0.0.1:" (port-of first-server))
        second-url (str "http://127.0.0.1:" (port-of second-server))
        gateway (open-gateway (document (http-upstream first-url
                                                  :retries 0
                                                  :methods #{:get})
                                   "/who"
                                   {:methods #{:get}}))
        response (pipeline/handle gateway (ring :get "/who" {} nil))]
    (try
      (is (deref hit 2000 false))
      (is (= {:applied 2}
             (table/swap-in!
              (:table gateway)
              {:revision 2
               :routes [{:id "route"
                         :methods #{:get}
                         :path "/who"
                         :upstream "up"}]
               :upstreams [(assoc (http-upstream second-url :methods #{:get})
                                  :id "up")]}
              (:targets gateway))))
      (deliver go true)
      (let [first-response (deref response 2000 ::timeout)
            second-response (await-response gateway (ring :get "/who" {} nil))]
        (is (= "a" (get (:headers first-response) "x-who")))
        (is (= "b" (get (:headers second-response) "x-who"))))
      (finally
        (deliver go true)
        (stop-gateway! gateway)
        (close! first-server)
        (close! second-server)))))

(deftest websocket-messages-are-spliced
  (let [upstream (listen
                  (fn [request]
                    (d/chain
                     (http/websocket-connection request)
                     (fn [socket]
                       (s/consume
                        (fn [message]
                          (s/put! socket message))
                        socket)
                       nil))))
        url (str "http://127.0.0.1:" (port-of upstream))
        gateway (open-gateway (document (http-upstream url :methods #{:get})
                                   "/*path"
                                   {:methods #{:get}}))
        bound (server/start
               (fn [request]
                 (pipeline/handle gateway request))
               0
               {:executor :none})]
    (try
      (let [socket @(http/websocket-client
                     (str "ws://127.0.0.1:" (:port bound) "/chat"))]
        (s/put! socket "ping")
        (is (= "ping" (str @(s/take! socket))))
        (s/close! socket))
      (finally
        (server/stop bound)
        (stop-gateway! gateway)
        (close! upstream)))))
