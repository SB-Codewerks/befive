# Decisions log

**Relevance:** 0.x and 1.0. This is the authoritative list.
- **Precedence:** owner decisions (§1) > handoff defaults (§3) > 02 Appendix A (§5) > 01 §5 (§4) > the body text of 02 and 03.
- A later-dated owner decision overrides anything earlier.
- When the source docs conflict and nothing here resolves it, follow the AGENTS.md ambiguity rule: use the documented default, and add an `A-n` entry to §6.

## 1. Owner decisions, accepted October 1, 2026

### D-OCT1-KEYS: Okta client keys (`private_key_jwt`)

- **Developer-uploaded public keys are the default.**
- **BeFive-generated key pairs are opt-in by an Administrator only**, and are **never available for Restricted APIs**.
- A generated private key is shown for **one-time download**. It is **deleted from the secrets store right after the one-time download, or after 24 hours if never downloaded**.
  - Deletion on download happens in the same transaction as the download.
  - A sweeper (`befive.access.okta-key-sweeper`, every 10 min) deletes expired keys and deactivates the never-downloaded key in Okta.
  - Both paths emit the audit event `client_key.private_deleted` with reason `downloaded` or `expired`.
  - The console shows a countdown and the "Expired before download" state, with *Generate new key* or *Upload public key*.
- **Applied to the source docs on Oct 1** (each has an Oct 1 changelog line):
  - 01: decision 32, R11, §7 and §2.6
  - 02: §24.8 (and its security considerations), §25.8, the §35.1 #16 note, the §35.2 risk row and Appendix A decision 50
  - 03: §5.14.6, §5.38
  - 04: SEC-003
- Backups of the pre-edit versions are in `/workspace/tools/befive-backup-oct1-pre-handoff/`.

### D-OCT1-SPEC: clojure.spec instead of malli, everywhere

- Use `clojure.spec.alpha` and `cljs.spec.alpha` for:
  - the shared `schema` module
  - node settings and config validation
  - Admin API coercion and validation (`reitit.coercion.spec`)
  - `b5ctl`
  - plugin config schemas
  - console and portal forms
- **No malli dependency.**
- Where the design relied on malli features, use these replacements:

  | malli feature | Replacement |
  |---|---|
  | `humanize` errors | `explain-data` → `befive.schema.errors/explain->problems` plus a shared message table |
  | Generated forms | An `s/form` walk plus the form-metadata registry `befive.schema.meta` |
  | Default transformers | An explicit, pure `befive.schema.defaults/apply-defaults` step before validation |
  | JSON transformers | `befive.schema.coerce` / `reitit.coercion.spec` |
  | `malli.json-schema` | spec-tools or the in-house `befive.schema.json-schema` (S-2) |
  | Closed maps | `closed-keys` |
  | `{:befive/since n}` | Meta `:since` |
  | Generators | `clojure.spec.gen.alpha` and test.check |

- Details are in [validation-clojure-spec.md](validation-clojure-spec.md).
- The source design docs (01 §3.1, 02 §1/§3/§5/§6/§15/§16/§17/§21/§22, 03 §4/§8) **still say malli**. This file supersedes them, and editing them is a follow-up.

### D-OCT1-BUILD: builder defaults

| ID | Decision |
|---|---|
| D-OCT1-BUILD-1 | Private GitHub repository named **`befive`** |
| D-OCT1-BUILD-2 | **Proprietary license.** CI fails on any GPL or AGPL dependency (direct or transitive, JVM and npm). A **third-party license list** is generated and published with every release. LGPL, MPL and EPL dependencies are allowed; flag new LGPL or MPL ones in the PR (see open-questions). |
| D-OCT1-BUILD-3 | **JDK 21 (Eclipse Temurin)**, **Clojure 1.12**, **PostgreSQL 15–17** (CI tests 15 and 17; dev compose uses 16) |
| D-OCT1-BUILD-4 | **Pinned dependencies.** Exact versions in `deps.edn` and `package-lock.json`, with no ranges, SNAPSHOTs or `RELEASE`/`LATEST`. Upgrades are deliberate PRs. |
| D-OCT1-BUILD-5 | **All libraries pending approval in 01 §3.2 are approved.** |
| D-OCT1-BUILD-6 | CI is **GitHub Actions** |
| D-OCT1-BUILD-7 | Images go to **GHCR** first, then **ECR** before design partners receive builds (M10) |
| D-OCT1-BUILD-8 | **Semver starting at 0.1.0.** Milestone n → `v0.n.0`. |
| D-OCT1-BUILD-9 | **Conventional Commits** (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`, `perf:`, `build:`, `ci:`; `!` or `BREAKING CHANGE:` for breaking changes). The CHANGELOG is generated from them. |
| D-OCT1-BUILD-10 | **Numbered SQL migrations** (`V0001__description.sql` …), append-only. This replaces the timestamp naming in 02 §21.1. |
| D-OCT1-BUILD-11 | **Mock IdP plus LocalStack** for all Okta and AWS tests until the owner provides a real Okta developer org and an AWS test account. The real-tenant nightly jobs of 02 §22 are then enabled. |

### D-OCT1-PRODUCT: 0.x product decisions

| ID | Decision | Resolves |
|---|---|---|
| D-OCT1-P1 | **0.x includes a pre-authentication per-IP rate limiter** (H-3 gives the details) | 02 §35.1 Q1, §7.4 |
| D-OCT1-P2 | **TOTP is required for local break-glass admins** (H-5) | 02 §35.1 Q3, §18.4, §9.12 |
| D-OCT1-P3 | **Evaluation mode: 1 gateway node, all features, no time limit** | 02 §35.1 Q6 |
| D-OCT1-P4 | **The Okta wizard accepts any domain** (not only `*.okta.com`/`*.oktapreview.com`), for custom domains. It is still subject to the SSRF guard and HTTPS. | 02 §35.1 Q7 |
| D-OCT1-P5 | **Design-partner focus: Okta-protected REST APIs on AWS behind an ALB.** Prioritize that path in tests, docs and demos. | 01 §4.1 |
| D-OCT1-P6 | **API Owners can publish and deprecate but not retire.** Only Operators and Administrators retire. | 03 §10 |
| D-OCT1-P7 | **Console navigation opens the active group plus as many other groups as fit, remembered per user** | 03 §10 |

### D-OCT1-1.0: defaults that apply only from 1.0

| ID | Decision | Resolves |
|---|---|---|
| D-OCT1-L1 | Quotas use **UTC** calendar boundaries | 02 §35.1 Q2 |
| D-OCT1-L2 | **FIPS after 1.0** | 02 §35.1 Q4 |
| D-OCT1-L3 | **No upstream HTTP/2 until gRPC** | 02 §35.1 Q5 |
| D-OCT1-L4 | ServiceNow support targets **Washington DC and later** | 02 §35.1 Q12 |
| D-OCT1-L5 | Source-IP data is **kept 24 h by default, up to 7 days** | 02 §35.1 Q14 |
| D-OCT1-L6 | **Alertmanager through the generic webhook** | 02 §35.1 Q15 |
| D-OCT1-L7 | **Dark mode after 1.0** | 03 §10 |
| D-OCT1-L8 | Live metrics: **2 h at 10 s, and 24 h at 1 min** (matches 02 Appendix A 22) | 03 §10 |
| D-OCT1-L9 | **Tablets are read-only and untested** | 03 §10 |
| D-OCT1-L10 | The **production script lock to Git-only** is available but **off by default** | 03 §10 |
| D-OCT1-L11 | **Try-it against production** is set **per API** and **off by default** | 03 §10 |
| D-OCT1-L12 | **Portal branding:** name, logo, accent color, footer | 03 §10 |
| D-OCT1-L13 | **Automation Managers cannot approve traffic actions** | 03 §10 |
| D-OCT1-L14 | **LLM providers are decided at 1.1** | 02 §35.1 Q13 |

## 2. Where the owner decisions override the source docs

- 02 §7.4 puts per-IP pre-auth limiting after the MVP. Superseded by **D-OCT1-P1**.
- 02 §18.4, §9.12 and §35.1 Q3 leave TOTP optional or open. Superseded by **D-OCT1-P2**.
- 02 §21.1 uses timestamped migrations. Superseded by **D-OCT1-BUILD-10**.
- 02 §2.1 says "PostgreSQL 14+". Superseded by **D-OCT1-BUILD-3** (15–17).
- 02 §22 plans nightly real-Okta and AWS tests. Deferred by **D-OCT1-BUILD-11**.
- malli throughout 01, 02 and 03. Superseded by **D-OCT1-SPEC**.

## 3. Handoff defaults (H-n): build-time choices made while distilling the docs

These resolve conflicts or gaps in the sources. Each one is the documented default where one exists. The owner may override any of them.

| ID | Default | Why / source conflict |
|---|---|---|
| H-1 | The Draft 4 entity kinds (APIs, versions, operations, organizations, applications, policies) are **0.x** and ship at **feature level 1 = 0.1.0** | 02 §21.3 implies they need a "1.0 feature level", but 01 §4.1/§4.2 put them in 0.x (M3). 01 wins on scope. |
| H-2 | **PostgreSQL 15–17** (CI on 15 and 17, compose on 16) | 02 §2.1 says 14+. The owner said 15–17. |
| H-3 | **Pre-auth per-IP limiter:**<br>• Slot 6, right after the IP filter and before CORS and authentication.<br>• On by default: 100 rps per IP, burst 200.<br>• Local (per-node share) or Redis, with key `rl:{env}:preauth:client-ip={ip}`; IPv6 grouped by /64.<br>• Exempt: trusted proxies, LB health-check sources and protected CIDRs.<br>• Configured only at global or environment level, under `:security {:preauth-ip-limit …}`.<br>• Rejects with 429, log reason `ratelimit.preauth_ip`, metric `PreAuthRateLimited`. | Implements D-OCT1-P1. The numbers are a handoff default, to be tuned with k6. |
| H-4 | 0.x break-glass notification is a critical audit event, an ERROR log line and an optional signed webhook. Slack and email come in 1.0. | 02 §9.12 and §18.4 name channels that arrive in 1.0 |
| H-5 | **TOTP for all local accounts** (not just break-glass):<br>• RFC 6238: 6 digits, 30 s, SHA-1, ±1 step.<br>• Enrolled at first sign-in, including `/setup`.<br>• 10 recovery codes, hashed with Argon2id.<br>• Seed envelope-encrypted.<br>• Resets audited as critical. | Implements D-OCT1-P2. Local accounts *are* the break-glass path. |
| H-6 | Subscriptions and access requests are 1.0. In 0.x, access is decided by policies (scopes, claims, groups, keys). | 01 §4.1 |
| H-7 | In 0.x, deprecated usage comes from the usage and access-log fields plus recipe dashboards and queries. The console report and `deprecated_last_call` are 1.0. | 04 eval-prompt-7 text mentions a "deprecated-usage report by consumer" for 0.x, but reports are 1.0 (01 §4.2 M15) |
| H-8 | Numbered migrations | = D-OCT1-BUILD-10 (02 §21.1 conflict) |
| H-9 | **OTLP traces are in 0.x** (M7) | 02 §12.8 says 1.0. 01 §4.1 and M7 say 0.x. 01 wins on scope. |
| H-10 | **Audit retention defaults to 2555 days (7 years), minimum 365** | 02 §12.5 says 400 days; 02 §33.1 says 2555. The later, InfoSec-specific section wins. |
| H-11 | **`config_change` retention is 365 days**, never fewer than the last 10,000 revisions; minimum setting 30 days | 02 §5.4 says 30 days; §33.1 says 365 |
| H-12 | **No portal changelog in the 0.x preview** | 03 §5.33 includes it; 01 §4.1 and 02 §25.1 do not |
| H-13 | In 0.x **classification is a free tag** on APIs. Classification-driven default policies and approval routing are 1.0. | 01 §4.2 M12 |
| H-14 | The Okta wizard's any-domain input goes through the outbound SSRF guard (no private, link-local or metadata addresses unless allow-listed) and HTTPS only | D-OCT1-P4 plus 02 SSRF rules |
| H-15 | The 0.x portal branding may ship **name and logo only**. Color and footer come by 1.0. | D-OCT1-L12, scoped to the preview |
| H-16 | The DSL alias is `[befive.dsl :as dsl]`. The examples in 02 using `em/` (a leftover from the earlier working name "Emissary") are read as `dsl/`. | 02 §16.4 |
| H-17 | Where 03 §2.2 lets Operators edit global-level policies, **02 wins**: global attachments and locks are Administrator-only | 02 §15.6, §23.2; Appendix A 43 |
| H-18 | LLM prompt retention: 02 §33.1 says 30 days; Appendix A 38 and §35.1 Q13 say 90 days. **Decide at 1.1** with the providers (D-OCT1-L14). Nothing is built in 0.x. | Conflict noted |
| H-19 | Release labels: almost nothing in 02 is marked "(0.x)", so **0.x scope comes from 01 §4.1/§4.2 and 04's release column**, not from 02's labels | 02 front matter |
| H-20 | The 0.x preview may be reached by partners over the internet (02 Appendix A 52, the Oct 1 update). This supersedes the earlier Draft 4.1 changelog wording "internal by default". | 01 changelog vs Appendix A 52 |

## 4. Decisions from 01 §5 (decisions 1–35), verbatim

*Copied from `01-mvp-scope-and-stack.md` §5 (Draft 4.1 update 3). Decision 32 includes the Oct 1 key-deletion edit. Where §1 or §3 above differs, §1 or §3 wins.*

Carried over from Draft 3, updated:

1. PostgreSQL is the only required dependency. Redis is optional (clustered rate limiting and the shared cache tier), and object storage is optional (asynchronous results and files). There is no file-only mode.
2. *Superseded.* The developer portal was deferred in Draft 3; it is now in 1.0 because DEV-001 is a Must.
3. AWS (ECS and EKS) is the primary documented deployment target; the image runs anywhere Docker runs, and Kubernetes service discovery is supported.
4. Okta is the flagship identity provider, built on generic OIDC, so other providers keep working.
5. Perpetual license with an optional maintenance subscription, verified offline.
6. The product's working name is **BeFive**; the CLI is `b5ctl`, with key prefixes `b5k_` and `b5a_`. The short form B5 appears only inside identifiers. The trademark and search-confusion check is a **pre-launch checklist item**, not an open question (Draft 4.1); derived names change mechanically if it fails.
7. Anomaly detection and scripted response stays in the MVP (1.0), running on the gateways' exact per-minute summaries, in the Draft 4.1 scope (2.19).
8. LLM analysis (from 1.1) and automatic traffic actions are both off by default. LLM output is advisory and never triggers actions.
9. Response scripts run in a sandboxed, separate process and can only request actions. A fixed **Automation Manager** role edits response rules and scripts, enables and configures traffic actions, and manages detectors and silences; Administrators keep all these rights; Operators can approve and revert traffic actions (Draft 4.1).
10. Eclipse Angus Mail for email (approvals, scheduled reports, and deprecation notices in 1.0, anomaly notifications from 1.1); AWS services through the approved AWS SDK v2 (new modules in 3.2 pending approval).
11. *Applied in Draft 4.1.* The Draft 3 anomaly cut points are taken from the start: Event Management, Teams and email anomaly notifications, and LLM analysis move to 1.1, and only IP block and rate-limit tightening ship in 1.0 (consumer block and route disable in 1.1 or later). One cut point remains in reserve if the 1.0 date is at risk: ship the two traffic actions in dry-run and approval modes only. None of this touches a Must requirement; the IdP health alarm (IAM-008) is also provided by the CloudWatch and Datadog recipes.

New in Draft 4:

12. **Scope rule.** Every Must in `00-customer-requirements.md` is in 1.0, the Should (DEV-006) is planned for 1.1, and the Future item (GraphQL) is on the roadmap. Rationale: the document defines Must as required for selection.
13. **Domain model.** APIs with versions and operations compile onto routes; organizations group consumers, whose applications hold credentials. Rationale: the catalog, versioning, policy hierarchy, and reporting by API all need these entities, and compiling onto routes keeps the proven data plane.
14. **Policy hierarchy.** Global, environment, API, version, path, operation; most specific wins; higher levels can lock policies against weakening; resolved at compile time; an effective policy view shows the origin of each value. Rationale: predictable precedence (POL-002) without request-time cost, and central security rules that cannot be undone locally.
15. **Identity forwarding.** Validated context goes upstream as headers and optionally as a short-lived gateway-signed internal JWT (Ed25519 or ES256) verifiable through BeFive's JWKS, with optional mTLS to upstreams; all configured identity headers are stripped from inbound requests. This settles open question 8 of `02-architecture.md` 23.1 in Draft 3 (35.1 in Draft 4; design in 02 9.10): the signed token is in the MVP as an option. Rationale: IAM-004 and IAM-005 require that upstreams can trust the context without re-authenticating.
16. **IdP outage behavior.** Local JWT validation, stale-if-error JWKS for 24 hours by default with a persisted copy, bounded introspection cache with a grace TTL, fail-closed by default with fail-open-for-cached as a per-route option, break-glass local admin, IdP health metric and alarm, and an Okta outage runbook. Rationale: safe operation during an outage without silently accepting unverifiable tokens.
17. **Keys and secrets.** HMAC-hashed keys with rotation, revocation, expiry, and audit, associated with applications; envelope-encrypted secrets with an optional AWS KMS key provider. Rationale: already designed; the requirements confirm it.
18. **Classification-driven governance.** Configurable levels (default Public, Internal, Confidential, Restricted) drive approval routing and default, optionally locked, policies. Approvals are built in or run through ServiceNow (existing connector, webhook plus polling) or a generic webhook adapter, with automatic provisioning and write-back. Rationale: SEC-003, SEC-004, and GOV-4 require classification to act, not just be stored.
19. **IP restrictions** as CIDR allow and deny rules at any policy level, based on the trusted-proxy client address. Rationale: SEC-005 with the existing proxy handling.
20. **Developer portal** as a separate ClojureScript application served by the control plane on its own hostname, with Okta login, group and user visibility, PostgreSQL full-text search, docs from OpenAPI, and try-it against the sandbox by default. There is **one portal, hosted by the production cluster**, listing APIs from linked environments with per-environment availability; try-it traffic goes to the Sandbox cluster (Draft 4.1). Rationale: a separate hostname and application keep the developer-facing surface apart from the admin console for security and branding, while the same image and schemas keep operations simple; one portal gives developers one catalog.
21. **Versioning and deprecation** by path, header, host, media type, or query parameter, with `Deprecation` (RFC 9745), `Sunset` (RFC 8594), and `Link` headers and a report of who still calls deprecated operations. Rationale: standard headers that clients and tools already understand.
22. **Composite endpoints** as bounded, loop-free step graphs stored as EDN, with timeouts and step limits, optional SCI expressions through the sandboxed runner, and identity and policies applied to each step. Rationale: low-code composition (API-003) without turning the gateway into a general workflow engine.
23. **Functions and containers.** Lambda upstreams through the AWS SDK v2 with API Gateway payload 2.0 compatibility; Function URLs as HTTP; service discovery through DNS, Cloud Map, and Kubernetes EndpointSlices. Rationale: existing Lambda functions work unchanged, and container platforms need no sidecar.
24. **Environments.** One BeFive cluster per environment, linked for promotion of signed, versioned bundles with diff, approval, and audit, plus a Git and CI path through `b5ctl`; environment-specific values in overlays. Rationale: strong isolation between Dev, Test, and Prod, and identical promoted content.
25. **Rate limiting and caching.** Limits by client, key, user, group, scope, operation, IP, and tenant with standard `RateLimit`/`RateLimit-Policy` and `Retry-After` headers by default (`X-RateLimit-*` as an option; BeFive follows the final RFC wording); encrypted Caffeine cache with optional Redis, partitioned by authorization context, classification-aware, with purge through the Admin API. Rationale: TRAF-001, TRAF-002, and TRAF-004, with sensitive data protected by default.
26. **Asynchronous endpoints** with `202 Accepted`, job state in PostgreSQL, dispatch by HTTP callback, Lambda asynchronous invocation, or SQS, polling and HMAC-signed callbacks, results in S3, S3-compatible storage, or the local filesystem, and retrieval only by authorized callers. Rationale: TRAF-006 and TRAF-007 without requiring a new mandatory dependency.
27. **Telemetry sinks.** CloudWatch EMF, Datadog, OTLP, and Prometheus, all from the same aggregators; a Datadog Terraform recipe mirroring the CloudWatch one. Rationale: OBS-003 requires Datadog, and one source of numbers avoids disagreements between tools.
28. **Reporting and InfoSec.** A report builder over PostgreSQL rollups (1-minute kept briefly, 1-hour kept 13 months) with scheduled exports; the existing hash-chained audit log extended to all new actions, retention settings, RBAC on logs and reports, dictionary-driven redaction, and an evidence export. Rationale: OBS-007 and OBS-008 without an external analytics store.
29. **Roles.** Administrator, Operator, Consumer Manager, and Auditor stay; API Owner (scoped to owned APIs, approves access), Automation Manager (rules, scripts, detectors, silences, traffic-action settings), and the portal-side Developer role are added. Rationale: access approval belongs with API owners, automation deserves a role that is neither full administrator nor on-call operator, and least privilege (GOV-3) needs roles narrower than Administrator and Operator.
30. **Release plan.** A 0.x design-partner build, then 1.0 GA containing every Must (section 4). Rationale: 1.0 is large, and design partners can validate the core gateway and Okta integration early.

New in Draft 4.1 (the owner's answers to the Draft 4 questions):

31. **Traffic actions in 1.0:** IP block and rate-limit tightening only; consumer block and route disable in 1.1 or later. Rationale: the two most useful actions with the smallest blast radius first.
32. **Optional Okta OAuth app creation** in 1.0, off by default, using the customer's own least-privilege Okta credential stored in the secrets store, with every Management API call audited; binding existing client IDs stays the default. Created apps use `private_key_jwt` only (October 1 update): developers upload only their public key by default and BeFive never holds the private key; BeFive-generated keys are an opt-in, Administrator-only install setting, excluded for Restricted APIs and disableable per API, and a generated private key is deleted from the secrets store immediately after the one-time download, or after 24 hours if it is never downloaded (update 3). Rationale: fully self-service machine onboarding for customers who want it, without making an Okta admin credential a requirement.
33. **0.x portal preview:** read-only portal and catalog (Okta login, group and user visibility, search and facets, OpenAPI docs) without subscriptions, keys, try-it, or access requests; partners may reach it from outside (October 1 update), so it is hardened and security-reviewed for internet exposure in 0.x. Rationale: evaluation prompts 4 and 6 can be partly shown in 0.x, and the preview is the first slice of the 1.0 portal.
34. **Anomaly scope in 1.0:** detection, incidents and grouping, silences, scripted response rules, ServiceNow incidents, Slack and signed-webhook notifications, and the two traffic actions; LLM root-cause analysis, Event Management, and Teams and email anomaly notifications in 1.1 together with DEV-006. Rationale: 1.0 is much larger than planned, and none of the moved items is needed by a Must requirement.
35. **Deprecation emails in 1.0** (October 1 update): notices to developers and application owners who called a deprecated operation, at deprecation, before sunset, and at sunset, configurable or turned off per API, with per-application opt-out. Rationale: the owner wants callers told directly, not only through headers and the portal.


## 5. Decisions from 02 Appendix A (decisions 1–61), verbatim

*Copied from `02-architecture.md` Appendix A (update 3). Decision 50 includes the Oct 1 key-deletion edit. Notes: decision 9 (HTTP/1.1 upstream only) is consistent with D-OCT1-L3; decisions 22 and 30 are consistent with D-OCT1-L8 and D-OCT1-L5; decision 38's 90-day prompt retention is open per H-18. Where §1 or §3 differs, §1 or §3 wins.*

1. Separate operations port 9901 for health, readiness, and internal metrics; data-plane ports do not expose them by default.
2. JSONB document storage with extracted relational columns; natural slug IDs for configuration entities; soft delete only for consumers; credentials revoked, never deleted.
3. One global config revision serialized by a row lock; deltas from `config_change` with full-snapshot fallback; last-known-good snapshot files on local disk (default on).
4. In-house Sieppari-compatible pipeline executor instead of depending on Sieppari.
5. Fixed phase order with four plugin extension phases (`:pre-auth`, `:post-auth`, `:pre-proxy`, `:response`); rate limiting after authorization.
6. `:any` authentication mode does not fall through after a present-but-invalid credential.
7. JWKS and introspection fetched with Aleph's non-blocking client, Nimbus used for parsing and verification only.
8. Panic routing on by default when all targets are unhealthy.
9. Upstream protocol HTTP/1.1 only in the MVP.
10. *Changed in Draft 4:* rate-limit headers default to the IETF draft `RateLimit-Policy`/`RateLimit` fields plus `Retry-After`, with `X-RateLimit-*` as a setting (11.5); monthly quotas are UTC calendar counters; clusters without Redis divide limits by live node count.
11. EMF dimension design: `[Environment]` and `[Environment, Route]` only (plus the `_unmatched` pseudo-route); consumers analyzed with Logs Insights on usage and access lines, not metrics.
12. Live console metrics pushed through an `UNLOGGED` PostgreSQL table, streamed to browsers with Server-Sent Events.
13. Monthly usage report as an EventBridge Scheduler → ECS task running the same image (not a Lambda). This adds the AWS SDK for Java v2 (CloudWatch Logs, S3, KMS modules) to the stack.
14. SCI embedded in the native CLI to evaluate DSL files (new dependency).
15. RE2J for header-match regular expressions (new dependency, linear-time matching).
16. Audit log hash chain for tamper evidence.
17. License file as JSON with base64url payload and Ed25519 signature; uncovered builds refuse to start the gateway role, running builds never stop; node limits are warnings only.
18. Cluster feature level gating for mixed-version safety.
19. JDK TLS provider in the MVP (BoringSSL evaluated later).
20. cosign key-pair signing (not keyless) for air-gapped verification.
21. First-run setup protected by a one-time bootstrap token printed to the container log.
22. Live console metrics retained 2 hours at 10-second resolution and 24 hours at 1-minute resolution in PostgreSQL.
23. EMF metrics come from per-node route summary lines every 60 s (latency as expanded log-linear histograms) in both logging modes, not from per-request lines; doc 1, 2.5 is updated to match.
24. Usage accounting (monthly report, usage and quota widgets) comes from per-node usage summary lines, with a sequence-number completeness check in the report.
25. Optional access-log sampling, off by default: trace-ID-consistent (OpenTelemetry rule), per-route rates, always-keep rules with a per-node keep budget, admin-enabled force-log header, and `sample_rate`/`in_sample`/`log_reason` on every line.
26. Four log groups on AWS (`/befive/access`, `/befive/metrics`, `/befive/app`, `/befive/audit`); the metrics group must be Standard class, the access group may be Infrequent Access.
27. Product name BeFive with derived names: CLI `b5ctl`, key prefixes `b5k_`/`b5a_`, CloudWatch namespace `BeFive`, schema names `befive.<stream>/<n>`, headers `X-BeFive-*`, Clojure namespaces `befive.*`, image `befive`.
28. Anomaly detection runs in the control plane on a leader elected with a PostgreSQL advisory lock, from the exact interval aggregates, independent of CloudWatch and of access-log sampling.
29. Gateways deliver per-minute signal rows by inserting into PostgreSQL (`summary_inbox`), not by pushing to the control plane; they buffer 15 minutes and prune their own rows.
30. Per-IP detection uses Space-Saving top-k sketches (k = 256) per node per minute for authentication failures and for `403`/`429` rejections; IP data kept 24 hours by default.
31. Detector kinds threshold, rate of change, seasonal baseline (robust z-score over the same time-of-week slots of 4 weeks, EWMA warm-up), source-IP burst, upstream flap, and absence; built-in detectors notify the console only.
32. Incident = group of anomalies sharing a strong entity within 15 minutes; one BeFive incident maps to at most one ServiceNow incident.
33. Response rules as EDN plus optional pure Clojure scripts returning action data; scripts run in SCI inside a separate native-image runner process with hard time and memory limits; scripts are editable by Administrators and (Draft 4.1) Automation Managers.
34. Plugin API 1.1 adds response actions for trusted JVM code.
35. Durable action outbox with idempotency keys, retries for 24 hours, per-integration rate and storm limits.
36. Traffic-affecting actions as expiring runtime overrides enforced by gateways; off by default; dry-run, approval, and on modes; blast-radius rails.
37. ServiceNow through the Table API (incident) with OAuth client credentials or basic authentication, `correlation_id` dedupe, work notes, configurable resolution, plain-text summary attachment; Event Management (`em_event`) as an alternative target from 1.1.
38. LLM analysis (1.1) off by default; OpenAI, Azure OpenAI, Bedrock (AWS SDK `bedrockruntime` module), and OpenAI-compatible providers over plain HTTP; redacted context bundle; structured output; advisory only; prompts stored 90 days.
39. New dependencies: Eclipse Angus Mail for SMTP; AWS SDK v2 `bedrockruntime` and `cloudwatch` modules. No statistics or LLM SDK libraries.
40. Hooks endpoints `/hooks/v1/*` for SNS, EventBridge, and generic webhooks, disabled by default; the CloudWatch alarm poller is the recommended AWS path.

**Draft 4 decisions**

41. APIs, versions, and operations are first-class entities that compile onto ordinary routes (6.9); hand-written routes stay supported. Organizations and applications are added, with a default application per existing consumer (5.7).
42. Policy hierarchy global > environment > API > version > path > operation; most specific wins for unlocked values, locks bound lower levels by a strength order and are checked at compile time and on write; resolution happens at compile time only (23).
43. Global locks are Administrator-only; Operators lock at environment level and below; API Owners attach only unlocked policies on owned APIs (15.6, 23.2).
44. Claims expression language as EDN data compiled to closures, with RE2J regular expressions; no general-purpose scripting in the request path (10.5).
45. Inbound identity headers are stripped in slot 5, before any interceptor reads headers (9.9).
46. Optional gateway-signed internal JWT (Ed25519 default, ES256 option) with a BeFive JWKS endpoint and key rotation (9.10).
47. IdP outage resilience: JWKS persisted in PostgreSQL and served stale up to 24 h by default, introspection grace cache off by default, per-route fail-closed default, break-glass local admin (9.12).
48. Classification levels with locked defaults for Restricted (no caching, no body logging, approval required); classification has its own console screen (24.1).
49. Access requests as a state machine with built-in, ServiceNow, or generic-webhook approval; decisions are always re-verified (ServiceNow record re-read; webhook HMAC with replay protection) (24).
50. Okta app creation is optional and off by default, never deletes Okta apps (revocation deactivates), excludes Restricted APIs by default, and uses `private_key_jwt` only: developers upload public keys by default and BeFive never holds the private key; BeFive-generated keys are an Administrator opt-in, excluded for Restricted APIs, and a generated private key is deleted from the secrets store immediately after the one-time download, or after 24 hours if it is never downloaded (24.8; Update 3).
51. One portal SPA on its own hostname and listener, served by the production control plane, with its own API `/portal/v1`; linked-environment catalogs federated through signed feeds; try-it proxied to Sandbox only unless an API enables a production target (off by default) (25).
52. The 0.x portal preview is read-only and may be exposed to partners on the internet, with WAF, rate limits, strict CSP, and a security review from 0.x (25.1, 25.3).
53. OpenAPI import through swagger-parser with remote `$ref`s disabled; the import diff classifies breaking changes and shows effective-policy changes; retired versions answer `410` (26).
54. Deprecation, Sunset, and Link headers from lifecycle data; deprecated usage is a tab on the API page and a shipped report (27).
55. Composite endpoints as a declarative step graph dispatched in-process through each target's chain with Manifold deferreds; expressions in SCI runners (28).
56. Response cache encrypted with AES-GCM, partitioned by tenant and identity by default, Caffeine L1 and optional Redis L2, purge by scope (29).
57. Async endpoints with a PostgreSQL job table, dispatch by HTTP callback, Lambda async, or SQS, results in S3-compatible storage, presigned GET of 60 s by default; console downloads by Administrators only (30).
58. Telemetry sinks fed from the existing aggregators: EMF (unchanged), DogStatsD, Datadog logs, OTLP traces and metrics, Prometheus; Datadog Terraform recipe shipped (31).
59. Reporting from 1-minute and 1-hour PostgreSQL rollups with a typed query model, schedules, and CSV/JSON/XLSX exports (32); retention settings and signed evidence exports (33).
60. Linked environments push signed bundles as proposals; the target validates, diffs, and requires approval; overlays keep environment-specific values; nothing is pulled (34). IETF `RateLimit` headers by default (11.5).
61. Deprecation notice emails in 1.0 from a leader job through the action outbox and Angus Mail, at deprecation, before sunset, and at sunset, configurable per API with per-application opt-out (27.5).

## 6. Build-time assumptions (A-n)

*Grok Build appends entries here. Use this format:*

```
### A-<n> (<YYYY-MM-DD>, M<milestone>) <short title> [needs-owner]?
Context: <what was ambiguous; cite doc §>
Choice: <the documented default you used>
Why: <one or two sentences>
Revert: <how to change it later>
```

### A-1 (2026-10-02, M1) In-house migration runner

Context: D-OCT1-BUILD-10 names files `V0001__description.sql`, while AGENTS.md still names Migratus `0001-name.up.sql` and 02 uses timestamp names.
Choice: `befive.cp.migrate` applies `VNNNN__name.sql` under advisory lock `70551001`. The `schema_migrations` table matches Migratus (id, applied, description). `VNNNN__name.down.sql` is stored and not run. The runner creates `schema_migrations` itself. `V0001__init.sql` creates the `befive_meta` singleton.
Why: The owner filename wins, and keeping the Migratus table shape leaves a later swap reversible. An in-house runner avoids a second migration dialect in 0.1.0.
Revert: Depend on Migratus and rename the files to `0001-init.up.sql`. The table can stay.

### A-2 (2026-10-02, M1) Testcontainers 1.21.4

Context: Testcontainers 2.0.5 is current, but `org.testcontainers/postgresql` stops at 1.21.4.
Choice: Pin `testcontainers`, `jdbc`, and `postgresql` to 1.21.4. Integration tests use `postgres:15-alpine` and `postgres:17-alpine`.
Why: The JDBC module and the generic client have to share one version. 1.21.4 is the newest set that still ships the PostgreSQL module.
Revert: Move all three artifacts together when a 2.x PostgreSQL module exists.

### A-3 (2026-10-02, M1) BusyBox entrypoint on distroless

Context: deployment-docker.md §5 appends `BEFIVE_JAVA_OPTS` to the JVM flags. Distroless has no shell, so the image cannot expand that variable itself.
Choice: Copy the static `busybox:1.36.1-musl` binary to `/busybox` and run `docker/entrypoint.sh` with it. The script execs Temurin 21 (jlink) with the §5 flags and then `${BEFIVE_JAVA_OPTS:-}`. The image user is UID 10001.
Why: A static BusyBox binary runs on distroless without glibc. The flags stay in one script instead of being baked into a Java process launcher.
Revert: Replace the entrypoint with a small Java launcher that splits `BEFIVE_JAVA_OPTS`, and drop the BusyBox copy.

### A-4 (2026-10-02, M1) Database startup timeout

Context: A control plane should fail when PostgreSQL never arrives, and a gateway should keep serving `/healthz` while it is down. The docs do not give the wait.
Choice: `:db-startup-timeout-ms` defaults to 60000, overridable with `BEFIVE_DB_STARTUP_TIMEOUT_MS`. Role `:gateway` returns the pool immediately and stays not-ready. Roles `:control-plane` and `:all` wait, then throw. `/healthz` does not ping the database. `/readyz` does.
Why: One minute is long enough for a local Postgres to accept connections and short enough that a bad URL fails a deploy. Liveness must stay fast or the orchestrator will kill a node whose database is down.
Revert: Remove the setting and the env var, and pick a new wait at the call site.

### A-5 (2026-10-02, M1) Minimal CycloneDX writer

Context: The tag pipeline must publish an SBOM. The build classpath does not include jsonista, and a full CycloneDX CLI is a second toolchain.
Choice: `clojure -T:build sbom` writes CycloneDX 1.5 JSON to `target/befive-sbom.cdx.json` from the resolved Maven coordinates. It is a component list, not a full CycloneDX document with hashes and licenses.
Why: The tag job needs a file GHCR can store. Hashes and license expressions can be added without changing the task name.
Revert: Replace the writer with the CycloneDX CLI or the cyclonedx-maven plugin and keep the same output path.

### A-6 (2026-10-02, M1) Dev-stack image tags and mock IdP port

Context: deployment-docker.md §3 names Postgres 16, Redis 7, LocalStack, a mock IdP, an echo upstream, and optional Toxiproxy. It does not pin image tags or the mock port. LocalStack's current tags are dated (`2026.08.5`), not `4`.
Choice: Compose uses `postgres:16-alpine`, `redis:7-alpine`, `localstack/localstack:2026.08.5` (services `iam,kms,lambda`), `ghcr.io/shopify/toxiproxy:2.12.0`, and `traefik/whoami:v1.10`. The mock IdP is its own Clojure project under `test/support/mock-idp`, port 8088, and is not on the gateway classpath. Product image bases are `eclipse-temurin:21-jdk-jammy`, `busybox:1.36.1-musl`, and `gcr.io/distroless/base-debian12`.
Why: Dated or version tags keep the dev stack reproducible. Leaving the mock IdP out of the uberjar keeps the production classpath free of a test issuer.
Revert: Change the tags in `docker/docker-compose.yml`. Move the mock into `modules/` only if a later milestone needs it on the main classpath. The LocalStack tag in this choice is superseded by A-17.

### A-7 (2026-10-02, M2) Lambda URL-connection client

Context: API-009 needs the AWS SDK for Lambda. The SDK's default Netty client would put a second Netty on the classpath beside Aleph 0.9.11 (Netty 4.1.137).
Choice: Depend on `software.amazon.awssdk/lambda` and `url-connection-client` 2.55.11, and exclude `netty-nio-client` and `apache5-client`. An optional `:endpoint` on the Lambda upstream selects LocalStack or another compatible endpoint and uses static test credentials. One client is cached per region, endpoint and timeout. No client is built at namespace load. The client class is settled by A-18.
Why: Aleph keeps the only Netty. The endpoint override is how the 0.x tests and the compose stack reach LocalStack without a second HTTP stack.
Revert: Remove `:endpoint` and the exclusions, and accept the SDK's Netty client only if it is pinned to 4.1.137.

### A-8 (2026-10-02, M2) Size limits combine by the minimum

Context: data-plane-proxy-core.md says limits exist at several levels. It does not say how they combine, or which status a header-block overrun gets. The 6 MB figure in the Lambda section is the invoke payload cap, not an HTTP body cap.
Choice: The effective cap for each of request body, response body and header block is the minimum value set on the snapshot, the route's API and the route. An absent value does not raise a tighter one. Header overrun is 413 `limits.headers_too_large`. A known request `Content-Length` over the cap is 413 `limits.body_too_large`. A known response `Content-Length` over the cap is 502 `limits.response_too_large`. Bodies without a length are not buffered to enforce the cap.
Why: A route must not be able to raise a global cap. Buffering every body to measure it would break streaming.
Revert: Combine limits with a different operator in `befive.gateway.limits/effective`, and buffer bodies only if a later decision requires it.

### A-9 (2026-10-02, M2) Idempotent retries and the 20 percent budget

Context: 02 §8 allows retries for idempotent requests inside a 20 percent budget. It does not list the methods, and it does not say whether POST can opt in.
Choice: The default methods are GET, HEAD, OPTIONS, PUT, DELETE and TRACE. POST and PATCH retry only when the upstream sets `:retry-methods`, which replaces that default. The budget admits `ceil(0.2 * requests)` retries, so the first request may retry once and the ratio settles at 20 percent. HTTP retries only connection failures (connect timeout, connect failed, TLS). Lambda retries only a result whose `:retryable` flag is true, and never a function error or a timeout.
Why: RFC 9110's idempotent methods are the safe default. A floor of 20 percent would reject every retry until the fifth request, which makes the budget unusable for a single call.
Revert: Change `idempotent-methods` and `reserve-retry!` in `befive.gateway.balance`.

### A-10 (2026-10-02, M2) reitit-core 0.11.0

Context: 01 names reitit as the router. Maven Central has no `metosin/reitit` artifact that carries the linear router, and `metosin/reitit-core` 0.11.0 is EPL-1.0. The trie router throws when a static template and a parameter template share a prefix.
Choice: Depend on `metosin/reitit-core` 0.11.0. Compile with `{:router r/linear-router :conflicts nil}` and sort static templates ahead of parameter templates. EPL is flagged for review and does not fail the license scan.
Why: This is the documented router, and the linear router matches the reference oracle without rejecting legal gateway templates.
Revert: Replace the dependency and `befive.gateway.router/router-for`.

### A-11 (2026-10-02, M2) Aleph timeout mapping

Context: Upstreams have connect, header and idle timeouts. Aleph's `:read-timeout` applies until the response body completes, which would cancel an SSE stream.
Choice: `:connect-timeout-ms` is the connect timeout and the pool timeout. `:read-timeout-ms` is Aleph's `:request-timeout` (time to response headers). `:idle-timeout-ms` is the pool connection idle timeout. The gateway does not set Aleph `:read-timeout`. The data-plane server uses `:executor :none` so the handler returns a deferred on the event loop. Ops and admin servers stay on Aleph's default executor.
Why: Header timeout and idle timeout are the two clocks the docs name, and SSE has to stay open past the header timeout.
Revert: Pass a different Aleph timeout from `befive.gateway.proxy/send-once`.

### A-12 (2026-10-02, M2) Consistent-hash key and weights

Context: 02 §8 names consistent hash and smooth weighted round-robin. It does not define the hash input, the ring size, or whether weight applies to every algorithm.
Choice: The hash key is the path plus the query string. The ring has 64 virtual points per target, ordered by unsigned FNV-1a 64. Weight applies only to smooth weighted round-robin. Least-requests ignores weight and breaks ties toward the smaller target id. A panic threshold of 0 never panics.
Why: Path plus query is stable for a retry of the same request. Sixty-four points is enough for a handful of targets without a large ring.
Revert: Change `befive.gateway.balance/hash-key` and `hash-pick`.

### A-13 (2026-10-02, M2) Active probes are opt-in

Context: Health checks are in M2. The docs do not say that every target is probed, or which client sends the probe.
Choice: Active probes run only when the upstream has `:health` with `:interval-ms` (path defaults to `/`, unhealthy-after defaults to 2). The probe is a JDK `HttpClient` GET with redirects disabled, on a one-thread scheduler every second, not on the proxy pool and not on the event loop. Passive ejection always runs, with the same default of two failures.
Why: A target without `:health` is a normal upstream. Probing it would create traffic the operator did not ask for.
Revert: Start probes for every target in `befive.gateway.health/start`.

### A-14 (2026-10-02, M2) Last-known-good file format

Context: config-revisions.md says a gateway starts from the last good revision when the database is down. It does not define the file bytes. There are no DEKs until secrets land in M10.
Choice: Each file is gzip of EDN `{:sha256 :revision :document}`. `:document` is the `pr-str` of the snapshot, and `:sha256` is the SHA-256 of that string. Writes use a temp file, `FileChannel.force`, and an atomic move. The newest three `snapshot-<rev>.edn.gz` files are kept. Reads bind `*read-eval*` to false and reject a hash mismatch. A failed write is swallowed.
Why: `pr-str` of a map is not stable across a later read and print, so the hash has to cover the exact string that was stored. Plain EDN is enough until snapshots contain secrets.
Revert: Change `befive.gateway.lkg` and delete the files under the LKG directory.

### A-15 (2026-10-02, M2) Snapshot file outranks the database

Context: Until the Admin API exists, a design-partner node needs a config source. config-revisions.md makes PostgreSQL the source of truth once it is reachable.
Choice: When `BEFIVE_SNAPSHOT_FILE` is set, that file is the only document source and the node does not apply database snapshots. Otherwise the node loads the newest `config_snapshot`, applies a contiguous `config_change` delta, and falls back to LKG if the database is down or the config tables are missing. `BEFIVE_LKG_DIR` defaults to `/var/lib/befive/lkg`. Readiness stays as in M1: a database outage still makes `/readyz` return 503 while `/healthz` stays 200. Feature level stays 1.
Why: A mounted file lets the compose demo serve a route before the Admin API can write one. The database remains the source of truth as soon as the file is unset.
Revert: Ignore `:snapshot-file` in `befive.gateway.sync/start-load!`.

### A-16 (2026-10-02, M2) Static routes skip the linear router

Context: A-10 compiles every template with reitit's linear router. That router builds a trie per template, so 2,000 static routes compile in about 300 ms on the first call. The M2 budget is 200 ms. A static template and a parameter template that share a prefix also conflict in the trie router.
Choice: Templates with no parameters and no splat are a hash map keyed by the path. Parameter and splat templates still use `{:router r/linear-router :conflicts nil}`, ordered by specificity. An exact static hit wins over a dynamic template, including when the method does not match (405, not a fall-through). A 405 still carries the path parameters.
Why: The hash map compiles and matches in constant time, and pulling static templates out of the trie is what makes `/orders/new` and `/orders/:id` legal. Match order stays the one the oracle uses.
Revert: Send every template through `router-for`'s linear router again.

### A-17 (2026-10-02, M2) LocalStack community tag

Context: A-6 pinned `localstack/localstack:2026.08.5`. That image exits 55 on start when `LOCALSTACK_AUTH_TOKEN` is unset. LocalStack retired the unauthenticated community image on 2026-03-23, and calendar tags from that date require a token. This environment has no token.
Choice: Compose and the Lambda integration test use `localstack/localstack:4.14.0`, the last community release (February 2026). Compose still requests services `iam,kms,lambda`. The test requests `lambda` and still binds the Docker socket so the Lambda executor can start a function container.
Why: 4.14.0 boots without a token and still accepts a payload 2.0 invoke, which is all API-009 needs in 0.x.
Revert: Set `LOCALSTACK_AUTH_TOKEN` and restore `localstack/localstack:2026.08.5` in `docker/docker-compose.yml` and `lambda_integration_test.clj`.

### A-18 (2026-10-02, M2) Lambda uses the sync client off the event loop

Context: A-7 keeps the AWS SDK off Netty by using `UrlConnectionHttpClient`. That client implements `SdkHttpClient`. `LambdaAsyncClient.httpClient` requires `SdkAsyncHttpClient`, and passing the URL-connection client throws `ClassCastException` before any invoke. The design's async client needs the SDK Netty client or the AWS CRT client.
Choice: Build a synchronous `LambdaClient` with `UrlConnectionHttpClient`. `befive.gateway.lambda.aws/invoke` runs the call through `befive.gateway.block/off-loop`, which hops to a virtual thread when the caller is on the Netty event loop. The transport still returns a Manifold deferred of a payload map or `{:reason :retryable}`.
Why: One Netty stays on the classpath, and the event loop does not block on `Invoke`.
Revert: Add an async HTTP client pinned to Netty 4.1.137, or the AWS CRT client, and build `LambdaAsyncClient` again.

### A-19 (2026-10-02, M2) LocalStack Lambda needs the Docker socket without SELinux confinement

Context: LocalStack 4.14 runs Lambda functions by starting containers through the host Docker socket. Mounting `/var/run/docker.sock` is not enough on this host. The socket is labeled `container_file_t`, SELinux is enforcing, and a root process in the container gets `EACCES` on connect. LocalStack then marks the function `Failed` with "Docker not available".
Choice: The Lambda integration test and the compose LocalStack service bind `/var/run/docker.sock` and set the Docker security option `label=disable`. That option is a no-op where SELinux is disabled.
Why: The function runtime container is started by the host daemon. Disabling the label on the LocalStack container is the usual way to let it use the socket without relabeling the host socket.
Revert: Remove `label=disable` and the compose socket mount. On an enforcing host, relabel the socket so a confined container may connect.
