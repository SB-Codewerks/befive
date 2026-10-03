(ns befive.gateway.net-test
  (:require [befive.gateway.client-ip :as client-ip]
            [befive.gateway.net :as net]
            [clojure.test :refer [deftest is]]))

(deftest cidr-membership-rejects-leading-zeros-and-mapped-ipv6
  (is (net/contains-cidr? "10.0.0.0/8" "10.1.2.3"))
  (is (not (net/contains-cidr? "10.0.0.0/8" "11.0.0.1")))
  (is (not (net/contains-cidr? "10.0.0.0/8" "010.0.0.1")))
  (is (net/contains-cidr? "2001:db8::/32" "2001:db8::1"))
  (is (net/contains-cidr? "::1/128" "::1"))
  (is (not (net/contains-cidr? "::/0" "::ffff:10.0.0.1")))
  (is (net/trusted? ["192.168.0.0/16"] "192.168.1.9"))
  (is (not (net/trusted? ["192.168.0.0/16"] "10.0.0.1"))))

(deftest forwarded-for-walks-from-the-right
  (let [trusted ["10.0.0.0/8"]
        headers {"x-forwarded-for" "203.0.113.9, 10.1.1.1"}]
    (is (= "203.0.113.9"
           (client-ip/client-ip headers "10.0.0.4" trusted)))
    (is (= "203.0.113.4"
           (client-ip/client-ip headers "203.0.113.4" trusted)))
    (is (= "203.0.113.9, 10.1.1.1, 203.0.113.9"
           (client-ip/forwarded-for headers "203.0.113.9")))))
