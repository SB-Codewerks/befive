(ns befive.app.ops-test
  (:require [befive.app.ops :as ops]
            [befive.core.json :as json]
            [clojure.test :refer [deftest is]]))

(defn- body
  [response]
  (json/read-str (:body response)))

(deftest healthz-does-not-read-the-snapshot
  (let [response (ops/handle {:snapshot nil :info {}} {:uri "/healthz"})]
    (is (= 200 (:status response)))
    (is (= {:status "ok"} (body response)))
    (is (= "no-store" (get-in response [:headers "cache-control"])))))

(deftest readyz-reports-the-three-checks
  (let [down (ops/handle {:snapshot {:database :down
                                     :config :not-loaded
                                     :listeners :bound}
                          :info {}}
                         {:uri "/readyz"})
        up (ops/handle {:snapshot {:database :up
                                   :config :loaded
                                   :listeners :bound}
                        :info {}}
                       {:uri "/readyz"})]
    (is (= 503 (:status down)))
    (is (= {:database "down"
            :config "not-loaded"
            :listeners "bound"}
           (:checks (body down))))
    (is (= 200 (:status up)))
    (is (= {:status "ready"} (body up)))))

(deftest info-returns-only-the-supplied-map
  (let [info {:version "0.1.0-SNAPSHOT"
              :role :gateway
              :node-id "n1"
              :environment "dev"}
        response (ops/handle {:snapshot {} :info info}
                             {:uri "/internal/info"})]
    (is (= 200 (:status response)))
    (is (= {:version "0.1.0-SNAPSHOT"
            :role "gateway"
            :node-id "n1"
            :environment "dev"}
           (body response)))))

(deftest other-paths-are-not-found
  (let [response (ops/handle {:snapshot {} :info {}} {:uri "/admin"})
        payload (body response)]
    (is (= 404 (:status response)))
    (is (= "not-found" (:error payload)))
    (is (string? (:request-id payload)))))
