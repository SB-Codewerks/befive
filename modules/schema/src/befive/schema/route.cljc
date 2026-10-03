(ns befive.schema.route
  "Route and upstream specs for the proxy compiler.
  Feature level 1. Documents keep unqualified keys."
  (:require [befive.schema.core :as schema]
            [befive.schema.meta :as meta]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(s/def ::id ::schema/non-blank-string)
(s/def ::method
  #{:get :head :post :put :patch :delete :options :trace})
(s/def ::methods
  (s/coll-of ::method :kind set? :min-count 1))
(s/def ::path
  (s/and string?
         #(str/starts-with? % "/")
         #(<= (count %) 512)))
(s/def ::host ::schema/non-blank-string)
(s/def ::priority int?)
(s/def :befive.schema.route.ref/upstream ::schema/non-blank-string)
(s/def ::api ::schema/slug)
(s/def ::api-version ::schema/slug)
(s/def ::operation ::schema/slug)
(s/def ::deprecated boolean?)
(s/def ::lifecycle #{:active :deprecated :retired})
(s/def ::deprecation ::schema/non-blank-string)
(s/def ::sunset ::schema/non-blank-string)
(s/def ::link
  (s/and string? #(not (str/blank? %)) #(<= (count %) 4600)))
(s/def ::vary ::schema/non-blank-string)
(s/def ::version-selected-by
  #{:path :host :header :query :media-type :default})
(s/def ::upstream-path ::path)
(s/def ::strip-query ::schema/non-blank-string)
(s/def ::strategy #{:path :host :header :media-type :query})
(s/def ::token ::schema/slug)
(s/def ::default boolean?)
(s/def ::header ::schema/header-name)
(s/def ::query ::schema/non-blank-string)
(s/def ::media ::schema/non-blank-string)
(s/def ::forward boolean?)
(s/def ::aliases (s/map-of ::schema/non-blank-string ::schema/slug))
(s/def ::group ::schema/non-blank-string)
(s/def ::version-select
  (s/and (s/keys :req-un [::strategy]
                 :opt-un [::group ::token ::default ::header ::query
                          ::media ::forward ::aliases ::vary])
         (schema/closed-spec ::version-select {})))
(s/def ::max-request-bytes (s/int-in 1 67108865))
(s/def ::max-response-bytes (s/int-in 1 67108865))
(s/def ::max-header-bytes (s/int-in 1 1048577))

(s/def ::limits
  (s/and (s/keys :opt-un [::max-request-bytes ::max-response-bytes
                          ::max-header-bytes])
         (schema/closed-spec ::limits {})))

(s/def ::route
  (s/and (s/keys :req-un [::id ::methods ::path
                          :befive.schema.route.ref/upstream]
                 :opt-un [::host ::priority ::api ::api-version
                          ::operation ::deprecated ::lifecycle
                          ::deprecation ::sunset ::link ::vary
                          ::strip-query ::version-select ::limits
                          ::version-selected-by ::upstream-path])
         (schema/closed-spec ::route {})))

(s/def ::url ::schema/http-url)
(s/def ::weight (s/int-in 1 10001))
(s/def ::target
  (s/and (s/keys :req-un [::url] :opt-un [::id ::weight])
         (schema/closed-spec ::target {})))
(s/def ::targets (s/coll-of ::target :kind vector? :min-count 1))
(s/def ::kind #{:http :lambda})
(s/def ::balance #{:round-robin :least-requests :consistent-hash})
(defn- unit-fraction?
  "A finite double from 0 through 1."
  [value]
  (and (number? value)
       #?(:clj (Double/isFinite (double value))
          :cljs (js/isFinite value))
       (<= 0.0 (double value) 1.0)))

(s/def ::panic-threshold unit-fraction?)
(s/def ::max-retries (s/int-in 0 9))
(s/def ::connect-timeout-ms (s/int-in 1 120001))
(s/def ::read-timeout-ms (s/int-in 1 300001))
(s/def ::idle-timeout-ms (s/int-in 1 600001))
(s/def ::max-connections (s/int-in 1 10001))
(s/def ::interval-ms (s/int-in 1 300001))
(s/def ::unhealthy-after (s/int-in 1 101))
(s/def ::health
  (s/and (s/keys :opt-un [::interval-ms ::path ::unhealthy-after])
         (schema/closed-spec ::health {})))
(s/def ::endpoint ::schema/http-url)
(s/def ::function ::schema/non-blank-string)
(s/def ::qualifier ::schema/non-blank-string)
(s/def ::region ::schema/non-blank-string)
(s/def ::payload-format #{"2.0"})
(s/def ::timeout-ms (s/int-in 1 900001))
(s/def ::max-concurrency (s/int-in 1 10001))
(s/def ::max-pending (s/int-in 0 100001))
(s/def ::binary-media-types
  (s/coll-of ::schema/non-blank-string :kind set?))
(s/def ::lambda
  (s/and (s/keys :req-un [::function ::region]
                 :opt-un [::qualifier ::payload-format ::timeout-ms
                          ::max-concurrency ::max-pending
                          ::binary-media-types ::endpoint])
         (schema/closed-spec ::lambda {})))

(s/def ::retry-methods
  (s/coll-of ::method :kind set?))

(s/def ::upstream
  (s/and (s/keys :req-un [::id ::kind]
                 :opt-un [::balance ::panic-threshold ::max-retries
                          ::retry-methods
                          ::connect-timeout-ms ::read-timeout-ms
                          ::idle-timeout-ms ::max-connections
                          ::targets ::health ::lambda])
         (schema/closed-spec ::upstream {})))

(s/def ::revision (s/int-in 0 1000000001))
(s/def ::routes (s/coll-of ::route :kind vector?))
(s/def ::upstreams (s/coll-of ::upstream :kind vector?))
(s/def ::apis (s/map-of ::api ::limits))
(s/def ::trusted-proxies (s/coll-of ::schema/cidr :kind vector?))

(s/def ::snapshot
  (s/and (s/keys :req-un [::revision ::routes ::upstreams]
                 :opt-un [::limits ::apis ::trusted-proxies])
         (schema/closed-spec ::snapshot {})))

(meta/register! ::path
  {:title "Path template"
   :help "Starts with /. Parameters are :name. A trailing /* is a splat."
   :since 1})

(meta/register! ::balance
  {:default :round-robin
   :title "Load balancing"
   :widget :select
   :since 1
   :enum-labels {:round-robin "Round robin"
                 :least-requests "Least requests"
                 :consistent-hash "Consistent hash"}})
