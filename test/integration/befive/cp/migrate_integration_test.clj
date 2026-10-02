(ns befive.cp.migrate-integration-test
  (:require [befive.app.system :as system]
            [befive.core.json :as json]
            [befive.core.settings :as settings]
            [befive.cp.migrate :as migrate]
            [clojure.test :refer [deftest is]]
            [integrant.core :as ig])
  (:import (java.net URI)
           (java.net.http HttpClient HttpRequest HttpResponse
                          HttpResponse$BodyHandlers)
           (java.sql Connection Statement ResultSet)
           (java.time Duration)
           (javax.sql DataSource)
           (org.testcontainers.containers PostgreSQLContainer)
           (org.testcontainers.utility DockerImageName)))

(defn- start-postgres
  ^PostgreSQLContainer
  [image]
  (doto (PostgreSQLContainer.
         ^DockerImageName (DockerImageName/parse image))
    (.withDatabaseName "befive")
    (.withUsername "befive")
    (.withPassword "befive")
    (.withStartupTimeout (Duration/ofMinutes 3))
    (.start)))

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

(defn- schema-version
  [db]
  (with-open [^Connection conn (.getConnection
                                ^DataSource (:datasource db))
              ^Statement statement (.createStatement conn)
              ^ResultSet rows (.executeQuery
                               statement
                               "select schema_version from befive_meta")]
    (when (.next rows)
      (.getInt rows 1))))

(defn- exercise
  [image]
  (let [container (start-postgres image)]
    (try
      (let [config (system/config
                    (assoc (settings/example)
                           :role :all
                           :db-url (.getJdbcUrl container)
                           :db-user (.getUsername container)
                           :db-password (.getPassword container)
                           :db-startup-timeout-ms 60000))
            running (ig/init config)]
        (try
          (let [ops (:port (:befive/ops running))
                gateway (:port (:befive/gateway-http running))
                admin (:port (:befive/admin-http running))
                ready (http-get ops "/readyz")
                info (http-get ops "/internal/info")]
            (is (= 200 (:status (http-get ops "/healthz"))))
            (is (= 200 (:status ready)) (:body ready))
            (is (= {:status "ready"} (:body ready)))
            (is (= "all" (:role (:body info))))
            (is (= "0.1.0-SNAPSHOT" (:version (:body info))))
            (is (= 404 (:status (http-get gateway "/"))))
            (is (= 404 (:status (http-get admin "/"))))
            (is (= 1 (schema-version (:befive/db running))))
            (is (= {:applied 0}
                   (migrate/run-migrations! (:befive/db running)))))
          (finally
            (ig/halt! running))))
      (finally
        (.stop container)))))

(deftest ^:integration postgres-15
  (exercise "postgres:15-alpine"))

(deftest ^:integration postgres-17
  (exercise "postgres:17-alpine"))
