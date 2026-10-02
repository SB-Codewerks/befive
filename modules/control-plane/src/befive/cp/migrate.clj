(ns befive.cp.migrate
  "Apply VNNNN__name.sql files under a PostgreSQL advisory lock.
  Down files are kept beside them and are not run."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.io File)
           (java.net JarURLConnection URL)
           (java.sql Connection PreparedStatement ResultSet Statement)
           (java.util.jar JarEntry JarFile)
           (javax.sql DataSource)))

(set! *warn-on-reflection* true)

(def lock-id
  "Session advisory lock for schema migrations."
  70551001)

(def filename-re
  #"V(\d+)__(.+)\.sql")

(defn parse-filename
  "Return `{:id :description :file}` for an up migration, else nil."
  [filename]
  (when (and (string? filename)
             (not (str/ends-with? filename ".down.sql")))
    (when-let [[_ id description] (re-matches filename-re filename)]
      {:id (parse-long id)
       :description description
       :file (str "migrations/" filename)})))

(defn split-sql
  "Split a script on semicolons. Line comments are dropped first."
  [sql]
  (->> (str/split-lines sql)
       (map #(str/replace % #"--.*$" ""))
       (str/join "\n")
       (#(str/split % #";"))
       (map str/trim)
       (remove str/blank?)
       vec))

(defn- names-from-file
  [^URL url]
  (->> (file-seq (io/file (.toURI url)))
       (filter (fn [^File file] (.isFile file)))
       (map (fn [^File file] (.getName file)))))

(defn- names-from-jar
  [^URL url]
  (let [^JarURLConnection conn (.openConnection url)]
    (with-open [^JarFile jar (.getJarFile conn)]
      (into []
            (comp (map (fn [^JarEntry entry] (.getName entry)))
                  (filter #(str/starts-with? % "migrations/"))
                  (remove #(str/ends-with? % "/"))
                  (map #(subs % (inc (str/last-index-of % "/")))))
            (enumeration-seq (.entries jar))))))

(defn- resource-names
  []
  (let [^ClassLoader loader (.getContextClassLoader
                             (Thread/currentThread))
        urls (enumeration-seq (.getResources loader "migrations"))]
    (->> urls
         (mapcat (fn [^URL url]
                   (case (.getProtocol url)
                     "file" (names-from-file url)
                     "jar" (names-from-jar url)
                     [])))
         distinct)))

(defn migrations
  "Up migrations on the classpath, in id order."
  []
  (->> (resource-names)
       (keep parse-filename)
       (sort-by :id)
       (map (fn [migration]
              (let [sql (slurp (io/resource (:file migration))
                               :encoding "UTF-8")]
                (assoc migration :statements (split-sql sql)))))
       vec))

(defn- connection
  ^Connection
  [db]
  (.getConnection ^DataSource (:datasource db)))

(defn- lock!
  [^Connection conn]
  (with-open [^PreparedStatement ps
              (.prepareStatement conn "select pg_advisory_lock(?)")]
    (.setLong ps 1 (long lock-id))
    (.execute ps)))

(defn- unlock!
  [^Connection conn]
  (with-open [^PreparedStatement ps
              (.prepareStatement conn "select pg_advisory_unlock(?)")]
    (.setLong ps 1 (long lock-id))
    (.execute ps)))

(defn- ensure-table!
  [^Connection conn]
  (with-open [^Statement statement (.createStatement conn)]
    (.execute statement
              (str "create table if not exists schema_migrations ("
                   "id bigint primary key, "
                   "applied timestamp not null default now(), "
                   "description varchar(1024) not null)"))))

(defn- applied-ids
  [^Connection conn]
  (with-open [^Statement statement (.createStatement conn)
              ^ResultSet rs (.executeQuery
                             statement
                             "select id from schema_migrations")]
    (loop [ids #{}]
      (if (.next rs)
        (recur (conj ids (.getLong rs "id")))
        ids))))

(defn- apply-one!
  [^Connection conn migration]
  (.setAutoCommit conn false)
  (try
    (doseq [sql (:statements migration)]
      (with-open [^Statement statement (.createStatement conn)]
        (.execute statement sql)))
    (with-open [^PreparedStatement ps
                (.prepareStatement
                 conn
                 (str "insert into schema_migrations "
                      "(id, description) values (?, ?)"))]
      (.setLong ps 1 (long (:id migration)))
      (.setString ps 2 (:description migration))
      (.executeUpdate ps))
    (.commit conn)
    (catch Throwable thrown
      (.rollback conn)
      (throw thrown))
    (finally
      (.setAutoCommit conn true))))

(defn run-migrations!
  "Apply pending migrations. Returns `{:applied n}`."
  [db]
  (with-open [^Connection conn (connection db)]
    (lock! conn)
    (try
      (ensure-table! conn)
      (let [done (applied-ids conn)
            pending (remove #(contains? done (:id %)) (migrations))
            n (count pending)]
        (doseq [migration pending]
          (apply-one! conn migration))
        {:applied n})
      (finally
        (unlock! conn)))))
