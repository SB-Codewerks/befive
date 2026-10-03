(ns befive.gateway.block
  "Keep blocking work off the Netty event loop.
  `note!` records a violation when it runs on that loop. Proxy
  work goes through `off-loop`, which hops to a virtual thread
  first."
  (:require [manifold.deferred :as d])
  (:import (io.netty.util.concurrent FastThreadLocalThread)
           (java.util.concurrent ExecutorService Executors)))

(set! *warn-on-reflection* true)

(def violations (atom []))

(def ^ExecutorService executor
  (Executors/newVirtualThreadPerTaskExecutor))

(defn event-loop?
  []
  (instance? FastThreadLocalThread (Thread/currentThread)))

(defn note!
  "Record `label` when called on the event loop."
  [label]
  (when (event-loop?)
    (swap! violations conj {:label label :thread (.getName (Thread/currentThread))})))

(defn off-loop
  "Run `work` away from the event loop. `work` returns a plain value."
  [work]
  (if (event-loop?)
    (d/future-with executor (work))
    (d/success-deferred (work))))

(defn sleep
  "Wait `ms` milliseconds on a virtual thread."
  [ms]
  (d/future-with executor
    (Thread/sleep (long ms))
    true))

(defn reset-violations!
  []
  (reset! violations []))
