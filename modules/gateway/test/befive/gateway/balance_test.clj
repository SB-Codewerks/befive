(ns befive.gateway.balance-test
  (:require [befive.gateway.balance :as balance]
            [clojure.test :refer [deftest is]]))

(defn- target
  [id weight & {:keys [healthy? inflight current]
                :or {healthy? true inflight 0 current 0}}]
  {:id id
   :weight weight
   :healthy? healthy?
   :inflight inflight
   :current current
   :url (str "http://" id)})

(defn- counts
  [upstream targets picks]
  (loop [targets targets
         left picks
         seen {}]
    (if (zero? left)
      seen
      (let [[chosen updated] (balance/pick upstream targets nil)]
        (recur (balance/release-inflight updated (:id chosen))
               (dec left)
               (update seen (:id chosen) (fnil inc 0)))))))

(deftest smooth-weights-follow-the-nginx-ratio
  (let [upstream {:balance :round-robin :panic-threshold 0.2}
        targets [(target "a" 5) (target "b" 1) (target "c" 1)]]
    (is (= {"a" 50 "b" 10 "c" 10}
           (counts upstream targets 70)))))

(deftest inflight-returns-to-zero
  (let [upstream {:balance :round-robin :panic-threshold 0.2}
        [chosen updated] (balance/pick upstream [(target "a" 1)] nil)
        released (balance/release-inflight updated (:id chosen))]
    (is (= 1 (:inflight chosen)))
    (is (= 0 (:inflight (first released))))))

(deftest least-requests-ignores-weight-and-breaks-id-ties
  (let [upstream {:balance :least-requests :panic-threshold 0.0}
        targets [(target "b" 9 :inflight 0)
                 (target "a" 1 :inflight 0)
                 (target "c" 1 :inflight 2)]
        [chosen] (balance/pick upstream targets nil)]
    (is (= "a" (:id chosen)))))

(deftest consistent-hash-sticks-until-the-target-is-down
  (let [upstream {:balance :consistent-hash :panic-threshold 0.0}
        targets [(target "a" 5) (target "b" 1) (target "c" 1)]
        sticky "/orders?x=1"
        [first-pick] (balance/pick upstream targets sticky)
        [second-pick] (balance/pick upstream targets sticky)
        down (mapv #(if (= (:id %) (:id first-pick))
                      (assoc % :healthy? false)
                      %)
                   targets)
        [moved] (balance/pick upstream down sticky)]
    (is (= (:id first-pick) (:id second-pick)))
    (is (not= (:id first-pick) (:id moved)))
    (is (= (str "/orders?x=1")
           (balance/hash-key {:uri "/orders" :query-string "x=1"})))))

(deftest a-zero-panic-threshold-never-uses-an-unhealthy-target
  (let [targets [(target "a" 1 :healthy? false)]
        upstream {:balance :round-robin :panic-threshold 0.0}]
    (is (empty? (balance/eligible targets 0.0)))
    (is (nil? (first (balance/pick upstream targets nil))))
    (is (not (balance/panic? targets 0.0)))))

(deftest panic-opens-every-target-when-the-healthy-share-is-low
  (let [targets (conj (mapv #(target (str "h" %) 1) (range 4))
                      (target "down" 1 :healthy? false))]
    (is (= 5 (count (balance/eligible targets 0.9))))
    (is (balance/panic? targets 0.9))))

(deftest retry-budget-uses-the-ceiling
  (let [budget (balance/budget)]
    (balance/reserve-request! budget)
    (is (balance/reserve-retry! budget))
    (is (not (balance/reserve-retry! budget)))
    (is (not (balance/retryable-method? {} :post)))
    (is (balance/retryable-method? {:retry-methods #{:post}} :post))
    (is (balance/retryable-method? {} :get))))
