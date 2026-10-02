# 0.x milestones, acceptance criteria and definition of done

**Relevance:** 0.x: build plan. **Sources:** 01 §4.1–§4.2 (the milestone list is verbatim in substance), 02 §22 (tests), §20.1/§20.5 (performance), 00 (evaluation prompts), 04 (requirement IDs), plus the owner decisions of Oct 1, 2026. Where this file adds detail beyond the sources (tags, demo scripts, exact checks), it is a **handoff default**. Change it by logging an `A-n` entry in [decisions.md](decisions.md).

**Conventions:**
- Each milestone ends with a tag, `v0.<n>.0` (milestone 1 = `v0.1.0` … milestone 10 = `v0.10.0`), and a CHANGELOG entry generated from Conventional Commits.
- Patch releases (`v0.n.1`) are for fixes only.
- A milestone is **done** only when all its acceptance criteria pass in CI (unit, generative and integration suites), its docs are updated, and the per-PR definition of done in AGENTS.md §12 holds for every merged PR.
- Milestones run mostly in order. Milestones 7 (telemetry) and 8 (console) may overlap 5–6 once the Admin API shape is stable.

---

## M1: Skeleton → `v0.1.0`

**Scope (01 §4.2):** repository, build, CI, Docker image, Integrant system, migrations, health endpoints.

**Acceptance criteria:**
- [ ] The private GitHub repo `befive` has the layout in AGENTS.md §5, plus `deps.edn` with pinned versions, `build.clj`, `.clj-kondo`, `cljfmt.edn`, `tests.edn` (Kaocha) and `shadow-cljs.edn` (empty console and portal shells).
- [ ] GitHub Actions on every PR runs:
  - lint (clj-kondo, zero warnings) and the format check
  - unit tests
  - integration tests (Testcontainers PG 15 and 17)
  - the license check, which **fails on GPL or AGPL** dependencies
  - the uberjar build
  - the Docker build
  - Trivy
- [ ] On a tag, Actions generates the third-party license list and the SBOM, and pushes the image to **GHCR**. ECR comes before partners (M10).
- [ ] `befive.main` starts a gateway-only, control-plane-only or combined system from Aero settings plus `BEFIVE_*` environment variables. Settings are validated by **clojure.spec**: an invalid value exits with code 78 and a path-qualified message.
- [ ] Numbered SQL migrations (`V0001__init.sql` …) run under a PostgreSQL advisory lock at control-plane start. A `b5ctl`-free `befive migrate` sub-command exists.
- [ ] `/healthz` (liveness) and `/readyz` (readiness: DB, config loaded) on the gateway and control plane.
- [ ] The `docker compose` dev stack has befive, PG 16, Redis, the mock IdP, LocalStack and Toxiproxy. `docker compose up` produces a healthy node.
- [ ] The image is non-root and distroless or minimal, with the JVM flags from deployment-docker.md §5.
- [ ] The `schema` module exists, with base specs (`slug`, `cidr`, `duration`, `http-url`), `closed-keys`, `explain->problems` with the message table, `apply-defaults` and the meta registry, plus their generative tests on the JVM and in Node.

## M2: Proxy core → `v0.2.0`

**Scope:** routes, upstreams, load balancing, health checks, pooling, hot reload, structured access logs, size limits, SSE and WebSocket pass-through, Lambda upstreams. **Requirements:** TRAF-001 (partly), TRAF-005, API-009 (opaque proxying). **Docs:** data-plane-proxy-core.md, async-composites-lambda.md (Lambda), config-revisions.md.

**Acceptance criteria:**
- [ ] The compiled router matches the reference oracle on generated route sets. 2,000 routes compile in under 200 ms.
- [ ] The pipeline phase order is as documented (including the slot reserved for the pre-auth limiter in M5). The blocking detector reports no event-loop blocking.
- [ ] Upstreams:
  - round-robin, least-requests and consistent hash
  - active and passive health with ejection
  - pooled HTTP/1.1 with keep-alive
  - retries only for idempotent requests, within budget
  - timeouts at connect, header and idle
- [ ] Streaming:
  - 1 MiB+ bodies stream without buffering, and a slow reader doesn't exhaust memory
  - SSE events flush through
  - WebSocket upgrade and proxying work
  - size limits apply at global, API and route level with a 413
- [ ] Lambda upstreams map events against LocalStack, cover every error-mapping row and enforce the concurrency limiter.
- [ ] Hot reload: a new revision applies without dropping in-flight requests. A compile failure keeps the old revision and logs an error. Polling works when LISTEN/NOTIFY is unavailable. Start from LKG works with the DB down.
- [ ] The access-log line validates against the field catalog. Hop-by-hop headers are stripped. Smuggling test cases are rejected.
- [ ] **Demo:** proxy a REST API with an SSE endpoint and a 50 MiB download through the compose stack (the streaming half of eval prompt 11).

## M3: Domain model → `v0.3.0`

**Scope:** APIs, versions, operations, organizations, consumers and applications; basic OpenAPI import onto routes; version routing strategies and deprecation headers. **Requirements:** API-001, API-002, API-004, API-005, the 0.x part of DEV-001 (import) and DEV-007 (headers). **Docs:** domain-model.md.

**Acceptance criteria:**
- [ ] Specs for every Draft 4 entity kind (feature level 1, H-1). Documents round-trip through JSON, EDN and Transit. Closed-for-write validation tolerates `x-` keys.
- [ ] Routes are generated from operations, and hand-written routes still work.
- [ ] OpenAPI 3.0/3.1 YAML and JSON import creates or updates an API version with its operations. Unsupported constructs give warnings, not failures.
- [ ] Version routing by path, header and query, with a default version.
- [ ] A deprecated operation returns `Deprecation` (RFC 9745) and `Sunset` (RFC 8594) headers, plus `Link` where configured.
- [ ] Owner rule: **API Owners can publish and deprecate but not retire**. Retire needs an Operator or Admin, and the RBAC matrix data encodes this.
- [ ] Consumers and applications are linked to organizations, and keys reference applications.
- [ ] **Demo:** eval prompt 7, import a spec, route v1 and v2, deprecate an endpoint, and show the headers.

## M4: Identity and policy → `v0.4.0`

**Scope:** API keys, JWT with JWKS, introspection, mTLS, the Okta preset, claims expressions, anti-spoofing, internal JWT, IdP-outage resilience, the policy hierarchy with locks and compile-time resolution, IP rules. **Requirements:** IAM-001…008, SEC-001, SEC-002, SEC-005, POL-001…003, TRAF-003 (IP part). **Docs:** identity.md, policy-hierarchy.md, security-infosec.md.

**Acceptance criteria:**
- [ ] **JWT** test vectors pass. `alg=none` and HMAC/RSA confusion are rejected. JWKS rotation and a stale-key grace period work, and an unknown `kid` during an outage **fails closed**. Introspection has its cache and circuit breaker.
- [ ] **Okta preset** against the mock IdP's Okta profile:
  - The wizard **accepts any Okta domain** (custom domains included), through the SSRF guard and over HTTPS only.
  - Discovery, JWKS and introspection endpoints are derived, and the connection test works.
  - Client credentials use `private_key_jwt`. **Developer-uploaded public keys are the default.** A BeFive-generated key is **Admin opt-in only**, is **excluded for Restricted APIs**, and is downloadable once. The private key is **deleted right after the download, or after 24 h if never downloaded** (sweeper plus audit `client_key.private_deleted`).
- [ ] **API keys** use the `b5k_` prefix (admin tokens use `b5a_`). Keys are stored as hashes and compared in constant time. Each key allows two active keys with a grace rotation. Revocation takes effect within one config propagation. Every lifecycle step is audited (eval prompt 5).
- [ ] **mTLS** client certificates map to consumers.
- [ ] **Claims language:** table tests, plus generative tests showing the compiled and interpreted results agree, and RE2J safety checks.
- [ ] **Anti-spoofing:** forged `X-BeFive-*` and internal-JWT headers, in every casing and duplicate variant, never reach the upstream.
- [ ] **The internal JWT** is verified by Nimbus and by Node `jose`. The JWKS endpoint, rotation and revocation are covered.
- [ ] **IdP outage:**
  - The Toxiproxy outage test passes across a restart.
  - Break-glass local admins **require TOTP** (RFC 6238), and enrollment is enforced at first sign-in, including `/setup`.
  - A break-glass sign-in emits a critical audit event, an ERROR log and an optional signed webhook (H-4).
- [ ] **The policy hierarchy** (global → environment → API → version → operation/route) satisfies the six properties of 02 §23.7. Locks are enforced, the effective-policy golden files match, and global attachments and locks are Admin-only.
- [ ] **IP allow/deny rules** work per level, behind trusted-proxy handling of XFF.
- [ ] **Demos:** eval prompts 1, 2, 3, 9 (inheritance part) and 12, plus the 0.x part of the identity runbook.

## M5: Rate limiting and quotas → `v0.5.0`

**Scope:** all dimensions and standard headers, **plus the pre-auth per-IP limiter (owner decision)**. **Requirements:** TRAF-002, TRAF-003. **Docs:** rate-limiting-caching.md.

**Acceptance criteria:**
- [ ] Limit dimensions: consumer, application, key, organization, plan, IP, route, operation and claim, with plan precedence. The local and Redis backends are accurate across 3 simulated nodes. Fail-open and fail-closed are configurable and tested by killing Redis.
- [ ] Quotas use calendar periods with an injectable clock (UTC; the configurable time zone is 1.0). Refunds work across 3 or more buckets.
- [ ] The IETF `RateLimit` and `RateLimit-Policy` headers are the default. A 429 carries `Retry-After`.
- [ ] The **pre-auth per-IP limiter** (H-3):
  - It runs before authentication, defaults to 100 rps with a burst of 200, and groups IPv6 by /64.
  - Trusted proxies, LB health checks and protected CIDRs are exempt.
  - It is configurable only at the global or environment level.
  - The flood test shows **no IdP calls** for rejected traffic. It reports `PreAuthRateLimited` and log reason `ratelimit.preauth_ip`.
- [ ] Bucket keys never contain raw subjects (they are hashed).
- [ ] **Demo:** eval prompt 8 (IP, rate, throttle, quota), with logs and metrics.

## M6: Admin API and CLI → `v0.6.0`

**Scope:** full REST coverage, OpenAPI output, `validate`, `diff`, `apply`, `export` and effective-policy queries. **Requirements:** POL-002, POL-003, GOV-2, GOV-6 and the config-as-code part of eval prompt 9. **Docs:** admin-api-and-b5ctl.md, config-revisions.md, environments-promotion.md (0.x part).

**Acceptance criteria:**
- [ ] Every entity has CRUD under `/admin/v1`, with ETag and `If-Match`, cursor pagination, and RFC 9457 problem details. The 422 body lists `errors[]` from `explain->problems`, with stable codes.
- [ ] Validation and coercion use `reitit.coercion.spec`, or the in-house spec coercion (S-2). Defaults are applied explicitly before validation.
- [ ] The OpenAPI document is generated from the specs and checked for backward compatibility with oasdiff in CI.
- [ ] The RBAC matrix (every role × every endpoint) is tested as data. Every mutation writes a hash-chained audit event.
- [ ] `b5ctl` is a native binary for Linux and macOS (Windows best effort) with `validate`, `diff`, `apply`, `export` and `effective-policy`. It authenticates with a `b5a_` token or OIDC device flow. Validation output carries the file, line and column. `diff` output equals the server's diff, and golden files cover the output.
- [ ] Exporting from one environment and applying to another works through `b5ctl`, and the round-trip is lossless.
- [ ] **Demo:** eval prompt 9, with a Git-driven `b5ctl apply` from CI.

## M7: Telemetry → `v0.7.0`

**Scope:** EMF, Datadog (DogStatsD, logs, OTLP traces), OTLP, Prometheus, and the CloudWatch and Datadog recipes. **Requirements:** OBS-001…006. **Docs:** telemetry-logging.md.

**Acceptance criteria:**
- [ ] EMF output validates against the schema, with at most 100 values per array, under CloudWatch namespace `BeFive`. Per-minute summaries summed across nodes equal the ground truth.
- [ ] DogStatsD metrics and JSON logs reach the Datadog Agent container. OTLP traces (H-9) reach the OTel collector with W3C trace context propagation. Prometheus `/metrics` passes promtool.
- [ ] **All sinks agree** with the EMF sums for a recorded workload.
- [ ] Canary secret tests show no key, token or secret value in any stream.
- [ ] The recipes:
  - CloudWatch dashboards, alarms and Logs Insights queries as Terraform and CloudFormation (`terraform validate` and cfn-lint pass)
  - Datadog dashboards and monitors as JSON and Terraform
  - Queries use only catalog fields.
- [ ] **Demo:** eval prompt 10 (latency, 4xx/5xx rates, usage, alerts, audit data in Datadog), plus the CloudWatch equivalent.

## M8: Console basics → `v0.8.0`

**Scope:** the existing Draft 3 screens plus APIs, organizations and applications, and the effective policy view. **Docs:** console-ui.md, mockups.

**Acceptance criteria:**
- [ ] shadow-cljs `:console` build with re-frame and Ant Design wrappers (`befive.ui.*`), served by the control plane with a strict CSP.
- [ ] Okta OIDC sign-in, plus local break-glass sign-in with TOTP.
- [ ] Screens:
  - first-run `/setup` (license, admin with TOTP, first IdP)
  - dashboard
  - routes and upstreams
  - APIs, versions and operations, with OpenAPI import
  - organizations, consumers and applications
  - keys, with a one-time secret display
  - IdP and Okta wizard
  - policies, with the effective-policy view and lock indicators
  - revisions and diff
  - audit log
  - settings
  - users and roles
- [ ] Forms are spec-driven: validation on input through `explain->problems`, server 422s merged by path, the EDN preview showing explicit defaults, and generated forms for plugin configs from the meta registry.
- [ ] Navigation opens the active group plus as many groups as fit, remembered per user.
- [ ] Playwright flows 6.1, 6.2, 6.3, 6.4, 6.8 (basic) and 6.11 pass. axe-core reports no serious or critical violations. Visual snapshots match at 1440 × 900 and 1280 × 720.
- [ ] Screens match the mockups in [mockups/](mockups/) for layout and naming.

## M9: Portal and catalog preview (read-only) → `v0.9.0`

**Scope:** the portal shell on its own hostname, Okta login, group and user visibility, catalog search with PostgreSQL full-text search and facets, OpenAPI-rendered docs. **Built as the first slice of milestone 11**, not as throwaway work. **Requirements:** the 0.x parts of DEV-001/002/003. **Docs:** developer-portal.md.

**Acceptance criteria:**
- [ ] A separate `:portal` build with its own hostname, `index.html`, CSP, `__Host-` cookies and OIDC client. `/portal/v1` is read-only.
- [ ] Visibility by Okta group and individual user. Hidden items return 404, and facet counts don't leak hidden items (property tests).
- [ ] Search uses PG full-text search with facets. OpenAPI docs are rendered and sanitized, and the XSS corpus is blocked.
- [ ] The preview smoke suite shows no subscriptions, keys, try-it or access requests: those routes are absent (404). There is no portal changelog in 0.x (H-12). Branding is name and logo.
- [ ] **Demo:** the visibility part of eval prompt 4, and the import, catalog and docs part of prompt 6.

## M10: 0.x hardening → `v0.10.0`, design-partner release

**Scope (01 §4.2):** secrets encryption, evaluation licensing, a focused security review of identity and anti-spoofing, a portal-preview security review for internet exposure, load tests of rate limiting, the Okta outage runbook.

**Acceptance criteria:**
- [ ] Secrets are envelope-encrypted (KMS through LocalStack in CI). Key rotation is documented and tested. There are no plaintext secrets in the DB, logs or LKG files.
- [ ] Evaluation licensing: signed license files are verified offline. **Evaluation mode is 1 gateway node with all features and no time limit.** Tampered and unknown keys are rejected.
- [ ] Security review of identity and anti-spoofing, with findings fixed or accepted in writing.
- [ ] **The portal preview's internet-exposure review:**
  - an external penetration test of the preview hostname
  - a CSP and sanitizer review
  - WAF and rate-limit guidance
  - session and OIDC checks
  - enumeration tests
- [ ] The k6 load tests of rate limiting, plus the 02 §20.5 scenarios 1–5 and 7. Results are published with their setup and checked against the targets: p99 added latency under 5 ms, at least 10k rps per 4-vCPU node.
- [ ] The Okta outage runbook is written and exercised in a game day on the compose stack and an ECS/ALB test deployment.
- [ ] The image is pushed to **ECR** (in addition to GHCR), signed, and shipped with an SBOM and the third-party license list.

---

## 0.x definition of done

The 0.x design-partner build is done when **all** of the following hold:

1. **Requirements.**
   - All 29 0.x requirement IDs in [roadmap.md §2](roadmap.md) are implemented, and each has at least one automated test referencing the ID (for example `^{:req ["IAM-003"]}` metadata on the deftest).
   - The 0.x parts of DEV-001/002/003, DEV-007, POL-004 and GOV-1/3 are delivered as listed.
2. **Evaluation prompts.**
   - Prompts **1, 2, 3, 5, 7, 8, 9, 10 and 12** pass end-to-end.
   - So do the partial ones: the streaming half of 11, the visibility part of 4, and the import, catalog and docs part of 6.
   - They are demonstrated on a clean **AWS ECS deployment behind an ALB** with Okta (mock IdP until a real Okta dev org exists; then once against the real org).
3. **Owner decisions of Oct 1, 2026** are implemented:
   - the pre-auth limiter
   - TOTP
   - evaluation mode
   - the any-domain Okta wizard
   - generated-key deletion
   - the API Owner retire rule
   - navigation memory
4. **Quality.**
   - CI is green on PG 15 and 17.
   - clj-kondo reports zero warnings, and there are no reflection warnings in the hot-path modules.
   - The generative suites pass at nightly sizes.
   - Playwright passes, and axe reports no serious or critical violations.
5. **Performance.** The k6 results meet or explain the targets in 01 §3.3, and are published with their setup.
6. **Security.**
   - The identity review and the preview pen test are complete, with no open high or critical findings.
   - Canary secret tests pass, and Trivy finds no critical CVEs without a waiver.
   - TOTP is enforced.
   - Generated private keys are verified deleted (download and expiry paths).
7. **Release artifacts.**
   - a signed image on GHCR and ECR
   - an SBOM and the third-party license list (with no GPL or AGPL)
   - `b5ctl` binaries
   - the CHANGELOG
   - semver tag `v0.10.0` (or later 0.x)
8. **Documentation.**
   - an install guide (Docker, ECS/ALB)
   - the operations runbook, including the Okta outage
   - configuration and EDN reference generated from the specs
   - the Admin API OpenAPI
   - the CloudWatch and Datadog recipes
   - the evaluation-license terms
   - `docs/decisions.md`, with all `A-n` assumptions reviewed by the owner
9. **Open items.** Everything tagged `[needs-owner]` is answered.

## 1.0 and 1.1 milestones (for orientation; not part of 0.x)

| # | Milestone | Release | Docs |
|---|---|---|---|
| 11 | Developer portal and developer tools (completes the preview) | 1.0 | developer-portal.md |
| 12 | Governance workflow (classification, access requests, ServiceNow, API Owner role) | 1.0 | governance-access-requests.md |
| 13 | Backends and traffic (composites, discovery, encrypted caching, async and files) | 1.0 | async-composites-lambda.md, rate-limiting-caching.md |
| 14 | Environments (linked, signed bundles, overlays, promotion) | 1.0 | environments-promotion.md |
| 15 | Reporting and InfoSec | 1.0 | telemetry-logging.md, security-infosec.md |
| 16 | Anomaly detection and scripted response (may run in parallel with 11–15) | 1.0 | anomaly-response.md |
| 17 | Release hardening | 1.0 | security-infosec.md, testing-strategy.md |
| 18 | LLM and notifications (DEV-006, RCA, Event Management, Teams and email) | 1.1 | anomaly-response.md, roadmap.md |
