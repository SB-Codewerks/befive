(ns befive.core.server
  "Start and stop an Aleph listener."
  (:require [aleph.http :as http]
            [aleph.netty :as netty])
  (:import (java.io Closeable)))

(set! *warn-on-reflection* true)

(defn start
  "Bind `handler` on `port`. Port 0 asks the OS for a free port.
  `options` are passed to Aleph. The gateway uses `:executor :none`
  so its handler stays on the event loop and returns a deferred."
  ([handler port]
   (start handler port {}))
  ([handler port options]
   (let [server (http/start-server
                 handler
                 (merge {:port port :join? false} options))]
     {:server server
      :port (netty/port server)})))

(defn stop
  [component]
  (when-let [server (:server component)]
    (.close ^Closeable server)))
