(ns befive.mock-idp.main
  "Tiny OIDC issuer for the local stack. It can be marked down
  without losing its own health check."
  (:gen-class)
  (:require [aleph.http :as http]
            [jsonista.core :as j]))

(def down?
  (atom false))

(def jwks
  {"keys" [{"kty" "oct"
            "kid" "mock"
            "alg" "HS256"
            "k" "bW9jay1pZHAtc2VjcmV0"}]})

(defn- json
  [status body]
  {:status status
   :headers {"content-type" "application/json"}
   :body (j/write-value-as-string body)})

(defn- discovery
  [request]
  (let [host (or (get-in request [:headers "host"]) "localhost:8088")
        base (str "http://" host)]
    {"issuer" base
     "authorization_endpoint" (str base "/oauth2/v1/authorize")
     "token_endpoint" (str base "/oauth2/v1/token")
     "jwks_uri" (str base "/oauth2/v1/keys")
     "introspection_endpoint" (str base "/oauth2/v1/introspect")
     "response_types_supported" ["code"]
     "subject_types_supported" ["public"]
     "id_token_signing_alg_values_supported" ["RS256"]}))

(defn handler
  [request]
  (let [uri (:uri request)]
    (cond
      (= uri "/healthz") (json 200 {"status" "ok"})
      (= uri "/control/down")
      (do (reset! down? true) (json 200 {"down" true}))
      (= uri "/control/up")
      (do (reset! down? false) (json 200 {"down" false}))
      @down? (json 503 {"error" "down"})
      (= uri "/.well-known/openid-configuration")
      (json 200 (discovery request))
      (= uri "/oauth2/v1/keys") (json 200 jwks)
      (= uri "/oauth2/v1/token")
      (json 200 {"access_token" "mock-token"
                 "token_type" "Bearer"
                 "expires_in" 3600})
      (= uri "/oauth2/v1/introspect")
      (json 200 {"active" true
                 "sub" "mock-user"
                 "scope" "openid"})
      :else (json 404 {"error" "not-found"}))))

(defn -main
  [& _]
  (let [port (or (parse-long (or (System/getenv "MOCK_IDP_PORT") ""))
                 8088)]
    (println "mock idp listening on" port)
    (http/start-server handler {:port port :join? true})))
