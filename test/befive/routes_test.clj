(ns befive.routes-test
  (:require [clojure.test :refer [deftest is testing]]
            [befive.routes :as sut]))

(deftest healthcheck-test
  (let [handler (sut/handler {})]
    (testing "GET /healthcheck returns 200 OK"
      (let [resp (handler {:request-method :get :uri "/healthcheck"})]
        (is (= 200 (:status resp)))
        (is (= "OK" (:body resp)))))

    (testing "unknown route falls through to 404"
      (let [resp (handler {:request-method :get :uri "/nope"})]
        (is (= 404 (:status resp)))))))
