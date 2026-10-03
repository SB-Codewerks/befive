(ns befive.openapi.parse
  "Read OpenAPI YAML or JSON. Remote $refs are rewritten to a local
  marker before the parser runs, so the parser does not fetch them."
  (:require [clojure.string :as str])
  (:import (com.fasterxml.jackson.databind JsonNode ObjectMapper)
           (io.swagger.v3.core.util Json)
           (io.swagger.v3.parser OpenAPIV3Parser)
           (io.swagger.v3.parser.core.models ParseOptions)
           (java.util Map$Entry)))

(set! *warn-on-reflection* true)

(defn- clj-key
  "Path templates stay strings. A keyword cannot keep a leading slash."
  [^String text]
  (if (str/includes? text "/")
    text
    (keyword text)))

(defn- node->clj
  [^JsonNode node]
  (cond
    (or (nil? node) (.isNull node)) nil
    (.isTextual node) (.textValue node)
    (.isIntegralNumber node) (.longValue node)
    (.isFloatingPointNumber node) (.doubleValue node)
    (.isBoolean node) (.booleanValue node)
    (.isArray node)
    (mapv node->clj (iterator-seq (.elements node)))
    (.isObject node)
    (into {}
          (map (fn [^Map$Entry entry]
                 [(clj-key (.getKey entry))
                  (node->clj ^JsonNode (.getValue entry))]))
          (iterator-seq (.fields node)))
    :else nil))

(defn- model->clj
  [model]
  (let [^ObjectMapper mapper (Json/mapper)
        text (.writeValueAsString mapper model)]
    (node->clj (.readTree mapper text))))

(defn- external-ref?
  [value]
  (let [value (str/trim value)]
    (and (not (str/blank? value))
         (not (str/starts-with? value "#")))))

(defn disarm-remote-refs
  "Replace $ref values that are not JSON pointers inside the document."
  [text]
  (let [found (atom [])
        replaced (str/replace
                  text
                  #"(\$ref\s*:\s*['\"]?)([^'\"\n\r,}]+)(['\"]?)"
                  (fn [[whole prefix value suffix]]
                    (if (external-ref? value)
                      (do (swap! found conj (str/trim value))
                          (str prefix
                               "#/components/schemas/RemoteDisabled"
                               suffix))
                      whole)))]
    {:text replaced :refs (vec (distinct @found))}))

(defn- format-of
  [text]
  (if (str/starts-with? (str/trim text) "{") :json :yaml))

(defn parse-text
  "Parse `text` into a keywordized OpenAPI map.
  `:refs` lists remote references that were not fetched."
  [^String text]
  (let [{:keys [text refs]} (disarm-remote-refs text)
        options (ParseOptions.)
        _ (.setResolve options false)
        _ (.setResolveFully options false)
        result (.readContents (OpenAPIV3Parser.) text nil options)
        model (.getOpenAPI result)
        messages (vec (or (.getMessages result) []))]
    (when (nil? model)
      (throw (ex-info "OpenAPI document rejected"
                      {:messages messages :refs refs})))
    {:document (model->clj model)
     :messages messages
     :refs refs
     :format (format-of text)}))
