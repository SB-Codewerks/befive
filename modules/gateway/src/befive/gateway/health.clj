(ns befive.gateway.health
  "Active HTTP probes for upstreams that set `:health`.
  Probes run on a scheduler thread, never on the Netty event loop."
  (:require [befive.gateway.targets :as targets]
            [clojure.string :as str])
  (:import (java.net URI)
           (java.net.http HttpClient HttpClient$Redirect HttpRequest
                          HttpResponse$BodyHandlers)
           (java.time Duration)
           (java.util.concurrent Executors ScheduledExecutorService
                                 TimeUnit)))

(set! *warn-on-reflection* true)

(defn probe-url
  [target-url path]
  (let [base (str/replace (str target-url) #"/+$" "")
        path (if (str/blank? path) "/" path)]
    (str base (if (str/starts-with? path "/") path (str "/" path)))))

(defn- client
  []
  (-> (HttpClient/newBuilder)
      (.connectTimeout (Duration/ofMillis 2000))
      (.followRedirects HttpClient$Redirect/NEVER)
      (.build)))

(defn probe
  "True when the probe returns a 2xx status."
  [^HttpClient http url timeout-ms]
  (let [request (-> (HttpRequest/newBuilder)
                    (.uri (URI/create url))
                    (.timeout (Duration/ofMillis (long timeout-ms)))
                    (.GET)
                    (.build))
        response (.send http request (HttpResponse$BodyHandlers/discarding))
        status (.statusCode response)]
    (<= 200 status 299)))

(defn- due?
  [target now]
  (let [health (:health target)
        interval (:interval-ms health)]
    (and interval
         (>= now (+ (:probed-at target 0) (long interval))))))

(defn sweep!
  "Probe every due target. Failures count toward ejection."
  [http targets-atom now]
  (doseq [[upstream-id entry] @targets-atom
          target (:targets entry)
          :when (due? target now)]
    (let [health (:health target)
          url (probe-url (:url target) (:path health "/"))
          timeout (min 2000 (long (:interval-ms health 2000)))
          ok? (try
                (probe http url timeout)
                (catch Exception _
                  false))]
      (if ok?
        (targets/note-success! targets-atom upstream-id (:id target))
        (targets/note-failure! targets-atom upstream-id (:id target)
                               (:unhealthy-after health 2))))))

(defn start
  "Probe once a second. Stop with `:stop!`."
  [targets-atom]
  (let [^ScheduledExecutorService scheduler
        (Executors/newScheduledThreadPool 1)
        http (client)]
    (.scheduleWithFixedDelay
     scheduler
     (fn []
       (try
         (sweep! http targets-atom (System/currentTimeMillis))
         (catch Throwable _
           nil)))
     1 1 TimeUnit/SECONDS)
    {:stop! (fn []
              (.shutdownNow scheduler))}))
