(ns befive.gateway.lambda.aws
  "Invoke Lambda with the URL-connection client so Aleph keeps the
  only Netty version on the classpath. No client is built at load.
  The client is synchronous. The call runs off the event loop."
  (:require [befive.gateway.block :as block]
            [manifold.deferred :as d])
  (:import (java.net URI)
           (java.time Duration)
           (software.amazon.awssdk.auth.credentials AwsBasicCredentials
                                                    StaticCredentialsProvider)
           (software.amazon.awssdk.awscore.exception AwsServiceException)
           (software.amazon.awssdk.core SdkBytes)
           (software.amazon.awssdk.core.client.config
            ClientOverrideConfiguration)
           (software.amazon.awssdk.core.exception ApiCallTimeoutException
                                                  SdkClientException)
           (software.amazon.awssdk.http.urlconnection UrlConnectionHttpClient)
           (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.lambda LambdaClient
                                                    LambdaClientBuilder)
           (software.amazon.awssdk.services.lambda.model
            InvocationType
            InvokeRequest
            InvokeRequest$Builder
            InvokeResponse
            ResourceNotFoundException
            TooManyRequestsException)))

(set! *warn-on-reflection* true)

(defn- override
  ^ClientOverrideConfiguration
  [^long timeout-ms]
  (-> (ClientOverrideConfiguration/builder)
      (.apiCallTimeout (Duration/ofMillis timeout-ms))
      (.apiCallAttemptTimeout (Duration/ofMillis timeout-ms))
      (.build)))

(defn client
  "A synchronous client. `endpoint` selects LocalStack and test credentials."
  [{:keys [region endpoint timeout-ms]}]
  (let [^LambdaClientBuilder builder (LambdaClient/builder)]
    (.httpClient builder (UrlConnectionHttpClient/create))
    (.region builder (Region/of (or region "us-east-1")))
    (let [^ClientOverrideConfiguration config
          (override (long (or timeout-ms 29000)))]
      (.overrideConfiguration builder config))
    (when endpoint
      (.endpointOverride builder (URI/create endpoint))
      (.credentialsProvider
       builder
       (StaticCredentialsProvider/create
        (AwsBasicCredentials/create "test" "test"))))
    (.build builder)))

(defn close!
  [^LambdaClient aws-client]
  (when aws-client
    (.close aws-client)))

(defn exception-reason
  "Translate an SDK failure into a Lambda error reason."
  [^Throwable thrown]
  (cond
    (instance? TooManyRequestsException thrown) "lambda.throttled"
    (instance? ApiCallTimeoutException thrown) "lambda.timeout"
    (instance? java.util.concurrent.TimeoutException thrown) "lambda.timeout"
    (instance? java.net.http.HttpTimeoutException thrown) "lambda.timeout"
    (instance? ResourceNotFoundException thrown) "lambda.invoke_failed"
    (and (instance? AwsServiceException thrown)
         (contains? #{401 403 404}
                    (.statusCode ^AwsServiceException thrown)))
    "lambda.invoke_failed"
    (instance? SdkClientException thrown) "lambda.invoke_failed"
    :else "lambda.invoke_failed"))

(defn retryable-exception?
  "Throttling and connection failures can be retried. Timeouts cannot."
  [^Throwable thrown]
  (or (instance? TooManyRequestsException thrown)
      (and (instance? SdkClientException thrown)
           (not (instance? ApiCallTimeoutException thrown)))))

(defn- interpret
  [^InvokeResponse response]
  (let [payload (.payload response)
        raw (if payload
              (.asByteArray payload)
              (byte-array 0))
        metadata (.responseMetadata response)]
    {:payload raw
     :function-error (.functionError response)
     :request-id (when metadata (.requestId metadata))}))

(defn- send-invoke
  ^InvokeResponse
  [^LambdaClient aws-client ^String function qualifier ^bytes payload]
  (let [^InvokeRequest$Builder builder (InvokeRequest/builder)]
    (.functionName builder function)
    (.invocationType builder InvocationType/REQUEST_RESPONSE)
    (.payload builder (SdkBytes/fromByteArray payload))
    (when qualifier
      (.qualifier builder (str qualifier)))
    (let [^InvokeRequest request (.build builder)]
      (.invoke aws-client request))))

(defn invoke
  "Invoke off the event loop. The deferred is a payload map or
  `{:reason :retryable}`."
  [^LambdaClient aws-client {:keys [function qualifier payload]}]
  (block/off-loop
   (fn []
     (try
       (interpret (send-invoke aws-client
                               (str function)
                               qualifier
                               payload))
       (catch Throwable thrown
         {:reason (exception-reason thrown)
          :retryable (retryable-exception? thrown)})))))

(defn transport
  "A function from an invoke map to a deferred result. SDK exceptions
  become `{:reason ...}` so the mapper can retry them."
  [^LambdaClient aws-client]
  (fn [call]
    (d/catch (invoke aws-client call)
             (fn [thrown]
               {:reason (exception-reason thrown)
                :retryable (retryable-exception? thrown)}))))

(defn cached-transport
  "One client per region and endpoint."
  [clients {:keys [region endpoint timeout-ms]}]
  (let [cache-key [(or region "us-east-1") endpoint (or timeout-ms 29000)]
        aws-client (locking clients
                     (or (get @clients cache-key)
                         (let [created (client {:region (or region "us-east-1")
                                                :endpoint endpoint
                                                :timeout-ms timeout-ms})]
                           (swap! clients assoc cache-key created)
                           created)))]
    (transport aws-client)))
