(ns befive.gateway.limits-test
  (:require [befive.gateway.headers :as headers]
            [befive.gateway.limits :as limits]
            [clojure.test :refer [deftest is]]))

(deftest the-minimum-set-value-wins
  (is (= {:max-request-bytes 10
          :max-response-bytes 20
          :max-header-bytes 30}
         (limits/effective
          {:limits {:max-request-bytes 10 :max-response-bytes 50}
           :apis {"orders" {:max-response-bytes 20
                            :max-header-bytes 80}}}
          {:api "orders"
           :limits {:max-header-bytes 30
                    :max-request-bytes 100}}))))

(deftest an-absent-cap-does-not-raise-another
  (is (nil? (:max-request-bytes
             (limits/effective {:limits {}} {:limits {}})))))

(deftest smuggling-and-size-problems
  (is (= ["bad_request" "request.malformed"]
         (limits/request-problem
          {"content-length" "1, 2"}
          {})))
  (is (= ["bad_request" "request.malformed"]
         (limits/request-problem
          {"content-length" "8" "transfer-encoding" "chunked"}
          {})))
  (is (= ["bad_request" "request.malformed"]
         (limits/request-problem
          {"transfer-encoding" "gzip"}
          {})))
  (is (nil? (headers/smuggling-reason
             {"transfer-encoding" "Chunked"})))
  (is (= ["payload_too_large" "limits.headers_too_large"]
         (limits/request-problem
          {"x-big" "hello"}
          {:max-header-bytes 1})))
  (is (= ["payload_too_large" "limits.body_too_large"]
         (limits/request-problem
          {"content-length" "100"}
          {:max-request-bytes 10})))
  (is (limits/response-too-large?
       {"content-length" "100"}
       {:max-response-bytes 10})))

(deftest hop-by-hop-and-identity-headers-are-removed
  (let [headers {"Host" "api.example.com"
                 "Connection" "close, x-custom"
                 "X-Custom" "1"
                 "Keep-Alive" "timeout=5"
                 "X-BeFive-Subject" "forged"
                 "x-befive-identity" "forged"}
        stripped (-> headers
                     headers/strip-hop-by-hop
                     headers/strip-identity)]
    (is (= {"host" "api.example.com"}
           (update-keys stripped headers/header-name)))
    (is (nil? (headers/header stripped "x-custom")))
    (is (nil? (headers/header stripped "x-befive-subject")))))
