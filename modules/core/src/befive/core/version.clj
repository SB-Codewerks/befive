(ns befive.core.version
  "Build manifest. The image build overwrites version.edn."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)

(def fallback
  {:version "0.1.0-SNAPSHOT"
   :git-sha "unknown"
   :build-date "unknown"})

(defn info
  []
  (if-let [resource (io/resource "befive/version.edn")]
    (merge fallback (edn/read-string (slurp resource)))
    fallback))
