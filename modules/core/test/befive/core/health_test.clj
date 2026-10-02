(ns befive.core.health-test
  (:require [befive.core.health :as health]
            [clojure.test :refer [deftest is]]))

(deftest ready-requires-database-config-and-listeners
  (is (health/ready? {:database :up
                      :config :loaded
                      :listeners :bound}))
  (is (not (health/ready? {:database :down
                           :config :loaded
                           :listeners :bound})))
  (is (not (health/ready? {:database :up
                           :config :not-loaded
                           :listeners :bound}))))

(deftest gateway-loads-an-empty-route-table
  (let [updated (health/next-snapshot
                 @(health/new-health)
                 {:role :gateway
                  :database-up? true
                  :migrations-done? false
                  :listeners-bound? true})]
    (is (= :loaded (:config updated)))
    (is (= {:revision 0 :routes []} (:route-table updated)))))

(deftest control-plane-waits-for-migrations
  (let [waiting (health/next-snapshot
                 @(health/new-health)
                 {:role :control-plane
                  :database-up? true
                  :migrations-done? false
                  :listeners-bound? true})
        done (health/next-snapshot
              @(health/new-health)
              {:role :control-plane
               :database-up? true
               :migrations-done? true
               :listeners-bound? true})]
    (is (= :not-loaded (:config waiting)))
    (is (nil? (:route-table waiting)))
    (is (= :loaded (:config done)))))

(deftest gateway-reports-migrations-done
  (let [state (health/new-health)]
    (is (health/migrations-done? state :gateway))
    (is (not (health/migrations-done? state :control-plane)))
    (health/mark! state :migrations-done? true)
    (is (health/migrations-done? state :all))))
