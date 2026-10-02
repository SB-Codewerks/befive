# Admin API, b5ctl and the plugin SPI

**Relevance:** 0.x: build (milestone 6; the route tester and effective policy are needed for milestones 4 and 8). 1.0-only resources are listed at the end. **Sources:** 02 §15 (Admin API), §16 (CLI and config as code), §17 (plugin SPI), 01 §2.12, 03 §5.13 (config-as-code screen).

## 1. Conventions (02 §15.1)

- Base path `/admin/v1` on port 9000. Changes within v1 are additive.
- Media types: JSON (default), EDN (CLI), Transit (console), all through Muuntaja with identical kebab-case keys.
  - In JSON, keyword values are strings. Coercion back to keywords uses the **spec registry**: a spec whose form is `#{…}` of keywords, or which is marked with `:befive/keyword true` in the form-metadata registry, is coerced from a string.
- `PUT /kind/{id}` creates or replaces (idempotent). `POST` to the collection creates with the id in the body. `PATCH` uses JSON Merge Patch (RFC 7396). `DELETE` removes.
- Server-managed fields go under `meta` and are ignored on input.
- Every response carries `X-BeFive-Revision`. `X-Request-ID` is accepted or generated, and audited.

## 2. Resources needed in 0.x (02 §15.2)

| Resource | Notes |
|---|---|
| `/services`, `/upstreams` (+ `/{id}/health`), `/routes`, `/policies` (+ `/{id}/usage`), `/consumer-groups`, `/plans` | CRUD. Deleting a referenced object returns 409 |
| `/organizations`, `/consumers`, `/applications`, `/applications/{id}/credentials` (issue; `/rotate` with `{"grace-days":7}`; revoke). `/subscriptions` is 1.0 | Keys are returned once |
| `/consumers/{id}/usage` | Month-to-date quota and live counters |
| `/apis`, `/apis/{api}/versions/{v}`, `…/operations/{op}` | Filters: `domain`, `owner`, `state`, `classification`, `tag`, `q` |
| `/apis/{api}/versions/{v}/spec` | Basic OpenAPI import (the full diff is 1.0) |
| `/apis/{api}/versions/{v}/transition` | Lifecycle. Retire is Operator or Admin only |
| `/policy-attachments`, `/ip-sets` | Hierarchy attachments and locks (02 §23.2), IP sets (§23.5) |
| `…/effective-policy`, `/effective-policy/preview` | See policy-hierarchy.md §7 |
| `/identity-providers` (+ `/test`, `/{id}/test`, `/{id}/refresh-keys`, `/okta/derive`) | Okta wizard support |
| `/certificates`, `/secrets` (metadata only), `/keyring` (+ `/rotate`), `/jwt-signing-keys` (+ `/rotate`) | Secrets are write-only |
| `/plugins` | Loaded plugins and config schemas as JSON Schema (generated from spec) |
| `/test-requests` | Route tester (§5) |
| `/config/export`, `/config/diff`, `/config/apply`, `/config/revisions` (+ `/{rev}`, `/{rev}/status`, `/{rev}/rollback`) | Config as code |
| `/cluster/nodes`, `/metrics/live`, `/events` (SSE) | Console live data |
| `/audit-events` (+ `/verify`) | Hash-chain verification |
| `/admin-users`, `/api-tokens`, `/settings/{key}`, `/license`, `/session*`, `/setup` | Platform |
| `/openapi.json` | Generated OpenAPI 3.1 |

## 3. Pagination, concurrency and errors (02 §15.3–§15.5)

- **Pagination:**
  - Keyset cursors: `?limit=50&cursor=…&sort=id`. The default limit is 50 and the maximum 500.
  - Responses look like `{"items": […], "next-cursor": "…", "total-estimate": n}`. The estimate is exact below 10k.
  - `q` is a case-insensitive substring match (trigram index).
- **Concurrency:** see [config-revisions.md](config-revisions.md) §5.
- **Errors** follow RFC 9457 `application/problem+json`:
  ```json
  {"type": "https://docs.befive.example/errors/validation", "title": "Validation failed", "status": 422,
   "detail": "The route document has 2 errors.", "request-id": "…",
   "errors": [{"path": ["match","paths",0], "code": "pattern", "message": "must start with /, params as :name, optional trailing /*"},
              {"path": ["access"], "code": "required", "message": "routes need an access policy or {\"public\": true}"}]}
  ```
  - Error types: `validation` (422), `reference` (422), `conflict` (409), `revision-conflict` (409), `cluster-upgrade-in-progress` (409), `precondition-required` (428), `precondition-failed` (412), `not-found`, `unauthenticated`, `forbidden`, `license` (403).
  - **With spec:** `errors` is produced by `befive.schema.errors/explain->problems` from `s/explain-data`. `path` is the `:in` path, `code` comes from the predicate (`required`, `pattern`, `type`, `enum`, `range`, `closed`, …), and `message` comes from the message table. See validation-clojure-spec.md.

## 4. Authentication and RBAC (02 §15.6)

- Console: an OIDC session (identity.md §11).
- Automation: `Authorization: Bearer b5a_<prefix>_<secret>`. Tokens have a name, roles and a **mandatory expiry of at most 1 year**. They are HMAC-hashed like API keys, `last_used_at` is batched, and they cannot use session endpoints.
- **Fixed roles:** Administrator, Operator, API Owner (scoped through `api.owners`), Consumer Manager, Automation Manager (1.0 features) and Auditor. The full matrix is in 02 §15.6. It is authoritative and must be tested as data: every role × every endpoint.
  - Global policy attachments and locks: **Administrator only**. Operators handle environment level and below.
  - Lifecycle publish and deprecate: Admin, Operator, API Owner (own APIs). **Retire: Admin and Operator only** (owner decision).
  - Organizations, consumers, applications, plans and credentials: Admin and Consumer Manager.
  - Identity providers: Admin edits; the others view, and Operators can also run tests.
  - Certificates and secrets: Admin.
  - Config apply: "their kinds" per role (02 §15.6). The Auditor gets diff only.
  - Approving traffic actions (1.0): Admin and Operator. **Automation Managers cannot approve** (owner default).
- `GET /admin/v1/session` returns the computed permission set the console uses.

## 5. Route tester (02 §15.8)

`POST /admin/v1/test-requests` takes `{method, host, path, headers, client-ip, revision: "current"|proposed, send-upstream: false}`.
- It compiles the current or proposed config with `befive.gateway.embed`, runs the request in-process, and returns a trace:
  - the matched route, and why the other candidates did not match
  - each interceptor's decision and timing
  - the identity
  - the policy evaluation tree
  - the rate-limit decisions
  - the headers that would be forwarded
- Dry run is available to all roles. `send-upstream` requires Admin or Operator.

## 6. Admin API OpenAPI (02 §15.7)

reitit routes declare `:parameters` and `:responses` with **specs** (`reitit.coercion.spec`). The OpenAPI 3.1 document is generated at build time with reitit's OpenAPI support, through `spec-tools`'s JSON Schema transformation. If spec-tools is not acceptable, use the in-house `befive.schema.json-schema` mapper (decision D-OCT1-SPEC, open item S-2). The document is served at `/admin/v1/openapi.json` and embedded with Redoc in the docs. CI runs `oasdiff` for backward compatibility.

## 7. SSE events (02 §15.9)

- 0.x events: `revision`, `nodes`, `metrics`, `health`, `license`, `system`, `idp-health`.
- 1.0 events: `incident`, `approval`, `override`, `access-request`, `promotion`, `job`.
- A comment heartbeat every 15 s keeps connections alive (the ALB idle timeout is 60 s). Clients fall back to polling every 10 s.

## 8. b5ctl (02 §16)

- The `b5ctl` binary is a GraalVM native-image (also shipped as a JAR).
- Auth: `BEFIVE_TOKEN` or `--token-file`, plus `BEFIVE_SERVER`. `b5ctl login` writes `~/.config/b5ctl/config.edn` with mode 0600.
- **0.x commands:**

| Command | Exit codes |
|---|---|
| `b5ctl validate [dir]` (offline; `--server` adds plugin specs) | 0 valid, 1 invalid |
| `b5ctl diff [dir]` | 0 no changes, 2 changes, 1 error |
| `b5ctl apply [dir] [--auto-approve] [--wait] [--env <name>]` | 0, 1 error, 3 conflict |
| `b5ctl export [dir] [--kinds …]` (EDN only; never secrets or hashes) | 0, 1 |
| `b5ctl login`, `b5ctl status` | — |
| `b5ctl secrets put <name>` (value read from stdin only) | — |
| `b5ctl consumers issue-key <consumer>` (printed once) | — |
| `b5ctl openapi import <file> --api --version [--apply]` | 0, 2 changes, 1 error (4 is reserved for the 1.0 breaking-change check) |
| `b5ctl api transition <api> <version> --to deprecated --sunset <date>` | 0, 1 |
| `b5ctl policy effective <op-or-route> [--at-revision N]`, `b5ctl policy explain` | 0, 1 |
| `b5ctl idp check <issuer>` | 0, 1 |

- **1.0 commands:** `anomaly replay|export-signals|test-script`, `incidents`, `silence`, `overrides`, `openapi export`, `bundle create|sign|verify`, `promote`, `cache purge`, `evidence export|verify`, `dictionary rebuild`.

### Sync algorithm (02 §16.2)

1. Load `befive.edn`. Expand the `:include` globs. Read EDN with the `#befive/var`, `#befive/env` and `#befive/secret` tags. Evaluate DSL `.clj` files in SCI. Merge into `{kind {id doc}}`; duplicate ids are errors.
2. Normalize both sides with the shared `.cljc` normalizer: **apply defaults explicitly**, sort sets and order-insensitive vectors, drop `meta`, lowercase header names.
3. Scope by `:sync {:kinds … :select-tags … :prune true}`.
4. Diff with `befive.schema.diff`: create, update (with path-level changes) and delete (only when `:prune` is set).
5. Order changes by dependency: secrets → certs → IdPs → upstreams → services → policies → … → routes → APIs → specs → versions → operations → attachments.
6. The server re-validates everything and commits **one revision**, `source = cli-apply`.
7. Report the new revision; with `--wait`, report per-node status.

Credentials are excluded unless `--include credentials` is given. Even then, only `:oauth-client` and `:mtls` mappings are synced, never API keys.

### File layout (02 §16.3)

```
befive-config/
├── befive.edn          ; {:befive/format 1 :include [...] :sync {...}}
├── env/<env>.edn       ; variables for --env (Draft 3 style; overlays/ in 1.0)
├── upstreams/ services/ routes/ policies/ plans/ consumers/ identity-providers/ certificates/
├── apis/<api>/api.edn, apis/<api>/v2.openapi.yaml, apis/<api>/policies.edn
└── src/**/*.clj        ; optional DSL
```

### DSL (02 §16.4)

`befive.dsl` lives in the schema module as `.cljc`. It is **plain functions returning data, with no macros**. SCI evaluates it with only `clojure.core`, `clojure.string`, `clojure.set` and `befive.dsl`. Each DSL function validates its arguments eagerly **with spec** (`s/assert*` or an explicit `s/valid?` that throws `ex-info` carrying the problems), so errors point at the call site. Require it as `[befive.dsl :as dsl]`. The source examples use the alias `em/`, a leftover from an earlier working name.

## 9. Plugin SPI (02 §17)

- Packaging: a plugin JAR or a source directory in `BEFIVE_PLUGINS_DIR`, loaded through an isolated classloader with a manifest. The SPI version must match (`befive.plugin.api`, SPI 1).
- A plugin definition has an id, phase (pre-auth, post-auth, pre-proxy or response), a **config spec** (registered in the spec registry under the plugin's namespace, `:befive.plugin.<id>/config`), and `:compile` (config → interceptor). Exceptions are isolated.
- A plugin can see request, response and identity data. It cannot reach secrets, the DB, or the identity headers it would need to spoof (02 §17.3).
- Example plugins ship in `plugins/examples/`. 0.x ships the SPI and the examples. Customer plugin support is a design-partner topic.
