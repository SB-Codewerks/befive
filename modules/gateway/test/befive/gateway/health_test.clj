(ns befive.gateway.health-test
  (:require [aleph.http :as http]
            [aleph.netty :as netty]
            [befive.gateway.health :as health]
            [befive.gateway.targets :as targets]
            [clojure.test :refer [deftest is]])
  (:import (java.net.http HttpClient)))

(defn- upstream
  [id url health]
  {:id id
   :kind :http
   :balance :round-robin
   :panic-threshold 0.0
   :targets [{:id url :url url :weight 1}]
   :health health})

(deftest active-probes-eject-and-restore
  (let [server (http/start-server (fn [_] {:status 200 :body "ok"})
                                  {:port 0 :join? false})
        port (netty/port server)
        url (str "http://127.0.0.1:" port)
        bad "http://127.0.0.1:1"
        targets-atom (atom {})
        client (HttpClient/newHttpClient)]
    (try
      (targets/reconcile!
       targets-atom
       {"ok" (upstream "ok" url {:interval-ms 500 :path "/"
                                 :unhealthy-after 1})
        "bad" (upstream "bad" bad {:interval-ms 500 :unhealthy-after 1})})
      (health/sweep! client targets-atom (System/currentTimeMillis))
      (is (:healthy? (first (:targets (targets/entry targets-atom "ok")))))
      (is (not (:healthy?
                (first (:targets (targets/entry targets-atom "bad"))))))
      (is (nil? (targets/pick! targets-atom "bad" "/")))
      (finally
        (.close server)))))

(deftest passive-ejection-skips-a-target-after-the-threshold
  (let [targets-atom (atom {})
        url "http://127.0.0.1:9"]
    (targets/reconcile!
     targets-atom
     {"u" (upstream "u" url {:unhealthy-after 2})})
    (targets/note-failure! targets-atom "u" url 2)
    (is (some? (targets/pick! targets-atom "u" "/")))
    (targets/release! targets-atom "u" url)
    (targets/note-failure! targets-atom "u" url 2)
    (is (nil? (targets/pick! targets-atom "u" "/")))))
