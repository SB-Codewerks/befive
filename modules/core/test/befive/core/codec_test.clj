(ns befive.core.codec-test
  (:require [befive.core.codec :as codec]
            [clojure.test :refer [deftest is]]))

(def document
  {:apis [{:id "orders"
           :name "Orders"
           :owners [{:group "platform"}]
           :tags #{"commerce" "public"}
           :versioning {:strategy :path
                        :path {:template "/{version}"}}
           :visibility {:audience :restricted
                        :groups #{"platform"}
                        :users #{"kim"}
                        :organizations #{"acme"}}}]
   :versions [{:api "orders"
               :id "v1"
               :service "orders"
               :state :published}]
   :operations [{:api "orders"
                 :version "v1"
                 :id "list-orders"
                 :method :get
                 :path "/orders"}]
   :organizations [{:id "acme"
                    :name "Acme"
                    :kind :partner
                    :status :active
                    :portal {:okta-groups #{"acme-dev"}
                             :email-domains #{"acme.example"}}}]
   :applications [{:id "billing"
                   :kind :service
                   :environment-scope :production}]})

(deftest documents-round-trip
  (doseq [codec [:edn :json :transit]]
    (is (= document (codec/round-trip codec document))
        (name codec))))

(deftest edn-does-not-evaluate
  (is (thrown? Exception
               (codec/read-edn "#=(java.lang.System/exit 0)"))))
