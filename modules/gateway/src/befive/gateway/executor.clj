(ns befive.gateway.executor
  "Sieppari-style interceptor executor.
  Each phase has `:enter`, `:leave` and `:error`. A phase returns
  a context or a Manifold deferred of a context. `:response` skips
  the remaining enters. `:error` runs inside-out and, when it sets
  `:response`, leave continues outward."
  (:require [manifold.deferred :as d]))

(set! *warn-on-reflection* true)

(defn- realize
  [value]
  (if (d/deferred? value)
    value
    (d/success-deferred value nil)))

(defn- step
  [phase ctx slot]
  (try
    (realize (if-let [f (get phase slot)]
               (f ctx)
               ctx))
    (catch Throwable t
      (d/error-deferred t))))

(defn execute
  "Run `phases` and return a deferred of the final context."
  [phases ctx]
  (letfn [(leave [entered ctx]
            (if (empty? entered)
              (d/success-deferred ctx nil)
              (d/chain (step (first entered) ctx :leave)
                       (fn [ctx'] (leave (rest entered) ctx')))))
          (on-error [entered ctx t]
            (if (empty? entered)
              (d/error-deferred t)
              (let [phase (first entered)
                    rest-entered (rest entered)]
                (if-let [handler (:error phase)]
                  (d/catch
                   (d/chain (step (assoc phase :error handler)
                                  (assoc ctx :error t)
                                  :error)
                            (fn [ctx']
                              (if (:response ctx')
                                (leave rest-entered (dissoc ctx' :error))
                                (on-error rest-entered ctx' t))))
                   (fn [t2]
                     (on-error rest-entered ctx t2)))
                  (on-error rest-entered ctx t)))))
          (enter [remaining entered ctx]
            (if (empty? remaining)
              (leave entered ctx)
              (let [phase (first remaining)]
                (d/catch
                 (d/chain (step phase ctx :enter)
                          (fn [ctx']
                            (let [entered' (cons phase entered)]
                              (if (:response ctx')
                                (leave entered' ctx')
                                (enter (rest remaining) entered' ctx')))))
                 (fn [t]
                   (on-error (cons phase entered) ctx t))))))]
    (enter phases () ctx)))
