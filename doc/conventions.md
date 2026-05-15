# Conventions

## Namespaces

- File path mirrors namespace exactly: `befive.interceptors.auth`
  lives at `src/befive/interceptors/auth.clj`.
- Every `require` is aliased. Common aliases used across the codebase:
    [next.jdbc :as jdbc]
    [next.jdbc.sql :as sql]
    [honey.sql :as hsql]
    [honey.sql.helpers :as h]
    [integrant.core :as ig]
    [com.brunobonacci.mulog :as u]
    [hato.client :as http]
- No `:refer :all`. `:refer [...]` is acceptable for `clojure.test`
  and similar test-only utilities.

## Functions

- Pure functions in `befive.db.*` namespaces take a `db` (datasource)
  as the first arg. Never close over a global.
- Side-effecting functions end in `!`. Examples: `start-server!`,
  `enqueue-log!`, `migrate!`.
- Multi-arity for "with config" vs "with config + opts":
    (defn fetch-permissions
      ([db user-id]      (fetch-permissions db user-id {}))
      ([db user-id opts] ...))

## Errors

- Throw `ex-info` with a structured `:type` keyword:
    (throw (ex-info "JWT signature invalid"
                    {:type ::auth/invalid-signature
                     :kid  kid}))
- Interceptors catch via a top-level error interceptor in the chain
  and translate `:type` to an HTTP response code.
- Never log secrets or full tokens in ex-data.

## Observability

- Structured events via mulog:
    (u/log ::request-proxied
           :api api
           :route route-name
           :status status
           :duration-ms ms)
- One mulog publisher in production (JSON to stdout, scraped by the
  log aggregator); a console publisher in dev.
- Correlation id (`:request-id`) is set in the request map by the
  first interceptor and included in every mulog event for that
  request via `u/with-context`.

## Tests

- `test/` mirrors `src/`. A namespace `befive.foo.bar` is tested in
  `befive.foo.bar-test`.
- DB tests use `next.jdbc/with-transaction` with `:rollback-only true`
  to leave no residue.
- Interceptors are tested by calling the interceptor function with a
  fabricated context map. No HTTP server is started in unit tests.
- An integration test namespace boots a minimal Integrant system
  against a test database (configured in `resources/config.edn`
  under the `:test` profile).

## Configuration

- All configuration is loaded from `resources/config.edn` via Aero.
- Per-environment overrides are expressed with `#profile {:dev ...
  :prod ...}` rather than separate files.
- Secrets are pulled via `#env` and never committed.

## Commits / PRs

- Schema changes ship in their own commit with the migration file.
- New dependencies are justified in the PR description and reflected
  in `docs/architecture.md` if they change a stack choice.
- Behavioral changes update or add tests in the same PR.
