(ns befive.core.health
  "Liveness and readiness as plain data.")

(set! *warn-on-reflection* true)

(defn new-health
  []
  (atom {:database :unknown
         :config :not-loaded
         :listeners :unbound
         :route-table nil
         :migrations-done? false}))

(defn mark!
  [health k value]
  (swap! health assoc k value)
  health)

(defn snapshot
  [health]
  @health)

(defn ready?
  "True when the database is up, config is loaded and listeners
  are bound."
  [snap]
  (boolean (and (= (:database snap) :up)
                (= (:config snap) :loaded)
                (= (:listeners snap) :bound))))

(defn next-snapshot
  "Return the next health value. `migrations-done?` is true for a
  gateway, which does not migrate, and for a control plane after
  its migration step."
  [snap {:keys [role database-up? migrations-done? listeners-bound?]}]
  (let [gateway? (contains? #{:gateway :all} role)
        control-plane? (contains? #{:control-plane :all} role)
        config-loaded? (and database-up?
                            (if control-plane?
                              migrations-done?
                              true))]
    (assoc snap
           :database (if database-up? :up :down)
           :config (if config-loaded? :loaded :not-loaded)
           :route-table (when (and gateway? database-up?)
                          {:revision 0 :routes []})
           :listeners (if listeners-bound? :bound :unbound)
           :migrations-done? (boolean migrations-done?))))

(defn migrations-done?
  [health role]
  (if (contains? #{:control-plane :all} role)
    (boolean (:migrations-done? (snapshot health)))
    true))
