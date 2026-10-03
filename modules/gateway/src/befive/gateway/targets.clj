(ns befive.gateway.targets
  "Mutable target state. The compiled route table stays immutable.
  Health, in-flight counts and the retry budget live here."
  (:require [befive.gateway.balance :as balance]
            [befive.gateway.lambda :as lambda]))

(set! *warn-on-reflection* true)

(defn- same-target
  [previous target]
  (merge target
         (select-keys previous
                      [:healthy? :current :inflight :failures :probed-at])))

(defn- fresh-target
  [upstream target previous]
  (same-target
   previous
   {:id (or (:id target) (:url target))
    :url (:url target)
    :weight (long (:weight target 1))
    :healthy? true
    :current 0
    :inflight 0
    :failures 0
    :probed-at 0
    :health (:health upstream)}))

(defn reconcile
  "Keep health for targets that are still in the revision."
  [state upstreams]
  (into {}
        (map (fn [[id upstream]]
               (let [previous (get state id)
                     old (into {}
                               (map (juxt :id identity))
                               (:targets previous []))]
                 [id
                  {:upstream upstream
                   :budget (or (:budget previous) (balance/budget))
                   :limiter (or (:limiter previous)
                                (when (= :lambda (:kind upstream))
                                  (let [config (:lambda upstream)]
                                    (lambda/limiter
                                     (:max-concurrency config 100)
                                     (:max-pending config 200)))))
                   :targets (mapv (fn [target]
                                    (let [tid (or (:id target) (:url target))]
                                      (fresh-target upstream target
                                                    (get old tid))))
                                  (:targets upstream))}])))
        upstreams))

(defn reconcile!
  [targets-atom upstreams]
  (locking targets-atom
    (swap! targets-atom reconcile upstreams)
    targets-atom))

(defn entry
  [targets-atom id]
  (get @targets-atom id))

(defn pick!
  [targets-atom upstream-id hash-key]
  (locking targets-atom
    (when-let [row (get @targets-atom upstream-id)]
      (let [[chosen updated] (balance/pick (:upstream row)
                                           (:targets row)
                                           hash-key)]
        (swap! targets-atom assoc-in [upstream-id :targets] updated)
        chosen))))

(defn release!
  [targets-atom upstream-id target-id]
  (locking targets-atom
    (swap! targets-atom update-in [upstream-id :targets]
           (fn [targets]
             (balance/release-inflight (or targets []) target-id)))))

(defn- update-target!
  [targets-atom upstream-id target-id f]
  (locking targets-atom
    (swap! targets-atom update-in [upstream-id :targets]
           (fn [targets]
             (mapv (fn [target]
                     (if (= (:id target) target-id)
                       (f target)
                       target))
                   (or targets []))))))

(defn note-success!
  [targets-atom upstream-id target-id]
  (update-target! targets-atom upstream-id target-id
                  (fn [target]
                    (assoc target
                           :failures 0
                           :healthy? true
                           :probed-at (System/currentTimeMillis)))))

(defn note-failure!
  [targets-atom upstream-id target-id unhealthy-after]
  (update-target! targets-atom upstream-id target-id
                  (fn [target]
                    (let [failures (inc (:failures target 0))]
                      (assoc target
                             :failures failures
                             :healthy? (< failures (long unhealthy-after))
                             :probed-at (System/currentTimeMillis))))))
