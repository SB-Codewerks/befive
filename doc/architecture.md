# Architecture

## Goal

Sit in front of a small set of backend REST APIs and provide:
1. Identity (who is this caller? — Okta-issued JWT)
2. Authorization (is this caller allowed on this route? — internal DB)
3. Rate limiting (have they exceeded their budget?)
4. Reverse proxy (forward to the correct upstream)
5. Audit (durable log of every request)

## Why Clojure

- Data-oriented routing (Reitit) makes the route table introspectable,
  which matters because the gateway's behavior IS the route table plus
  the interceptor chain.
- REPL-driven development: we can reconfigure permissions and routes
  against a live system during development without restart.
- JVM ecosystem: mature HTTP server (Jetty + Loom), mature HTTP client
  (JDK HttpClient via hato), mature JDBC.

## Why these libraries

- **Reitit over Pedestal**: both offer interceptors. Reitit has a larger
  community and a more flexible routing model. Pedestal was considered;
  the interceptor-async story is a wash now that virtual threads exist.
- **Jetty over Aleph**: virtual threads (JDK 21+) let us write blocking
  proxy code and still scale to tens of thousands of in-flight requests
  per node. Aleph would force manifold/deferred through the codebase
  for marginal gain.
- **hato over clj-http**: native JDK HttpClient, HTTP/2, no Apache
  HttpClient transitive dependencies.
- **buddy-sign over java-jwt directly**: idiomatic Clojure surface,
  handles JWKS-based verification cleanly.
- **next.jdbc over clojure.java.jdbc**: faster, simpler, actively
  maintained by the same author.
- **Integrant over Mount**: explicit dependency graph, restartable
  components, no globals.

## Request flow

    ┌────────┐   1. JWT in Authorization header
    │ client │──────────────────────────────────┐
    └────────┘                                  ▼
                                        ┌──────────────┐
                                        │   verify-jwt │  ── JWKS cache (TTL)
                                        └──────┬───────┘
                                               │  signature valid → claims
                                               ▼
                                        ┌──────────────┐
                                        │ load-claims  │  ── sub, scopes, etc
                                        └──────┬───────┘
                                               ▼
                                        ┌──────────────┐
                                        │ check-authz  │  ── Postgres lookup
                                        └──────┬───────┘     (cached per claim)
                                               ▼
                                        ┌──────────────┐
                                        │ check-limit  │  ── counter store
                                        └──────┬───────┘
                                               ▼
                                        ┌──────────────┐
                                        │ proxy-upstrm │  ── hato → backend
                                        └──────┬───────┘
                                               ▼
                                        ┌──────────────┐
                                        │ enqueue-log  │  ── core.async chan
                                        └──────────────┘     → batched insert

## JWT verification

- On startup, the JWKS interceptor pre-warms by fetching Okta's JWKS
  URL. Keys are cached in an atom with a TTL (default 1h) and refreshed
  lazily on cache miss.
- `kid` from the JWT header selects the key. Unknown `kid` triggers a
  forced JWKS refresh once before failing the request.
- Verified claims (`sub`, `email`, `scp`, `exp`) are attached to the
  request map under `:befive/claims`.

## Authorization

- A `(user_id, api, route_pattern, method)` lookup against Postgres
  determines whether a request is permitted.
- Permission rows are cached in-process keyed by `sub` with a short
  TTL (e.g. 60s). Cache invalidation is "wait it out"; permission
  changes are not real-time.
- Routes are matched by Reitit; `check-authz` receives the resolved
  route name, not the raw URI.

## Rate limiting

Three tiers of implementation, chosen by deployment scale:

1. **Postgres-only** (default for now): upserts on a per-user,
   per-window counter row. Acceptable up to a few hundred req/s.
2. **In-memory + periodic flush**: counters in an atom, flushed
   asynchronously. Loses counts on restart; usually fine.
3. **Redis (Carmine)**: token bucket via Lua script. Reserved for
   when horizontal scaling demands shared counter state.

The rate-limit interceptor depends on a `RateLimiter` protocol, so
swapping implementations is a config change.

## Request logging

- Every completed request (success or failure) is enqueued to a
  `core.async` channel by the `:leave` of the logging interceptor.
- A consumer drains the channel into batched multi-row inserts
  (target batch size 100, max delay 500ms).
- The `requests` table is range-partitioned by day. A retention job
  (separate concern) drops partitions older than N days.

## Failure modes and what happens

- Upstream API down → proxy interceptor returns 502 with structured
  error; request still logged with status 502.
- JWKS endpoint down → JWT verification fails closed (401) until
  cache refresh succeeds.
- Postgres down → befive returns 503; no request is served because
  authz and rate-limit cannot resolve. This is intentional — the
  gateway is the source of truth for "may this happen," not the
  upstreams.
- Log channel saturated → the producer applies backpressure;
  requests slow rather than fail. The channel size and drop policy
  are tunable in config.

## TODO

- **Dev secret loading.** `clj -M:dev → (go)` currently requires
  `DATABASE_URL` and `OKTA_JWKS_URL` to be exported in the shell
  before the REPL launches, because Aero's `#env` reads only
  `System/getenv` at config-read time. Two paths to decide between:
  1. **Pure Aero.** `#or [#env X "dev-default"]` under the `:dev`
     profile in `resources/config.edn`, with an `#include` of a
     gitignored EDN file for real secrets. One config system, no
     parser, no reader override. Preferred if `config.edn` stays the
     sole consumer.
  2. **Dotenv + custom `aero/reader 'env`.** Read `.env` from the
     project root in `dev/user.clj` and have the reader consult it
     before `System/getenv`. Preferred if `.env` becomes load-bearing
     for other tooling (`podman run --env-file`, compose, CI), since
     it keeps a single source of truth across processes.

  Decision driver: whether `.env` will feed anything besides the
  Clojure process.
