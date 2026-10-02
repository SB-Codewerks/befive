(ns befive.schema.core-test
  (:require [befive.schema.core :as schema]
            [befive.schema.errors :as errors]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            #?(:clj [clojure.spec.test.alpha :as stest])))

(s/def ::sample
  (s/and (s/keys :req-un [::schema/slug]
                 :opt-un [::schema/duration])
         (schema/closed-spec ::sample {})))

(s/def ::prefixed
  (s/and (s/keys :opt-un [::schema/slug])
         (schema/closed-spec ::prefixed {:allow-prefix "x-"})))

#?(:clj
   (clojure.test/use-fixtures :once
     (fn [tests]
       (stest/instrument
        `[schema/keys-of
          schema/unknown-keys
          schema/closed-spec
          errors/explain->problems
          errors/format-problems])
       (try (tests)
            (finally (stest/unstrument))))))

(defn- passes?
  [spec generator]
  (let [result (tc/quick-check
                40
                (prop/for-all [value generator]
                  (s/valid? spec value)))]
    (is (:pass? result) (pr-str result))))

(deftest slug-grammar
  (is (s/valid? ::schema/slug "a"))
  (is (s/valid? ::schema/slug "a-b2"))
  (is (not (s/valid? ::schema/slug "A")))
  (is (not (s/valid? ::schema/slug "-a")))
  (is (not (s/valid? ::schema/slug (apply str (repeat 64 "a")))))
  (let [problems (errors/explain->problems ::schema/slug "A")]
    (is (= :pattern (:code (first problems))))
    (is (re-find #"lowercase" (:message (first problems))))))

(deftest cidr-grammar
  (testing "accepted addresses"
    (is (schema/cidr? "10.0.0.0/8"))
    (is (schema/cidr? "0.0.0.0/0"))
    (is (schema/cidr? "255.255.255.255/32"))
    (is (schema/cidr? "::/128"))
    (is (schema/cidr? "::1/128"))
    (is (schema/cidr? "1::/64"))
    (is (schema/cidr? "2001:db8::1/64")))
  (testing "rejected addresses"
    (is (not (schema/cidr? "10.0.0.0/33")))
    (is (not (schema/cidr? "127.00.0.1/32")))
    (is (not (schema/cidr? ":::1/128")))
    (is (not (schema/cidr? ":::/128")))
    (is (not (schema/cidr? "10.0.0.0")))
    (is (not (schema/cidr? "not-a-cidr"))))
  (let [problems (errors/explain->problems ::schema/cidr "nope")]
    (is (= :pattern (:code (first problems))))))

(deftest http-url-grammar
  (is (schema/http-url? "https://example.com"))
  (is (schema/http-url? "http://example.com:8080/v1"))
  (is (not (schema/http-url? "ftp://example.com")))
  (is (not (schema/http-url? "https://example.com:99999/v1")))
  (is (not (schema/http-url? "https://example.com:0/x")))
  (let [problems (errors/explain->problems
                  ::schema/http-url
                  "ftp://example.com")]
    (is (= :pattern (:code (first problems))))))

(deftest header-name-grammar
  (is (schema/header-name? "X-BeFive-Subject"))
  (is (not (schema/header-name? "Bad Header")))
  (is (not (schema/header-name? ""))))

(deftest duration-range
  (is (s/valid? ::schema/duration 0))
  (is (s/valid? ::schema/duration 3600000))
  (is (not (s/valid? ::schema/duration -1)))
  (is (not (s/valid? ::schema/duration 3600001)))
  (let [problems (errors/explain->problems ::schema/duration 3600001)]
    (is (= :range (:code (first problems))))
    (is (re-find #"3600000" (:message (first problems))))))

(deftest explain-is-nil-for-a-valid-value
  (is (nil? (errors/explain->problems ::schema/slug "ok")))
  (is (seq (errors/explain->problems ::schema/slug 1))))

(deftest closed-map-rejects-unknown-keys
  (is (s/valid? ::sample {:slug "ab" :duration 10}))
  (let [explained (s/explain-data ::sample {:slug "ab" :nope 1})
        problems (errors/explain->problems
                  ::sample
                  {:slug "ab" :nope 1})]
    (is (some #(and (= [:nope] (:path %))
                    (= :closed (:code %)))
              problems)
        (str (pr-str problems) " " (pr-str explained))))
  (is (s/valid? ::prefixed {:slug "a" :x-extra 1}))
  (is (not (s/valid? ::prefixed {:other 1}))))

(deftest keys-of-reads-unqualified-names
  (is (= #{:slug :duration} (schema/keys-of ::sample))))

(deftest ^:generative generators-match-their-specs
  (passes? ::schema/slug (s/gen ::schema/slug))
  (passes? ::schema/cidr (s/gen ::schema/cidr))
  (passes? ::schema/http-url (s/gen ::schema/http-url))
  (passes? ::schema/header-name (s/gen ::schema/header-name))
  (passes? ::schema/duration (s/gen ::schema/duration)))
