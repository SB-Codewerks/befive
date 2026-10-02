# Domain model

**Relevance:** 0.x: build (milestone 3). Subscriptions, access requests, the dictionary, composites and async entities are 1.0. **Sources:** 01 §2.1, §2.8, 02 §5 (principles, entities, PG schema, revisions), §5.6–§5.7 (Draft 4 entities), §6.9 (compiling operations), §26.1–§26.5 (OpenAPI import, lifecycle), §27 (versioning, deprecation).

## Principles (02 §5.1)

- **Slug IDs** chosen by people: `^[a-z0-9][a-z0-9-]{0,62}$`, immutable, with no UUIDs in configuration. This is what makes export from one environment and apply to another work. Server IDs are used only for non-promoted things: credentials `cred_` + ULID, audit UUIDv7, and so on.
- **One document shape.** The spec defines it. PostgreSQL stores `jsonb` plus extracted columns (foreign keys, uniqueness, filters). JSON, EDN and Transit share kebab-case unqualified keys. Server metadata lives under `:meta`, which diff ignores.
  - With spec: entity specs use `s/keys :req-un/:opt-un` over namespaced spec names (`:befive.route/paths`), so documents keep **unqualified** keys on the wire (see [validation-clojure-spec.md](validation-clojure-spec.md)).
- **Open for reading, closed for writing.** Write validation rejects unknown keys (spec is open by default, so enforce closure explicitly with `befive.schema.core/closed-keys`). The compiler tolerates `:x-*` keys (customer annotations).
- **Secrets are references:** `{:secret "okta-introspection-secret"}` → the encrypted `secret` table.

## Entities

### Core entities (Draft 3 base, 02 §5.2)

| Entity | Purpose | Promoted |
|---|---|---|
| Upstream | Targets, LB, health checks, TLS, pool limits; kinds `:http`, `:lambda` (0.x), `:discovered` (1.0) | Yes |
| Service | Logical backend: one upstream plus route defaults (timeouts, retries, base path, forwarding) | Yes |
| Route | Match plus pipeline (authn, access, limits, transforms, plugins). Hand-written routes coexist with derived ones | Yes |
| Policy | A named reusable access rule tree | Yes |
| Consumer group | Named list of consumers used by policies | Yes |
| Plan | Rate limits and quotas | Yes |
| Consumer | A partner or team; belongs to an organization | Optional (`--include consumers`) |
| Credential | API key hash, OAuth client ID binding, or mTLS mapping; belongs to an **application** | Metadata only |
| Identity provider | OIDC/OAuth issuer (Okta preset or generic) | Yes (secret refs) |
| Certificate | SNI certificates, CA bundles, upstream client certificates | Yes (private key by ref) |
| Secret | Encrypted value by name | Names only |
| Admin user, API token, audit event, setting, license | Platform objects | No (settings: selected keys) |

### Draft 4 entities (02 §5.6)

All of these are 0.x unless marked.

| Entity | Purpose |
|---|---|
| **Api** | `:id :name :domain :owners (Okta group/user, 1–16) :description (Markdown) :classification :versioning :default-version :visibility :docs :try-it :tags`. Owners define the API Owner role scope. |
| **ApiVersion** | Label, lifecycle state, backing service, spec reference, deprecation dates, visibility narrowing |
| **Operation** | Method plus path template (usually imported), operation-level overrides, backend kind (`:proxy` and Lambda in 0.x; `:composite`, `:async` and `:mock` in 1.0) |
| **ApiSpec** | Original bytes, normalized JSON, sha256 |
| **Organization** | Tenant (partner or business unit), optional plan, portal membership mapping |
| **Application** | Belongs to a consumer. Holds keys, client-ID bindings and certificate mappings. The unit for limits, reports and subscriptions. |
| **PolicyAttachment** | A policy kind at one hierarchy level, optionally locked ([policy-hierarchy.md](policy-hierarchy.md)) |
| Subscription | Application × version (× plan). **1.0** (created by access approval or an admin). In 0.x, access is decided by policies (scopes, claims, groups, consumers). Handoff default H-6. |
| Dictionary field/term | **1.0** (02 §26.8) |

**Migration rule:** each pre-existing consumer gets a default application `<consumer-id>-default`. Consumer-level API calls address that application (02 §5.6).

**Lifecycle (02 §26.5):** `:design → :published → :deprecated → :retired`.
- Retired versions compile to a single 410 `version.retired` route with no auth.
- Design versions are not routed unless mocks are on or `:allow-design-routes` is set (Sandbox).
- **Who may transition (owner decision):** API Owners may publish and deprecate their own APIs. Only **Operators and Administrators may retire**.

## Persistence (02 §5.4, §5.5, §5.7)

- One table per kind with an `id` primary key, `doc jsonb`, extracted columns, and `version bigint` (a per-row counter used for ETag/If-Match), plus `created_at`/`updated_at`.
- `config_state` (a single row) holds the global `revision`, taken under a row lock in every config write.
- `config_change (revision, seq, kind, entity_id, op, doc, before)` holds the history used for deltas and rollback. Retention is in [config-revisions.md](config-revisions.md).
- ETag format: `"route:orders-get:7"`. Writes require `If-Match` (412 on mismatch).

## Compiling operations onto routes (02 §6.9)

The same pure function runs in the gateway compiler and in the control plane for previews and the route tester (`befive.gateway.compile.api`):
1. Skip retired operations (they become a 410 route) and design versions (unless mocks or `:allow-design-routes`).
2. Derived route ID `op-<api>-<version>-<operation>`. If the ID exceeds 63 characters, use the first 54 characters + `-` + 8 hex of sha256. The `op-` prefix is reserved for derived IDs.
3. The version supplies the match (hosts, prefix, predicates) and the operation supplies the method and template.
4. The effective policy ([policy-hierarchy.md](policy-hierarchy.md)) supplies authn, access, IP, limits, logging, size limits and deprecation.
5. The backend comes from the version's service and upstream (HTTP or Lambda).

A conflict check covers derived and hand-written routes together and names both sources. A hand-written route resolves policy as global → environment → route.

## OpenAPI import, basic in 0.x (02 §26.1–§26.3)

- Uses swagger-parser v3 (OpenAPI 3.0/3.1, YAML or JSON). **Remote `$ref`s are off.** Extensions are `x-befive-*`.
- Operation IDs come from `operationId`, or are slugged from the method and path (`get-orders-id`).
- Import maps to Api, ApiVersion and Operations, and stores the ApiSpec.
- Lint (basic) and the diff preview with breaking-change analysis are **1.0**. In 0.x, import shows the planned create/update/delete operation set before applying, which is the minimal diff needed for safe re-import.
- CLI: `b5ctl openapi import <file> --api <id> --version <v> [--apply]`. Exit codes 0/2/1, and 4 is reserved for the 1.0 breaking-change check.
- Export, mocks, the dictionary and DEV-006 come in 1.0/1.1.

## Versioning and deprecation (02 §27), 0.x

- Strategies are `:path` (the default, `/v{version}`), `:host`, `:header`, `:media-type` and `:query`, with `:default-version` and `:aliases`.
  - An unknown explicit version → `400 version.unknown` (header/query) or `406` (media type).
  - Responses vary on the selecting header.
- Access-log fields: `api_version`, `version_selected_by`.
- `:deprecation {:deprecated-at :sunset-at :link :headers}` is a **policy kind** (not lockable). The lifecycle slot appends precomputed headers:
  - `Deprecation: @<unix>` (RFC 9745)
  - `Sunset: <HTTP-date>` (RFC 8594)
  - `Link: <…>; rel="deprecation"`, `rel="sunset"`
  - Upstream values win by default.
- **Deprecated usage, "who still calls it":**
  - In 0.x it comes from the `deprecated` field on access and usage lines and from dashboard panels and queries in the CloudWatch and Datadog recipes.
  - The console report and the `deprecated_last_call` table come in 1.0 (02 §27.4), and so do deprecation emails (§27.5).
  - Handoff default H-7.
