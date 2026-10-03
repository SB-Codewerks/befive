(ns befive.cp.migrate-test
  (:require [befive.cp.migrate :as migrate]
            [clojure.test :refer [deftest is]]))

(deftest parse-filename-keeps-up-migrations
  (is (= {:id 1
          :description "init"
          :file "migrations/V0001__init.sql"}
         (migrate/parse-filename "V0001__init.sql")))
  (is (nil? (migrate/parse-filename "V0001__init.down.sql")))
  (is (nil? (migrate/parse-filename "notes.sql")))
  (is (= 70551001 migrate/lock-id)))

(deftest split-sql-drops-comments-and-semicolons
  (is (= ["create table t (id int)"
          "insert into t (id) values (1)"]
         (migrate/split-sql
          (str "-- header\n"
               "create table t (id int);\n"
               "insert into t (id) values (1);"))))
  (is (= [] (migrate/split-sql "-- only a comment\n"))))

(deftest classpath-lists-numbered-migrations
  (let [found (migrate/migrations)]
    (is (= [1 2 3] (mapv :id found)))
    (is (= ["init" "config" "domain"] (mapv :description found)))
    (is (= 2 (count (:statements (first found)))))))
