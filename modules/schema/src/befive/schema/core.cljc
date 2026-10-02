(ns befive.schema.core
  "Base specs shared by the server, the CLI and the browser.
  Slug, CIDR, duration, HTTP URL and header name, plus closed maps."
  (:require [befive.schema.errors :as errors]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]))

(def slug-re #"^[a-z0-9][a-z0-9-]{0,62}$")

(def header-name-re #"^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

(def http-url-re
  (re-pattern
   (str "(?i)^https?://[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
        "(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*"
        "(:[0-9]{1,5})?(/[^\\s]*)?$")))

(defn- parse-int
  [text]
  (when (and (string? text) (re-matches #"\d{1,5}" text))
    #?(:clj (Long/parseLong text)
       :cljs (js/parseInt text 10))))

(defn slug-chars?
  "True when `text` matches the slug grammar."
  [text]
  (boolean (and (string? text) (re-matches slug-re text))))

(defn header-name?
  "True when `text` is a token header field name."
  [text]
  (boolean (and (string? text) (re-matches header-name-re text))))

(defn- octet?
  [text]
  (and (re-matches #"\d{1,3}" text)
       (or (= (count text) 1)
           (not (str/starts-with? text "0")))
       (let [n (parse-int text)]
         (and n (<= 0 n 255)))))

(defn- ipv4?
  [text]
  (let [parts (str/split text #"\.")]
    (and (= 4 (count parts))
         (every? octet? parts))))

(defn- hex-groups
  "Hex groups of an IPv6 side, or nil when the side is illegal.
  A blank side is the empty vector, which is how `::` compression
  sits at an edge."
  [text]
  (cond
    (str/blank? text) []
    (or (str/starts-with? text ":")
        (str/ends-with? text ":")) nil
    :else
    (let [groups (str/split text #":")]
      (when (and (seq groups)
                 (every? #(re-matches #"[0-9A-Fa-f]{1,4}" %) groups))
        groups))))

(defn- ipv6?
  [text]
  (cond
    (str/includes? text ":::") false
    (str/includes? text "::")
    (let [i (str/index-of text "::")
          left (subs text 0 i)
          right (subs text (+ i 2))
          lg (hex-groups left)
          rg (hex-groups right)]
      (and lg rg
           (not (str/includes? right "::"))
           (< (+ (count lg) (count rg)) 8)))
    :else
    (when-let [groups (hex-groups text)]
      (= 8 (count groups)))))

(defn cidr?
  "True when `text` is an IPv4 CIDR (prefix 0-32) or an IPv6 CIDR
  (prefix 0-128)."
  [text]
  (boolean
   (when (string? text)
     (let [slash (str/last-index-of text "/")]
       (when (and slash (pos? slash)
                  (< slash (dec (count text))))
         (let [addr (subs text 0 slash)
               prefix (subs text (inc slash))
               n (parse-int prefix)]
           (and n
                (cond
                  (ipv4? addr) (<= 0 n 32)
                  (ipv6? addr) (<= 0 n 128)
                  :else false))))))))

(defn- url-port-ok?
  [text]
  (if-let [match (re-find #":(\d+)(/|$)" text)]
    (let [n (parse-int (second match))]
      (and n (<= 1 n 65535)))
    true))

(defn http-url?
  "True for an http or https URL with a host and an optional port."
  [text]
  (boolean (and (string? text)
                (re-matches http-url-re text)
                (url-port-ok? text))))

(def slug-alphabet
  (vec "abcdefghijklmnopqrstuvwxyz0123456789"))

(def slug-tail-alphabet
  (vec "abcdefghijklmnopqrstuvwxyz0123456789-"))

(s/def ::slug
  (s/with-gen
    (s/and string? slug-chars?)
    #(gen/fmap
      (fn [[head tail]]
        (apply str head tail))
      (gen/tuple
       (gen/elements slug-alphabet)
       (gen/vector (gen/elements slug-tail-alphabet) 0 12)))))

(s/def ::cidr
  (s/with-gen
    (s/and string? cidr?)
    #(gen/fmap
      (fn [[a b c d bits]]
        (str a "." b "." c "." d "/" bits))
      (gen/tuple (gen/choose 0 255)
                 (gen/choose 0 255)
                 (gen/choose 0 255)
                 (gen/choose 0 255)
                 (gen/choose 0 32)))))

(s/def ::duration
  (s/int-in 0 3600001))

(s/def ::http-url
  (s/with-gen
    (s/and string? http-url?)
    #(gen/fmap
      (fn [[host port]]
        (str "https://" host ".example.com:" port "/v1"))
      (gen/tuple (gen/elements ["a" "api" "b2"])
                 (gen/choose 1 65535)))))

(s/def ::header-name
  (s/with-gen
    (s/and string? header-name?)
    #(gen/fmap
      (fn [letters]
        (apply str letters))
      (gen/vector (gen/elements (vec "abcXYZ-")) 1 12))))

(s/def ::non-blank-string
  (s/and string?
         #(not (str/blank? %))
         #(<= (count %) 128)))

(defn- spec-op?
  [form op]
  (and (sequential? form)
       (symbol? (first form))
       (= (name (first form)) op)))

(defn- collect-keys
  [form seen]
  (cond
    (keyword? form)
    (if (or (contains? seen form) (not (s/get-spec form)))
      #{}
      (collect-keys (s/form form) (conj seen form)))

    (spec-op? form "keys")
    (let [opts (apply hash-map (rest form))]
      (into #{}
            (map #(keyword (name %)))
            (concat (:req-un opts) (:opt-un opts)
                    (:req opts) (:opt opts))))

    (or (spec-op? form "and") (spec-op? form "merge"))
    (reduce
     (fn [acc sub]
       (into acc (collect-keys sub seen)))
     #{}
     (rest form))

    :else #{}))

(defn keys-of
  "Unqualified keys declared by an `s/keys` spec."
  [spec]
  (collect-keys (if (keyword? spec) spec (s/form spec)) #{}))

(s/fdef keys-of
  :args (s/cat :spec any?)
  :ret set?)

(defn allowed-key?
  [k allowed allow-prefix]
  (or (contains? allowed k)
      (boolean
       (and allow-prefix
            (keyword? k)
            (str/starts-with? (name k) allow-prefix)))))

(defn unknown-keys
  "Keys of `value` that `closed-spec` would reject.
  Returns nil when `value` is not a map."
  [value allowed allow-prefix]
  (when (map? value)
    (into []
          (remove #(allowed-key? % allowed allow-prefix))
          (keys value))))

(s/fdef unknown-keys
  :args (s/cat :value any?
               :allowed set?
               :allow-prefix (s/nilable string?))
  :ret (s/nilable vector?))

(defn- closed-spec*
  "Spec that rejects unknown keys. `gfn` is the optional generator
  installed by `with-gen*`."
  [spec-kw opts gfn]
  (let [prefix (:allow-prefix opts)]
    (reify
      s/Specize
      (specize* [this] this)
      (specize* [this _] this)
      s/Spec
      (conform* [_ value]
        (if (or (not (map? value))
                (empty? (unknown-keys value
                                       (keys-of spec-kw)
                                       prefix)))
          value
          ::s/invalid))
      (unform* [_ value] value)
      (explain* [_ path via in value]
        (let [unknown (when (map? value)
                        (unknown-keys value
                                      (keys-of spec-kw)
                                      prefix))]
          (if (seq unknown)
            (mapv (fn [k]
                    {:path (conj (vec path) k)
                     :pred errors/closed-sentinel
                     :val k
                     :via via
                     :in (conj (vec in) k)})
                  unknown)
            [{:path path
              :pred errors/closed-sentinel
              :val value
              :via via
              :in in}])))
      (gen* [_ _overrides _path _rmap]
        (when gfn (gfn)))
      (with-gen* [_ new-gfn]
        (closed-spec* spec-kw opts new-gfn))
      (describe* [_]
        `(closed-spec ~spec-kw ~opts)))))

(defn closed-spec
  "A spec that rejects keys other than those `keys-of` reports for
  `spec-kw`. `opts` may contain `:allow-prefix`, for example
  `\"x-\"`. A non-map is left for the `s/keys` spec in front of this
  one."
  [spec-kw opts]
  (closed-spec* spec-kw opts nil))

(s/fdef closed-spec
  :args (s/cat :spec qualified-keyword? :opts map?)
  :ret some?)

(errors/register-messages!
 {[:befive.schema.core/slug :pattern]
  (str "must be 1 to 63 characters: a lowercase letter or digit, "
       "then lowercase letters, digits or hyphens")
  [:befive.schema.core/cidr :pattern]
  "must be an IPv4 CIDR (prefix 0-32) or an IPv6 CIDR (prefix 0-128)"
  [:befive.schema.core/http-url :pattern]
  "must be an http or https URL"
  [:befive.schema.core/header-name :pattern]
  "must be a valid HTTP header name"
  [:befive.schema.core/duration :range]
  "must be a duration in milliseconds from 0 to 3600000"})
