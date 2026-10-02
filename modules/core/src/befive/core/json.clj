(ns befive.core.json
  "JSON encoding for ops and error bodies."
  (:require [jsonista.core :as j]))

(set! *warn-on-reflection* true)

(def mapper
  (j/object-mapper {:encode-key-fn true
                    :decode-key-fn true}))

(defn write-str
  [value]
  (j/write-value-as-string value mapper))

(defn read-str
  [^String text]
  (j/read-value text mapper))
