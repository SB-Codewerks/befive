(ns befive.gateway.router
  "Compile host routers and match them the same way a linear scan does.
  reitit linear routers are ordered by specificity, then priority,
  then id. The oracle scans every route and picks that same winner."
  (:require [clojure.string :as str]
            [reitit.core :as r]))

(set! *warn-on-reflection* true)

(defn hostname
  "Host header value without a port. An IPv6 literal is left whole."
  [value]
  (when (string? value)
    (let [text (str/lower-case (str/trim value))]
      (cond
        (str/blank? text) nil
        (str/starts-with? text "[")
        (let [end (str/index-of text "]")]
          (if end (subs text 0 (inc end)) text))
        :else (let [cut (str/index-of text ":")]
                (if cut (subs text 0 cut) text))))))

(defn- segments
  [path]
  (if (or (str/blank? path) (= path "/"))
    []
    (str/split (subs path 1) #"/" -1)))

(defn compile-template
  "Tokenize `template`. A splat is only legal as the last segment."
  [template]
  (let [tokens (mapv (fn [segment]
                       (cond
                         (str/starts-with? segment ":")
                         [:param (keyword (subs segment 1))]
                         (str/starts-with? segment "*")
                         [:splat (keyword (if (= segment "*")
                                            "splat"
                                            (subs segment 1)))]
                         :else [:static segment]))
                     (segments template))]
    (when (some (fn [token]
                  (and (= :splat (first token))
                       (not= token (peek tokens))))
                tokens)
      (throw (ex-info "splat must be the last path segment"
                      {:path template})))
    tokens))

(defn- match-tokens
  [tokens path]
  (loop [tokens tokens
         segs (segments path)
         params {}]
    (cond
      (empty? tokens) (when (empty? segs) params)
      (= :splat (ffirst tokens))
      (assoc params (second (first tokens)) (str/join "/" segs))
      (empty? segs) nil
      (= :static (ffirst tokens))
      (when (= (second (first tokens)) (first segs))
        (recur (rest tokens) (rest segs) params))
      (= :param (ffirst tokens))
      (recur (rest tokens) (rest segs)
             (assoc params (second (first tokens)) (first segs))))))

(defn- wildcard-suffix
  [pattern]
  (when (and (string? pattern) (str/starts-with? pattern "*."))
    (str/lower-case (subs pattern 1))))

(defn host-class
  "Rank vector for a route host. Exact beats a longer wildcard,
  which beats the default."
  [pattern]
  (cond
    (str/blank? pattern) [0 0]
    (wildcard-suffix pattern) [1 (count (wildcard-suffix pattern))]
    :else [2 0]))

(defn host-matches?
  [pattern host]
  (let [host (hostname host)]
    (cond
      (str/blank? pattern) true
      (wildcard-suffix pattern)
      (let [suffix (wildcard-suffix pattern)]
        (and (some? host)
             (str/ends-with? host suffix)
             (> (count host) (count suffix))))
      :else (= (str/lower-case pattern) host))))

(defn template-rank
  "Higher vectors are more specific. Methods on one template share it."
  [route]
  (let [tokens (compile-template (:path route))
        static (count (filter #(= :static (first %)) tokens))
        params (count (filter #(= :param (first %)) tokens))
        splat? (some #(= :splat (first %)) tokens)]
    [(host-class (:host route))
     static
     (if splat? 0 1)
     (- params)
     (long (:priority route 0))]))

(defn- reitit-path
  [template]
  (let [tokens (compile-template template)]
    (if (empty? tokens)
      "/"
      (str "/" (str/join "/"
                         (map (fn [[kind token]]
                                (case kind
                                  :static token
                                  :param (str token)
                                  :splat (str "*" (name token))))
                              tokens))))))

(defn- normalize-host
  "Lower-case an exact host. `*.Example.com` becomes `*.example.com`."
  [host]
  (cond
    (str/blank? host) nil
    (wildcard-suffix host) (str "*" (wildcard-suffix host))
    :else (hostname host)))

(defn- better-route
  [a b]
  (let [rank (compare (template-rank a) (template-rank b))]
    (cond
      (pos? rank) a
      (neg? rank) b
      (neg? (compare (:id a) (:id b))) a
      :else b)))

(defn- group-templates
  [routes]
  (reduce (fn [groups route]
            (let [template-key [(:host route) (:path route)]
                  method-map
                  (reduce (fn [method-map method]
                            (update method-map method
                                    (fn [current]
                                      (if current
                                        (better-route current route)
                                        route))))
                          (:methods (get groups template-key) {})
                          (:methods route))]
              (assoc groups template-key {:host (:host route)
                                 :path (:path route)
                                 :methods method-map})))
          {}
          routes))

(defn- group-rank
  [{:keys [path host] :as group}]
  (template-rank
   {:path path
    :host host
    :priority (apply max (map #(long (:priority % 0))
                              (vals (:methods group))))}))

(defn- min-id
  [group]
  (->> (:methods group) vals (map :id) sort first))

(defn- by-specificity
  "Higher rank first. Equal ranks keep the smaller route id first.
  The rank is computed once per group."
  [groups]
  (let [ranked (mapv (fn [group]
                       (assoc group ::rank (group-rank group)))
                     groups)]
    (sort (fn [a b]
            (let [rank (compare (::rank b) (::rank a))]
              (if (zero? rank)
                (compare (min-id a) (min-id b))
                rank)))
          ranked)))

(defn- static-template?
  "A template with no parameters and no splat."
  [path]
  (every? (fn [[kind]] (= :static kind)) (compile-template path)))

(defn- router-for
  "Static templates are a path map. Parameter and splat templates stay
  on a reitit linear router, already ordered by specificity."
  [groups]
  (let [split (reduce (fn [acc {:keys [path] method-map :methods}]
                        (if (static-template? path)
                          (assoc-in acc [:static (reitit-path path)]
                                    method-map)
                          (update acc :dynamic conj
                                  [(reitit-path path)
                                   {:methods method-map}])))
                      {:static {} :dynamic []}
                      (by-specificity groups))
        dynamic (:dynamic split)]
    (when (or (seq (:static split)) (seq dynamic))
      {:static (:static split)
       :dynamic (when (seq dynamic)
                  (r/router dynamic
                            {:router r/linear-router :conflicts nil}))})))

(defn compile-routes
  "Build exact, wildcard and default routers from normalized routes."
  [routes]
  (let [routes (mapv (fn [route]
                       (if-let [host (normalize-host (:host route))]
                         (assoc route :host host)
                         (dissoc route :host)))
                     routes)
        groups (vals (group-templates routes))
        exact (group-by :host
                         (filter #(and (:host %)
                                       (not (wildcard-suffix (:host %))))
                                 groups))
        wild (->> groups
                  (filter #(wildcard-suffix (:host %)))
                  (group-by :host)
                  (map (fn [[pattern grouped]]
                         {:suffix (wildcard-suffix pattern)
                          :router (router-for grouped)}))
                  (sort-by :suffix #(compare (count %2) (count %1)))
                  vec)
        default (router-for (filter #(str/blank? (:host %)) groups))]
    {:exact (into {}
                  (map (fn [[host grouped]]
                         [host (router-for grouped)]))
                  exact)
     :wildcards wild
     :default default
     :routes (vec routes)
     :groups (vec (by-specificity groups))}))

(defn- from-methods
  [method-map method path-params]
  (let [route (get method-map method)]
    (if route
      {:status :matched
       :route route
       :path-params path-params}
      {:status :method-not-allowed
       :allow (set (keys method-map))
       :route (some-> method-map vals first)
       :path-params path-params})))

(defn- from-hit
  [hit method]
  (when hit
    (from-methods (:methods (:data hit))
                  method
                  (:path-params hit))))

(defn- match-compiled
  [router method path]
  (when router
    (if-let [method-map (get (:static router) path)]
      (from-methods method-map method {})
      (when-let [dynamic (:dynamic router)]
        (from-hit (r/match-by-path dynamic path) method)))))

(defn- wildcard-routers
  "Longest matching suffix first. A later suffix is tried only when
  an earlier one does not match the path."
  [compiled host]
  (when host
    (keep (fn [{:keys [suffix router]}]
            (when (and (str/ends-with? host suffix)
                       (> (count host) (count suffix)))
              router))
          (:wildcards compiled))))

(defn match
  "Match `request` against a compiled table. Host buckets are tried
  exact, then longest wildcard suffix, then the default router."
  [compiled {:keys [host method path]}]
  (let [host (hostname host)
        method (if (keyword? method)
                 method
                 (keyword (str/lower-case (str method))))
        exact (when host (get (:exact compiled) host))]
    (or (match-compiled exact method path)
        (some #(match-compiled % method path)
              (wildcard-routers compiled host))
        (match-compiled (:default compiled) method path)
        {:status :not-found})))

(defn- from-group
  [group method path]
  (let [method-map (:methods group)
        route (get method-map method)
        params (match-tokens (compile-template (:path group)) path)]
    (if route
      {:status :matched :route route :path-params params}
      {:status :method-not-allowed
       :allow (set (keys method-map))
       :route (first (vals method-map))
       :path-params params})))

(defn oracle-match
  "Slow reference matcher. Tests require `match` to agree with it."
  [routes {:keys [host method path]}]
  (let [compiled (compile-routes routes)
        method (if (keyword? method)
                 method
                 (keyword (str/lower-case (str method))))]
    (or (some (fn [group]
                (when (and (host-matches? (:host group) host)
                           (match-tokens (compile-template (:path group))
                                         path))
                  (from-group group method path)))
              (:groups compiled))
        {:status :not-found})))
