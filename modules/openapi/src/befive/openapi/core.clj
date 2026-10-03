(ns befive.openapi.core
  "OpenAPI import. `parse-text` reads YAML or JSON. `import-document`
  maps a parsed document onto an API version. `apply-import` merges
  that result. Re-import uses `diff-operations` before apply."
  (:require [befive.openapi.import :as import]
            [befive.openapi.parse :as parse]))

(def parse-text parse/parse-text)

(def import-document import/import-document)

(def diff-operations import/diff-operations)

(def apply-import import/apply-import)

(defn import-text
  "Parse `text` and map it onto one API version."
  [text opts]
  (let [parsed (parse/parse-text text)]
    (assoc (import/import-document
            (:document parsed)
            (assoc opts
                   :text text
                   :format (:format parsed)
                   :remote-refs (:refs parsed)))
           :messages (:messages parsed))))
