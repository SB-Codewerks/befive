(ns befive.gateway.lkg-test
  (:require [befive.gateway.compile :as compile]
            [befive.gateway.lkg :as lkg]
            [befive.gateway.sync :as sync]
            [befive.gateway.table :as table]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]])
  (:import (java.nio.file Files)
           (java.sql SQLException)
           (javax.sql DataSource)))

(defn- temp-dir
  []
  (.toFile (Files/createTempDirectory "befive-lkg" (make-array
                                                    java.nio.file.attribute.FileAttribute
                                                    0))))

(def snapshot
  {:revision 1
   :routes []
   :upstreams []})

(deftest round-trip-rejects-a-tampered-body-and-keeps-three
  (let [dir (temp-dir)
        text (lkg/encode snapshot)]
    (try
      (is (= snapshot (lkg/decode text)))
      (is (nil? (lkg/decode (str/replace-first text ":routes" ":routeZ"))))
      (doseq [revision (range 1 5)]
        (lkg/write! dir (assoc snapshot :revision revision)))
      (let [names (set (map (fn [^java.io.File file] (.getName file))
                            (.listFiles dir)))]
        (is (= #{"snapshot-2.edn.gz" "snapshot-3.edn.gz" "snapshot-4.edn.gz"}
               names)))
      (is (= 4 (:revision (lkg/load-latest dir))))
      (finally
        (doseq [file (.listFiles dir)]
          (.delete file))
        (.delete dir)))))

(deftest a-failed-compile-keeps-the-previous-revision
  (let [current (atom (compile/empty-table))
        targets (atom {})
        good {:revision 1 :routes [] :upstreams []}
        bad {:revision 2
             :routes [{:id "r"
                       :methods #{:get}
                       :path "/"
                       :upstream "missing"}]
             :upstreams []}]
    (is (= {:applied 1} (table/swap-in! current good targets)))
    (let [failed (table/swap-in! current bad targets)]
      (is (= 1 (:kept failed)))
      (is (string? (:error failed)))
      (is (= 1 (:revision @current))))))

(defn- down-datasource
  []
  (reify DataSource
    (getConnection [_]
      (throw (SQLException. "down")))
    (getConnection [_ _ _]
      (throw (SQLException. "down")))))

(defn- wait-for
  [pred]
  (loop [left 40]
    (cond
      (pred) true
      (zero? left) false
      :else (do
              (Thread/sleep 50)
              (recur (dec left))))))

(deftest file-snapshot-wins-and-lkg-serves-when-the-database-is-down
  (let [dir (temp-dir)
        file (io/file dir "snapshot.edn")
        lkg (io/file dir "lkg")
        table-atom (atom (compile/empty-table))
        document {:revision 4 :routes [] :upstreams []}]
    (.mkdirs lkg)
    (spit file (pr-str document))
    (let [running (sync/start
                   {:table table-atom
                    :targets (atom {})
                    :settings {:snapshot-file (.getAbsolutePath file)
                               :lkg-dir (.getAbsolutePath lkg)
                               :node-id "node"
                               :db-startup-timeout-ms 200}
                    :datasource (down-datasource)})]
      (try
        (is (wait-for #(= 4 (:revision @table-atom))))
        (finally
          ((:stop! running)))))
    (doseq [file (.listFiles lkg)]
      (.delete file))
    (lkg/write! lkg {:revision 3 :routes [] :upstreams []})
    (let [table-atom (atom (compile/empty-table))
          running (sync/start
                   {:table table-atom
                    :targets (atom {})
                    :settings {:lkg-dir (.getAbsolutePath lkg)
                               :node-id "node"
                               :db-startup-timeout-ms 300}
                    :datasource (down-datasource)})]
      (try
        (is (wait-for #(= 3 (:revision @table-atom))))
        (finally
          ((:stop! running)))))
    (doseq [file (file-seq dir)]
      (.delete file))))

(deftest a-gap-in-the-delta-returns-nil
  (let [model {:revision 0 :routes [] :upstreams []}
        route {:id "r" :methods #{:get} :path "/" :upstream "u"}
        changes [{:revision 1
                  :entity "route"
                  :entity-id "r"
                  :after route}
                 {:revision 2
                  :entity "limits"
                  :entity-id "global"
                  :after {:max-request-bytes 10}}]
        folded (sync/merge-delta model changes 2)]
    (is (= 2 (:revision folded)))
    (is (= [route] (:routes folded)))
    (is (= {:max-request-bytes 10} (:limits folded)))
    (is (nil? (sync/merge-delta model [(second changes)] 2)))
    (is (nil? (sync/merge-delta model changes 3)))
    (let [removed (sync/apply-change folded
                                     {:entity "route"
                                      :entity-id "r"
                                      :after nil})]
      (is (= [] (:routes removed))))))
