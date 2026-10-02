(ns befive.schema.meta
  "Form-metadata registry: title, help, widget, secret, since and defaults."
  (:require [befive.schema.core :as schema]
            [befive.schema.errors :as errors]
            [clojure.spec.alpha :as s]))

(s/def ::title string?)
(s/def ::help string?)
(s/def ::widget keyword?)
(s/def ::secret boolean?)
(s/def ::min int?)
(s/def ::max int?)
(s/def ::since pos-int?)
(s/def ::default any?)
(s/def ::order int?)
(s/def ::enum-labels (s/map-of keyword? string?))

(s/def ::entry
  (s/and (s/keys :opt-un [::title ::help ::widget ::secret ::min ::max
                          ::since ::default ::order ::enum-labels])
         (schema/closed-spec ::entry {})))

(defonce registry (atom {}))

(defn all
  []
  @registry)

(defn lookup
  [k]
  (get @registry k))

(defn register!
  "Store metadata for spec keyword `k`. Throws when `entry` is not
  a closed metadata map."
  [k entry]
  (when-not (qualified-keyword? k)
    (throw (ex-info "metadata key must be a qualified keyword"
                    {:key k})))
  (when-let [problems (errors/explain->problems ::entry entry)]
    (throw (ex-info "invalid form metadata"
                    {:key k :problems problems})))
  (swap! registry assoc k entry)
  entry)

(s/fdef register!
  :args (s/cat :k any? :entry any?)
  :ret map?)
