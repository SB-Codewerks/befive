(ns befive.gateway.embed
  "In-process pipeline entry for the route tester.
  The pipeline arrives in a later milestone. Control-plane code may
  depend on this namespace and on no other gateway namespace.")

(set! *warn-on-reflection* true)

(defn handle
  "Return a not-implemented result. The route tester calls this."
  [_request]
  {:status 501
   :body {:error "not-implemented"}})
