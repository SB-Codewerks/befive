(ns befive.schema.errors
  "Turn `s/explain-data` into `{:path :code :message}` problems.
  The message table is shared by the server, the CLI and the browser."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def type-pred-names
  #{"string?" "int?" "integer?" "boolean?" "map?" "keyword?"
    "uuid?" "inst?" "pos-int?" "nat-int?" "double?" "number?"
    "coll?" "vector?" "set?" "seq?" "ifn?" "fn?" "nil?"
    "false?" "true?" "bytes?" "char?"})

(def pattern-pred-names
  #{"slug-chars?" "cidr?" "http-url?" "header-name?" "jdbc-url?"})

(def closed-sentinel
  "Problem predicate for one unknown key in a closed map."
  :befive.schema/closed-keys)

(def default-messages
  {[:required] "is required"
   [:type] "has the wrong type"
   [:pattern] "has the wrong format"
   [:enum] "is not an allowed value"
   [:range] "is outside the allowed range"
   [:closed] "is not an allowed key"
   [:custom] "is invalid"})

(defonce messages (atom default-messages))

(defonce expanders (atom {}))

(defn register-messages!
  "Merge `table` into the shared message table.
  Keys are `[spec-keyword code]` or `[code]`."
  [table]
  (swap! messages merge table)
  nil)

(defn register-expander!
  "Register `f` under `k`. `f` takes one explain problem and returns
  a sequence of problems, or nil to leave the problem alone."
  [k f]
  (swap! expanders assoc k f)
  nil)

(defn- problems-of
  [explained]
  (or (:clojure.spec.alpha/problems explained)
      (:cljs.spec.alpha/problems explained)
      (some (fn [[k v]]
              (when (= (name k) "problems")
                v))
            explained)))

(defn- symbol-name
  [pred]
  (when (symbol? pred)
    (name pred)))

(defn- list-head
  [pred]
  (when (and (sequential? pred) (symbol? (first pred)))
    (name (first pred))))

(defn contains-key
  "The unqualified key named by a `contains?` predicate.
  `s/keys` wraps that call in `fn*`, so the search walks the form."
  [pred]
  (letfn [(from [form]
            (cond
              (and (sequential? form)
                   (= (list-head form) "contains?")
                   (>= (count form) 3)
                   (keyword? (nth form 2)))
              (keyword (name (nth form 2)))
              (sequential? form) (some from form)
              :else nil))]
    (from pred)))

(defn- mentions-int-in-range?
  [form]
  (letfn [(walk [node]
            (cond
              (and (symbol? node) (= (name node) "int-in-range?")) true
              (sequential? node) (some walk node)
              :else false))]
    (walk form)))

(defn problem-code
  "Classify an explain problem as `:required`, `:type`, `:pattern`,
  `:enum`, `:range`, `:closed` or `:custom`."
  [problem]
  (let [pred (:pred problem)
        head (list-head pred)
        n (symbol-name pred)]
    (cond
      (= pred closed-sentinel) :closed
      (set? pred) :enum
      (contains-key pred) :required
      (or (= n "int-in-range?")
          (= head "int-in-range?")
          (mentions-int-in-range? pred)) :range
      (and n (contains? type-pred-names n)) :type
      (and head (contains? type-pred-names head)) :type
      (contains? pattern-pred-names (or n head)) :pattern
      :else :custom)))

(defn- enum-message
  [pred]
  (str "must be one of: "
       (str/join ", "
                 (sort (map #(if (keyword? %) (name %) (str %))
                            pred)))))

(defn message-for
  "Look up a message by spec key and code, then by code."
  [spec-kw code problem]
  (or (get @messages [spec-kw code])
      (when (and (= code :enum) (set? (:pred problem)))
        (enum-message (:pred problem)))
      (get @messages [code])
      "is invalid"))

(defn- expand-problem
  [problem]
  (or (some (fn [f]
              (let [out (f problem)]
                (when (seq out)
                  out)))
            (vals @expanders))
      [problem]))

(defn- to-problem
  [problem]
  (let [code (problem-code problem)
        missing (when (= code :required)
                  (contains-key (:pred problem)))
        path (if (= (:pred problem) closed-sentinel)
               (vec (:path problem))
               (let [base (vec (:in problem))]
                 (if missing (conj base missing) base)))]
    {:path path
     :code code
     :message (message-for (last (:via problem)) code problem)}))

(defn- distinct-problems
  [problems]
  (:out
   (reduce (fn [acc {:keys [path code] :as problem}]
             (let [marker [(vec path) code]]
               (if (contains? (:seen acc) marker)
                 acc
                 (-> acc
                     (update :seen conj marker)
                     (update :out conj problem)))))
           {:seen #{} :out []}
           problems)))

(defn explain->problems
  "Problems for `value` against `spec`, or nil when it is valid.
  Each problem is `{:path [...] :code keyword :message string}`."
  [spec value]
  (when-let [explained (s/explain-data spec value)]
    (let [raw (or (problems-of explained) [])]
      (distinct-problems (map to-problem (mapcat expand-problem raw))))))

(s/fdef explain->problems
  :args (s/cat :spec any? :value any?)
  :ret (s/nilable vector?))

(defn format-problems
  "One path-qualified line per problem."
  [problems]
  (->> problems
       (map (fn [{:keys [path message]}]
              (str (pr-str (vec path)) " " message)))
       (str/join "\n")))

(s/fdef format-problems
  :args (s/cat :problems (s/coll-of map?))
  :ret string?)
