(ns befive.schema.errors-test
  (:require [befive.schema.core :as schema]
            [befive.schema.errors :as errors]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(s/def ::code #{:a :b})
(s/def ::age (s/int-in 0 10))
(s/def ::label ::schema/slug)
(s/def ::row (s/keys :req-un [::label]
                     :opt-un [::code ::age]))

(defn- problem
  [value path]
  (some #(when (= path (:path %)) %)
        (errors/explain->problems ::row value)))

(deftest codes-cover-the-message-table
  (is (= :required (:code (problem {} [:label]))))
  (is (= :type (:code (problem {:label 1} [:label]))))
  (is (= :pattern (:code (problem {:label "NO"} [:label]))))
  (is (= :enum (:code (problem {:label "a" :code :nope} [:code]))))
  (is (= :range (:code (problem {:label "a" :age 50} [:age]))))
  (is (re-find #"one of" (:message (problem {:label "a" :code :nope}
                                            [:code])))))

(deftest format-problems-qualifies-the-path
  (let [text (errors/format-problems
              (errors/explain->problems ::row {}))]
    (is (str/includes? text "[:label]"))
    (is (str/includes? text "is required"))))
