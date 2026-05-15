# Befive

A Clojure API gateway: authenticates OAuth2/JWT requests against Okta,
authorizes them against an internal RDBMS permission model, applies
rate limits, reverse-proxies to several backend REST APIs, and logs
every request to Postgres for usage visibility.

## Stack

- **Routing**: Reitit + Ring + Jetty (JDK 21 virtual threads enabled)
- **HTTP client**: hato (JDK HttpClient wrapper, HTTP/2)
- **Auth**: buddy-sign for JWT; JWKS fetched and cached from Okta
- **Database**: Postgres via next.jdbc + HoneySQL + HikariCP;
  Migratus for migrations
- **Lifecycle**: Integrant (system as data, restart-friendly)
- **Config**: Aero (environment-aware EDN)
- **Observability**: μ/log (mulog) for structured events

Rationale, alternatives considered, and tradeoffs are in
`doc/architecture.md`. Read that before proposing stack changes.

## Request lifecycle

Jetty → Reitit interceptor chain:

    verify-jwt → load-claims → check-route-permission
      → check-rate-limit → proxy-upstream
      → (:leave) enqueue-log-entry

Each interceptor lives in its own namespace under
`befive.interceptors.*` and is independently testable.
The chain is composed in `befive.routes`.

## Code layout

    src/befive/
      core.clj             ; -main entrypoint
      system.clj           ; integrant config + start/stop
      config.clj           ; aero loading
      routes.clj           ; reitit route data + interceptor chain
      interceptors/
        auth.clj           ; JWT signature verification
        claims.clj         ; pull user/roles from validated claims
        authz.clj          ; route-permission lookup
        ratelimit.clj      ; rate-limit check + decrement
        proxy.clj          ; upstream forwarding via hato
        logging.clj        ; async enqueue to request log
      db/
        core.clj           ; datasource + jdbc helpers
        permissions.clj    ; permission queries
        ratelimits.clj     ; rate-limit reads + counter writes
        requests.clj       ; batched request-log inserts
      okta/
        jwks.clj           ; JWKS fetch + TTL cache

## Conventions

- One namespace per file. Alias every require; never `:refer :all`.
- Side-effecting functions end in `!` (e.g. `log-request!`).
- DB functions take an explicit datasource as their first argument.
  No global db var. This keeps tests and the REPL sane.
- Errors are signaled with `(throw (ex-info msg {:type ::some-kw ...}))`.
  Interceptors catch and translate to HTTP responses; no bare exceptions
  escape the chain.
- SQL is HoneySQL maps in application code; raw SQL is fine in
  migration files only.
- Observability events use mulog: `(u/log ::event-name :k v ...)`.
  Do not use `println` for anything that survives a commit.

## Build / run / test

- Dev REPL:        `clj -M:dev`, then `(go)` / `(reset)` in `user` ns
- Run tests:       `clj -M:test`
- Build uberjar:   `clj -T:build uber`
- Run uberjar:     `java -jar target/befive-0.1.0-standalone.jar`
- Migrations:      invoked from REPL via `(migratus.core/migrate cfg)`
                   or the `:migrate` build task

## When working in this codebase

- Read the relevant interceptor namespace before adding to its chain.
- Prefer adding an interceptor over inlining behavior in `proxy.clj`.
- Schema changes require a new Migratus migration file. Never alter
  tables ad hoc, even in dev.
- New routes go in `routes.clj` as data. The route table is read at
  system start from Integrant config; no hot-reload of routes outside
  REPL `reset`.
- Verify behavior by `(reset)` in the REPL and issuing a real request
  (curl or httpie against localhost) before declaring a change complete.
- If you add a dependency, justify it in the PR description and update
  `doc/architecture.md` if it replaces or changes a stack choice.

## Non-goals

- Not a service mesh — assumes a small, statically configured set of
  backend APIs.
- Not multi-tenant at the auth layer — single Okta org.
- Not a response cache — purely a control-plane gateway.
- Not a transformation engine — bodies pass through unmodified unless
  an explicit interceptor says otherwise.

## Deeper context

- `doc/architecture.md` — design rationale, alternatives, request flow
- `doc/data-model.md` — schema, indexes, partitioning, retention
- `doc/conventions.md` — Clojure style specifics, error handling, logging
