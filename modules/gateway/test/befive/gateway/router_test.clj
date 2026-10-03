(ns befive.gateway.router-test
  (:require [befive.gateway.router :as router]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]))

(defn- route
  [id path method & {:keys [host priority]}]
  (cond-> {:id id
           :path path
           :methods #{method}
           :upstream "upstream"
           :priority (or priority 0)}
    host (assoc :host host)))

(defn- view
  [result]
  {:status (:status result)
   :id (get-in result [:route :id])
   :allow (:allow result)
   :params (not-empty (:path-params result))})

(defn- agree?
  [routes request]
  (= (view (router/match (router/compile-routes routes) request))
     (view (router/oracle-match routes request))))

(deftest static-template-beats-a-parameter
  (let [routes [(route "param" "/orders/:id" :get)
                (route "static" "/orders/new" :get)]]
    (is (= "static"
           (:id (view (router/match
                       (router/compile-routes routes)
                       {:method :get :path "/orders/new"})))))
    (is (= {:id "abc"}
           (:params (view (router/match
                           (router/compile-routes routes)
                           {:method :get :path "/orders/abc"})))))))

(deftest one-template-merges-methods-and-returns-405
  (let [routes [(route "get" "/orders" :get)
                (route "post" "/orders" :post)]
        compiled (router/compile-routes routes)
        denied (router/match compiled {:method :delete :path "/orders"})]
    (is (= :method-not-allowed (:status denied)))
    (is (= #{:get :post} (:allow denied)))
    (is (agree? routes {:method :delete :path "/orders"}))
    (is (agree? routes {:method :post :path "/orders"}))))

(deftest wildcard-host-needs-an-extra-label
  (let [routes [(route "wild" "/v1" :get :host "*.example.com")
                (route "exact" "/v1" :post :host "a.example.com")]
        compiled (router/compile-routes routes)]
    (is (= "exact"
           (:id (view (router/match compiled
                                    {:host "a.example.com"
                                     :method :post
                                     :path "/v1"})))))
    (is (= "wild"
           (:id (view (router/match compiled
                                    {:host "b.example.com"
                                     :method :get
                                     :path "/v1"})))))
    (is (= :not-found
           (:status (router/match compiled
                                  {:host "example.com"
                                   :method :get
                                   :path "/v1"}))))
    (is (= "exact"
           (:id (view (router/match compiled
                                    {:host "A.Example.com:443"
                                     :method :post
                                     :path "/v1"})))))))

(deftest splat-is-only-legal-at-the-end
  (is (thrown? clojure.lang.ExceptionInfo
               (router/compile-template "/files/*path/extra"))))

(deftest ipv6-host-keeps-the-literal-and-drops-the-port
  (is (= "[::1]" (router/hostname "[::1]:443")))
  (is (= "api.example.com" (router/hostname "API.Example.com:8080"))))

(deftest a-shorter-wildcard-is-tried-when-the-longer-one-misses
  (let [routes [(route "short" "/v1/:name" :get :host "*.com")
                (route "long" "/" :get :host "*.example.com")]
        request {:host "api.example.com"
                 :method :get
                 :path "/v1/item"}]
    (is (= "short"
           (:id (view (router/oracle-match routes request)))))
    (is (agree? routes request))
    (is (agree? routes {:host "api.example.com"
                        :method :post
                        :path "/files/a/b"}))))

(deftest catch-all-matches-the-root
  (let [routes [(route "all" "/*path" :get)]
        request {:method :get :path "/"}]
    (is (= :matched
           (:status (router/oracle-match routes request))))
    (is (agree? routes request))
    (is (agree? routes {:method :get :path "/a/b"}))))

(deftest two-thousand-routes-compile-within-200ms
  (let [routes (mapv (fn [n]
                       (route (str "r" n) (str "/r/" n) :get))
                     (range 2000))]
    (router/compile-routes (subvec routes 0 20))
    (let [started (System/nanoTime)
          compiled (router/compile-routes routes)
          elapsed (/ (- (System/nanoTime) started) 1000000.0)]
      (is (< elapsed 200.0) elapsed)
      (is (= "r1999"
             (:id (view (router/match compiled
                                      {:method :get :path "/r/1999"}))))))))

(def ^:private templates
  ["/" "/orders" "/orders/new" "/orders/:id"
   "/orders/:id/items" "/files/*path" "/v1/:name"])

(def ^:private hosts
  [nil "api.example.com" "API.Example.com" "*.example.com" "*.com"])

(defn- concrete
  [template]
  (cond
    (= template "/") "/"
    (re-find #":id" template) (str/replace template ":id" "abc")
    (re-find #":name" template) (str/replace template ":name" "item")
    (re-find #"\*path" template) "/files/a/b"
    :else template))

(deftest ^:generative compiled-router-matches-the-oracle
  (let [result
        (tc/quick-check
         40
         (prop/for-all
          [routes (gen/vector
                   (gen/fmap
                    (fn [[id template method host priority]]
                      (route (str "r" id) template method
                             :host host
                             :priority priority))
                    (gen/tuple (gen/choose 0 40)
                               (gen/elements templates)
                               (gen/elements [:get :post :put :delete])
                               (gen/elements hosts)
                               (gen/choose 0 3)))
                   1 12)
           template (gen/elements templates)
           method (gen/elements [:get :post :delete :head])
           host (gen/elements hosts)]
          (agree? routes {:host host
                          :method method
                          :path (concrete template)})))]
    (is (:pass? result) (pr-str result))))
