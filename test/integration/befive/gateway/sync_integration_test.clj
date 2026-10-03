(ns befive.gateway.sync-integration-test
  "Config sync against PostgreSQL: NOTIFY, the poll, and the heartbeat."
  (:require [befive.core.db :as db]
            [befive.cp.migrate :as migrate]
            [befive.gateway.compile :as compile]
            [befive.gateway.sync :as sync]
            [befive.gateway.targets :as targets]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import (java.nio.file Files)
           (java.sql Connection Statement ResultSet)
           (java.time Duration)
           (javax.sql DataSource)
           (org.testcontainers.containers PostgreSQLContainer)
           (org.testcontainers.utility DockerImageName)))

(defn- start-postgres
  ^PostgreSQLContainer []
  (doto (PostgreSQLContainer.
         ^DockerImageName (DockerImageName/parse "postgres:16-alpine"))
    (.withDatabaseName "befive")
    (.withUsername "befive")
    (.withPassword "befive")
    (.withStartupTimeout (Duration/ofMinutes 3))
    (.start)))

(defn- schema-version
  [ds]
  (with-open [^Connection conn (.getConnection ^DataSource ds)
              ^Statement statement (.createStatement conn)
              ^ResultSet rows
              (.executeQuery statement
                             "select schema_version from befive_meta")]
    (when (.next rows)
      (.getInt rows 1))))

(defn- insert-snapshot!
  [ds revision document]
  (jdbc/execute!
   ds
   ["insert into config_snapshot (revision, document) values (?, ?)"
    revision
    (pr-str document)]))

(defn- notify!
  [ds revision]
  (jdbc/execute!
   ds
   ["select pg_notify('befive_config', ?)" (str revision)]))

(defn- wait-revision
  [table revision timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) (long timeout-ms))]
    (loop []
      (cond
        (= revision (:revision @table)) true
        (> (System/currentTimeMillis) deadline) false
        :else (do
                (Thread/sleep 100)
                (recur))))))

(defn- node-row
  [ds node-id]
  (first
   (jdbc/execute!
    ds
    [(str "select applied_revision, status, config_source "
          "from gateway_node where node_id = ?")
     node-id]
    {:builder-fn rs/as-unqualified-lower-maps})))

(defn- delete-tree!
  [dir]
  (doseq [child (reverse (file-seq (io/file dir)))]
    (.delete ^java.io.File child)))

(deftest ^:integration notify-and-poll-apply-revisions
  (let [container (start-postgres)
        lkg (str (Files/createTempDirectory
                  "befive-lkg"
                  (make-array java.nio.file.attribute.FileAttribute 0)))]
    (try
      (let [started (db/start {:role :control-plane
                               :db-url (.getJdbcUrl container)
                               :db-user (.getUsername container)
                               :db-password (.getPassword container)
                               :db-startup-timeout-ms 60000}
                              {:required true})
            ds (:datasource started)
            table (atom (compile/empty-table))
            targets (atom {})
            node-id "node-sync"]
        (try
          (is (= {:applied 3} (migrate/run-migrations! started)))
          (is (= 1 (schema-version ds)))
          (targets/reconcile! targets {})
          (let [running (sync/start
                         {:table table
                          :targets targets
                          :datasource ds
                          :settings {:node-id node-id
                                     :lkg-dir lkg
                                     :db-startup-timeout-ms 5000}})]
            (try
              (Thread/sleep 800)
              (insert-snapshot! ds 1 {:revision 1
                                      :routes []
                                      :upstreams []})
              (notify! ds 1)
              (is (wait-revision table 1 3000)
                  "NOTIFY should apply revision 1 within 3 seconds")
              (insert-snapshot! ds 2 {:revision 2
                                      :routes []
                                      :upstreams []})
              (is (wait-revision table 2 13000)
                  "the 10 second poll should apply revision 2")
              (let [row (node-row ds node-id)]
                (is (= 2 (:applied_revision row)))
                (is (= "in-sync" (:status row)))
                (is (= "database" (:config_source row))))
              (finally
                ((:stop! running)))))
          (finally
            (db/stop started))))
      (finally
        (.stop container)
        (delete-tree! lkg)))))
