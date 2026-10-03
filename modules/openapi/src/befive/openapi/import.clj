(ns befive.openapi.import
  "Map an OpenAPI document onto an API version and its operations.
  Remote references are reported and not followed. Unsupported
  constructs are warnings."
  (:require [befive.schema.compile :as compile]
            [clojure.string :as str]))

(def http-methods
  #{:get :put :post :delete :options :head :patch :trace})

(def unsupported
  {:callbacks "callbacks are not imported"
   :links "links are not imported"
   :xml "xml bindings are not imported"
   :webhooks "webhooks are not imported"})

(defn slug
  "Lower-case slug, or nil when `text` cannot be one.
  Camel case becomes dashes, so `listOrders` is `list-orders`."
  [text]
  (let [normalized (-> (str text)
                        (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
                        (str/lower-case)
                        (str/replace #"[^a-z0-9]+" "-")
                        (str/replace #"^-+|-+$" ""))]
    (when (re-matches #"[a-z0-9][a-z0-9-]{0,62}" normalized)
      normalized)))

(defn- clip
  [text n fallback]
  (let [text (str (or (not-empty text) fallback))]
    (subs text 0 (min n (count text)))))

(defn- key-name
  [value]
  (if (keyword? value) (name value) (str value)))

(defn- pointer
  [document pointer-text]
  (when (and (string? pointer-text)
             (str/starts-with? pointer-text "#/"))
    (reduce (fn [node token]
              (let [token (-> token
                              (str/replace "~1" "/")
                              (str/replace "~0" "~"))]
                (cond
                  (nil? node) nil
                  (map? node) (or (get node (keyword token))
                                  (get node token))
                  (and (vector? node) (re-matches #"\d+" token))
                  (get node (parse-long token))
                  :else nil)))
            document
            (str/split (subs pointer-text 2) #"/"))))

(defn- path-item
  [document item]
  (let [local (when (map? item) (or (:$ref item) (get item "$ref")))]
    (if (and local (str/starts-with? (str local) "#/"))
      (or (pointer document local) item)
      item)))

(defn- path-template
  [openapi-path]
  (str/replace openapi-path
               #"\{[^}]+\}"
               (fn [param]
                 (str ":" (subs param 1 (dec (count param)))))))

(defn- add-warn
  [warnings path message]
  (if (>= (count @warnings) 32)
    warnings
    (swap! warnings conj {:path path :message message})))

(defn- walk-warnings
  [node path warnings]
  (cond
    (map? node)
    (do
      (when-let [found (or (:$ref node) (get node "$ref"))]
        (when-not (str/starts-with? (str found) "#")
          (add-warn warnings path
                    (str "remote $ref is disabled: " found))))
      (doseq [[field value] node]
        (when-let [message (get unsupported field)]
          (add-warn warnings (conj path field) message))
        (when (and (= field :parameters) (sequential? value))
          (doseq [param value]
            (when (= "cookie" (str/lower-case (str (:in param))))
              (add-warn warnings (conj path :parameters)
                        "cookie parameters are not imported"))))
        (walk-warnings value (conj path field) warnings)))

    (sequential? node)
    (doseq [[index value] (map-indexed vector node)]
      (walk-warnings value (conj path index) warnings))

    :else nil))

(defn- unique-slug
  [used base]
  (loop [n 1]
    (let [candidate (if (= n 1)
                      base
                      (slug (str base "-" n)))]
      (cond
        (and candidate (not (contains? @used candidate)))
        (do (swap! used conj candidate)
            candidate)

        (> n 20) base
        :else (recur (inc n))))))

(defn- operation
  [api-id version-id template method op used warnings]
  (let [path (path-template (key-name template))
        fallback (slug (str (name method) "-" (key-name template)))
        id (unique-slug used (or (slug (:operationId op)) fallback))
        dep (:x-befive-deprecation op)]
    (when (:deprecated op)
      (add-warn warnings [template method]
                (str "deprecated: true has no sunset date; "
                     "set a deprecation policy after import")))
    (cond-> {:api api-id
             :version version-id
             :id id
             :method method
             :path path}
      (:summary op) (assoc :summary (clip (:summary op) 256 id))
      (map? dep) (assoc :deprecation dep))))

(defn operations-of
  "Operations for one API version. Ids come from operationId, or
  from the method and path."
  [parsed api-id version-id warnings]
  (let [used (atom #{})]
    (vec
     (for [[template item] (:paths parsed)
           :let [item (path-item parsed item)]
           :when (map? item)
           method http-methods
           :let [op (or (get item method)
                        (get item (name method)))]
           :when (map? op)]
       (operation api-id version-id template method op used warnings)))))

(defn diff-operations
  "Create, update and delete by operation id. An update is a changed
  method, path or summary."
  [previous current]
  (let [old (into {} (map (juxt :id identity)) previous)
        latest (into {} (map (juxt :id identity)) current)]
    (reduce
     (fn [acc id]
       (let [before (get old id)
             after (get latest id)
             view #(select-keys % [:id :method :path :summary])]
         (cond
           (and before (nil? after))
           (update acc :delete conj (view before))

           (and after (nil? before))
           (update acc :create conj (view after))

           (not= (view before) (view after))
           (update acc :update conj {:id id
                                     :before (view before)
                                     :after (view after)})

           :else acc)))
     {:create [] :update [] :delete []}
     (sort (set (concat (keys old) (keys latest)))))))

(defn import-document
  "Build an API, version, operations and spec record.
  `opts` requires `:api`, `:version` and `:service`."
  [parsed {:keys [api version service owners text source
                  state remote-refs]
           body-format :format
           :or {owners [{:group "api-owners"}]
                state :design
                source :upload
                body-format :json}}]
  (when (or (str/blank? api) (str/blank? version) (str/blank? service))
    (throw (ex-info "api, version and service are required"
                    {:api api :version version :service service})))
  (let [warnings (atom [])
        _ (walk-warnings parsed [] warnings)
        _ (doseq [remote remote-refs]
            (add-warn warnings [:$ref]
                      (str "remote $ref is disabled: " remote)))
        title (get-in parsed [:info :title])
        info-version (some-> (get-in parsed [:info :version]) str)
        description (get-in parsed [:info :description])
        openapi (or (:openapi parsed) (:swagger parsed))
        ops (operations-of parsed api version warnings)
        hashed (compile/sha256-hex (or text (pr-str parsed)))]
    (when (:swagger parsed)
      (add-warn warnings [:swagger]
                "Swagger 2.0 is not imported in 0.x"))
    (when-not (:openapi parsed)
      (add-warn warnings [:openapi] "openapi version is missing"))
    {:api (cond-> {:id api
                   :name (clip title 128 api)
                   :owners owners}
            (not (str/blank? description))
            (assoc :description (clip description 8192 api)))
     :version (cond-> {:api api
                       :id version
                       :service service
                       :state state
                       :spec {:sha256 hashed}}
                (not (str/blank? info-version))
                (assoc :label (clip info-version 64 version)))
     :operations ops
     :spec {:sha256 hashed
            :format body-format
            :openapi-version (clip (str (or openapi "3.0.0")) 32 "3.0.0")
            :source source}
     :warnings @warnings
     :upstream-url (when (string? (get-in parsed [:servers 0 :url]))
                     (get-in parsed [:servers 0 :url]))
     :diff {:create (mapv #(select-keys % [:id :method :path]) ops)
            :update []
            :delete []}}))

(defn- without
  [rows pred]
  (vec (remove pred (or rows []))))

(defn apply-import
  "Replace one API version's operations with an import result.
  Other versions and hand-written data stay."
  [domain imported]
  (let [api (:api imported)
        version (:version imported)
        ops (:operations imported)
        same-api? #(= (:id %) (:id api))
        same-version? #(and (= (:api %) (:api version))
                            (= (:id %) (:id version)))
        same-op? #(and (= (:api %) (:id api))
                       (= (:version %) (:id version)))]
    (-> domain
        (update :apis #(conj (without % same-api?) api))
        (update :versions #(conj (without % same-version?) version))
        (update :operations #(into (without % same-op?) ops))
        (update :specs (fn [rows]
                         (conj (without rows
                                        (fn [spec]
                                          (= (:sha256 spec)
                                             (:sha256 (:spec imported)))))
                               (:spec imported)))))))
