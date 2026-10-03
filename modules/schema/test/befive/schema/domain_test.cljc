(ns befive.schema.domain-test
  (:require [befive.schema.domain :as domain]
            [befive.schema.errors :as errors]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(def api
  {:id "orders"
   :name "Orders"
   :owners [{:group "platform"}]
   :description "Order API"
   :x-team "commerce"})

(deftest api-accepts-an-annotation-and-rejects-unknown-keys
  (is (s/valid? ::domain/api api))
  (is (not (s/valid? ::domain/api (assoc api :extra true))))
  (is (not (s/valid? ::domain/api (assoc api :owners [])))))

(deftest owner-is-exactly-one-of-group-or-user
  (is (domain/owner? {:group "platform"}))
  (is (domain/owner? {:user "kim"}))
  (is (not (domain/owner? {:group "platform" :user "kim"})))
  (is (not (domain/owner? {})))
  (is (not (domain/owner? {:group ""}))))

(deftest backend-is-proxy-only
  (is (s/valid? ::domain/backend {:kind :proxy}))
  (is (s/valid? ::domain/backend
                {:kind :proxy :upstream-path "/internal/orders"}))
  (is (not (s/valid? ::domain/backend {:kind :lambda})))
  (is (not (s/valid? ::domain/backend {:kind :composite})))
  (is (not (s/valid? ::domain/backend {:kind :mock}))))

(deftest tenancy-links-use-slug-refs
  (let [document {:organizations [{:id "acme" :name "Acme"}]
                  :consumers [{:id "billing"
                               :name "Billing"
                               :organization "acme"}]
                  :applications [{:id "billing-default"
                                  :name "Billing"
                                  :consumer "billing"
                                  :owners [{:user "kim"}]}]
                  :credentials [{:id "cred_01"
                                 :application "billing-default"
                                 :kind :api-key}]
                  :specs [{:sha256 (apply str (repeat 64 "a"))
                           :format :json
                           :openapi-version "3.1.0"}]
                  :policy-attachments
                  [{:id "pa_orders_ip"
                    :scope {:level :api :api "orders"}
                    :kind :ip
                    :value {:allow ["10.0.0.0/8"]}
                    :locked true}]}]
    (is (s/valid? ::domain/document document)
        (pr-str (errors/explain->problems ::domain/document document)))))

(deftest version-state-may-be-omitted
  (is (s/valid? ::domain/version
                {:api "orders" :id "v1" :service "orders"}))
  (is (s/valid? ::domain/version
                {:api "orders"
                 :id "v2"
                 :service "orders"
                 :state :published
                 :match {:strategy :header
                         :header {:name "api-version"}}})))

(deftest template-keys-ignore-parameter-names
  (is (= (domain/template-key :get "/orders/{id}")
         (domain/template-key :get "/orders/:orderId")
         [:get "/orders/:p"]))
  (let [result (tc/quick-check
                30
                (prop/for-all [param (gen/not-empty gen/string-alphanumeric)]
                  (let [path (str "/orders/{" param "}")]
                    (= (domain/template-key :get path)
                       [:get "/orders/:p"]))))]
    (is (:pass? result) (pr-str result))))

(deftest default-application-id-appends-default
  (is (= "billing-default" (domain/default-application-id "billing"))))

(deftest visibility-organizations-are-slugs
  (is (s/valid? ::domain/visibility
                {:audience :restricted
                 :organizations #{"acme"}}))
  (is (s/valid? ::domain/api
                (assoc api
                       :visibility {:audience :public-portal
                                    :organizations #{"acme"}})))
  (is (s/valid? ::domain/document
                {:organizations [{:id "acme" :name "Acme"}]})))

(deftest versioning-fills-the-path-default
  (is (= {:strategy :path
          :path {:template "/{version}"}
          :default-version "v2"}
         (domain/versioning-of {:default-version "v2"} {:id "v1"})))
  (is (= :header
         (:strategy (domain/versioning-of
                     {:versioning {:strategy :path}}
                     {:match {:strategy :header
                              :header {:name "api-version"}}})))))
