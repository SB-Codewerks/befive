(ns befive.openapi.core-test
  (:require [befive.openapi.core :as openapi]
            [befive.schema.domain :as domain]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is]]))

(def orders-yaml
  (str "openapi: 3.0.3\n"
       "info:\n"
       "  title: Orders\n"
       "  version: \"1.0.0\"\n"
       "paths:\n"
       "  /orders:\n"
       "    get:\n"
       "      operationId: listOrders\n"
       "      summary: List orders\n"
       "      responses:\n"
       "        \"200\":\n"
       "          description: ok\n"
       "    post:\n"
       "      summary: Create\n"
       "      responses:\n"
       "        \"201\":\n"
       "          description: created\n"
       "  /orders/{id}:\n"
       "    get:\n"
       "      operationId: getOrder\n"
       "      deprecated: true\n"
       "      callbacks:\n"
       "        onData:\n"
       "          x:\n"
       "            post:\n"
       "              responses:\n"
       "                \"200\":\n"
       "                  description: ok\n"
       "      responses:\n"
       "        \"200\":\n"
       "          description: ok\n"))

(def orders-json
  (str "{\"openapi\":\"3.1.0\",\"info\":{\"title\":\"Orders\","
       "\"version\":\"1.0.0\"},\"paths\":{\"/orders\":{\"get\":"
       "{\"operationId\":\"listOrders\",\"responses\":{\"200\":"
       "{\"description\":\"ok\"}}}}}}"))

(def remote-yaml
  (str "openapi: 3.0.3\n"
       "info:\n"
       "  title: Orders\n"
       "  version: \"1.0.0\"\n"
       "paths:\n"
       "  /orders:\n"
       "    $ref: https://127.0.0.1:1/orders.yaml\n"))

(def opts
  {:api "orders"
   :version "v1"
   :service "orders-svc"
   :owners [{:group "platform"}]
   :state :published
   :source :cli})

(defn- ids
  [result]
  (set (map :id (:operations result))))

(deftest yaml-and-json-import-operations
  (let [yaml (openapi/import-text orders-yaml opts)
        json (openapi/import-text orders-json (assoc opts :version "v2"))]
    (is (= #{"list-orders" "post-orders" "get-order"} (ids yaml)))
    (is (= "/orders/:id"
           (:path (some #(when (= "get-order" (:id %)) %)
                        (:operations yaml)))))
    (is (= #{"list-orders"} (ids json)))
    (is (some #(re-find #"callbacks" (:message %)) (:warnings yaml)))
    (is (some #(re-find #"sunset" (:message %)) (:warnings yaml)))
    (is (s/valid? ::domain/api (:api yaml)))
    (is (s/valid? ::domain/version (:version yaml)))
    (is (s/valid? ::domain/api-spec (:spec yaml)))))

(deftest remote-refs-are-not-fetched
  (let [result (openapi/import-text remote-yaml opts)]
    (is (empty? (:operations result)))
    (is (some #(re-find #"remote \$ref" (:message %)) (:warnings result)))))

(deftest reimport-shows-create-update-and-delete
  (let [before (openapi/import-text orders-yaml opts)
        changed (str "openapi: 3.0.3\n"
                     "info:\n"
                     "  title: Orders\n"
                     "  version: \"1.0.0\"\n"
                     "paths:\n"
                     "  /orders:\n"
                     "    get:\n"
                     "      operationId: listOrders\n"
                     "      summary: List everything\n"
                     "      responses:\n"
                     "        \"200\":\n"
                     "          description: ok\n"
                     "    delete:\n"
                     "      operationId: deleteOrders\n"
                     "      responses:\n"
                     "        \"204\":\n"
                     "          description: gone\n")
        after (openapi/import-text changed opts)
        diff (openapi/diff-operations (:operations before)
                                      (:operations after))]
    (is (= ["delete-orders"] (map :id (:create diff))))
    (is (= ["list-orders"] (map :id (:update diff))))
    (is (= #{"post-orders" "get-order"} (set (map :id (:delete diff)))))))

(deftest apply-import-keeps-other-versions
  (let [v1 (openapi/import-text orders-json (assoc opts :version "v1"))
        v2 (openapi/import-text orders-json (assoc opts :version "v2"))
        domain (-> {:services [{:id "orders-svc"
                                :name "Orders"
                                :upstream "up"}]}
                   (openapi/apply-import v1)
                   (openapi/apply-import v2))]
    (is (= ["orders"] (map :id (:apis domain))))
    (is (= #{"v1" "v2"} (set (map :id (:versions domain)))))
    (is (= 2 (count (:operations domain))))
    (is (s/valid? ::domain/document domain)
        (pr-str domain))))
