(ns befive.core.db
  "HikariCP pool. A gateway keeps running when PostgreSQL is down.
  A control plane waits, then fails."
  (:import (com.zaxxer.hikari HikariConfig HikariDataSource)
           (java.sql Connection Statement)
           (javax.sql DataSource)))

(set! *warn-on-reflection* true)

(defn- config
  [{:keys [db-url db-user db-password]}]
  (doto (HikariConfig.)
    (.setJdbcUrl db-url)
    (.setUsername db-user)
    (.setPassword db-password)
    (.setMaximumPoolSize 4)
    (.setMinimumIdle 0)
    (.setConnectionTimeout 1000)
    (.setValidationTimeout 1000)
    (.setInitializationFailTimeout 0)
    (.setPoolName "befive")))

(defn ping
  "True when `SELECT 1` succeeds within the pool's connect timeout."
  [db]
  (try
    (let [^DataSource ds (:datasource db)]
      (with-open [^Connection conn (.getConnection ds)
                  ^Statement statement (.createStatement conn)]
        (.setQueryTimeout statement 2)
        (.execute statement "select 1")))
    true
    (catch Exception _
      false)))

(defn wait-until-up
  [db timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) (long timeout-ms))]
    (loop []
      (cond
        (ping db) true
        (> (System/currentTimeMillis) deadline) false
        :else (do (Thread/sleep 200) (recur))))))

(defn stop
  [db]
  (when-let [ds (:datasource db)]
    (.close ^HikariDataSource ds)))

(defn start
  "Open a pool. A gateway returns immediately so it can stay up
  while PostgreSQL is down. A control plane waits up to
  `:db-startup-timeout-ms` and then throws."
  ([settings]
   (start settings {}))
  ([settings {:keys [required]}]
   (let [required? (if (some? required)
                     required
                     (not= (:role settings) :gateway))
         ds (HikariDataSource. (config settings))
         db {:datasource ds :required required?}]
     (cond
       (not required?) db
       (wait-until-up db (:db-startup-timeout-ms settings)) db
       :else
       (do
         (stop db)
         (throw (ex-info "PostgreSQL did not become ready"
                         {:url (:db-url settings)
                          :timeout-ms (:db-startup-timeout-ms settings)})))))))
