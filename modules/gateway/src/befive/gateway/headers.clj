(ns befive.gateway.headers
  "Hop-by-hop removal, inbound identity stripping, and smuggling checks."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(set! *warn-on-reflection* true)

(def hop-by-hop
  #{"connection" "keep-alive" "proxy-authenticate" "proxy-authorization"
    "te" "trailers" "transfer-encoding" "upgrade"})

(defn header-name
  [value]
  (str/lower-case (if (keyword? value) (name value) (str value))))

(defn values-of
  "Every value of header `wanted`, split if the server joined them."
  [headers wanted]
  (let [wanted (header-name wanted)]
    (mapcat (fn [[k v]]
              (when (= wanted (header-name k))
                (if (sequential? v)
                  (map str v)
                  (str/split (str v) #","))))
            headers)))

(defn smuggling-reason
  "A request-smuggling signal, or nil. `content-length` must be one
  canonical integer, and `transfer-encoding` must be exactly chunked."
  [headers]
  (let [lengths (map str/trim (values-of headers "content-length"))
        encodings (map str/trim (values-of headers "transfer-encoding"))
        length-ok? #(re-matches #"(?:0|[1-9][0-9]*)" %)]
    (cond
      (and (seq lengths) (seq encodings)) "request.malformed"
      (> (count lengths) 1) "request.malformed"
      (some (complement length-ok?) lengths) "request.malformed"
      (and (seq encodings)
           (not= ["chunked"]
                 (mapv #(str/lower-case %) encodings)))
      "request.malformed"
      :else nil)))

(s/fdef smuggling-reason
  :args (s/cat :headers (s/nilable map?))
  :ret (s/nilable string?))

(defn strip-hop-by-hop
  [headers]
  (let [listed (->> (values-of headers "connection")
                    (map #(str/lower-case (str/trim %)))
                    set)]
    (into {}
          (remove (fn [[k _v]]
                    (let [lowered (header-name k)]
                      (or (hop-by-hop lowered) (listed lowered)))))
          headers)))

(defn strip-identity
  "Drop inbound `X-BeFive-*` headers. The gateway writes its own."
  [headers]
  (into {}
        (remove (fn [[k _v]]
                  (str/starts-with? (header-name k) "x-befive-")))
        headers))

(defn header
  [headers wanted]
  (some (fn [[k v]]
          (when (= (header-name wanted) (header-name k))
            (if (sequential? v) (first v) v)))
        headers))
