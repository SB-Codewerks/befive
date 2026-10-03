(ns befive.gateway.fdef-test
  "Instrument the proxy fdefs and check the pure functions they cover."
  (:require [befive.gateway.headers :as headers]
            [befive.gateway.limits :as limits]
            [befive.gateway.net :as net]
            [befive.gateway.sync :as sync]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer [deftest is]]))

(def instrumented
  `[limits/effective
    headers/smuggling-reason
    sync/contiguous?
    sync/merge-delta
    net/contains-cidr?])

(defn- with-fdefs
  [f]
  (stest/instrument instrumented)
  (try
    (f)
    (finally
      (stest/unstrument instrumented))))

(deftest effective-limit-is-the-minimum-present
  (with-fdefs
    (fn []
      (is (= {:max-request-bytes 10
              :max-response-bytes nil
              :max-header-bytes 4}
             (limits/effective
              {:limits {:max-request-bytes 10
                        :max-header-bytes 8}
               :apis {"orders" {:max-header-bytes 4}}}
              {:api "orders"
               :limits {:max-request-bytes 50}})))
      (is (= {:max-request-bytes nil
              :max-response-bytes nil
              :max-header-bytes nil}
             (limits/effective {} nil))))))

(deftest smuggling-reason-rejects-mixed-length-and-encoding
  (with-fdefs
    (fn []
      (is (nil? (headers/smuggling-reason {"content-length" "12"})))
      (is (= "request.malformed"
             (headers/smuggling-reason
              {"content-length" "1"
               "transfer-encoding" "chunked"}))))))

(deftest contiguous-deltas-merge-and-gaps-do-not
  (with-fdefs
    (fn []
      (is (sync/contiguous? 3 [4 5 6]))
      (is (not (sync/contiguous? 3 [4 6])))
      (is (not (sync/contiguous? 0 [])))
      (is (= {:revision 2
              :routes [{:id "b"}]}
             (sync/merge-delta
              {:revision 1 :routes [{:id "a"}]}
              [{:revision 2
                :entity "route"
                :entity-id "a"
                :after nil}
               {:revision 2
                :entity "route"
                :entity-id "b"
                :after {:id "b"}}]
              2)))
      (is (nil? (sync/merge-delta
                 {:revision 1 :routes []}
                 [{:revision 3
                   :entity "limits"
                   :after {:max-request-bytes 1}}]
                 3))))))

(deftest cidr-membership-accepts-literals-only
  (with-fdefs
    (fn []
      (is (true? (net/contains-cidr? "10.0.0.0/8" "10.1.2.3")))
      (is (not (net/contains-cidr? "10.0.0.0/8" "11.0.0.1")))
      (is (nil? (net/contains-cidr? "not-a-cidr" "10.0.0.1")))
      (is (true? (net/contains-cidr? "2001:db8::/32" "2001:db8::1"))))))
