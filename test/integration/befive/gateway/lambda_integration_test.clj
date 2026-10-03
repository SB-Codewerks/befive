(ns befive.gateway.lambda-integration-test
  "Invoke a LocalStack function through the gateway pipeline."
  (:require [befive.core.json :as json]
            [befive.gateway.access-log :as access-log]
            [befive.gateway.compile :as compile]
            [befive.gateway.lambda.aws :as aws]
            [befive.gateway.pipeline :as pipeline]
            [befive.gateway.targets :as targets]
            [clj-commons.byte-streams :as bs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]])
  (:import (java.io ByteArrayOutputStream)
           (java.net URI)
           (java.nio.charset StandardCharsets)
           (java.time Duration)
           (org.testcontainers.containers GenericContainer)
           (org.testcontainers.containers.wait.strategy Wait)
           (org.testcontainers.utility DockerImageName)
           (software.amazon.awssdk.auth.credentials AwsBasicCredentials
                                                    StaticCredentialsProvider)
           (software.amazon.awssdk.core SdkBytes)
           (software.amazon.awssdk.core.client.config
            ClientOverrideConfiguration)
           (software.amazon.awssdk.http.urlconnection UrlConnectionHttpClient)
           (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.lambda LambdaClient)
           (software.amazon.awssdk.services.lambda.model
            CreateFunctionRequest
            FunctionCode
            ResourceConflictException)))

(def ^:private handler-source
  (str "import json\n"
       "def handler(event, context):\n"
       "    body = {\n"
       "        'version': event.get('version'),\n"
       "        'rawPath': event.get('rawPath'),\n"
       "        'pathParameters': event.get('pathParameters'),\n"
       "        'headers': event.get('headers') or {},\n"
       "    }\n"
       "    return {\n"
       "        'statusCode': 200,\n"
       "        'headers': {'content-type': 'application/json'},\n"
       "        'body': json.dumps(body),\n"
       "    }\n"))

(defn- zip-bytes
  ^bytes [^String entry ^String source]
  (let [out (ByteArrayOutputStream.)]
    (with-open [^java.util.zip.ZipOutputStream zip
                (java.util.zip.ZipOutputStream. out)]
      (.putNextEntry zip (java.util.zip.ZipEntry. entry))
      (.write zip (.getBytes source StandardCharsets/UTF_8))
      (.closeEntry zip))
    (.toByteArray out)))

(defn- start-localstack
  ^GenericContainer []
  (doto (GenericContainer.
         (DockerImageName/parse "localstack/localstack:4.14.0"))
    (.withEnv "SERVICES" "lambda")
    (.withFileSystemBind "/var/run/docker.sock"
                         "/var/run/docker.sock")
    ;; SELinux denies the socket otherwise (A-19). Ignored when it is off.
    (.withCreateContainerCmdModifier
     (reify java.util.function.Consumer
       (accept [_ cmd]
         (let [host (.getHostConfig cmd)]
           (.withHostConfig
            cmd
            (.withSecurityOpts
             host
             (java.util.ArrayList. ["label=disable"])))))))
    (.withExposedPorts (into-array Integer [(Integer/valueOf 4566)]))
    (.waitingFor (-> (Wait/forHttp "/_localstack/health")
                     (.forPort 4566)
                     (.forStatusCode 200)
                     (.forResponsePredicate
                      (reify java.util.function.Predicate
                        (test [_ body]
                          (boolean
                           (re-find #"\"lambda\"\s*:\s*\"(?:available|running)\""
                                    (str body))))))
                     (.withStartupTimeout (Duration/ofMinutes 3))))
    (.withStartupTimeout (Duration/ofMinutes 3))
    (.start)))

(defn- admin-client
  ^LambdaClient [^String endpoint]
  (let [^software.amazon.awssdk.services.lambda.LambdaClientBuilder
        builder (LambdaClient/builder)]
    (.httpClient builder (UrlConnectionHttpClient/create))
    (.overrideConfiguration
     builder
     (-> (ClientOverrideConfiguration/builder)
         (.apiCallTimeout (Duration/ofMinutes 2))
         (.apiCallAttemptTimeout (Duration/ofMinutes 2))
         (.build)))
    (.region builder (Region/of "us-east-1"))
    (.endpointOverride builder (URI/create endpoint))
    (.credentialsProvider
     builder
     (StaticCredentialsProvider/create
      (AwsBasicCredentials/create "test" "test")))
    (.build builder)))

(defn- create-echo!
  [^LambdaClient client]
  (let [^bytes zip (zip-bytes "lambda_function.py" handler-source)
        code (-> (FunctionCode/builder)
                 (.zipFile (SdkBytes/fromByteArray zip))
                 (.build))
        request (-> (CreateFunctionRequest/builder)
                    (.functionName "echo")
                    (.runtime
                     software.amazon.awssdk.services.lambda.model.Runtime/PYTHON3_12)
                    (.role "arn:aws:iam::000000000000:role/lambda")
                    (.handler "lambda_function.handler")
                    (.code code)
                    (.build))]
    (try
      (.createFunction client request)
      (catch ResourceConflictException _))))

(defn- gateway
  [endpoint]
  (let [document {:revision 1
                  :routes [{:id "echo"
                            :methods #{:get}
                            :path "/echo/:name"
                            :upstream "fn"}]
                  :upstreams [{:id "fn"
                               :kind :lambda
                               :lambda {:function "echo"
                                        :region "us-east-1"
                                        :endpoint endpoint
                                        :timeout-ms 20000}}]}
        compiled (compile/compile-snapshot document)
        targets (atom {})]
    (targets/reconcile! targets (:upstreams compiled))
    {:settings {:node-id "node-1" :environment "dev"}
     :table (atom compiled)
     :targets targets
     :pools (atom {})
     :lambda-clients (atom {})
     :ready-revision (atom (:revision compiled))
     :access-log (access-log/start {})
     :datasource nil}))

(defn- invoke-until
  [state]
  (let [deadline (+ (System/currentTimeMillis) 180000)]
    (loop []
      (let [response (deref (pipeline/handle
                             state
                             {:request-method :get
                              :uri "/echo/widget"
                              :scheme :http
                              :remote-addr "203.0.113.8"
                              :headers {"host" "api.example.com"
                                        "x-befive-subject" "forged"}})
                            25000
                            ::timeout)]
        (cond
          (= 200 (:status response)) response
          (> (System/currentTimeMillis) deadline) response
          :else (do
                  (Thread/sleep 1000)
                  (recur)))))))

(deftest ^:integration localstack-echoes-the-payload-v2-event
  (let [container (start-localstack)]
    (try
      (let [port (.getMappedPort container 4566)
            endpoint (str "http://127.0.0.1:" port)
            client (admin-client endpoint)]
        (try
          (loop [left 30]
            (let [created (try
                            (create-echo! client)
                            :ok
                            (catch Exception _
                              (when (zero? left)
                                (throw _))
                              :retry))]
              (when (= created :retry)
                (Thread/sleep 1000)
                (recur (dec left)))))
          (let [state (gateway endpoint)]
            (try
              (let [response (invoke-until state)
                    body (when (:body response)
                           (json/read-str (bs/to-string (:body response))))]
                (is (= 200 (:status response)) response)
                (is (= "2.0" (:version body)))
                (is (= "/echo/widget" (:rawPath body)))
                (is (= {:name "widget"} (:pathParameters body)))
                (is (nil? (get (:headers body) :x-befive-subject)))
                (is (not (str/includes?
                          (str (:headers body))
                          "forged"))))
              (finally
                (access-log/stop! (:access-log state))
                (doseq [cached (vals @(:lambda-clients state))]
                  (aws/close! cached)))))
          (finally
            (.close client))))
      (finally
        (.stop container)))))
