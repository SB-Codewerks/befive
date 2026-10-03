(ns befive.schema.domain
  "API, version, operation and tenancy specs. Feature level 1.
  Documents keep unqualified keys. x- keys are annotations."
  (:require [befive.schema.core :as schema]
            [befive.schema.meta :as meta]
            [befive.schema.route :as route]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defn- bounded?
  [value floor ceiling]
  (and (string? value) (<= floor (count value) ceiling)))

(defn owner-text?
  [value]
  (bounded? value 1 256))

(defn owner?
  "One of `{:group ...}` or `{:user ...}`."
  [value]
  (and (map? value)
       (= 1 (count value))
       (or (owner-text? (:group value))
           (owner-text? (:user value)))))

(s/def ::id ::schema/slug)
(s/def ::name (s/and string? #(bounded? % 1 128)))
(s/def ::description (s/and string? #(bounded? % 0 8192)))
(s/def ::summary (s/and string? #(bounded? % 0 256)))
(s/def ::label (s/and string? #(bounded? % 0 64)))
(s/def ::classification ::schema/slug)
(s/def ::domain ::schema/slug)
(s/def ::title (s/and string? #(bounded? % 1 128)))
(s/def ::url ::schema/http-url)
(s/def ::template (s/and string? #(bounded? % 1 256)))
(s/def ::pattern ::template)
(s/def ::instant
  (s/and string?
         #(re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z" %)))
(s/def ::deprecated-at ::instant)
(s/def ::sunset-at ::instant)
(s/def ::link (s/and string? #(bounded? % 1 2048)))
(s/def ::sha256 (s/and string? #(re-matches #"[0-9a-f]{64}" %)))
(s/def ::tag
  (s/and string?
         #(re-matches #"^[a-z0-9][a-z0-9-_.:/]{0,62}$" %)))
(s/def ::tags (s/coll-of ::tag :kind set? :max-count 32))
(s/def ::strategy #{:path :host :header :media-type :query})
(s/def ::state #{:design :published :deprecated :retired})
(s/def ::kind #{:partner :internal})
(s/def ::status #{:active :suspended})
(s/def ::app-kind #{:service :user-facing})
(s/def ::environment-scope #{:production :sandbox})
(s/def ::credential-kind #{:api-key :oauth :mtls})
(s/def ::plan ::schema/slug)
(s/def ::default-version ::schema/slug)
(s/def ::allow-design-routes boolean?)
(s/def ::sandbox boolean?)
(s/def ::production boolean?)
(s/def ::flag boolean?)

(s/def :befive.schema.domain.ref/api ::schema/slug)
(s/def :befive.schema.domain.ref/version ::schema/slug)
(s/def :befive.schema.domain.ref/service ::schema/slug)
(s/def :befive.schema.domain.ref/organization ::schema/slug)
(s/def :befive.schema.domain.ref/consumer ::schema/slug)
(s/def :befive.schema.domain.ref/application ::schema/slug)
(s/def :befive.schema.domain.header/name ::schema/header-name)
(s/def :befive.schema.domain.query/name ::schema/non-blank-string)
(s/def :befive.schema.domain.flags/deprecation ::flag)
(s/def :befive.schema.domain.flags/sunset ::flag)
(s/def :befive.schema.domain.flags/link ::flag)
(s/def :befive.schema.domain.api/owners
  (s/coll-of owner? :kind vector? :min-count 1 :max-count 16))
(s/def :befive.schema.domain.app/owners
  (s/coll-of owner? :kind vector? :min-count 1 :max-count 8))
(s/def :befive.schema.domain.credential/id
  (s/and string? #(re-matches #"^[A-Za-z0-9_-]{1,80}$" %)))

(s/def ::doc
  (s/and (s/keys :req-un [::title ::url])
         (schema/closed-spec ::doc {:allow-prefix "x-"})))
(s/def ::docs (s/coll-of ::doc :kind vector? :max-count 16))
(s/def ::try-it
  (s/and (s/keys :opt-un [::sandbox ::production])
         (schema/closed-spec ::try-it {:allow-prefix "x-"})))
(s/def ::audience #{:public-portal :restricted})
(s/def ::groups
  (s/coll-of ::schema/non-blank-string :kind set? :max-count 32))
(s/def ::users
  (s/coll-of ::schema/non-blank-string :kind set? :max-count 32))
(s/def :befive.schema.domain.visibility/organizations
  (s/coll-of ::schema/slug :kind set? :max-count 32))
(s/def ::visibility
  (s/and (s/keys :req-un [::audience]
                 :opt-un [::groups ::users
                          :befive.schema.domain.visibility/organizations])
         (schema/closed-spec ::visibility {:allow-prefix "x-"})))
(s/def ::path
  (s/and (s/keys :req-un [::template])
         (schema/closed-spec ::path {:allow-prefix "x-"})))
(s/def ::host
  (s/and (s/keys :req-un [::template])
         (schema/closed-spec ::host {:allow-prefix "x-"})))
(s/def ::header
  (s/and (s/keys :req-un [:befive.schema.domain.header/name])
         (schema/closed-spec ::header {:allow-prefix "x-"})))
(s/def ::query
  (s/and (s/keys :req-un [:befive.schema.domain.query/name]
                 :opt-un [:befive.schema.route/forward])
         (schema/closed-spec ::query {:allow-prefix "x-"})))
(s/def ::media-type
  (s/and (s/keys :req-un [::pattern])
         (schema/closed-spec ::media-type {:allow-prefix "x-"})))
(s/def ::aliases
  (s/map-of ::schema/non-blank-string ::schema/slug))
(s/def ::versioning
  (s/and (s/keys :req-un [::strategy]
                 :opt-un [::path ::host ::header ::query ::media-type
                          ::default-version ::aliases])
         (schema/closed-spec ::versioning {:allow-prefix "x-"})))
(s/def ::headers
  (s/and (s/keys :opt-un [:befive.schema.domain.flags/deprecation
                          :befive.schema.domain.flags/sunset
                          :befive.schema.domain.flags/link])
         (schema/closed-spec ::headers {:allow-prefix "x-"})))
(s/def ::deprecation
  (s/and (s/keys :opt-un [::deprecated-at ::sunset-at ::link ::headers])
         (schema/closed-spec ::deprecation {:allow-prefix "x-"})))
(s/def ::spec
  (s/and (s/keys :req-un [::sha256])
         (schema/closed-spec ::spec {:allow-prefix "x-"})))
(s/def :befive.schema.domain.backend/kind #{:proxy})
(s/def :befive.schema.domain.backend/upstream-path ::route/path)
(s/def ::backend
  (s/and (s/keys :req-un [:befive.schema.domain.backend/kind]
                 :opt-un [:befive.schema.domain.backend/upstream-path])
         (schema/closed-spec ::backend {:allow-prefix "x-"})))
(s/def ::match ::versioning)

(s/def ::api
  (s/and (s/keys :req-un [::id ::name :befive.schema.domain.api/owners]
                 :opt-un [::domain ::description ::classification
                          ::versioning ::default-version ::visibility
                          ::docs ::try-it ::tags])
         (schema/closed-spec ::api {:allow-prefix "x-"})))

(s/def ::version
  (s/and (s/keys :req-un [:befive.schema.domain.ref/api ::id
                          :befive.schema.domain.ref/service]
                 :opt-un [::label ::state ::spec ::match ::versioning
                          ::deprecation ::classification
                          ::visibility ::tags])
         (schema/closed-spec ::version {:allow-prefix "x-"})))

(s/def ::operation
  (s/and (s/keys :req-un [:befive.schema.domain.ref/api
                          :befive.schema.domain.ref/version
                          ::id
                          :befive.schema.route/method
                          :befive.schema.route/path]
                 :opt-un [::summary ::backend ::deprecation ::tags])
         (schema/closed-spec ::operation {:allow-prefix "x-"})))

(s/def ::service
  (s/and (s/keys :req-un [::id ::name :befive.schema.route.ref/upstream]
                 :opt-un [::tags])
         (schema/closed-spec ::service {:allow-prefix "x-"})))

(s/def ::okta-groups ::groups)
(s/def ::email-domains ::groups)
(s/def ::portal
  (s/and (s/keys :opt-un [::okta-groups ::email-domains])
         (schema/closed-spec ::portal {:allow-prefix "x-"})))
(s/def ::organization
  (s/and (s/keys :req-un [::id ::name]
                 :opt-un [::kind ::plan ::portal ::status ::tags])
         (schema/closed-spec ::organization {:allow-prefix "x-"})))
(s/def ::consumer
  (s/and (s/keys :req-un [::id ::name
                          :befive.schema.domain.ref/organization]
                 :opt-un [::status ::tags])
         (schema/closed-spec ::consumer {:allow-prefix "x-"})))
(s/def ::application
  (s/and (s/keys :req-un [::id ::name
                          :befive.schema.domain.ref/consumer
                          :befive.schema.domain.app/owners]
                 :opt-un [:befive.schema.domain.app/kind
                          ::environment-scope ::plan ::status ::tags])
         (schema/closed-spec ::application {:allow-prefix "x-"})))

(s/def :befive.schema.domain.app/kind ::app-kind)

(s/def ::credential
  (s/and (s/keys :req-un [:befive.schema.domain.credential/id
                          :befive.schema.domain.ref/application]
                 :opt-un [:befive.schema.domain.credential/kind
                          :befive.schema.domain.ref/consumer
                          ::tags])
         (schema/closed-spec ::credential {:allow-prefix "x-"})))

(s/def :befive.schema.domain.credential/kind ::credential-kind)

(s/def ::apis (s/coll-of ::api :kind vector?))
(s/def ::versions (s/coll-of ::version :kind vector?))
(s/def ::operations (s/coll-of ::operation :kind vector?))
(s/def ::services (s/coll-of ::service :kind vector?))
(s/def ::organizations (s/coll-of ::organization :kind vector?))
(s/def ::consumers (s/coll-of ::consumer :kind vector?))
(s/def ::applications (s/coll-of ::application :kind vector?))
(s/def ::credentials (s/coll-of ::credential :kind vector?))

(s/def ::format #{:json :yaml})
(s/def ::openapi-version (s/and string? #(bounded? % 1 32)))
(s/def ::source #{:upload :url :cli :bundle})
(s/def ::normalized (s/and string? #(<= (count %) 2000000)))
(s/def ::api-spec
  (s/and (s/keys :req-un [::sha256 ::format ::openapi-version]
                 :opt-un [::source ::normalized])
         (schema/closed-spec ::api-spec {:allow-prefix "x-"})))
(s/def ::specs (s/coll-of ::api-spec :kind vector?))

(s/def ::level
  #{:global :environment :api :version :path :operation})
(s/def ::prefix ::route/path)
(s/def :befive.schema.domain.ref/operation ::schema/slug)
(s/def ::scope
  (s/and (s/keys :req-un [::level]
                 :opt-un [:befive.schema.domain.ref/api
                          :befive.schema.domain.ref/version
                          :befive.schema.domain.ref/operation
                          ::prefix])
         (schema/closed-spec ::scope {:allow-prefix "x-"})))
(s/def :befive.schema.domain.attachment/kind
  #{:security :ip :rate-limit :logging :cache :limits :deprecation})
(s/def ::value map?)
(s/def ::locked boolean?)
(s/def ::reason (s/and string? #(bounded? % 0 512)))
(s/def :befive.schema.domain.attachment/id
  (s/and string? #(re-matches #"[A-Za-z0-9_-]{1,80}" %)))
(s/def ::policy-attachment
  (s/and (s/keys :req-un [:befive.schema.domain.attachment/id
                          ::scope
                          :befive.schema.domain.attachment/kind
                          ::value]
                 :opt-un [::locked ::reason])
         (schema/closed-spec ::policy-attachment {:allow-prefix "x-"})))
(s/def ::policy-attachments
  (s/coll-of ::policy-attachment :kind vector?))

(s/def ::document
  (s/and (s/keys :opt-un [::apis ::versions ::operations ::services
                          ::organizations ::consumers ::applications
                          ::credentials ::specs ::policy-attachments
                          ::allow-design-routes])
         (schema/closed-spec ::document {:allow-prefix "x-"})))

(doseq [[spec-key title]
        [[::api "API"]
         [::version "API version"]
         [::operation "Operation"]
         [::service "Service"]
         [::organization "Organization"]
         [::consumer "Consumer"]
         [::application "Application"]
         [::credential "Credential"]
         [::api-spec "API specification"]
         [::policy-attachment "Policy attachment"]
         [::document "Domain document"]]]
  (meta/register! spec-key {:title title :since 1}))

(defn default-application-id
  "The application created for a consumer that has none yet."
  [consumer-id]
  (str consumer-id "-default"))

(defn versioning-of
  "The version rule, or the path default `/{version}`.
  A default version on the API or the version fills the rule when
  the rule itself does not name one."
  [api version]
  (let [rule (or (:match version)
                 (:versioning version)
                 (:versioning api)
                 {:strategy :path :path {:template "/{version}"}})
        default (or (:default-version rule)
                    (:default-version version)
                    (:default-version api))]
    (cond-> rule
      default (assoc :default-version default))))

(def path-normalizer
  "Parameter names do not distinguish two templates."
  #"\{[^}]+\}|:[A-Za-z0-9_-]+")

(defn template-key
  "Method plus a path whose parameters are all `:p`."
  [method path]
  [(if (keyword? method) method (keyword (str/lower-case (str method))))
   (str/replace path path-normalizer ":p")])
