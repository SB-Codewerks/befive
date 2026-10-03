(ns befive.schema.settings
  "Node settings spec. An invalid value is an exit code 78."
  (:require [befive.schema.core :as schema]
            [befive.schema.errors :as errors]
            [befive.schema.meta :as meta]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defn jdbc-url?
  "True for a PostgreSQL JDBC URL."
  [value]
  (boolean (and (string? value)
                (str/starts-with? value "jdbc:postgresql:"))))

(s/def ::role #{:all :gateway :control-plane})
(s/def ::node-id ::schema/non-blank-string)
(s/def ::environment ::schema/slug)
(s/def ::db-url
  (s/and string? jdbc-url?))
(s/def ::db-user ::schema/non-blank-string)
(s/def ::db-password
  (s/and string? #(not (str/blank? %))))
(s/def ::migrate-on-start boolean?)
(s/def ::eval boolean?)
(s/def ::trusted-proxies
  (s/coll-of ::schema/cidr :kind vector?))
(s/def ::ops-port (s/int-in 0 65536))
(s/def ::gateway-port (s/int-in 0 65536))
(s/def ::admin-port (s/int-in 0 65536))
(s/def ::db-startup-timeout-ms (s/int-in 1 3600001))
(s/def ::redis-uri ::schema/non-blank-string)
(s/def ::snapshot-file ::schema/non-blank-string)
(s/def ::lkg-dir ::schema/non-blank-string)

(s/def ::settings
  (s/and (s/keys :req-un [::node-id ::db-url ::db-user ::db-password]
                 :opt-un [::role ::environment ::migrate-on-start ::eval
                          ::trusted-proxies ::ops-port ::gateway-port
                          ::admin-port ::db-startup-timeout-ms
                          ::redis-uri ::snapshot-file ::lkg-dir])
         (schema/closed-spec ::settings {})))

(meta/register! ::role
  {:default :all
   :title "Role"
   :widget :select
   :enum-labels {:all "All"
                 :gateway "Gateway"
                 :control-plane "Control plane"}})

(meta/register! ::environment
  {:default "dev" :title "Environment"})

(meta/register! ::migrate-on-start
  {:default true :title "Migrate on start" :widget :switch})

(meta/register! ::eval
  {:default false :title "Evaluation mode" :widget :switch})

(meta/register! ::trusted-proxies
  {:default [] :title "Trusted proxies"})

(meta/register! ::ops-port
  {:default 9901 :title "Ops port" :widget :number :min 0 :max 65535})

(meta/register! ::gateway-port
  {:default 8080 :title "Gateway port" :widget :number :min 0 :max 65535})

(meta/register! ::admin-port
  {:default 9000 :title "Admin port" :widget :number :min 0 :max 65535})

(meta/register! ::db-startup-timeout-ms
  {:default 60000
   :title "DB startup timeout (ms)"
   :widget :number
   :min 1
   :max 3600000
   :since 1})

(meta/register! ::snapshot-file
  {:title "Snapshot file"
   :help "When set, this EDN file is the config source."
   :since 1})

(meta/register! ::lkg-dir
  {:default "/var/lib/befive/lkg"
   :title "Last-known-good directory"
   :since 1})

(errors/register-messages!
 {[:befive.schema.settings/role :enum]
  "must be one of: all, gateway, control-plane"
  [:befive.schema.settings/db-url :pattern]
  "must be a jdbc:postgresql: URL"
  [:befive.schema.settings/environment :pattern]
  "must be a slug"
  [:befive.schema.settings/ops-port :range]
  "must be a TCP port from 0 to 65535"
  [:befive.schema.settings/gateway-port :range]
  "must be a TCP port from 0 to 65535"
  [:befive.schema.settings/admin-port :range]
  "must be a TCP port from 0 to 65535"
  [:befive.schema.settings/db-startup-timeout-ms :range]
  "must be a duration in milliseconds from 1 to 3600000"})
