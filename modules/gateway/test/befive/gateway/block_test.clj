(ns befive.gateway.block-test
  (:require [befive.gateway.block :as block]
            [befive.gateway.compile :as compile]
            [befive.gateway.pipeline :as pipeline]
            [clojure.test :refer [deftest is]])
  (:import (io.netty.util.concurrent FastThreadLocalThread)))

(deftest note-records-only-the-event-loop-and-off-loop-leaves-it
  (block/reset-violations!)
  (let [seen (promise)
        thread (FastThreadLocalThread.
                ^Runnable
                (fn []
                  (block/reset-violations!)
                  (block/note! "on-loop")
                  (let [worker (promise)
                        value @(block/off-loop
                                (fn []
                                  (deliver worker
                                           (.getName (Thread/currentThread)))
                                  :done))]
                    (deliver seen {:value value
                                   :worker @worker
                                   :violations @block/violations
                                   :loop (.getName (Thread/currentThread))}))))]
    (.start thread)
    (.join thread 2000)
    (let [result (deref seen 500 ::timeout)]
      (is (= :done (:value result)))
      (is (= ["on-loop"] (map :label (:violations result))))
      (is (not= (:loop result) (:worker result))))))

(deftest a-not-found-on-the-event-loop-records-no-violation
  (let [seen (promise)
        state {:settings {:node-id "n" :environment "dev"}
               :table (atom (compile/empty-table))
               :targets (atom {})
               :pools (atom {})
               :lambda-clients (atom {})
               :ready-revision (atom 0)
               :access-log nil
               :datasource nil}
        thread (FastThreadLocalThread.
                ^Runnable
                (fn []
                  (block/reset-violations!)
                  (let [response @(pipeline/handle
                                   state
                                   {:request-method :get
                                    :uri "/"
                                    :scheme :http
                                    :remote-addr "203.0.113.8"
                                    :headers {}})]
                    (deliver seen {:status (:status response)
                                   :violations @block/violations}))))]
    (.start thread)
    (.join thread 2000)
    (let [result (deref seen 500 ::timeout)]
      (is (= 404 (:status result)))
      (is (= [] (:violations result))))))
