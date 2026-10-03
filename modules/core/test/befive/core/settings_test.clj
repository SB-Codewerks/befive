(ns befive.core.settings-test
  (:require [aero.core :as aero]
            [befive.core.settings :as node]
            [befive.schema.settings :as settings]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]])
  (:import (java.util.concurrent TimeUnit)))

(deftest aero-readers-coerce-env-strings
  (node/install-readers!)
  (is (true? (aero/reader nil 'boolean "yes")))
  (is (false? (aero/reader nil 'boolean "no")))
  (is (= :befive.core.settings/invalid
         (aero/reader nil 'boolean "maybe")))
  (is (= 42 (aero/reader nil 'long "42")))
  (is (= :gateway (aero/reader nil 'keyword "gateway"))))

(deftest blank-node-id-and-proxies-are-normalized
  (let [result (node/prepare-map
                {:node-id ""
                 :db-url "jdbc:postgresql://localhost/befive"
                 :db-user "befive"
                 :db-password "befive"
                 :redis-uri "  "
                 :trusted-proxies "10.0.0.0/8, 192.168.0.0/16"})
        value (:settings result)]
    (is (nil? (:exit result)))
    (is (s/valid? ::settings/settings value))
    (is (not (str/blank? (:node-id value))))
    (is (not (contains? value :redis-uri)))
    (is (= ["10.0.0.0/8" "192.168.0.0/16"] (:trusted-proxies value)))
    (is (= :all (:role value)))))

(deftest password-file-replaces-the-inline-password
  (let [file (io/file (System/getProperty "java.io.tmpdir")
                      "befive-settings-password.txt")]
    (spit file "s3cret\n")
    (try
      (let [result (node/prepare-map
                    (assoc (node/example)
                           :db-password "ignored"
                           :db-password-file (.getAbsolutePath file)))]
        (is (= "s3cret" (:db-password (:settings result)))))
      (finally
        (.delete file)))))

(deftest missing-password-file-exits-78
  (let [result (node/prepare-map
                (assoc (node/example)
                       :db-password-file
                       "/tmp/befive-missing-password-file"))]
    (is (= 78 (:exit result)))
    (is (str/includes? (:message result) "[:db-password-file]"))))

(deftest invalid-role-exits-78
  (let [result (node/prepare-map
                (assoc (node/example) :role :nope))]
    (is (= 78 (:exit result)))
    (is (str/includes? (:message result) "[:role]"))))

(deftest blank-snapshot-file-is-removed
  (let [result (node/prepare-map
                (assoc (node/example)
                       :snapshot-file "  "
                       :lkg-dir "/tmp/befive-lkg"))]
    (is (nil? (:exit result)) (:message result))
    (is (not (contains? (:settings result) :snapshot-file)))
    (is (= "/tmp/befive-lkg" (:lkg-dir (:settings result))))))

(deftest bundled-settings-prepare
  (let [result (node/prepare)]
    (is (nil? (:exit result)) (:message result))
    (is (s/valid? ::settings/settings (:settings result)))))

(deftest process-exits-78
  (let [process (ProcessBuilder. ["clojure" "-M" "-m" "befive.main"])
        env (.environment process)
        path (or (System/getenv "PATH") "")]
    (.put env "PATH" (str (System/getenv "HOME") "/.local/bin:" path))
    (.put env "BEFIVE_ROLE" "nope")
    (.redirectErrorStream process true)
    (let [running (.start process)
          finished? (.waitFor running 180 TimeUnit/SECONDS)
          out (slurp (.getInputStream running))]
      (when-not finished?
        (.destroyForcibly running))
      (is finished? out)
      (is (= 78 (.exitValue running)) out)
      (is (str/includes? out "[:role]") out))))
