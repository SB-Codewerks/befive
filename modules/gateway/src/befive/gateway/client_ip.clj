(ns befive.gateway.client-ip
  "Client address from the socket, or from X-Forwarded-For when the
  peer is a trusted proxy. The walk starts at the right."
  (:require [befive.gateway.headers :as headers]
            [befive.gateway.net :as net]
            [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn- trusted?
  [trusted peer]
  (net/trusted? trusted peer))

(defn client-ip
  "The address BeFive will log and forward."
  [headers remote-addr trusted]
  (let [peer (some-> remote-addr str)
        forwarded (->> (headers/values-of headers "x-forwarded-for")
                       (map str/trim)
                       (remove str/blank?)
                       vec)]
    (if (trusted? trusted peer)
      (or (some (fn [addr] (when-not (trusted? trusted addr) addr))
                (reverse forwarded))
          peer)
      peer)))

(defn forwarded-for
  "X-Forwarded-For value to send upstream, with `client` appended."
  [headers client]
  (let [existing (->> (headers/values-of headers "x-forwarded-for")
                      (map str/trim)
                      (remove str/blank?))]
    (str/join ", " (concat existing (when client [client])))))
