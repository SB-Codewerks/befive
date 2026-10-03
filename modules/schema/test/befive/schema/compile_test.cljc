(ns befive.schema.compile-test
  (:require [befive.schema.compile :as compile]
            [befive.schema.route :as route]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            #?(:clj [clojure.spec.test.alpha :as stest])))

#?(:clj
   (clojure.test/use-fixtures :once
     (fn [tests]
       (stest/instrument `[compile/expand compile/choose])
       (try (tests)
            (finally (stest/unstrument))))))

(def upstreams
  [{:id "up" :kind :http :targets [{:url "http://127.0.0.1:9"}]}])

(defn- orders
  [strategy & {:keys [v1 v2 aliases forward]}]
  (let [versioning (cond-> {:strategy strategy}
                     (= strategy :header)
                     (assoc :header {:name "api-version"}
                            :default-version "v1"
                            :aliases (or aliases {}))
                     (= strategy :query)
                     (assoc :query {:name "api-version"
                                    :forward (boolean forward)}
                            :default-version "v1"
                            :aliases (or aliases {}))
                     (= strategy :media-type)
                     (assoc :media-type
                            {:pattern "application/vnd.orders.{version}+json"}
                            :default-version "v1")
                     (= strategy :host)
                     (assoc :host {:template "{version}.api.example.com"}))]
    {:revision 1
     :upstreams upstreams
     :domain
     {:apis [{:id "orders"
              :name "Orders"
              :owners [{:group "platform"}]
              :versioning versioning}]
      :services [{:id "orders-svc" :name "Orders" :upstream "up"}]
      :versions [{:api "orders"
                  :id "v1"
                  :service "orders-svc"
                  :state (or v1 :published)}
                 {:api "orders"
                  :id "v2"
                  :service "orders-svc"
                  :state (or v2 :published)
                  :deprecation {:deprecated-at "2026-10-01T00:00:00Z"
                                :sunset-at "2027-04-01T00:00:00Z"
                                :link "https://example.com/migrate"}}]
      :operations [{:api "orders" :version "v1" :id "list-orders"
                    :method :get :path "/orders"}
                   {:api "orders" :version "v2" :id "list-orders"
                    :method :get :path "/orders"
                    :backend {:kind :proxy
                              :upstream-path "/internal/orders"}}]}}))

(defn- paths
  [document]
  (set (map :path (:routes document))))

(defn- by-version
  [document version]
  (some #(when (= version (:api-version %)) %) (:routes document)))

(deftest sha256-matches-the-abc-vector
  (is (= "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
         (compile/sha256-hex "abc")))
  (is (= "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
         (compile/sha256-hex "")))
  (is (= "4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c"
         (compile/sha256-hex "é")))
  (is (= "c4cc90ed3d26f12d4b08a75140970a7904035c31cbb4515a83f19b9003c00d1d"
         (compile/sha256-hex "€"))))

(deftest derived-ids-stay-within-63-characters
  (is (= "op-orders-v2-list-orders"
         (compile/derived-id "orders" "v2" "list-orders")))
  (let [long-id (compile/derived-id (apply str (repeat 40 "a"))
                                    (apply str (repeat 20 "b"))
                                    "list-orders")]
    (is (= 63 (count long-id)))
    (is (re-matches #".+-[0-9a-f]{8}" long-id))))

(deftest timestamps-are-unix-and-imf-fix
  (is (= 0 (compile/instant-seconds "1970-01-01T00:00:00Z")))
  (is (= "Thu, 01 Jan 1970 00:00:00 GMT"
         (compile/http-date "1970-01-01T00:00:00Z")))
  (is (= 1790812800 (compile/instant-seconds "2026-10-01T00:00:00Z")))
  (is (= "Thu, 01 Oct 2026 00:00:00 GMT"
         (compile/http-date "2026-10-01T00:00:00Z"))))

(deftest path-versions-compile-beside-hand-written-routes
  (let [hand {:id "health"
              :methods #{:get}
              :path "/health"
              :upstream "up"}
        expanded (compile/expand (assoc (orders :path) :routes [hand]))]
    (is (= #{"/health" "/v1/orders" "/v2/orders"} (paths expanded)))
    (is (= :path (:version-selected-by (by-version expanded "v1"))))
    (let [v2 (by-version expanded "v2")]
      (is (= "@1790812800" (:deprecation v2)))
      (is (= "Thu, 01 Apr 2027 00:00:00 GMT" (:sunset v2)))
      (is (re-find #"rel=\"deprecation\"" (:link v2)))
      (is (re-find #"rel=\"sunset\"" (:link v2)))
      (is (:deprecated v2))
      (is (= :active (:lifecycle v2)))
      (is (= "/internal/orders" (:upstream-path v2)))
      (is (s/valid? ::route/route v2)
          (pr-str (s/explain-data ::route/route v2))))))

(deftest design-versions-are-not-routed
  (let [expanded (compile/expand (orders :path :v1 :design))]
    (is (= #{"/v2/orders"} (paths expanded))))
  (let [expanded (compile/expand
                  (assoc-in (orders :path :v1 :design)
                            [:domain :allow-design-routes] true))]
    (is (= #{"/v1/orders" "/v2/orders"} (paths expanded)))))

(deftest retired-path-version-is-one-splat
  (let [expanded (compile/expand (orders :path :v1 :retired))
        retired (by-version expanded "v1")]
    (is (= "/v1/*path" (:path retired)))
    (is (= :retired (:lifecycle retired)))
    (is (not (some #(= "/v1/orders" (:path %)) (:routes expanded))))))

(deftest header-versions-share-one-route
  (let [expanded (compile/expand (orders :header))
        group (get (:version-groups expanded) "orders list-orders")]
    (is (= #{"/orders"} (paths expanded)))
    (is (= 2 (count group)))
    (is (= "v1" (:api-version (first (:routes expanded)))))
    (testing "selection"
      (let [chosen (compile/choose (first (:routes expanded))
                                   {:headers {"api-version" "v2"}}
                                   (:version-groups expanded))]
        (is (= "v2" (:api-version chosen)))
        (is (= :header (:version-selected-by chosen)))
        (is (= "api-version" (:vary chosen))))
      (let [chosen (compile/choose (first (:routes expanded))
                                   {:headers {}}
                                   (:version-groups expanded))]
        (is (= "v1" (:api-version chosen)))
        (is (= :default (:version-selected-by chosen))))
      (let [aliased (compile/expand (orders :header :aliases {"latest" "v2"}))
            chosen (compile/choose (first (:routes aliased))
                                   {:headers {"api-version" "latest"}}
                                   (:version-groups aliased))]
        (is (= "v2" (:api-version chosen))))
      (let [unknown (compile/choose (first (:routes expanded))
                                    {:headers {"api-version" "v9"}}
                                    (:version-groups expanded))]
        (is (= {:selection-error "version.unknown" :error "bad_request"}
               unknown))))))

(deftest host-versions-are-baked-into-the-host
  (let [expanded (compile/expand (orders :host))
        v1 (by-version expanded "v1")
        v2 (by-version expanded "v2")]
    (is (= #{"/orders"} (paths expanded)))
    (is (= 2 (count (:routes expanded))))
    (is (= "v1.api.example.com" (:host v1)))
    (is (= "v2.api.example.com" (:host v2)))
    (is (= :host (:version-selected-by v1)))))

(deftest query-selection-strips-the-parameter
  (let [expanded (compile/expand (orders :query))
        chosen (compile/choose (first (:routes expanded))
                               {:query-string "api-version=v2&x=1"}
                               (:version-groups expanded))
        omitted (compile/choose (first (:routes expanded))
                                {:query-string "x=1"}
                                (:version-groups expanded))]
    (is (= "v2" (:api-version chosen)))
    (is (= "api-version" (:strip-query chosen)))
    (is (= "x=1" (compile/strip-query "api-version=v2&x=1" "api-version")))
    (is (= "v1" (:api-version omitted)))
    (is (= :default (:version-selected-by omitted))))
  (let [expanded (compile/expand (orders :query :forward true))
        chosen (compile/choose (first (:routes expanded))
                               {:query-string "api-version=v2"}
                               (:version-groups expanded))]
    (is (nil? (:strip-query chosen)))))

(deftest unknown-media-type-is-not-acceptable
  (let [expanded (compile/expand (orders :media-type))
        request {:headers {"accept" "application/vnd.orders.v9+json"}}
        chosen (compile/choose (first (:routes expanded))
                               request
                               (:version-groups expanded))]
    (is (= "not_acceptable" (:error chosen)))
    (is (= "version.unknown_media_type" (:selection-error chosen))))
  (let [expanded (compile/expand (orders :media-type))
        chosen (compile/choose (first (:routes expanded))
                               {:headers {"accept" "*/*"}}
                               (:version-groups expanded))]
    (is (= "v1" (:api-version chosen)))))

(defn- rejection
  [document]
  (try
    (compile/expand document)
    nil
    (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) caught
      caught)))

(deftest a-hand-written-route-conflicts-with-a-derived-one
  (let [caught (rejection
                (assoc (orders :path)
                       :routes [{:id "hand"
                                 :methods #{:get}
                                 :path "/v1/orders"
                                 :upstream "up"}]))]
    (is (= "domain document rejected" (ex-message caught)))))

(deftest tenancy-refs-must-exist
  (let [caught (rejection
                {:revision 1
                 :upstreams upstreams
                 :domain {:consumers [{:id "billing"
                                       :name "Billing"
                                       :organization "missing"}]
                          :applications [{:id "app"
                                          :name "App"
                                          :consumer "missing"
                                          :owners [{:group "platform"}]}]
                          :credentials [{:id "cred"
                                         :application "missing"}]}})
        problem-paths (set (map :path (:problems (ex-data caught))))]
    (is (contains? problem-paths [:consumers "billing" :organization]))
    (is (contains? problem-paths [:applications "app" :consumer]))
    (is (contains? problem-paths [:credentials "cred" :application]))))

(deftest a-missing-service-is-rejected
  (let [caught (rejection (assoc-in (orders :path) [:domain :services] []))]
    (is (= "domain document rejected" (ex-message caught)))))

(deftest no-domain-leaves-hand-written-routes-alone
  (let [document {:revision 1 :routes [{:id "health"}] :upstreams []}
        expanded (compile/expand document)]
    (is (= [{:id "health"}] (:routes expanded)))
    (is (= {} (:version-groups expanded)))))
