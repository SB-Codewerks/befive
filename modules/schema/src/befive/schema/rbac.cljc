(ns befive.schema.rbac
  "Role data for version lifecycle. Enforcement arrives with the Admin API.
  API Owners may publish and deprecate APIs they own. Retire is Operator
  or Administrator only."
  (:require [clojure.spec.alpha :as s]))

(def roles
  #{:administrator :operator :api-owner})

(def transitions
  {:publish #{:administrator :operator :api-owner}
   :deprecate #{:administrator :operator :api-owner}
   :retire #{:administrator :operator}})

(s/def ::role roles)

(s/def ::action #{:publish :deprecate :retire})

(defn allowed?
  "True when `role` may perform `action`, ignoring ownership."
  [role action]
  (contains? (get transitions action #{}) role))

(defn owns?
  "True when `subject` is one of the API's owners.
  `subject` is `{:group name}` or `{:user name}`."
  [api subject]
  (boolean
   (some (fn [owner]
           (or (and (:group subject)
                    (= (:group owner) (:group subject)))
               (and (:user subject)
                    (= (:user owner) (:user subject)))))
         (:owners api))))

(defn may-transition?
  "API Owners must own the API. Other allowed roles do not."
  [role action api subject]
  (and (allowed? role action)
       (or (not= role :api-owner)
           (owns? api subject))))

(s/fdef allowed?
  :args (s/cat :role ::role :action ::action)
  :ret boolean?)

(s/fdef may-transition?
  :args (s/cat :role ::role
               :action ::action
               :api map?
               :subject (s/nilable map?))
  :ret boolean?)
