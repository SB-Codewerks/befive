(ns befive.app.system-test
  (:require [befive.app.system :as system]
            [befive.core.db :as db]
            [befive.core.healthcheck :as healthcheck]
            [befive.core.json :as json]
            [befive.core.settings :as settings]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [integrant.core :as ig])
  (:import (java.net URI)
           (java.net.http HttpClient HttpRequest HttpResponse
                          HttpResponse$BodyHandlers)
           (java.time Duration)))

(defn- http-get
  [port path]
  (let [^HttpClient client (HttpClient/newHttpClient)
        uri (URI/create (str "http://127.0.0.1:" port path))
        ^HttpRequest request (-> (HttpRequest/newBuilder uri)
                                 (.timeout (Duration/ofSeconds 8))
                                 (.GET)
                                 (.build))
        ^HttpResponse response
        (.send client request (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode response)
     :body (json/read-str (.body response))}))

(deftest config-follows-the-role
  (let [gateway (system/config (assoc (settings/example) :role :gateway))
        control (system/config
                 (assoc (settings/example) :role :control-plane))
        both (system/config (assoc (settings/example) :role :all))]
    (is (contains? gateway :befive/gateway-http))
    (is (not (contains? gateway :befive/migrations)))
    (is (contains? control :befive/admin-http))
    (is (contains? control :befive/migrations))
    (is (not (contains? control :befive/gateway-http)))
    (is (and (contains? both :befive/gateway-http)
             (contains? both :befive/admin-http)))))

(deftest gateway-stays-up-when-the-database-is-down
  (let [config (system/config
                (assoc (settings/example)
                       :role :gateway
                       :db-url "jdbc:postgresql://127.0.0.1:1/befive"
                       :db-startup-timeout-ms 500))
        running (ig/init config)]
    (try
      (let [port (:port (:befive/ops running))
            live (http-get port "/healthz")
            ready (http-get port "/readyz")
            info (http-get port "/internal/info")]
        (is (= 200 (:status live)))
        (is (= {:status "ok"} (:body live)))
        (is (= 503 (:status ready)))
        (is (= "down" (get-in ready [:body :checks :database])))
        (is (= "gateway" (:role (:body info))))
        (is (zero? (healthcheck/exit-code port "/healthz")))
        (is (= 1 (healthcheck/exit-code port "/readyz"))))
      (finally
        (ig/halt! running)))))

(deftest control-plane-fails-when-the-database-stays-down
  (let [config (system/config
                (assoc (settings/example)
                       :role :control-plane
                       :db-url "jdbc:postgresql://127.0.0.1:1/befive"
                       :db-startup-timeout-ms 500))]
    (try
      (let [running (ig/init config)]
        (try
          (is false "control plane should have failed")
          (finally
            (ig/halt! running))))
      (catch Exception ex
        (let [text (str (ex-message ex) " "
                        (ex-message (ex-cause ex)))]
          (is (str/includes? text "did not become ready") text))))))

(deftest gateway-pool-stays-open-for-a-later-ping
  (let [database (db/start (assoc (settings/example)
                                  :role :gateway
                                  :db-url
                                  "jdbc:postgresql://127.0.0.1:1/befive"
                                  :db-startup-timeout-ms 200)
                           {:required false})]
    (try
      (is (false? (db/ping database)))
      (finally
        (db/stop database)))))
