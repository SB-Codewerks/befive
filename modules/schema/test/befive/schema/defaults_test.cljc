(ns befive.schema.defaults-test
  (:require [befive.schema.core :as schema]
            [befive.schema.defaults :as defaults]
            [befive.schema.settings :as settings]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.test :refer [deftest is]]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]))

(s/def ::flag boolean?)
(s/def ::note (s/nilable string?))
(s/def ::nested (s/keys :opt-un [::flag]))
(s/def ::outer (s/keys :opt-un [::nested ::schema/duration ::note]))
(s/def ::loop (s/keys :opt-un [::loop]))

(def registry
  {::flag {:default false}
   ::note {:default nil}
   ::schema/duration {:default 5}
   ::loop {:default {}}})

(deftest absent-nested-map-stays-absent
  (is (= {:duration 5 :note nil}
         (defaults/apply-defaults ::outer {} registry)))
  (is (not (contains? (defaults/apply-defaults ::outer {} registry)
                      :nested))))

(deftest present-nested-map-is-filled
  (is (= {:nested {:flag false} :duration 5 :note nil}
         (defaults/apply-defaults ::outer {:nested {}} registry))))

(deftest nil-and-false-defaults-count
  (let [value (defaults/apply-defaults ::outer {} registry)]
    (is (contains? value :note))
    (is (nil? (:note value)))))

(deftest apply-defaults-is-idempotent
  (let [once (defaults/apply-defaults ::outer {:nested {}} registry)
        twice (defaults/apply-defaults ::outer once registry)]
    (is (= once twice))))

(deftest depth-limit-stops-a-cycle
  (is (map? (defaults/apply-defaults ::loop {} registry))))

(deftest ^:generative settings-defaults-are-idempotent
  (let [result
        (tc/quick-check
         20
         (prop/for-all
          [role (gen/elements [:all :gateway :control-plane])
           environment (s/gen ::schema/slug)]
          (let [base {:node-id "n1"
                      :db-url "jdbc:postgresql://localhost/befive"
                      :db-user "u"
                      :db-password "p"
                      :role role
                      :environment environment}
                once (defaults/apply-defaults ::settings/settings base)
                twice (defaults/apply-defaults ::settings/settings once)]
            (and (= once twice)
                 (= role (:role once))
                 (= 60000 (:db-startup-timeout-ms once))))))]
    (is (:pass? result) (pr-str result))))
