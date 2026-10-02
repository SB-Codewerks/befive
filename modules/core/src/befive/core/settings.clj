(ns befive.core.settings
  "Load Aero settings and validate them. Failure is exit code 78."
  (:require [aero.core :as aero]
            [befive.schema.defaults :as defaults]
            [befive.schema.errors :as errors]
            [befive.schema.settings :as settings]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.net InetAddress)))

(set! *warn-on-reflection* true)

(def exit-config
  "sysexits EX_CONFIG."
  78)

(defonce readers-installed?
  (atom false))

(defn install-readers!
  []
  (when (compare-and-set! readers-installed? false true)
    (defmethod aero/reader 'boolean
      [_ _ value]
      (cond
        (true? value) true
        (false? value) false
        :else
        (let [text (str/lower-case (str value))]
          (cond
            (contains? #{"true" "1" "yes"} text) true
            (contains? #{"false" "0" "no"} text) false
            :else ::invalid))))
    (defmethod aero/reader 'long
      [_ _ value]
      (cond
        (int? value) (long value)
        (string? value) (parse-long value)
        :else nil))
    (defmethod aero/reader 'keyword
      [_ _ value]
      (cond
        (keyword? value) value
        (string? value) (keyword value)
        :else nil))))

(defn- blank-str?
  [value]
  (or (nil? value)
      (and (string? value) (str/blank? value))))

(defn- generated-node-id
  []
  (let [host (try
               (.getHostName ^InetAddress (InetAddress/getLocalHost))
               (catch Exception _
                 "befive"))
        host (if (blank-str? host) "befive" host)
        suffix (subs (str (random-uuid)) 0 8)]
    (str host "-" suffix)))

(defn- parse-proxies
  [value]
  (cond
    (or (nil? value) (= value "")) []
    (string? value)
    (->> (str/split value #",")
         (map str/trim)
         (remove str/blank?)
         vec)
    (vector? value) value
    :else value))

(defn- read-password-file
  [path]
  (cond
    (blank-str? path) {}
    (not (string? path))
    {:problem {:path [:db-password-file]
               :code :type
               :message "has the wrong type"}}
    :else
    (try
      {:password (str/trim (slurp path :encoding "UTF-8"))}
      (catch Exception _
        {:problem {:path [:db-password-file]
                   :code :custom
                   :message "could not be read"}}))))

(defn- dissoc-blanks
  [settings]
  (cond-> settings
    (blank-str? (:redis-uri settings)) (dissoc :redis-uri)))

(defn normalize
  "Coerce a raw settings map. Returns `{:settings map}` or
  `{:problems [...]}` when a password file cannot be read."
  [raw]
  (let [file-result (read-password-file (:db-password-file raw))]
    (if-let [problem (:problem file-result)]
      {:problems [problem]}
      (let [node-id (if (blank-str? (:node-id raw))
                      (generated-node-id)
                      (:node-id raw))
            settings (-> raw
                         (dissoc :db-password-file)
                         (assoc :node-id node-id
                                :trusted-proxies (parse-proxies
                                                  (:trusted-proxies raw)))
                         (cond-> (:password file-result)
                           (assoc :db-password (:password file-result)))
                         dissoc-blanks
                         (->> (defaults/apply-defaults ::settings/settings)))]
        {:settings settings}))))

(defn invalid
  [problems]
  {:exit exit-config
   :message (str "invalid settings\n"
                 (errors/format-problems problems))})

(defn prepare-map
  "Normalize and validate `raw`. Returns `{:settings map}` or
  `{:exit 78 :message string}`."
  [raw]
  (let [normalized (normalize raw)]
    (if-let [problems (:problems normalized)]
      (invalid problems)
      (if-let [problems (errors/explain->problems
                         ::settings/settings
                         (:settings normalized))]
        (invalid problems)
        {:settings (:settings normalized)}))))

(defn load-raw
  []
  (install-readers!)
  (aero/read-config (io/resource "befive/settings.edn")))

(defn prepare
  []
  (prepare-map (load-raw)))

(defn example
  "A valid settings map for tests."
  []
  {:role :all
   :node-id "node-test"
   :environment "dev"
   :db-url "jdbc:postgresql://127.0.0.1:5432/befive"
   :db-user "befive"
   :db-password "befive"
   :migrate-on-start true
   :eval false
   :trusted-proxies []
   :ops-port 0
   :gateway-port 0
   :admin-port 0
   :db-startup-timeout-ms 5000})
