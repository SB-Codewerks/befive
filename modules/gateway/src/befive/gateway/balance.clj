(ns befive.gateway.balance
  "Smooth weighted round-robin, least-requests and consistent hash.
  Panic routing uses every target when the healthy share is below
  the threshold. The retry budget is 20 percent of requests."
  (:require [clojure.string :as str])
  (:import (java.util.concurrent.atomic AtomicLong)))

(set! *warn-on-reflection* true)

(def idempotent-methods
  #{:get :head :options :put :delete :trace})

(def retry-share
  "Retries may not exceed this share of admitted requests."
  0.2)

(defn budget
  []
  {:requests (AtomicLong. 0)
   :retries (AtomicLong. 0)})

(defn reserve-request!
  [counters]
  (.incrementAndGet ^AtomicLong (:requests counters)))

(defn reserve-retry!
  "True when another retry still fits in the 20 percent budget.
  The cap is `ceil(0.2 * requests)`, so one admitted request may
  retry once. The share settles at 20 percent as traffic grows."
  [counters]
  (let [requests (max 1 (.get ^AtomicLong (:requests counters)))
        retries (.incrementAndGet ^AtomicLong (:retries counters))
        allowed (long (Math/ceil (* retry-share requests)))]
    (if (<= retries allowed)
      true
      (do
        (.decrementAndGet ^AtomicLong (:retries counters))
        false))))

(defn- hash64
  "FNV-1a 64-bit. Stable across JVMs."
  [^String text]
  (let [prime (unchecked-long 0x100000001b3)
        basis (unchecked-long 0xcbf29ce484222325)]
    (unchecked-long
     (reduce (fn [^long acc ch]
               (unchecked-multiply (bit-xor acc (long (int ch)))
                                   prime))
             basis
             text))))

(defn eligible
  "Healthy targets, or every target when the healthy share is in panic."
  [targets threshold]
  (let [total (count targets)
        healthy (filter :healthy? targets)
        share (if (zero? total) 1.0 (/ (count healthy) (double total)))]
    (cond
      (zero? total) []
      (< share (double (or threshold 0.2))) (vec targets)
      :else (vec healthy))))

(defn panic?
  [targets threshold]
  (let [total (count targets)]
    (and (pos? total)
         (< (/ (count (filter :healthy? targets)) (double total))
            (double (or threshold 0.2))))))

(defn- replace-target
  [targets chosen]
  (mapv (fn [target]
          (if (= (:id target) (:id chosen))
            chosen
            target))
        targets))

(defn smooth-pick
  "Nginx smooth weighted round-robin. Returns `[chosen targets]`."
  [targets]
  (let [stepped (mapv (fn [target]
                        (update target :current
                                (fnil + 0) (long (:weight target 1))))
                      targets)
        total (reduce + 0 (map #(long (:weight % 1)) stepped))
        best (reduce (fn [best target]
                       (cond
                         (> (:current target) (:current best)) target
                         (and (= (:current target) (:current best))
                              (neg? (compare (:id target) (:id best))))
                         target
                         :else best))
                     stepped)]
    [(update best :current - total)
     (replace-target stepped (update best :current - total))]))

(defn least-pick
  "Fewest in-flight requests. Ties break toward the smaller id."
  [targets]
  (reduce (fn [best target]
            (cond
              (< (:inflight target 0) (:inflight best 0)) target
              (and (= (:inflight target 0) (:inflight best 0))
                   (neg? (compare (:id target) (:id best))))
              target
              :else best))
          targets))

(defn- ring
  [targets]
  (->> targets
       (mapcat (fn [target]
                 (map (fn [n]
                        [(hash64 (str (:id target) "#" n)) (:id target)])
                      (range 64))))
       (sort-by first #(Long/compareUnsigned (long %1) (long %2)))
       vec))

(defn hash-pick
  "Consistent hash of `key` over 64 points per target."
  [targets hash-key]
  (let [points (ring targets)
        hashed (hash64 (or hash-key ""))
        hit (or (some (fn [[point id]]
                        (when (>= (Long/compareUnsigned (long point)
                                                        hashed)
                                  0)
                          id))
                      points)
                (second (first points)))]
    (some #(when (= (:id %) hit) %) targets)))

(defn pick
  "Choose a target and return `[chosen updated-targets]`.
  `updated-targets` includes targets that were not eligible, unchanged
  except for the chosen target's bookkeeping."
  [upstream targets hash-key]
  (let [pool (eligible targets (:panic-threshold upstream 0.2))]
    (if (empty? pool)
      [nil targets]
      (let [[chosen updated]
            (case (:balance upstream :round-robin)
              :least-requests [(least-pick pool) targets]
              :consistent-hash [(hash-pick pool hash-key) targets]
              (let [[chosen stepped] (smooth-pick pool)]
                [chosen (reduce (fn [current target]
                                  (replace-target current target))
                                targets
                                stepped)]))]
        (if chosen
          [(update chosen :inflight (fnil inc 0))
           (replace-target updated
                           (update chosen :inflight (fnil inc 0)))]
          [nil updated])))))

(defn release-inflight
  [targets id]
  (mapv (fn [target]
          (if (= (:id target) id)
            (update target :inflight #(max 0 (dec (long (or % 0)))))
            target))
        targets))

(defn hash-key
  "Sticky key for consistent hash: the path and the query string."
  [request]
  (str (:uri request (:path request))
       (when-let [query (:query-string request)]
         (when-not (str/blank? query)
           (str "?" query)))))

(defn retryable-method?
  [upstream method]
  (contains? (or (:retry-methods upstream) idempotent-methods) method))
