(ns befive.schema.defaults
  "Fill missing keys from the form-metadata registry.
  Pure and idempotent. Call it before validation."
  (:require [befive.schema.meta :as meta]
            [clojure.spec.alpha :as s]))

(declare apply-keys apply-coll)

(defn- op-name
  [form]
  (when (and (sequential? form) (symbol? (first form)))
    (name (first form))))

(defn- fill-form
  [form value registry depth]
  (if (or (nil? form) (> depth 32))
    value
    (case (op-name form)
      "keys" (apply-keys form value registry depth)
      "and" (reduce (fn [current sub]
                      (fill-form sub current registry (inc depth)))
                    value
                    (rest form))
      "merge" (reduce (fn [current sub]
                        (fill-form sub current registry (inc depth)))
                      value
                      (rest form))
      "coll-of" (apply-coll form value registry depth)
      (if (and (keyword? form) (s/get-spec form))
        (fill-form (s/form form) value registry (inc depth))
        value))))

(defn- fill-key
  [value spec-k map-k registry depth]
  (if (contains? value map-k)
    (assoc value map-k
           (fill-form spec-k (get value map-k) registry (inc depth)))
    (if-let [entry (get registry spec-k)]
      (if (contains? entry :default)
        (assoc value map-k
               (fill-form spec-k (:default entry) registry (inc depth)))
        value)
      value)))

(defn- apply-keys
  [form value registry depth]
  (if-not (map? value)
    value
    (let [opts (apply hash-map (rest form))
          value (reduce (fn [current spec-k]
                          (fill-key current spec-k (keyword (name spec-k))
                                    registry depth))
                        value
                        (:opt-un opts))
          value (reduce (fn [current spec-k]
                          (fill-key current spec-k spec-k registry depth))
                        value
                        (:opt opts))]
      (reduce (fn [current spec-k]
                (let [map-k (keyword (name spec-k))]
                  (if (contains? current map-k)
                    (fill-key current spec-k map-k registry depth)
                    current)))
              (reduce (fn [current spec-k]
                        (if (contains? current spec-k)
                          (fill-key current spec-k spec-k registry depth)
                          current))
                      value
                      (:req opts))
              (:req-un opts)))))

(defn- apply-coll
  [form value registry depth]
  (let [inner (second form)
        step #(fill-form inner % registry (inc depth))]
    (cond
      (vector? value) (mapv step value)
      (set? value) (into #{} (map step) value)
      (sequential? value) (mapv step value)
      :else value)))

(defn apply-defaults
  "Assoc missing optional keys that have a `:default` in `registry`.
  Walks nested `s/keys` and `s/coll-of` values. The two-arity form
  reads the metadata registry."
  ([spec value]
   (apply-defaults spec value (meta/all)))
  ([spec value registry]
   (fill-form (s/form spec) value registry 0)))

(s/fdef apply-defaults
  :args (s/cat :spec qualified-keyword?
               :value any?
               :registry (s/? map?))
  :ret any?)
