(ns befive.gateway.sync
  "Load config from a snapshot file, PostgreSQL, or the last-known-good
  directory. LISTEN/NOTIFY is debounced. A 10 second poll covers a
  missed notification. The loop never runs on the Netty event loop."
  (:require [befive.core.version :as version]
            [befive.gateway.lkg :as lkg]
            [befive.gateway.table :as table]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import (java.sql Connection SQLException)
           (javax.sql DataSource)
           (org.postgresql PGConnection PGNotification)))

(set! *warn-on-reflection* true)

(def feature-level
  "0.x nodes share feature level 1."
  1)

(defn read-document
  [text]
  (binding [*read-eval* false]
    (edn/read-string {:readers {}} text)))

(defn apply-change
  "Apply one config_change row to a snapshot document."
  [model {:keys [entity entity_id entity-id after after_doc]}]
  (let [id (or entity-id entity_id)
        after (or after after_doc)
        after (cond
                (nil? after) nil
                (string? after) (read-document after)
                :else after)
        drop-id (fn [rows]
                  (vec (remove #(= id (:id %)) rows)))]
    (case entity
      "route" (update model :routes
                      (fn [routes]
                        (let [kept (drop-id (or routes []))]
                          (if after (conj kept after) kept))))
      "upstream" (update model :upstreams
                         (fn [rows]
                           (let [kept (drop-id (or rows []))]
                             (if after (conj kept after) kept))))
      "limits" (if after
                 (assoc model :limits after)
                 (dissoc model :limits))
      "api" (if after
              (update model :apis (fn [apis] (assoc (or apis {}) id after)))
              (update model :apis dissoc id))
      model)))

(defn contiguous?
  "True when `revisions` is every integer from `applied` + 1 through
  the newest revision."
  [applied revisions]
  (boolean
   (and (seq revisions)
        (let [newest (apply max revisions)
              expected (set (range (inc (long applied)) (inc newest)))]
          (= expected (set revisions))))))

(defn merge-delta
  "Apply a contiguous delta. A gap returns nil so the caller loads
  the full snapshot."
  [model changes head-revision]
  (let [revisions (distinct (map :revision changes))]
    (when (and (contiguous? (:revision model 0) revisions)
               (= head-revision (apply max revisions)))
      (assoc (reduce apply-change model changes)
             :revision head-revision))))

(s/fdef contiguous?
  :args (s/cat :applied number? :revisions (s/coll-of number?))
  :ret boolean?)

(s/fdef merge-delta
  :args (s/cat :model map?
               :changes (s/coll-of map?)
               :head-revision number?)
  :ret (s/nilable map?))

(defn- maps
  [ds sql params]
  (jdbc/execute! ds (into [sql] params)
                 {:builder-fn rs/as-unqualified-lower-maps}))

(defn latest-row
  [ds]
  (first (maps ds
               (str "select revision, document from config_snapshot "
                    "order by revision desc limit 1")
               [])))

(defn changes-after
  [ds applied]
  (maps ds
        (str "select revision, entity, entity_id, after_doc "
             "from config_change where revision > ? "
             "order by revision, entity, entity_id")
        [applied]))

(defn- load-database
  [ds document]
  (when-let [row (latest-row ds)]
    (let [head (read-document (:document row))
          head-rev (long (:revision row))
          applied (long (:revision document 0))]
      (cond
        (nil? document) head
        (>= applied head-rev) nil
        :else (or (merge-delta document
                               (changes-after ds applied)
                               head-rev)
                  head)))))

(defn- file-document
  [path]
  (let [file (io/file path)]
    (when (.isFile file)
      {:mtime (.lastModified file)
       :document (read-document (slurp file :encoding "UTF-8"))})))

(defn- publish!
  [deps document source]
  (let [result (table/swap-in! (:table deps) document (:targets deps))]
    (when (:applied result)
      (lkg/write-safe! (:lkg-dir deps) document)
      (reset! (:source deps) source)
      (reset! (:document deps) document))
    (reset! (:error deps) (:error result))
    result))

(defn- ping
  [ds]
  (try
    (jdbc/execute! ds ["select 1"])
    true
    (catch Exception _
      false)))

(defn- wait-for-db
  [ds timeout-ms stop?]
  (let [deadline (+ (System/currentTimeMillis) (long timeout-ms))]
    (loop []
      (cond
        @stop? false
        (ping ds) true
        (> (System/currentTimeMillis) deadline) false
        :else (do
                (Thread/sleep 200)
                (recur))))))

(defn- heartbeat!
  [ds node-id revision source error]
  (jdbc/execute!
   ds
   [(str "insert into gateway_node "
         "(node_id, applied_revision, status, config_source, "
         "apply_error, version, feature_level, last_seen) "
         "values (?, ?, ?, ?, ?, ?, ?, now()) "
         "on conflict (node_id) do update set "
         "applied_revision = excluded.applied_revision, "
         "status = excluded.status, "
         "config_source = excluded.config_source, "
         "apply_error = excluded.apply_error, "
         "version = excluded.version, "
         "feature_level = excluded.feature_level, "
         "last_seen = now()")
    node-id
    revision
    (if error "behind" "in-sync")
    source
    error
    (:version (version/info))
    feature-level]))

(defn- listen
  [^Connection conn]
  (with-open [statement (.createStatement conn)]
    (.execute statement "listen befive_config")))

(defn- poll-notifications
  [^Connection conn]
  (let [^PGConnection pg (.unwrap conn PGConnection)
        notes (.getNotifications pg)]
    (when notes
      (keep (fn [^PGNotification note]
              (parse-long (.getParameter note)))
            notes))))

(defn- start-load!
  [deps]
  (let [file (:snapshot-file deps)
        ds (:datasource deps)]
    (cond
      file
      (when-let [loaded (file-document file)]
        (publish! deps (:document loaded) "file")
        (reset! (:mtime deps) (:mtime loaded)))

      (and ds (wait-for-db ds (:db-timeout-ms deps) (:stop? deps)))
      (try
        (when-let [document (load-database ds nil)]
          (publish! deps document "database"))
        (catch SQLException _
          (when-let [document (lkg/load-latest (:lkg-dir deps))]
            (publish! deps document "lkg"))))

      :else
      (when-let [document (lkg/load-latest (:lkg-dir deps))]
        (publish! deps document "lkg")))))

(defn- refresh-file!
  [deps]
  (when-let [loaded (file-document (:snapshot-file deps))]
    (when (not= (:mtime loaded) @(:mtime deps))
      (publish! deps (:document loaded) "file")
      (reset! (:mtime deps) (:mtime loaded)))))

(defn- refresh-database!
  [deps]
  (try
    (when-let [document (load-database (:datasource deps) @(:document deps))]
      (publish! deps document "database"))
    (catch SQLException _
      nil)))

(defn- notes?
  [conn]
  (try
    (boolean (seq (poll-notifications conn)))
    (catch SQLException _
      (throw _))))

(defn- ensure-listen!
  [ds listen-conn]
  (when (nil? @listen-conn)
    (let [^Connection conn (.getConnection ^DataSource ds)]
      (.setAutoCommit conn true)
      (listen conn)
      (reset! listen-conn conn)))
  @listen-conn)

(defn- drop-listen!
  [listen-conn]
  (when-let [^Connection conn @listen-conn]
    (try
      (.close conn)
      (catch Exception _))
    (reset! listen-conn nil)))

(defn- loop!
  [deps]
  (let [ds (:datasource deps)
        listen-conn (atom nil)]
    (try
      (start-load! deps)
      (loop [next-poll (+ (System/currentTimeMillis) 10000)
             next-beat (+ (System/currentTimeMillis) 5000)
             notified? false]
        (when-not @(:stop? deps)
          (let [now (System/currentTimeMillis)
                notified?
                (if (:snapshot-file deps)
                  (do
                    (refresh-file! deps)
                    false)
                  (if ds
                    (try
                      (let [conn (ensure-listen! ds listen-conn)]
                        (when (or notified? (>= now next-poll))
                          (refresh-database! deps))
                        (notes? conn))
                      (catch SQLException _
                        (drop-listen! listen-conn)
                        (when (or notified? (>= now next-poll))
                          (refresh-database! deps))
                        false))
                    notified?))]
            (when (and ds (>= now next-beat) (ping ds))
              (try
                (heartbeat! ds
                            (:node-id deps)
                            (:revision @(:table deps))
                            @(:source deps)
                            @(:error deps))
                (catch SQLException _
                  nil)))
            (when-not @(:stop? deps)
              (Thread/sleep 250)
              (recur (if (>= now next-poll)
                       (+ now 10000)
                       next-poll)
                     (if (>= now next-beat)
                       (+ now 5000)
                       next-beat)
                     notified?)))))
      (finally
        (drop-listen! listen-conn)))))

(defn start
  "Start the sync loop on a virtual thread."
  [{:keys [table targets settings datasource]}]
  (let [deps {:table table
              :targets targets
              :datasource datasource
              :snapshot-file (:snapshot-file settings)
              :lkg-dir (:lkg-dir settings "/var/lib/befive/lkg")
              :db-timeout-ms (:db-startup-timeout-ms settings 60000)
              :node-id (:node-id settings)
              :stop? (atom false)
              :source (atom "empty")
              :error (atom nil)
              :document (atom nil)
              :mtime (atom nil)}
        ^Thread thread (-> (Thread/ofVirtual)
                           (.name "befive-config-sync")
                           (.start
                            (fn []
                              (try
                                (loop! deps)
                                (catch InterruptedException _
                                  nil)
                                (catch Throwable thrown
                                  (log/error thrown "config sync stopped"))))))]
    {:stop! (fn []
              (reset! (:stop? deps) true)
              (.interrupt thread))
     :source (:source deps)}))
