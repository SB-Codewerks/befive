(ns build
  "tools.build entry. The namespace stays `build` so
  `clojure -T:build` can find it."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.build.api :as b])
  (:import (java.io PushbackReader)
           (java.time LocalDate)))

(def version
  "0.1.0-SNAPSHOT")

(def class-dir "target/classes")

(def src-dirs
  ["modules/schema/src"
   "modules/plugin-api/src"
   "modules/policy/src"
   "modules/openapi/src"
   "modules/core/src"
   "modules/core/resources"
   "modules/gateway/src"
   "modules/control-plane/src"
   "modules/control-plane/resources"
   "modules/app/src"
   "modules/app/resources"
   "modules/anomaly/src"
   "modules/cli/src"
   "modules/aws-recipe/src"
   "modules/datadog-recipe/src"
   "modules/script-runner/src"])

(def reflection-nses
  '[befive.core.json
    befive.core.codec
    befive.core.response
    befive.core.health
    befive.core.logging
    befive.core.server
    befive.core.db
    befive.core.settings
    befive.core.healthcheck
    befive.core.version
    befive.gateway.block
    befive.gateway.client-ip
    befive.gateway.errors
    befive.gateway.executor
    befive.gateway.headers
    befive.gateway.router
    befive.gateway.net
    befive.gateway.balance
    befive.gateway.limits
    befive.gateway.access-log
    befive.gateway.lambda
    befive.gateway.lambda.aws
    befive.gateway.targets
    befive.gateway.health
    befive.gateway.compile
    befive.gateway.table
    befive.gateway.lkg
    befive.gateway.sync
    befive.gateway.proxy
    befive.gateway.pipeline
    befive.gateway.http
    befive.gateway.embed
    befive.cp.http
    befive.cp.migrate])

(defn- fail!
  [message]
  (binding [*out* *err*]
    (println message))
  (System/exit 1))

(defn- java-cmd
  "Java executable, without resolving symlinks.
  tools.build canonicalizes `java`. On hosts where that path is
  the libalternatives dispatcher, the dispatcher rejects JVM
  arguments unless it is invoked as java."
  []
  (or (System/getenv "JAVA_CMD")
      (when-let [home (System/getenv "JAVA_HOME")]
        (let [exe (io/file home "bin" "java")]
          (when (.canExecute exe)
            (.getPath exe))))
      (some (fn [dir]
              (let [exe (io/file dir "java")]
                (when (and (.isFile exe) (.canExecute exe))
                  (.getPath exe))))
            (str/split (or (System/getenv "PATH") "")
                       (re-pattern java.io.File/pathSeparator)))
      "java"))

(defn git-sha
  "Short git revision, or \"unknown\" when git or the checkout
  is absent. The image build has neither."
  []
  (try
    (let [process (ProcessBuilder. ["git" "rev-parse" "--short" "HEAD"])]
      (.redirectErrorStream process true)
      (let [running (.start process)
            out (str/trim (slurp (.getInputStream running)))]
        (if (and (zero? (.waitFor running)) (seq out))
          out
          "unknown")))
    (catch java.io.IOException _
      "unknown")))

(defn reflection
  "Fail when gateway, core or migration code reflects.
  The check runs in a forked JVM on the project classpath.
  `clojure -T` does not put project sources or libraries on the
  tool classpath, so requiring those namespaces here cannot work."
  [_]
  (let [basis (b/create-basis {:project "deps.edn"})
        script (str "(doseq [sym '" (pr-str reflection-nses) "]"
                    " (require sym))")
        result (b/process
                (assoc (b/java-command
                        {:java-cmd (java-cmd)
                         :basis basis
                         :main 'clojure.main
                         :main-args ["-e" script]})
                       :out :capture
                       :err :capture))
        err (or (:err result) "")
        ours (filter #(and (str/starts-with? % "Reflection warning")
                           (str/includes? % "modules/"))
                     (str/split-lines err))]
    (cond
      (seq ours)
      (fail! (str/join "\n" ours))

      (not (zero? (:exit result)))
      (fail! (str "reflection check failed\n" err
                  (when (seq (:out result))
                    (str "\n" (:out result))))))))

(defn- write-version!
  [sha]
  (let [file (io/file class-dir "befive" "version.edn")]
    (io/make-parents file)
    (spit file
          (pr-str {:version version
                   :git-sha sha
                   :build-date (str (LocalDate/now))}))))

(defn uber
  "Build target/befive-<version>.jar."
  [_]
  (let [basis (b/create-basis {:project "deps.edn"})
        jar (str "target/befive-" version ".jar")]
    (reflection nil)
    (b/delete {:path "target"})
    (b/compile-clj {:java-cmd (java-cmd)
                    :basis basis
                    :src-dirs ["modules/app/src"
                               "modules/core/src"
                               "modules/gateway/src"
                               "modules/control-plane/src"
                               "modules/schema/src"
                               "modules/plugin-api/src"
                               "modules/policy/src"
                               "modules/openapi/src"
                               "modules/anomaly/src"
                               "modules/cli/src"
                               "modules/aws-recipe/src"
                               "modules/datadog-recipe/src"
                               "modules/script-runner/src"]
                    :class-dir class-dir
                    :ns-compile ['befive.main]
                    :compile-opts {:direct-linking true}})
    (b/copy-dir {:src-dirs src-dirs :target-dir class-dir})
    (write-version! (git-sha))
    (b/uber {:class-dir class-dir
             :uber-file jar
             :basis basis
             :main 'befive.main})
    (println "wrote" jar)))

(defn- json-escape
  [text]
  (-> text
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n")))

(defn- json
  [value]
  (cond
    (nil? value) "null"
    (true? value) "true"
    (false? value) "false"
    (number? value) (str value)
    (string? value) (str "\"" (json-escape value) "\"")
    (keyword? value) (json (name value))
    (map? value)
    (str "{"
         (str/join ","
                   (map (fn [[k v]]
                          (str (json (if (keyword? k) (name k) (str k)))
                               ":"
                               (json v)))
                        value))
         "}")
    (sequential? value)
    (str "[" (str/join "," (map json value)) "]")
    :else (json (str value))))

(defn- lib-versions
  []
  (let [resolved (map (fn [aliases]
                        (b/create-basis {:project "deps.edn"
                                         :aliases aliases}))
                      [[] [:test] [:cljs] [:lint] [:fmt] [:build]])]
    (reduce
     (fn [acc basis]
       (reduce-kv
        (fn [acc lib info]
          (if-let [ver (:mvn/version info)]
            (assoc acc [lib ver] basis)
            acc))
        acc
        (:libs basis)))
     {}
     resolved)))

(defn sbom
  "Write a minimal CycloneDX 1.5 bill of materials."
  [_]
  (let [components
        (mapv (fn [[[lib ver] _basis]]
                (let [group (or (namespace lib) "")
                      artifact (name lib)]
                  {:type "library"
                   :group group
                   :name artifact
                   :version ver
                   :purl (str "pkg:maven/" group "/" artifact
                              "@" ver)}))
              (sort-by (fn [[[lib ver]]] [(str lib) ver])
                       (lib-versions)))
        document {:bomFormat "CycloneDX"
                  :specVersion "1.5"
                  :version 1
                  :metadata {:component {:type "application"
                                         :name "befive"
                                         :version version}}}
        file (io/file "target" "befive-sbom.cdx.json")]
    (io/make-parents file)
    (spit file (json (assoc document :components components)))
    (println "wrote" (.getPath file))))

(defn- forbidden-alternative?
  [text]
  (cond
    (or (str/includes? text "affero")
        (str/includes? text "agpl")) true
    (or (str/includes? text "lesser")
        (str/includes? text "lgpl")
        (str/includes? text "classpath")) false
    (or (str/includes? text "general public license")
        (re-find #"(^|[^a-z])gpl([^a-z]|$)" text)) true
    :else false))

(defn- forbidden-license-text?
  "True when every alternative in a license expression is GPL
  or AGPL without a classpath or lesser exception."
  [text]
  (let [parts (-> text
                  str/lower-case
                  (str/split #"\s+or\s+|\s*\|\|\s*|\s*\|\s*"))]
    (and (seq parts) (every? forbidden-alternative? parts))))

(defn- pom-file
  [local-repo group artifact pom-version]
  (io/file local-repo
           (str/replace group "." "/")
           artifact
           pom-version
           (str artifact "-" pom-version ".pom")))

(defn- parent-coords
  [xml]
  (when-let [block (second (re-find #"(?s)<parent>(.*?)</parent>" xml))]
    (let [group (second (re-find #"<groupId>([^<]+)</groupId>" block))
          artifact (second (re-find #"<artifactId>([^<]+)</artifactId>"
                                    block))
          ver (second (re-find #"<version>([^<]+)</version>" block))]
      (when (and group artifact ver
                 (not (str/includes? ver "${")))
        [group artifact ver]))))

(defn- license-blocks
  [file local-repo depth seen]
  (cond
    (or (nil? file) (not (.isFile file)) (contains? seen file) (> depth 6))
    []
    :else
    (let [xml (slurp file)
          blocks (mapv second
                       (re-seq #"(?s)<license>(.*?)</license>" xml))]
      (if (seq blocks)
        blocks
        (if-let [[group artifact ver] (parent-coords xml)]
          (license-blocks (pom-file local-repo group artifact ver)
                          local-repo
                          (inc depth)
                          (conj seen file))
          [])))))

(defn- license-name
  [block]
  (or (second (re-find #"<name>([^<]*)</name>" block))
      (second (re-find #"<url>([^<]*)</url>" block))
      "unknown"))

(defn- maven-rows
  []
  (for [[[lib ver] basis] (sort-by (fn [[[lib ver]]] [(str lib) ver])
                                   (lib-versions))
        :let [group (namespace lib)
              artifact (name lib)
              local-repo (:mvn/local-repo basis)
              pom (when group (pom-file local-repo group artifact ver))
              blocks (license-blocks pom local-repo 0 #{})]]
    {:label (str lib " " ver)
     :names (if (seq blocks) (mapv license-name blocks) ["unknown"])
     :texts blocks}))

(defn- npm-rows
  []
  (let [file (io/file "package-lock.json")]
    (if-not (.isFile file)
      []
      (let [text (slurp file)
            names (concat
                   (map second
                        (re-seq #"\"license\"\s*:\s*\"([^\"]+)\"" text))
                   (map second
                        (re-seq
                         (re-pattern
                          (str "\"license\"\\s*:\\s*\\{\\s*"
                               "\"type\"\\s*:\\s*\"([^\"]+)\""))
                         text)))]
        (mapv (fn [license]
                {:label (str "npm " license)
                 :names [license]
                 :texts [license]})
              (distinct names))))))

(defn licenses
  "Write THIRD_PARTY_LICENSES.md. Exit 1 on GPL or AGPL."
  [_]
  (let [rows (concat (maven-rows) (npm-rows))
        forbidden (filter (fn [{:keys [texts]}]
                            (and (seq texts)
                                 (every? forbidden-license-text? texts)))
                          rows)
        lines (map (fn [{:keys [label names]}]
                     (str "- " label " — " (str/join "; " names)))
                   rows)
        file (io/file "THIRD_PARTY_LICENSES.md")]
    (spit file
          (str "# Third-party licenses\n\n"
               "Generated by `clojure -T:build licenses`.\n\n"
               (str/join "\n" lines)
               "\n"))
    (println "wrote" (.getPath file))
    (when (seq forbidden)
      (fail! (str "forbidden GPL or AGPL license:\n"
                  (str/join "\n" (map :label forbidden)))))))

(defn- read-ns
  [file]
  (with-open [reader (PushbackReader. (io/reader file))]
    (binding [*read-eval* false]
      (loop []
        (let [form (read {:eof ::eof
                          :read-cond :allow
                          :features #{:clj}}
                         reader)]
          (cond
            (= form ::eof) nil
            (and (seq? form) (= (first form) 'ns)) form
            :else (recur)))))))

(defn- require-symbols
  [spec]
  (cond
    (symbol? spec) [spec]
    (and (vector? spec)
         (symbol? (first spec))
         (some vector? (rest spec)))
    (mapv (fn [sub]
            (symbol (str (first spec) "." (first sub))))
          (filter vector? (rest spec)))
    (vector? spec) [(first spec)]
    :else []))

(defn- clause-symbols
  [ns-form clause]
  (->> ns-form
       (filter #(and (sequential? %) (= (first %) clause)))
       (mapcat rest)
       (mapcat require-symbols)
       (remove nil?)))

(defn- allowed-prefix?
  [sym prefixes]
  (let [text (str sym)]
    (or (str/starts-with? text "clojure.")
        (str/starts-with? text "cljs.")
        (some #(str/starts-with? text %) prefixes))))

(defn- violations
  [ns-sym requires imports]
  (let [n (str ns-sym)
        bad (fn [pred] (filter pred requires))]
    (cond
      (str/starts-with? n "befive.schema.")
      (remove #(allowed-prefix? % ["befive.schema."]) requires)

      (str/starts-with? n "befive.plugin.")
      (remove #(allowed-prefix? % ["befive.schema."]) requires)

      (str/starts-with? n "befive.policy.")
      (remove #(allowed-prefix? % ["befive.schema."]) requires)

      ;; OpenAPI may call its own namespaces. The parser is Java-only
      ;; and the mapper stays on schema, so the module still does not
      ;; see the gateway or the control plane.
      (str/starts-with? n "befive.openapi.")
      (remove #(allowed-prefix? % ["befive.schema." "befive.openapi."])
              requires)

      (str/starts-with? n "befive.gateway.")
      (bad #(str/starts-with? (str %) "befive.cp."))

      (str/starts-with? n "befive.cp.")
      (bad (fn [sym]
             (and (str/starts-with? (str sym) "befive.gateway.")
                  (not= sym 'befive.gateway.embed))))

      (str/starts-with? n "befive.cli.")
      (concat
       (bad (fn [sym]
              (let [text (str sym)]
                (or (str/starts-with? text "befive.core")
                    (str/starts-with? text "befive.gateway.")
                    (str/starts-with? text "befive.cp.")
                    (str/starts-with? text "aleph.")
                    (str/starts-with? text "manifold.")
                    (str/starts-with? text "next.jdbc")
                    (str/includes? text "hikari")))))
       (filter (fn [sym]
                 (str/includes? (str/lower-case (str sym)) "hikari"))
               imports))

      (str/starts-with? n "befive.runner.")
      (bad #(str/starts-with? (str %) "befive.core"))

      :else [])))

(defn- source-files
  []
  (let [sep java.io.File/separator
        src-mark (str sep "src" sep)
        test-mark (str sep "test" sep)]
    (->> (file-seq (io/file "modules"))
         (filter (fn [^java.io.File file]
                   (let [path (.getPath file)
                         filename (.getName file)]
                     (and (.isFile file)
                          (str/includes? path src-mark)
                          (not (str/includes? path test-mark))
                          (or (str/ends-with? filename ".clj")
                              (str/ends-with? filename ".cljc")
                              (str/ends-with? filename ".cljs")))))))))

(defn module-deps
  "Fail when a module requires a namespace it must not see."
  [_]
  (let [found
        (reduce
         (fn [acc file]
           (let [form (read-ns file)
                 ns-sym (second form)
                 bad (violations ns-sym
                                 (clause-symbols form :require)
                                 (clause-symbols form :import))]
             (if (seq bad)
               (conj acc (str (.getPath file) " -> " (vec bad)))
               acc)))
         []
         (source-files))]
    (when (seq found)
      (fail! (str "module dependency violation:\n"
                  (str/join "\n" found))))
    (println "module dependencies ok")))
