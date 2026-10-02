(ns befive.core.healthcheck
  "Process-exit probe used by the image health check."
  (:import (java.net URI)
           (java.net.http HttpClient HttpRequest HttpResponse
                          HttpResponse$BodyHandlers)
           (java.time Duration)))

(set! *warn-on-reflection* true)

(defn exit-code
  "Return 0 when `path` on the ops port responds with HTTP 200."
  [port path]
  (let [^HttpClient client (HttpClient/newHttpClient)
        uri (URI/create (str "http://127.0.0.1:" port path))
        ^HttpRequest request (-> (HttpRequest/newBuilder uri)
                                 (.timeout (Duration/ofSeconds 2))
                                 (.GET)
                                 (.build))
        ^HttpResponse response (.send client
                                      request
                                      (HttpResponse$BodyHandlers/discarding))]
    (if (= 200 (.statusCode response)) 0 1)))
