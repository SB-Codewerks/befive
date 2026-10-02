# AGENTS.md — BeFive 0.x build handoff

> Audience: **Grok Build**, the coding agent that builds the BeFive 0.x MVP, plus any human or agent working in the `befive` repository.
> Package date: October 1, 2026. Source design set: `gateway-design/` (00–04, the mockups), shipped next to this package as `befive-design-docs.zip`.
> **Precedence:** `01-mvp-scope-and-stack.md` (Draft 4.1) is authoritative. Where 02 or 03 disagree with it, 01 wins. The owner decisions of October 1, 2026 in `docs/decisions.md` override all of them. This package is a distillation that points back to source sections, given as `01 §4.2`, `02 §9.5`, `03 §5.22` and so on. When a detail is missing here, read the cited source section.

---

## 1. Product summary

BeFive is a **commercial, self-hosted enterprise API gateway written in Clojure**. Customers run it on their own infrastructure, mainly AWS (ECS/EKS behind an ALB). The image runs anywhere Docker runs.

- **Data plane (gateway nodes):** an Aleph/Netty reverse proxy. It runs a fixed interceptor pipeline for authentication (Okta-first OIDC/JWT, introspection, API keys, mTLS), authorization (scopes, claims, groups), IP rules, rate limits and quotas, identity forwarding with anti-spoofing, and streaming proxying to HTTP or AWS Lambda upstreams.
- **Control plane:** the Admin API (`/admin/v1`), the web console (ClojureScript), the developer portal (a separate hostname), config-as-code services, audit, licensing, and (in 1.0) anomaly detection, governance workflows, reporting and promotion.
- **PostgreSQL is the single source of truth** and the only required dependency. Redis (clustered rate limits, shared cache tier) and object storage (async results) are optional.
- **Config is plain data** (EDN documents) with one schema set, shared by server, CLI and browser. Validation uses **clojure.spec** (owner change of Oct 1, 2026; see §9).
- Licensing is perpetual with optional maintenance, verified offline with Ed25519 license files gated by build date.
- **Design-partner focus for 0.x:** Okta-protected REST APIs on AWS behind an ALB.

Sources: 00 (customer requirements), 01 §0–§2, 02 §1–§2.

## 2. What 0.x includes and excludes

0.x is the **design-partner build**. 1.0 is GA and contains every Must requirement. 1.1 follows right after. Full detail is in `docs/roadmap.md` and `docs/0x-milestones.md`.

### 2.1 In 0.x (29 requirement IDs complete, 4 partial)

| Area | Requirement IDs | Notes |
|---|---|---|
| Okta/OIDC authn and authz, scopes and claims, client credentials, user context, central auth | IAM-001, IAM-002, IAM-003, IAM-004, IAM-006, IAM-007 | JWT/JWKS, introspection, Okta preset and wizard (the wizard accepts **any** domain) |
| Forwarded identity and anti-spoofing, internal JWT | IAM-005, GOV-2 | `X-BeFive-*` headers, stripping, EdDSA internal JWT |
| IdP outage resilience | IAM-008 | Persisted JWKS, stale-if-error, fail-closed default, break-glass admin with **TOTP required** |
| API keys and secret storage | SEC-001, SEC-002 | `b5k_` keys, HMAC with pepper, envelope encryption |
| IP restrictions | SEC-005 | CIDR allow/deny at every policy level |
| Versioning and deprecation headers | API-001, API-002 | Path, header, host, media type and query strategies; `Deprecation`/`Sunset`/`Link` |
| Microservices, Lambda, no SOAP | API-004, API-005, API-009 | Lambda upstreams with payload 2.0 mapping |
| Rate limits and quotas on all dimensions, observable | TRAF-001, TRAF-002 | Plus the **pre-auth per-IP rate limiter** (owner decision) |
| Pooling, large payloads, streaming | TRAF-003, TRAF-005 | SSE, WebSocket, chunked, size limits |
| Logging, monitoring, Datadog, real time, 4xx/5xx, latency | OBS-001 to OBS-006 | CloudWatch EMF, Datadog, OTLP traces, Prometheus, and both recipes |
| Policy hierarchy, path/endpoint policies, config through the API | POL-001, POL-002, POL-003 | Six levels with locks, effective policy view, Admin API, `b5ctl` |
| Observable enforcement | GOV-6 | Access log reasons, metrics, route tester |
| Partial: portal preview (read-only) | DEV-001, DEV-002, DEV-003 (partial) | Okta login, group and user visibility, search and facets (no environment facet), OpenAPI docs |
| Partial: OpenAPI | DEV-007 (partial) | Basic import only |
| Partial: environments | POL-004 (partial) | export/diff/apply between environments with `b5ctl` |
| Partial: audit, least privilege | GOV-1, GOV-3 (partial) | Hash-chained audit of admin actions; fixed console roles |

Evaluation prompts that are demoable in 0.x are 1, 2, 3, 5, 7, 8, 9, 10 and 12, the streaming half of 11, and parts of 4 and 6 (04 §3).

### 2.2 Not in 0.x

- **1.0:**
  - SEC-003 access-request automation (built-in, ServiceNow or webhook) and optional Okta app creation
  - SEC-004 and GOV-4 classification-driven governance
  - The full portal: DEV-001, DEV-002 and DEV-003 completed with subscriptions, keys, try-it and access requests
  - DEV-004 lifecycle tooling, DEV-005 sandbox and mocks, DEV-007 OpenAPI export/diff, DEV-008 data dictionary
  - API-003 composites, API-006 service discovery, API-007 and POL-004 linked environments and signed promotion
  - TRAF-004 encrypted caching, TRAF-006 and TRAF-007 async jobs and files
  - OBS-007 reports, OBS-008 and GOV-5 InfoSec retention and evidence export
  - Anomaly detection with IP block and rate-limit tightening
  - Deprecation emails
- **1.1:**
  - DEV-006 AI-assisted specs and the LLM integration
  - LLM root-cause analysis, ServiceNow Event Management
  - Teams and email anomaly notifications
  - RFC 8693 token exchange, Okta group entitlements
  - Consumer block and route disable come in 1.1 or later
- **Deferred beyond 1.0:** Helm chart, SAML/SCIM, gRPC and upstream HTTP/2, custom RBAC roles, multi-region, monetization, FIPS 140-3, dark mode.
- **Roadmap:** API-008 GraphQL.
- **Rule:** 1.0-only screens and endpoints are **absent** in 0.x, not disabled. The portal preview does not register mutating routes; they return 404 (03 §4.8, 02 §25.1).

## 3. Stack and versions

All dependencies are pinned (exact versions in `deps.edn` and `package-lock.json`, no ranges or `RELEASE`/`LATEST`). Every library listed as pending in 01 §3.2 is **approved** (Oct 1, 2026).

| Concern | Choice |
|---|---|
| Runtime | **JDK 21, Eclipse Temurin** (a jlink-trimmed runtime in the image); **Clojure 1.12** |
| HTTP server/client, data plane | Aleph on Netty, Manifold; an in-house Sieppari-style interceptor executor; reitit for routing |
| Admin API | reitit + Ring + Muuntaja (JSON, EDN, Transit) with **clojure.spec** coercion and validation (replacing malli; see §9) |
| Validation | **clojure.spec.alpha** (CLJ), **cljs.spec.alpha** (CLJS), `clojure.spec.gen.alpha` + **test.check**. Not malli. |
| System and config | Integrant (a system per role) and Aero (node-local settings) |
| Database | **PostgreSQL 15–17**; next.jdbc, HoneySQL, HikariCP; Migratus with **numbered SQL migrations** (`0001-…up.sql`); PG full-text search with pg_trgm |
| Crypto and auth | Nimbus JOSE+JWT; buddy-hashers (Argon2id) for admin passwords; HMAC-SHA256 for API keys; JDK Ed25519 for licenses and the internal JWT; AES-GCM envelope encryption; RE2J for the claims regex |
| Rate limits and cache | Bucket4j (local), Lettuce (Redis); Caffeine |
| JSON and logging | jsonista; SLF4J + Logback with a JSON encoder |
| Telemetry | CloudWatch EMF (stdout), java-dogstatsd-client, OpenTelemetry SDK + OTLP exporter, in-house Prometheus text endpoint |
| OpenAPI | swagger-parser v3, networknt json-schema-validator |
| AWS | AWS SDK for Java v2 (lambda; sqs and servicediscovery are 1.0); KMS for the master key |
| Frontend | ClojureScript, shadow-cljs, re-frame/Reagent (React 18), Ant Design 5, ECharts, CodeMirror 6 (`@nextjournal/lang-clojure`), markdown-it + DOMPurify; React Flow `@xyflow/react` (1.0 composites) |
| CLI | `b5ctl` as a GraalVM native-image (and a JAR); SCI for the DSL |
| Sandboxed scripts | SCI in a separate native runner process (1.0 anomaly) |
| Email | Eclipse Angus Mail (1.0) |
| Build | `deps.edn` + tools.build (`build.clj`); a multi-stage Docker build |
| Tests | Kaocha, test.check, Testcontainers (PostgreSQL, Redis, LocalStack, MinIO, k3s, Datadog Agent, OTel collector), Toxiproxy, Playwright + axe-core, k6, criterium |
| Docs site | MkDocs Material + Redoc |
| CI and registry | **GitHub Actions**; images to **GHCR**, then to **Amazon ECR before partners** get access |

Performance targets (01 §3.3, 02 §20) are hypotheses to verify, not measurements:
- p99 added latency under 5 ms
- at least 10k rps per 4-vCPU node
- config live on all nodes within 5 s
- compile under 200 ms for 2k routes / 100k credentials

## 4. Naming

| Thing | Name |
|---|---|
| Product | **BeFive** (working name; the trademark check is a pre-launch item, and derived names change mechanically if it fails). "B5" appears only inside identifiers. |
| GitHub repository | `befive` (private) |
| CLI | `b5ctl` (config in `~/.config/b5ctl/config.edn`, mode 0600) |
| API keys | `b5k_<8 base32 prefix>_<43 base62 secret>` |
| Admin API tokens | `b5a_…` |
| Headers | `X-BeFive-*` (for example `X-BeFive-Subject`, `X-BeFive-Client-Id`, `X-BeFive-Consumer`, `X-BeFive-Scopes`, `X-BeFive-Groups`, `X-BeFive-Auth-Method`, `X-BeFive-Application`, `X-BeFive-Organization`, `X-BeFive-Api`, `X-BeFive-Claim-*`, `X-BeFive-Identity` for the internal JWT) |
| Clojure namespaces | `befive.*` (`befive.schema.*`, `befive.core.*`, `befive.gateway.*`, `befive.cp.*`, `befive.policy.*`, `befive.openapi.*`, `befive.cli.*`, `befive.console.*`, `befive.portal.*`, `befive.plugin.api`) |
| Spec keys | Namespaced keywords under `:befive.<entity>/…`, for example `:befive.route/paths` (see `docs/clojure-style-guide.md` §S) |
| Environment variables | `BEFIVE_*` (for example `BEFIVE_ROLE`, `BEFIVE_DB_URL`, `BEFIVE_MASTER_KEY_SOURCE`; the full list is in `docs/deployment-docker.md`) |
| CloudWatch metric namespace | `BeFive` |
| Well-known paths | `/.well-known/befive/jwks.json` (internal JWT keys); `/.well-known/befive/evidence-key` (1.0) |
| Cookies | `__Host-befive_session` (console), `__Host-befive_portal` (portal) |
| PG NOTIFY channel | `befive_config` |
| Log schemas | `befive.access/1`, `befive.metrics/1`, `befive.usage/1`, `befive.audit/1` |
| Config file format | `:befive/format 1`; reader tags `#befive/secret`, `#befive/var`, `#befive/ip-set` |

## 5. Repository layout (02 §3.1)

```
befive/                       (private GitHub repo "befive")
├── deps.edn                  ; aliases :dev :test :build :console :cli :recipe :bench :lint :fmt
├── build.clj                 ; tools.build: uber, console/portal release, native cli, sbom, recipe, licenses
├── modules/
│   ├── schema/        befive.schema.*   .cljc only: spec registry, domain specs, form metadata registry,
│   │                                    defaults, explain->message mapping, cross-entity validation, diff
│   │                                    engine, DSL fns, access-log field catalog, error codes
│   ├── core/          befive.core.*     settings (Aero), db, keyring, license, logging, JSON, SSRF-safe HTTP, ids
│   ├── plugin-api/    befive.plugin.api public versioned SPI (SPI version 1)
│   ├── gateway/       befive.gateway.*  listeners, pipeline, interceptors, compiler, sync, upstreams, authn/z,
│   │                                    ratelimit, access log/EMF, telemetry, lambda, embed (for the route tester)
│   ├── control-plane/ befive.cp.*       Admin API, sessions/OIDC, RBAC, config service, audit, migrations,
│   │                                    console/portal serving, befive.cp.portal (preview)
│   ├── policy/        befive.policy.*   .cljc: hierarchy resolution, strength order, locks, claims compiler
│   ├── openapi/       befive.openapi.*  swagger-parser wrapper, mapping, (1.0: diff, lint, export, mocks)
│   ├── anomaly/       befive.anomaly.*  .cljc (1.0)
│   ├── script-runner/ befive.runner.*   native SCI sandbox (1.0)
│   ├── app/           befive.main       entry point, Integrant system per role, subcommands (migrate, report)
│   ├── console/       ClojureScript: shadow-cljs builds :console and :portal
│   ├── cli/           befive.cli.*      b5ctl
│   ├── aws-recipe/    befive.recipe.*   Terraform JSON + CloudFormation generator
│   └── datadog-recipe/befive.recipe.datadog
├── plugins/examples/
├── docker/                   ; Dockerfile, docker-compose.yml (PG, Redis, LocalStack, mock IdP), ECS example
├── docs/                     ; product docs (MkDocs) — the agent handoff docs live in this package's docs/ and are
│                             ;   copied into befive/docs/design/ in milestone 1
├── test/e2e/                 ; Testcontainers end-to-end
├── perf/k6/
└── THIRD_PARTY_LICENSES.md   ; generated per release
```

**Dependency rules** (CI parses `deps.edn` and fails on violations):
- `schema` depends on nothing except `org.clojure/spec.alpha`, `test.check` (test/gen only) and the Clojure/CLJS core.
- `plugin-api` depends only on `schema`.
- `gateway` and `control-plane` never depend on each other, except for `control-plane` → `befive.gateway.embed` (one-way, for the route tester).
- `policy` and `openapi` depend only on `schema` (and `openapi` on swagger-parser).
- `cli` uses only native-image-safe pieces (no Netty, no HikariCP).
- `script-runner` never depends on `core`.

## 6. Build, test and lint commands

Milestone 1 creates these entry points. Keep the names stable, because CI and these docs reference them.

| Purpose | Command |
|---|---|
| REPL (all modules) | `clojure -M:dev` |
| Unit and integration tests (JVM) | `clojure -M:test -m kaocha.runner` (focused: `--focus befive.gateway.authn-test`; suites `:unit`, `:integration`, `:e2e`) |
| Generative and property tests only | `clojure -M:test -m kaocha.runner --focus :generative` |
| CLJS schema/console tests | `npx shadow-cljs compile test && node out/test.js` |
| Lint | `clojure -M:lint` (clj-kondo over `modules/`, zero warnings), `clojure -M:fmt check` (cljfmt) |
| Reflection check | part of `clojure -T:build uber`: `*warn-on-reflection*` is an error for `gateway` and `core` |
| Dependency policy | `clojure -T:build licenses` (fails on GPL/AGPL; writes `THIRD_PARTY_LICENSES.md`); `clojure -M:outdated` (antq, report only) |
| Uberjar | `clojure -T:build uber` → `target/befive-<ver>.jar` |
| Console and portal release | `npx shadow-cljs release console portal` (also run inside `uber`) |
| Native CLI | `clojure -T:build native-cli` → `target/b5ctl` |
| Docker image | `docker build -f docker/Dockerfile -t befive:dev .` |
| Local stack | `docker compose -f docker/docker-compose.yml up` (PostgreSQL 16, Redis, LocalStack, mock OIDC issuer, echo upstream) |
| Migrations | `java -jar target/befive-<ver>.jar migrate` (or `BEFIVE_MIGRATE_ON_START=true`) |
| SBOM and scan | `clojure -T:build sbom` (CycloneDX), `trivy image befive:dev` |
| Perf | `k6 run perf/k6/<scenario>.js` |

**CI (GitHub Actions)** on every PR runs: lint, format check, reflection check, unit + generative tests (JVM and CLJS), integration tests with Testcontainers, the license policy check, the module-dependency check, the RBAC matrix tests, Trivy, and the build of the jar and image. Nightly CI runs e2e and Playwright, the perf smoke and migration lock-duration tests. Real Okta and AWS jobs are added once the owner provides a real **Okta developer org and AWS test account**. Until then, use the **mock IdP** and **LocalStack**.

## 7. Commit and version conventions

- **Conventional Commits:** `feat(gateway): …`, `fix(cp): …`, `test(policy): …`, `docs: …`, `chore(deps): …`, `refactor: …`, `perf: …`. Mark breaking changes with `!` or a `BREAKING CHANGE:` footer. Scopes are module names.
- **SemVer starting at `0.1.0`.** Image, jar, CLI, recipes and the plugin API share the product version; the plugin API also carries SPI version `1` (02 §3.3). Each milestone ends with a tagged pre-release (`v0.1.0`, `v0.2.0`, …; see `docs/0x-milestones.md`). The build manifest records `version`, `git-sha` and `build-date`; licensing compares against `build-date`.
- **Migrations:** numbered SQL files `NNNN-short-name.up.sql` / `.down.sql` in `modules/control-plane/resources/migrations`. Follow the expand/contract rule (02 §21.1). Never edit a migration that has been released.
- **Feature levels:** the integer feature level starts at `1` with 0.1.0. Spec additions are tagged in the form-metadata registry with `:befive/since <level>` (02 §21.3).
- Small PRs; one milestone item per PR where practical; each PR updates `docs/decisions.md` when it makes an assumption.
- **License:** proprietary ("Copyright © BeFive. All rights reserved."). No open-source license file. Every release ships `THIRD_PARTY_LICENSES.md`. CI fails on any GPL- or AGPL-licensed dependency (LGPL and EPL/MPL are flagged for review, not failed; record any approval in `decisions.md`).

## 8. Security must-dos (non-negotiable from the first commit)

1. **The hot path never touches PostgreSQL or blocks the Netty event loop.** Config is an immutable compiled RouteTable swapped atomically. The only exception is async submit (1.0, 02 §1).
2. **Fail static and fail closed.** A failed compile keeps the last good revision. Gateways start from last-known-good files (gzip EDN, keep 3) if the DB is down. IdP failure mode defaults to `:fail-closed`.
3. **Default deny.** A route or operation without an access decision cannot be saved (02 §10.2).
4. **Strip inbound identity headers** (every configured `X-BeFive-*` and the internal JWT header) before authentication, in the order in 02 §9.9. Never trust client-supplied identity.
5. **Gateway errors reveal nothing.** Use the minimal JSON body with a request ID; the reason goes only to the access log (02 §7.6).
6. **Secrets are write-only.** Envelope encryption: a KEK from env, file or AWS KMS, and DEKs with AES-GCM using the entity id as AAD. Never log, export or return a secret or a credential hash. One-time values (API keys, tokens) are shown once, and in the browser they are kept only in component-local state, never in app-db.
7. **API keys:** HMAC-SHA256 with a pepper, constant-time compare, at most 2 active keys, 7-day rotation grace (02 §9.5).
8. **Admin auth:** `__Host-` cookies (30 min idle, 12 h absolute) with CSRF tokens. Local accounts use Argon2id (12+ characters) with lockout. **TOTP is required for local break-glass admins.** Break-glass sessions last 1 h, trigger a notification and a critical audit event. The bootstrap token is single-use.
9. **SSRF guard:** all outbound calls initiated by configuration (JWKS, introspection, webhooks, Okta) go through `befive.core.http/safe-client` with the outbound allow list.
10. **Audit every mutation** by construction (reitit middleware). The audit log is hash-chained.
11. **Supply chain:** pinned deps, the license policy, Trivy, CycloneDX SBOM, a cosign signature on the image and SLSA provenance.
12. **Portal preview is internet-exposed** (partners may reach it from outside). Required: strict CSP (no inline scripts), sanitized Markdown, its own listener, hostname, cookie, OIDC client and DB role, no mutating routes, no enumeration (hidden APIs → standard 404), a WAF and rate limits in front, and a security review with a pen test in milestone 10.
13. **Pre-auth per-IP rate limiter** in front of authentication (0.x, owner decision).
14. **Okta client keys (1.0):** developers upload public keys by default. BeFive-generated keys are an Administrator opt-in, never for Restricted APIs. A generated private key is **deleted from the secrets store right after the one-time download, or after 24 h if never downloaded**.
15. Use `*warn-on-reflection*` on the gateway and core modules, and ByteBuf leak detection at PARANOID in proxy tests.

## 9. Validation: clojure.spec everywhere (owner change, Oct 1, 2026)

The source design names **malli** in many places. **Use clojure.spec (`clojure.spec.alpha` / `cljs.spec.alpha`) instead, everywhere.** This covers:
- the shared `schema` module
- Aero/node settings and config-document validation
- Admin API coercion and validation
- `b5ctl validate`
- plugin config schemas
- console and portal forms
- query-parameter coercion in reitit-frontend
- generators in tests

Do not add malli as a dependency. Replacements:
- **malli `humanize`** → `s/explain-data` → `befive.schema.errors/explain->problems` (a message table keyed by spec key and predicate symbol). It yields `{:path [...] :code ... :message ...}` and feeds RFC 9457 `422` field errors and form messages.
- **Generated forms** → a spec-driven generator over `s/form` plus a small **form-metadata registry** (`befive.schema.meta`: title, help, widget, secret?, min/max, enum labels, since).
- **Default transformers** → an explicit `befive.schema.defaults/apply-defaults` step (a pure function driven by the same registry), run before validation on write and shown in the EDN preview.
- **JSON Schema** for OpenAPI and `/plugins` → a small in-house spec→JSON Schema mapper for the subset we use.

Full design: `docs/validation-clojure-spec.md`. The decision is logged as D-OCT1-SPEC in `docs/decisions.md`.

## 10. Doc reading order

1. This file.
2. `docs/README.md` (index, with 0.x/1.0 relevance per doc).
3. `docs/overview.md` → `docs/roadmap.md` → `docs/0x-milestones.md` (what to build, in what order, and when it is done).
4. `docs/decisions.md` and `docs/open-questions.md` (what is settled; never re-open a settled decision silently).
5. `docs/architecture-overview.md`, then the area docs for the milestone at hand:
   - data-plane-proxy-core, domain-model, identity, policy-hierarchy, rate-limiting-caching
   - admin-api-and-b5ctl, config-revisions, telemetry-logging, console-ui, developer-portal
   - security-infosec, deployment-docker, testing-strategy
6. `docs/validation-clojure-spec.md` and `docs/clojure-style-guide.md` before writing code.
7. 1.0-only docs (anomaly, governance, async/composites, environments) are context. Do not build them in 0.x, but do not paint the design into a corner either.
8. Source design docs (`befive-design-docs.zip`) for detail, at the cited sections. Read 01 in full once, because it is short and authoritative.

## 11. Rule for ambiguity

When the docs are silent, ambiguous or contradictory:
1. **Use the documented default.** Precedence:
   1. owner decisions in `docs/decisions.md`
   2. 01 Draft 4.1
   3. 02
   4. 03
   5. mockups (the text wins over the mockups)
2. If there is no documented default, choose the **simplest option that keeps the decision reversible** and stays inside the security must-dos.
3. **Log the assumption** in `docs/decisions.md` under "Build-time assumptions". Use the format: `A-<n> (date, milestone): context — choice — why — how to revert`. Reference it in the PR description.
4. Never silently change a security default, a public name (§4), an API or CLI contract, or a log schema. For those, log the assumption and flag it in the PR title with `[needs-owner]`.

## 12. Definition of done (every PR)

- Tests: unit tests, specs with `s/fdef` instrumented in tests, generative tests where data-shaped, and integration tests where I/O is involved.
- Lint, format and the reflection check are clean.
- Docs are updated: the relevant `docs/*.md` file, and `decisions.md` if an assumption was made.
- No new dependency without a pinned version and a license check; a new library not listed in §3 needs a `decisions.md` entry.
- Audit, metrics and access-log fields exist for every new behavior that affects traffic or configuration.
