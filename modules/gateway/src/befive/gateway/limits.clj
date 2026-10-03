(ns befive.gateway.limits
  "Effective size limits are the minimum set at global, API and route.
  A route cannot raise a global cap."
  (:require [befive.gateway.headers :as headers]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn- min-present
  [limit-key layers]
  (let [values (keep limit-key layers)]
    (when (seq values)
      (apply min values))))

(defn effective
  "The tightest limit of each kind. Missing layers are ignored."
  [snapshot route]
  (let [layers (keep identity
                     [(:limits snapshot)
                      (when-let [api (:api route)]
                        (get (:apis snapshot) api))
                      (:limits route)])]
    {:max-request-bytes (min-present :max-request-bytes layers)
     :max-response-bytes (min-present :max-response-bytes layers)
     :max-header-bytes (min-present :max-header-bytes layers)}))

(s/fdef effective
  :args (s/cat :snapshot map? :route (s/nilable map?))
  :ret map?)

(defn- nbytes
  [value]
  (alength (.getBytes (str value) "UTF-8")))

(defn header-bytes
  "Bytes of the header block BeFive will enforce."
  [headers]
  (reduce (fn [total [header value]]
            (let [values (if (sequential? value) value [value])]
              (reduce (fn [total value]
                        (+ total (nbytes header) (nbytes value) 4))
                      total
                      values)))
          0
          headers))

(defn content-length
  "The single canonical Content-Length, or nil."
  [headers]
  (let [value (headers/header headers "content-length")
        text (some-> value str str/trim)]
    (when (and text (re-matches #"(?:0|[1-9][0-9]*)" text))
      (parse-long text))))

(defn request-problem
  "A `[error reason]` pair, or nil when the request fits."
  [headers limits]
  (let [length (content-length headers)
        header-cap (:max-header-bytes limits)
        body-cap (:max-request-bytes limits)]
    (cond
      (headers/smuggling-reason headers)
      ["bad_request" "request.malformed"]

      (and header-cap (> (header-bytes headers) header-cap))
      ["payload_too_large" "limits.headers_too_large"]

      (and body-cap length (> length body-cap))
      ["payload_too_large" "limits.body_too_large"]

      :else nil)))

(defn response-too-large?
  "True when a known response length exceeds the cap."
  [headers limits]
  (let [cap (:max-response-bytes limits)
        length (content-length headers)]
    (boolean (and cap length (> length cap)))))
