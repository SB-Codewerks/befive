(ns befive.gateway.executor-test
  (:require [befive.gateway.executor :as executor]
            [clojure.test :refer [deftest is]]))

(deftest response-skips-later-enters-and-leaves-inside-out
  (let [order (atom [])
        phases [{:name :a
                 :enter (fn [ctx]
                          (swap! order conj :a-enter)
                          ctx)
                 :leave (fn [ctx]
                          (swap! order conj :a-leave)
                          ctx)}
                {:name :b
                 :enter (fn [ctx]
                          (swap! order conj :b-enter)
                          (assoc ctx :response {:status 200}))
                 :leave (fn [ctx]
                          (swap! order conj :b-leave)
                          ctx)}
                {:name :c
                 :enter (fn [ctx]
                          (swap! order conj :c-enter)
                          ctx)}]]
    (is (= 200 (:status (:response @(executor/execute phases {})))))
    (is (= [:a-enter :b-enter :b-leave :a-leave] @order))))

(deftest an-error-handler-that-sets-a-response-continues-leave
  (let [order (atom [])
        phases [{:name :a
                 :enter (fn [ctx]
                          (swap! order conj :a-enter)
                          ctx)
                 :leave (fn [ctx]
                          (swap! order conj :a-leave)
                          ctx)}
                {:name :b
                 :enter (fn [_]
                          (swap! order conj :b-enter)
                          (throw (ex-info "boom" {})))
                 :leave (fn [ctx]
                          (swap! order conj :b-leave)
                          ctx)
                 :error (fn [ctx]
                          (swap! order conj :b-error)
                          (assoc ctx :response {:status 502}))}]
        ctx @(executor/execute phases {})]
    (is (= 502 (:status (:response ctx))))
    (is (nil? (:error ctx)))
    (is (= [:a-enter :b-enter :b-error :a-leave] @order))))
