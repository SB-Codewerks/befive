(ns befive.schema.compile
  "Compile API operations onto proxy routes.
  The function is pure and shared by the gateway and the control plane."
  (:require [befive.schema.domain :as domain]
            [befive.schema.errors :as errors]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def ^:private sha-k
  [0x428a2f98 0x71374491 0xb5c0fbcf 0xe9b5dba5
   0x3956c25b 0x59f111f1 0x923f82a4 0xab1c5ed5
   0xd807aa98 0x12835b01 0x243185be 0x550c7dc3
   0x72be5d74 0x80deb1fe 0x9bdc06a7 0xc19bf174
   0xe49b69c1 0xefbe4786 0x0fc19dc6 0x240ca1cc
   0x2de92c6f 0x4a7484aa 0x5cb0a9dc 0x76f988da
   0x983e5152 0xa831c66d 0xb00327c8 0xbf597fc7
   0xc6e00bf3 0xd5a79147 0x06ca6351 0x14292967
   0x27b70a85 0x2e1b2138 0x4d2c6dfc 0x53380d13
   0x650a7354 0x766a0abb 0x81c2c92e 0x92722c85
   0xa2bfe8a1 0xa81a664b 0xc24b8b70 0xc76c51a3
   0xd192e819 0xd6990624 0xf40e3585 0x106aa070
   0x19a4c116 0x1e376c08 0x2748774c 0x34b0bcb5
   0x391c0cb3 0x4ed8aa4a 0x5b9cca4f 0x682e6ff3
   0x748f82ee 0x78a5636f 0x84c87814 0x8cc70208
   0x90befffa 0xa4506ceb 0xbef9a3f7 0xc67178f2])

(defn- mask32
  [n]
  (bit-and n 0xffffffff))

(defn- shr
  "Logical right shift. JavaScript keeps only 5 bits of the shift
  count, so a count of 32 or more is applied 31 bits at a time."
  [x n]
  (loop [value x
         left n]
    (cond
      (zero? left) value
      (>= left 31) (recur (unsigned-bit-shift-right value 31) (- left 31))
      :else (unsigned-bit-shift-right value left))))

(defn- add32
  [& xs]
  (mask32 (reduce + xs)))

(defn- rotr
  [x n]
  (let [x (mask32 x)]
    (mask32 (bit-or (unsigned-bit-shift-right x n)
                    (bit-shift-left x (- 32 n))))))

(defn- code-units
  "UTF-16 code units. Clojure chars and JavaScript charCodeAt agree.
  `(int text)` in ClojureScript is 0 because a string is not a number."
  [text]
  #?(:clj (mapv int text)
     :cljs (mapv #(.charCodeAt text %) (range (count text)))))

(defn- utf8
  [text]
  (into []
        (mapcat (fn [cp]
                  (cond
                    (< cp 128) [cp]
                    (< cp 2048) [(bit-or 0xc0 (unsigned-bit-shift-right cp 6))
                                 (bit-or 0x80 (bit-and cp 0x3f))]
                    :else
                    [(bit-or 0xe0 (unsigned-bit-shift-right cp 12))
                     (bit-or 0x80 (bit-and (unsigned-bit-shift-right cp 6)
                                           0x3f))
                     (bit-or 0x80 (bit-and cp 0x3f))])))
        (code-units text)))

(defn- sha-pad
  [data]
  (let [len (count data)
        bit-len (* len 8)
        with-one (conj (vec data) 0x80)
        zeros (mod (- 56 (count with-one)) 64)
        padded (into with-one (repeat zeros 0))]
    (into padded
          (map (fn [shift]
                 (bit-and (shr bit-len shift) 0xff))
               (range 56 -1 -8)))))

(defn- sha-word
  [block index]
  (let [offset (* index 4)]
    (mask32
     (bit-or (bit-shift-left (nth block offset) 24)
             (bit-shift-left (nth block (+ offset 1)) 16)
             (bit-shift-left (nth block (+ offset 2)) 8)
             (nth block (+ offset 3))))))

(defn- sha-extend
  [block]
  (loop [i 16
         words (mapv #(sha-word block %) (range 16))]
    (if (= i 64)
      words
      (let [w15 (nth words (- i 15))
            w2 (nth words (- i 2))
            s0 (bit-xor (rotr w15 7) (rotr w15 18)
                        (unsigned-bit-shift-right (mask32 w15) 3))
            s1 (bit-xor (rotr w2 17) (rotr w2 19)
                        (unsigned-bit-shift-right (mask32 w2) 10))
            word (add32 (nth words (- i 16)) s0
                        (nth words (- i 7)) s1)]
        (recur (inc i) (conj words word))))))

(defn- sha-compress
  [state block]
  (let [words (sha-extend block)]
    (loop [i 0
           a (nth state 0) b (nth state 1) c (nth state 2) d (nth state 3)
           e (nth state 4) f (nth state 5) g (nth state 6) h (nth state 7)]
      (if (= i 64)
        (mapv add32 state [a b c d e f g h])
        (let [s1 (bit-xor (rotr e 6) (rotr e 11) (rotr e 25))
              ch (bit-xor (bit-and e f) (bit-and (bit-not e) g))
              temp1 (add32 h s1 ch (nth sha-k i) (nth words i))
              s0 (bit-xor (rotr a 2) (rotr a 13) (rotr a 22))
              maj (bit-xor (bit-and a b) (bit-and a c) (bit-and b c))
              temp2 (add32 s0 maj)]
          (recur (inc i)
                 (add32 temp1 temp2) a b c
                 (add32 d temp1) e f g))))))

(def ^:private hex-digits "0123456789abcdef")

(defn- hex8
  [word]
  (let [word (mask32 word)]
    (apply str
           (map (fn [shift]
                  (nth hex-digits
                       (bit-and (unsigned-bit-shift-right word shift)
                                0xf)))
                [28 24 20 16 12 8 4 0]))))

(defn sha256-hex
  "Lower-case SHA-256 of `text`, UTF-8."
  [text]
  (let [padded (sha-pad (utf8 text))
        chunks (partition 64 padded)
        init [0x6a09e667 0xbb67ae85 0x3c6ef372 0xa54ff53a
              0x510e527f 0x9b05688c 0x1f83d9ab 0x5be0cd19]
        digest (reduce sha-compress init chunks)]
    (apply str (map hex8 digest))))

(defn derived-id
  "Route id for an operation. Longer than 63 characters is shortened."
  [api version operation]
  (let [raw (str "op-" api "-" version "-" operation)]
    (if (<= (count raw) 63)
      raw
      (str (subs raw 0 54) "-" (subs (sha256-hex raw) 0 8)))))

(defn- parse-int
  [text]
  #?(:clj (Long/parseLong text)
     :cljs (js/parseInt text 10)))

(defn- days-from-civil
  "Days since 1970-01-01 for a Gregorian date."
  [y m d]
  (let [y (if (<= m 2) (dec y) y)
        era (quot (if (>= y 0) y (- y 399)) 400)
        yoe (- y (* era 400))
        madj (if (> m 2) (- m 3) (+ m 9))
        doy (+ (quot (+ (* 153 madj) 2) 5) (dec d))
        doe (+ (* yoe 365) (quot yoe 4) (- (quot yoe 100)) doy)]
    (- (+ (* era 146097) doe) 719468)))

(defn- civil-from-days
  [z]
  (let [z (+ z 719468)
        era (quot (if (>= z 0) z (- z 146096)) 146097)
        doe (- z (* era 146097))
        yoe (quot (- (+ doe (- (quot doe 1460)) (quot doe 36524))
                     (quot doe 146096))
                  365)
        y (+ yoe (* era 400))
        doy (- doe (+ (* 365 yoe) (quot yoe 4) (- (quot yoe 100))))
        mp (quot (+ (* 5 doy) 2) 153)
        d (inc (- doy (quot (+ (* 153 mp) 2) 5)))
        m (+ mp (if (< mp 10) 3 -9))]
    [(if (<= m 2) (inc y) y) m d]))

(defn instant-seconds
  "Unix seconds for a `YYYY-MM-DDTHH:mm:ssZ` timestamp, or nil."
  [text]
  (when-let [parts (re-matches
                    #"(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})Z"
                    (str text))]
    (let [[_ y m d h minute sec] parts]
      (+ (* (days-from-civil (parse-int y) (parse-int m) (parse-int d))
            86400)
         (* (parse-int h) 3600)
         (* (parse-int minute) 60)
         (parse-int sec)))))

(def ^:private weekdays
  ["Sun" "Mon" "Tue" "Wed" "Thu" "Fri" "Sat"])

(def ^:private months
  [nil "Jan" "Feb" "Mar" "Apr" "May" "Jun"
   "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"])

(defn- pad2
  [n]
  (if (< n 10) (str "0" n) (str n)))

(defn http-date
  "IMF-fix HTTP-date for a UTC timestamp, or nil."
  [text]
  (when-let [seconds (instant-seconds text)]
    (let [days (quot seconds 86400)
          clock (mod seconds 86400)
          [y m d] (civil-from-days days)
          weekday (nth weekdays (mod (+ days 4) 7))]
      (str weekday ", " (pad2 d) " " (nth months m) " " y " "
           (pad2 (quot clock 3600)) ":"
           (pad2 (quot (mod clock 3600) 60)) ":"
           (pad2 (mod clock 60)) " GMT"))))

(defn- flag-on?
  [policy flag]
  (let [flags (:headers policy)]
    (if (and flags (contains? flags flag))
      (boolean (get flags flag))
      true)))

(defn- link-header
  [url]
  (str "<" url ">; rel=\"deprecation\"; type=\"text/html\", <"
       url ">; rel=\"sunset\"; type=\"text/html\""))

(defn deprecation-headers
  "Precomputed Deprecation, Sunset and Link values."
  [policy]
  (when (map? policy)
    (cond-> {}
      (and (flag-on? policy :deprecation) (:deprecated-at policy))
      (assoc :deprecation
             (str "@" (instant-seconds (:deprecated-at policy))))
      (and (flag-on? policy :sunset) (:sunset-at policy))
      (assoc :sunset (http-date (:sunset-at policy)))
      (and (flag-on? policy :link) (:link policy))
      (assoc :link (link-header (:link policy))))))

(defn- problem
  [path message]
  {:path path :code "domain" :message message})

(defn- index-by
  [rows key-fn]
  (into {} (map (fn [row] [(key-fn row) row])) rows))

(defn- substitute
  [template version-id]
  (str/replace (or template "") "{version}" (str version-id)))

(defn join-path
  [prefix path]
  (let [prefix (str/replace (or prefix "") #"/$" "")
        path (if (str/blank? path) "/" path)]
    (cond
      (= prefix "") path
      (or (= path "/") (= path "")) prefix
      :else (str prefix (if (str/starts-with? path "/") path
                            (str "/" path))))))

(defn- lifecycle-of
  [state]
  (case state
    :deprecated :deprecated
    :retired :retired
    :active))

(defn- routed?
  [state allow-design?]
  (or (contains? #{:published :deprecated :retired} state)
      (and allow-design? (= state :design))))

(defn- operation-policy
  [version operation]
  (or (:deprecation operation) (:deprecation version)))

(defn- policy-active?
  [policy]
  (and (map? policy)
       (or (:deprecated-at policy)
           (:sunset-at policy)
           (:link policy))))

(defn- base-route
  [api-id version upstream-id rule]
  (let [state (:state version :design)
        headers (deprecation-headers (operation-policy version nil))
        id (:id version)]
    (cond-> {:id (derived-id api-id id "retired")
             :methods #{:get :head :post :put :patch :delete
                        :options :trace}
             :upstream upstream-id
             :lifecycle (lifecycle-of state)
             :api api-id
             :api-version id
             :priority 0}
      (:deprecation headers) (assoc :deprecation (:deprecation headers))
      (:sunset headers) (assoc :sunset (:sunset headers))
      (:link headers) (assoc :link (:link headers))
      (or (= state :deprecated)
          (policy-active? (operation-policy version nil)))
      (assoc :deprecated true)
      (= (:strategy rule) :host)
      (assoc :host (substitute (get-in rule [:host :template]
                                       "{version}")
                               id)
             :version-selected-by :host)
      (= (:strategy rule) :path)
      (assoc :version-selected-by :path))))

(defn- operation-route
  [api-id version service operation rule]
  (let [state (:state version :design)
        policy (operation-policy version operation)
        headers (deprecation-headers policy)
        prefix (when (= (:strategy rule) :path)
                 (substitute (get-in rule [:path :template] "/{version}")
                             (:id version)))
        path (join-path prefix (:path operation))
        group (str api-id " " (:id operation))]
    (cond-> {:id (derived-id api-id (:id version) (:id operation))
             :methods #{(:method operation)}
             :path path
             :upstream (:upstream service)
             :lifecycle (lifecycle-of state)
             :api api-id
             :api-version (:id version)
             :operation (:id operation)
             :priority 0}
      (:deprecation headers) (assoc :deprecation (:deprecation headers))
      (:sunset headers) (assoc :sunset (:sunset headers))
      (:link headers) (assoc :link (:link headers))
      (or (= state :deprecated) (policy-active? policy))
      (assoc :deprecated true)
      (:upstream-path (:backend operation))
      (assoc :upstream-path (:upstream-path (:backend operation)))
      (= (:strategy rule) :host)
      (assoc :host (substitute (get-in rule [:host :template] "{version}")
                               (:id version))
             :version-selected-by :host)
      (= (:strategy rule) :path)
      (assoc :version-selected-by :path)
      (contains? #{:header :query :media-type} (:strategy rule))
      (assoc :version-select
             (cond-> {:strategy (:strategy rule)
                      :group group
                      :token (:id version)
                      :default (= (:id version)
                                  (:default-version rule))
                      :aliases (or (:aliases rule) {})
                      :forward (boolean (get-in rule [:query :forward]))}
               (= (:strategy rule) :header)
               (assoc :header (get-in rule [:header :name] "api-version")
                      :vary (get-in rule [:header :name] "api-version"))
               (= (:strategy rule) :query)
               (assoc :query (get-in rule [:query :name] "api-version"))
               (= (:strategy rule) :media-type)
               (assoc :vary "accept")
               (and (= (:strategy rule) :media-type)
                    (:pattern (:media-type rule)))
               (assoc :media (:pattern (:media-type rule))))))))

(defn- route-slots
  [route]
  (map (fn [method]
         [(or (:host route) "")
          method
          (second (domain/template-key method (:path route)))])
       (sort (:methods route))))

(defn- check-refs
  [domain upstreams]
  (let [apis (index-by (:apis domain) :id)
        versions (group-by (juxt :api :id) (:versions domain))
        services (index-by (:services domain) :id)
        orgs (index-by (:organizations domain) :id)
        consumers (index-by (:consumers domain) :id)
        apps (index-by (:applications domain) :id)]
    (concat
     (keep (fn [version]
             (cond
               (not (contains? apis (:api version)))
               (problem [:versions (:id version) :api]
                        "version names an API that is not in the document")
               (not (contains? services (:service version)))
               (problem [:versions (:id version) :service]
                        "version names a service that is not in the document")
               (not (contains? upstreams (:upstream (get services (:service version)))))
               (problem [:versions (:id version) :service]
                        "the service names an upstream that is not in the document")
               :else nil))
           (:versions domain))
     (keep (fn [operation]
             (when-not (contains? versions [(:api operation) (:version operation)])
               (problem [:operations (:id operation)]
                        "operation names a version that is not in the document")))
           (:operations domain))
     (keep (fn [consumer]
             (when-not (contains? orgs (:organization consumer))
               (problem [:consumers (:id consumer) :organization]
                        "consumer names an organization that is not in the document")))
           (:consumers domain))
     (keep (fn [application]
             (when-not (contains? consumers (:consumer application))
               (problem [:applications (:id application) :consumer]
                        "application names a consumer that is not in the document")))
           (:applications domain))
     (keep (fn [credential]
             (when-not (contains? apps (:application credential))
               (problem [:credentials (:id credential) :application]
                        "credential names an application that is not in the document")))
           (:credentials domain)))))

(defn- check-unique
  [domain]
  (concat
   (map (fn [id]
          (problem [:apis id] "API id is repeated"))
        (mapcat (fn [[id rows]] (when (> (count rows) 1) [id]))
                (group-by :id (:apis domain))))
   (map (fn [[api id]]
          (problem [:versions api id] "version id is repeated in the API"))
        (mapcat (fn [[group-key rows]]
                  (when (> (count rows) 1) [group-key]))
                (group-by (juxt :api :id) (:versions domain))))
   (map (fn [[api version method path]]
          (problem [:operations api version method path]
                   "method and path are repeated in the version"))
        (mapcat (fn [[group-key rows]]
                  (when (> (count rows) 1)
                    (let [[api version [method path]] group-key]
                      [[api version method path]])))
                (group-by (fn [operation]
                            [(:api operation)
                             (:version operation)
                             (domain/template-key (:method operation)
                                                  (:path operation))])
                          (:operations domain))))))

(defn- derive-routes
  [document]
  (let [domain (:domain document)
        allow? (:allow-design-routes domain)
        apis (index-by (:apis domain) :id)
        services (index-by (:services domain) :id)
        versions (:versions domain)]
    (vec
     (mapcat
      (fn [version]
        (let [state (:state version :design)
              api (get apis (:api version))
              service (get services (:service version))
              rule (domain/versioning-of api version)]
          (when (and (routed? state allow?) api service)
            (if (and (= state :retired)
                     (contains? #{:path :host} (:strategy rule)))
              [(assoc (base-route (:api version) version
                                  (:upstream service) rule)
                      :path (if (= (:strategy rule) :path)
                              (join-path
                               (substitute
                                (get-in rule [:path :template] "/{version}")
                                (:id version))
                               "/*path")
                              "/*path"))]
              (mapv (fn [operation]
                      (operation-route (:api version) version service
                                       operation rule))
                    (filter #(and (= (:api %) (:api version))
                                  (= (:version %) (:id version)))
                            (:operations domain)))))))
      versions))))

(defn- selector-group
  [route]
  (let [strategy (get-in route [:version-select :strategy])]
    (when (contains? #{:header :query :media-type} strategy)
      (get-in route [:version-select :group]))))

(defn- hand-problems
  [hand-slots derived]
  (mapcat
   (fn [route]
     (keep (fn [slot]
             (when-let [id (get hand-slots slot)]
               (problem [:routes id]
                        (str "route " id " and " (:id route)
                             " match the same host, method and path"))))
           (route-slots route)))
   derived))

(defn- slot-problems
  [derived]
  (let [owners (reduce
                (fn [acc route]
                  (reduce (fn [acc slot]
                            (update acc slot (fnil conj []) route))
                          acc
                          (route-slots route)))
                {}
                derived)]
    (keep
     (fn [[_slot routes]]
       (let [groups (set (keep selector-group routes))
             baked (remove selector-group routes)]
         (cond
           (and (seq baked)
                (or (> (count baked) 1) (seq groups)))
           (problem [:operations (:operation (first routes))]
                    "two versions compile to the same route")
           (> (count groups) 1)
           (problem [:operations]
                    "two operations compile to the same route")
           :else nil)))
     owners)))

(defn- selector-problems
  [routes]
  (let [tokens (map #(get-in % [:version-select :token]) routes)
        defaults (filter #(get-in % [:version-select :default]) routes)]
    (cond
      (not= (count tokens) (count (set tokens)))
      [(problem [:versions] "two versions share a selector value")]
      (> (count defaults) 1)
      [(problem [:default-version] "two versions are the default")]
      :else [])))

(defn- representative
  [routes]
  (or (some (fn [route]
              (when (get-in route [:version-select :default]) route))
            routes)
      (first routes)))

(defn- assemble
  [hand derived]
  (let [hand-slots (into {}
                         (mapcat (fn [route]
                                   (map (fn [slot] [slot (:id route)])
                                        (route-slots route))))
                         hand)
        selectors (filter selector-group derived)
        groups (group-by selector-group selectors)
        problems (vec (concat (hand-problems hand-slots derived)
                              (slot-problems derived)
                              (mapcat selector-problems (vals groups))))]
    (if (seq problems)
      {:routes [] :groups {} :problems problems}
      {:problems []
       :groups groups
       :routes (into (vec (remove selector-group derived))
                     (map representative)
                     (vals groups))})))

(defn expand
  "Add derived routes to a proxy document. No domain leaves the
  document unchanged apart from an empty version-group map."
  [document]
  (let [domain (:domain document)]
    (if-not domain
      (assoc document :version-groups {})
      (if-let [invalid (errors/explain->problems ::domain/document domain)]
        (throw (ex-info "domain document rejected" {:problems invalid}))
        (let [refs (check-refs domain
                               (set (map :id (:upstreams document))))
              unique (check-unique domain)
              problems (concat refs unique)]
          (if (seq problems)
            (throw (ex-info "domain document rejected"
                            {:problems (vec problems)}))
            (let [derived (remove nil? (derive-routes document))
                  built (assemble (or (:routes document) []) derived)]
              (if (seq (:problems built))
                (throw (ex-info "domain document rejected"
                                {:problems (:problems built)}))
                (assoc document
                       :routes (into (vec (or (:routes document) []))
                                     (:routes built))
                       :version-groups (:groups built))))))))))

(defn- field-name
  [value]
  (str/lower-case (if (keyword? value) (name value) (str value))))

(defn header-value
  [headers wanted]
  (some (fn [[k v]]
          (when (= (field-name wanted) (field-name k))
            (if (sequential? v) (str (first v)) (some-> v str))))
        headers))

(defn query-value
  [query param]
  (some (fn [pair]
          (when-not (str/blank? pair)
            (let [[k v] (str/split pair #"=" 2)]
              (when (= k param) (or v "")))))
        (str/split (or query "") #"&")))

(defn strip-query
  "Drop one query parameter. Returns nil when nothing remains."
  [query param]
  (let [kept (remove (fn [pair]
                       (let [[k] (str/split pair #"=" 2)]
                         (= k param)))
                     (remove str/blank?
                             (str/split (or query "") #"&")))]
    (when (seq kept)
      (str/join "&" kept))))

(defn- alias-token
  [route token]
  (let [aliases (get-in route [:version-select :aliases])]
    (or (get aliases token) token)))

(defn- by-token
  [routes token via]
  (or (some (fn [route]
              (when (= token (get-in route [:version-select :token]))
                (assoc route :version-selected-by via)))
            routes)
      {:selection-error "version.unknown" :error "bad_request"}))

(defn- select-token
  [routes raw via]
  (if (str/blank? raw)
    (or (some (fn [route]
                (when (get-in route [:version-select :default])
                  (assoc route :version-selected-by :default)))
              routes)
        {:selection-error "version.unknown" :error "bad_request"})
    (by-token routes (alias-token (first routes) (str/trim raw)) via)))

(defn media-token
  "The version captured by `pattern`, or nil when `media` is another type."
  [pattern media]
  (when (and (string? pattern)
             (string? media)
             (str/includes? pattern "{version}"))
    (let [[prefix suffix] (str/split pattern #"\{version\}" 2)
          value (str/lower-case
                 (str/trim (first (str/split media #";"))))
          prefix (str/lower-case prefix)
          suffix (str/lower-case (or suffix ""))]
      (when (and (str/starts-with? value prefix)
                 (str/ends-with? value suffix)
                 (> (count value) (+ (count prefix) (count suffix))))
        (subs value (count prefix) (- (count value) (count suffix)))))))

(defn- accept-items
  [header]
  (sort-by :q >
           (keep (fn [part]
                   (let [bits (map str/trim (str/split part #";"))
                         media (first bits)
                         qtext (some #(when (str/starts-with?
                                             (str/lower-case %) "q=")
                                        (subs % 2))
                                     (rest bits))
                         q (if qtext
                             #?(:clj (try (Double/parseDouble qtext)
                                          (catch Exception _ 0.0))
                                :cljs (let [n (js/parseFloat qtext)]
                                        (if (js/isNaN n) 0.0 n)))
                             1.0)]
                     (when-not (str/blank? media)
                       {:media media :q q})))
                 (str/split (or header "") #","))))

(defn- select-media
  [routes request]
  (let [headers (:headers request)
        pattern (get-in (first routes) [:version-select :media])
        accept (header-value headers "accept")
        source (if (str/blank? accept)
                 (header-value headers "content-type")
                 accept)
        items (if (str/blank? accept)
                (when-not (str/blank? source) [{:media source :q 1.0}])
                (accept-items source))]
    (loop [items items]
      (if-let [item (first items)]
        (if-let [token (media-token pattern (:media item))]
          (let [resolved (alias-token (first routes) token)]
            (or (some (fn [route]
                        (when (= resolved
                                 (get-in route [:version-select :token]))
                          (assoc route :version-selected-by :media-type)))
                      routes)
                {:selection-error "version.unknown_media_type"
                 :error "not_acceptable"}))
          (recur (rest items)))
        (select-token routes nil :default)))))

(defn select-version
  "Pick the version route for this request."
  [routes request]
  (case (get-in (first routes) [:version-select :strategy])
    :header (select-token routes
                          (header-value (:headers request)
                                        (get-in (first routes)
                                                [:version-select :header]))
                          :header)
    :query (select-token routes
                         (query-value (:query-string request)
                                      (get-in (first routes)
                                              [:version-select :query]))
                         :query)
    :media-type (select-media routes request)
    (first routes)))

(defn choose
  "Replace a grouped match with the version the request selected."
  [route request groups]
  (if-let [group (get groups (get-in route [:version-select :group]))]
    (let [chosen (select-version group request)]
      (if (:selection-error chosen)
        chosen
        (cond-> chosen
          (:vary (:version-select chosen))
          (assoc :vary (:vary (:version-select chosen)))
          (and (= :query (get-in chosen [:version-select :strategy]))
               (not (get-in chosen [:version-select :forward])))
          (assoc :strip-query (get-in chosen [:version-select :query])))))
    route))

(s/fdef expand
  :args (s/cat :document map?)
  :ret map?)

(s/fdef choose
  :args (s/cat :route (s/nilable map?)
               :request map?
               :groups map?)
  :ret (s/nilable map?))
