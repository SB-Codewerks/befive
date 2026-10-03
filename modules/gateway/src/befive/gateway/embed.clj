(ns befive.gateway.embed
  "In-process pipeline entry for the route tester.
  Control-plane code may depend on this namespace and on no other
  gateway namespace."
  (:require [befive.gateway.access-log :as access-log]
            [befive.gateway.compile :as compile]
            [befive.gateway.http :as http]
            [befive.gateway.pipeline :as pipeline]
            [manifold.deferred :as d]))

(set! *warn-on-reflection* true)

(defn handle
  "Run one request through the pipeline. `opts` may hold `:table`
  (a compiled snapshot or a document) and `:state`. This blocks the
  caller, which is the control plane, not the Netty event loop."
  ([request]
   (handle {} request))
  ([opts request]
   (let [compiled (cond
                    (:routers (:table opts)) (:table opts)
                    (:table opts) (compile/compile-snapshot (:table opts))
                    :else (compile/empty-table))
         owned? (nil? (:state opts))
         state (or (:state opts)
                   (http/state {:node-id "embed"
                                :environment "dev"
                                :trusted-proxies []}))]
     (try
       (reset! (:table state) compiled)
       (let [result @(d/timeout! (pipeline/handle state request) 5000)]
         (if (map? result)
           result
           {:status 204 :body nil}))
       (finally
         (when owned?
           (access-log/stop! (:access-log state))))))))
