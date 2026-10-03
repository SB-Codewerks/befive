(ns befive.core.codec
  "JSON, EDN and Transit for domain documents.
  JSON restores keywords on known fields and sets on known collections.
  The three codecs each round-trip to equal data."
  (:require [befive.core.json :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cognitect.transit :as transit])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)))

(set! *warn-on-reflection* true)

(def enum-keys
  #{:strategy :state :kind :status :method :audience
    :environment-scope :lifecycle :balance :version-selected-by
    :level :format :source :action :role})

(def set-keys
  #{:tags :methods :groups :users :organizations
    :okta-groups :email-domains})

(defn- keyword-value
  [text]
  (let [text (if (str/starts-with? text ":") (subs text 1) text)]
    (keyword text)))

(defn restore
  "Put back keywords and sets that JSON cannot carry."
  [value]
  (cond
    (map? value)
    (into {}
          (map (fn [[field item]]
                 [field (cond
                          (and (enum-keys field) (string? item))
                          (keyword-value item)

                          (and (set-keys field)
                               (sequential? item)
                               (every? #(or (string? %) (keyword? %)) item))
                          (set (map restore item))

                          :else (restore item))]))
          value)

    (sequential? value)
    (mapv restore value)

    :else value))

(defn write-edn
  [value]
  (pr-str value))

(defn read-edn
  [text]
  (edn/read-string {:eof nil :readers {}} text))

(defn write-json
  [value]
  (json/write-str value))

(defn read-json
  [text]
  (restore (json/read-str text)))

(defn write-transit
  [value]
  (let [^ByteArrayOutputStream out (ByteArrayOutputStream. 256)
        writer (transit/writer out :json)]
    (transit/write writer value)
    (.toString out "UTF-8")))

(defn read-transit
  [^String text]
  (let [raw (.getBytes text "UTF-8")
        in (ByteArrayInputStream. raw)]
    (transit/read (transit/reader in :json))))

(defn round-trip
  "Read back `value` through `codec`, one of `:edn`, `:json`, `:transit`."
  [codec value]
  (case codec
    :edn (read-edn (write-edn value))
    :json (read-json (write-json value))
    :transit (read-transit (write-transit value))))
