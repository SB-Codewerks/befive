(ns befive.core.server
  "Start and stop an Aleph listener."
  (:require [aleph.http :as http]
            [aleph.netty :as netty])
  (:import (java.io Closeable)))

(set! *warn-on-reflection* true)

(defn start
  "Bind `handler` on `port`. Port 0 asks the OS for a free port."
  [handler port]
  (let [server (http/start-server handler {:port port :join? false})]
    {:server server
     :port (netty/port server)}))

(defn stop
  [component]
  (when-let [server (:server component)]
    (.close ^Closeable server)))
