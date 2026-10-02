(ns befive.schema.settings-test
  (:require [befive.schema.defaults :as defaults]
            [befive.schema.errors :as errors]
            [befive.schema.settings :as settings]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is]]))

(def valid
  {:node-id "n1"
   :db-url "jdbc:postgresql://localhost/befive"
   :db-user "befive"
   :db-password "secret"})

(defn- filled
  [overrides]
  (defaults/apply-defaults ::settings/settings
                           (merge valid overrides)))

(deftest defaults-fill-the-optional-keys
  (let [value (filled {})]
    (is (s/valid? ::settings/settings value))
    (is (= :all (:role value)))
    (is (= "dev" (:environment value)))
    (is (true? (:migrate-on-start value)))
    (is (false? (:eval value)))
    (is (= [] (:trusted-proxies value)))
    (is (= 9901 (:ops-port value)))
    (is (= 8080 (:gateway-port value)))
    (is (= 9000 (:admin-port value)))
    (is (= 60000 (:db-startup-timeout-ms value)))))

(deftest role-enum-message
  (let [explained (s/explain-data ::settings/settings
                                  (filled {:role :nope}))
        problems (errors/explain->problems
                  ::settings/settings
                  (filled {:role :nope}))]
    (is (some #(and (= [:role] (:path %))
                    (= :enum (:code %))
                    (re-find #"gateway" (:message %)))
              problems)
        (str (pr-str problems) " " (pr-str explained)))))

(deftest unknown-key-is-closed
  (let [explained (s/explain-data ::settings/settings
                                  (filled {:extra true}))
        problems (errors/explain->problems
                  ::settings/settings
                  (filled {:extra true}))]
    (is (some #(and (= [:extra] (:path %))
                    (= :closed (:code %)))
              problems)
        (str (pr-str problems) " " (pr-str explained)))))

(deftest db-url-and-port-messages
  (let [url (errors/explain->problems
             ::settings/settings
             (filled {:db-url "jdbc:mysql://localhost/befive"}))
        port (errors/explain->problems
              ::settings/settings
              (filled {:ops-port 70000}))]
    (is (some #(and (= [:db-url] (:path %))
                    (= :pattern (:code %)))
              url)
        (pr-str url))
    (is (some #(and (= [:ops-port] (:path %))
                    (= :range (:code %)))
              port)
        (pr-str port))))
