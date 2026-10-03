(ns befive.schema.rbac-test
  (:require [befive.schema.rbac :as rbac]
            [clojure.test :refer [deftest is testing]]))

(def orders
  {:id "orders"
   :owners [{:group "platform"} {:user "kim"}]})

(deftest lifecycle-matrix
  (testing "publish and deprecate"
    (doseq [role [:administrator :operator :api-owner]
            action [:publish :deprecate]]
      (is (rbac/allowed? role action))))
  (testing "retire"
    (is (rbac/allowed? :administrator :retire))
    (is (rbac/allowed? :operator :retire))
    (is (not (rbac/allowed? :api-owner :retire)))))

(deftest api-owner-must-own-the-api
  (is (rbac/may-transition? :api-owner :publish orders {:group "platform"}))
  (is (rbac/may-transition? :api-owner :deprecate orders {:user "kim"}))
  (is (not (rbac/may-transition? :api-owner :publish orders {:user "sam"})))
  (is (not (rbac/may-transition? :api-owner :retire orders {:group "platform"})))
  (is (rbac/may-transition? :operator :retire orders nil))
  (is (rbac/may-transition? :administrator :retire orders nil)))
