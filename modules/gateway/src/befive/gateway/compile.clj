(ns befive.gateway.compile
  "Pure snapshot compile. Failure throws and the caller keeps the
  previous revision. This namespace does not open pools."
  (:require [befive.gateway.router :as router]
            [befive.schema.compile :as versions]
            [befive.schema.errors :as errors]
            [befive.schema.route :as route]
            [clojure.string :as str]))

(set! *warn-on-reflection* true)

(def http-defaults
  {:balance :round-robin
   :panic-threshold 0.2
   :max-retries 1
   :connect-timeout-ms 3000
   :read-timeout-ms 30000
   :idle-timeout-ms 60000
   :max-connections 64})

(def lambda-defaults
  {:payload-format "2.0"
   :timeout-ms 29000
   :max-concurrency 100
   :max-pending 200})

(defn- method
  [value]
  (keyword (str/lower-case
            (if (keyword? value) (name value) (str value)))))

(defn- method-set
  [value]
  (into #{} (map method) (if (set? value) value (seq value))))

(defn- fill
  [defaults value]
  (merge defaults (into {} (remove (fn [[_ v]] (nil? v))) value)))

(defn- normalize-target
  [target]
  (fill {:weight 1
         :id (or (:id target) (:url target))}
        target))

(defn- normalize-route
  [route]
  (let [route (-> route
                  (update :methods method-set)
                  (assoc :priority (long (:priority route 0))
                         :lifecycle (:lifecycle route :active)))]
    (into {} (remove (fn [[_ v]] (nil? v))) route)))

(defn- normalize-upstream
  [upstream]
  (let [kind (:kind upstream)
        upstream (cond-> (fill (if (= kind :lambda) {} http-defaults)
                               upstream)
                   (:targets upstream)
                   (update :targets #(mapv normalize-target %))
                   (= kind :lambda)
                   (update :lambda #(fill lambda-defaults (or % {}))))]
    (into {} (remove (fn [[_ v]] (nil? v))) upstream)))

(defn normalize
  "Apply proxy defaults and coerce methods to keywords."
  [document]
  (let [document (-> document
                     (update :routes #(mapv normalize-route (or % [])))
                     (update :upstreams #(mapv normalize-upstream (or % []))))]
    (into {} (remove (fn [[_ v]] (nil? v))) document)))

(defn compile-snapshot
  "Compile `document` or throw. The result is safe to publish.
  Domain records live under `:domain`. `:apis` stays the size-limit map."
  [document]
  (let [expanded (versions/expand (or document {}))
        groups (:version-groups expanded)
        domain (:domain expanded)
        document (normalize (dissoc expanded :version-groups :domain))]
    (when-let [problems (errors/explain->problems ::route/snapshot document)]
      (throw (ex-info "snapshot rejected"
                      {:problems problems})))
    (let [upstreams (into {} (map (juxt :id identity)) (:upstreams document))
          missing (into []
                        (comp (remove #(contains? upstreams (:upstream %)))
                              (map :id))
                        (:routes document))]
      (when (seq missing)
        (throw (ex-info "route upstream is missing"
                        {:routes missing})))
      (cond-> {:revision (:revision document)
               :routers (router/compile-routes (:routes document))
               :upstreams upstreams
               :limits (:limits document)
               :apis (:apis document)
               :trusted-proxies (:trusted-proxies document)
               :routes (:routes document)
               :document document}
        domain (assoc :domain domain)
        (seq groups) (assoc :version-groups groups)))))

(defn empty-table
  []
  (compile-snapshot {:revision 0 :routes [] :upstreams []}))
