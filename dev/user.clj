(ns user
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [integrant.core :as ig]
            [integrant.repl :refer [clear halt go init prep reset reset-all]]))

(defn- parse-dotenv-line [line]
  (let [line (str/trim line)]
    (when-not (or (str/blank? line) (str/starts-with? line "#"))
      (when-let [[k v] (some-> (str/split line #"=" 2) (->> (map str/trim)))]
        (let [v (or v "")
              quoted? (and (>= (count v) 2)
                           (#{\' \"} (first v))
                           (= (first v) (last v)))]
          [k (if quoted? (subs v 1 (dec (count v))) v)])))))

(defn- load-dotenv [path]
  (let [f (io/file path)]
    (when (.isFile f)
      (into {} (keep parse-dotenv-line) (str/split-lines (slurp f))))))

(def ^:private dotenv (load-dotenv ".env"))

;; Override Aero's built-inv #env so config.edn sees .env values
;; without needing the shell to export them before `clj -M:dev`
(defmethod aero/reader 'env [_ _ k]
  (or (get dotenv (str k)) (System/getenv (str k))))

(integrant.repl/set-prep!
 (fn []
   (let [config (aero/read-config (io/resource "config.edn") {:profile :dev})]
     (ig/load-namespaces config)
     config)))

(comment
  (go)
  (reset)
  (halt))