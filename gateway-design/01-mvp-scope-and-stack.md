# BeFive: MVP Scope and Technology Stack

Draft 4.1, September 28, 2026 (updated October 1, 2026). Covers stage 1 (MVP features) and stage 2 (Clojure stack). Implementation, documentation, UI design, and deployment get their own documents (`02-architecture.md`, `03-ui-design.md`); requirement-by-requirement coverage is in `04-requirements-traceability.md`.

## Changelog

- **Draft 4.1 update 3 (October 1, 2026).** Generated-key retention (owner decision): a BeFive-generated Okta client private key is deleted from the secrets store immediately after the one-time download, or after 24 hours if it is never downloaded; after deletion it cannot be recovered and the developer rotates to a new key. Developer-uploaded public keys stay the default; generated keys stay an Administrator opt-in, excluded for Restricted APIs (2.6, decision 32, R11, 7).
- **Draft 4.1 update 2 (October 1, 2026).** Key custody for Okta apps BeFive creates: by default developers generate their own key pair and upload only the public key, and BeFive never holds the private key; BeFive-generated keys become an opt-in, Administrator-only install setting, excluded for Restricted APIs (2.6, decision 32, R11, 4.3).
- **Draft 4.1 update (October 1, 2026).** Applies the owner's answers to the remaining Draft 4.1 questions and to two `02` questions. (1) Okta apps that BeFive creates use **`private_key_jwt` only**: BeFive generates the key pair, keeps the private key in the secrets store, registers the public JWK with Okta, and supports rotation; client secrets are not used for apps BeFive creates (2.6, decision 32). (2) Partners **may access the 0.x portal preview from outside**, so it is no longer internal-only by default; portal security notes are strengthened for internet exposure, R4 applies from 0.x, and milestone 10 gets a dedicated portal-preview security review (2.11, R4, 4.2). (3) The IETF `RateLimit` and `RateLimit-Policy` headers stay the default, `X-RateLimit-*` stays an option, and BeFive tracks the final RFC wording (2.7, decision 25). (4) **Deprecation emails to developers are in 1.0** (2.8, decision 35). The effort estimate grows slightly (4.3).
- **Draft 4.1 (September 28, 2026).** Applies the owner's answers to the seven Draft 4 open questions. (1) A new fixed **Automation Manager** console role edits anomaly response rules and scripts, enables and configures traffic actions, and manages detectors and silences; Administrators keep all rights, and Operators approve and revert traffic actions. (2) The 1.0 traffic actions are **IP block and rate-limit tightening** only; consumer block and route disable move to a later update (1.1 or later). (3) **BeFive** stays the working name; the trademark check becomes a pre-launch checklist item. (4) **One developer portal**, hosted by the production cluster, lists APIs from linked environments with per-environment availability, and sends try-it traffic to the Sandbox cluster. (5) **Okta OAuth app creation** through the Okta Management API on access approval is in 1.0 as an optional, off-by-default feature using the customer's own least-privilege Okta credential; binding existing client IDs stays the default. (6) The 0.x build gains a **thin read-only portal and catalog preview** (Okta login, group and user visibility, catalog search and facets, OpenAPI-rendered docs), so evaluation prompts 4 and 6 can be partly shown in 0.x. (7) ServiceNow Event Management, Microsoft Teams and email **anomaly** notifications, and the LLM root-cause analysis move to **1.1**, together with the LLM provider integration and DEV-006; email itself stays in 1.0 for access-request approvals and scheduled reports. Sections 0, 2.6, 2.11, 2.12, 2.17, 2.19 to 2.21, 3, 4, 5, 6, and 7 are updated; the effort estimate changes modestly (section 4.3). *Update, October 1, 2026:* references to `02-architecture.md` now use its Draft 4 numbering (open questions are 35.1), and the role, try-it, and preview details were aligned with `03-ui-design.md` Draft 4 (2.2): API Owners publish and deprecate but cannot set locks or retire versions, Automation Managers acknowledge and resolve incidents but do not approve traffic actions, Consumer Managers approve their access-request steps, try-it never defaults to production, and the 0.x preview is internal by default.
- **Draft 4 (September 28, 2026).** The product is renamed from Emissary to BeFive; all derived names change with it (CLI `b5ctl`, key prefixes `b5k_` and `b5a_`, headers `X-BeFive-*`, namespaces `befive.*`, variables `BEFIVE_*`, CloudWatch namespace `BeFive`). The customer requirements in `00-customer-requirements.md` are incorporated: every **Must** requirement is now in the MVP (release 1.0), the one **Should** (DEV-006) is planned right after 1.0, and the one **Future** item (API-008, GraphQL) is on a documented roadmap. The developer portal, OpenAPI import and export, response caching, and OpenTelemetry tracing move from the deferred list into the MVP. New areas: first-class APIs with versions and operations, organizations and applications, a policy hierarchy with inheritance and locking, IdP-outage resilience, classification-driven access requests with ServiceNow, composite endpoints, AWS Lambda upstreams and service discovery, asynchronous endpoints with secure file retrieval, linked environments with promotion, Datadog and other telemetry sinks, a report builder, and InfoSec controls. Milestones become a two-step release plan (0.x design-partner build, then 1.0 GA) with a revised effort estimate (section 4).
- **Draft 3 (September 28, 2026).** Named the product and added anomaly detection and scripted response, with ServiceNow and LLM integrations, as an MVP must-have.
- **Draft 2 (September 28, 2026).** Metrics moved into per-node summary lines; optional access-log sampling added.
- **Draft 1 (September 27, 2026).** Initial MVP scope and stack.

---

## 0. Executive summary

Draft 3 described a gateway with a web console: routing, authentication (including an Okta preset), per-route access policies, consumers and plans, rate limiting, CloudWatch logging and dashboards, configuration as code, custom plugins, and in-product anomaly detection with scripted responses. The customer requirements document asks for a full API management platform, and it marks almost everything as Must, which it defines as "required for selection or initial implementation". Draft 4 therefore treats every Must as MVP scope.

The main changes are these:

- **The domain model grows up.** Draft 3 configured services and routes. Draft 4 adds first-class APIs with versions and operations (usually imported from OpenAPI), which compile onto the existing routes, and groups consumers into organizations (tenants) whose applications hold the credentials. This is the foundation for the catalog, versioning, deprecation, the policy hierarchy, and reporting by API.
- **Policies become hierarchical.** Policies can be set at global, environment, API, version, path, and operation level. The most specific value wins, but a higher level can lock a policy so that lower levels cannot weaken it. The effective policy is computed at compile time, so the request path does not get slower.
- **Identity is deepened for Okta.** Claims rules with a small expression language, a gateway-signed internal JWT for upstreams, strict anti-spoofing, and a documented, tested approach to Okta outages.
- **Developer enablement joins the MVP.** A separate developer portal (catalog, docs, try-it against a sandbox, self-service keys, access requests), OpenAPI import and export, lifecycle states, and a data dictionary built from ingested schemas.
- **Governance becomes workflow.** API classification drives approval routing (built-in or through ServiceNow) and default policies such as caching, IP ranges, and redaction.
- **New backend and traffic patterns.** Composite endpoints, AWS Lambda upstreams, service discovery for container platforms, encrypted response caching, and asynchronous endpoints with authorized file retrieval.
- **Operations and evidence.** Datadog, OpenTelemetry, and Prometheus join CloudWatch as telemetry sinks fed by the same aggregators; a report builder replaces the fixed monthly report; the existing hash-chained audit log gains an evidence export and wider coverage; environments are linked for controlled promotion.

Everything approved in Draft 3 stays: the stack, one image with two roles, PostgreSQL as the gateways' only required dependency, the perpetual offline license, the logging and sampling design, the CloudWatch recipe, anomaly detection with scripted responses and ServiceNow incidents, and the console RBAC model (extended by the API Owner and Automation Manager roles, plus the portal-side Developer role). Draft 4.1 moves the LLM best guess, ServiceNow Event Management, and Teams and email anomaly notifications to the first update (1.1), and ships two of the four traffic actions in 1.0 (section 2.19). SAML, SCIM, the Helm chart, and gRPC stay deferred. SOAP is an explicit non-requirement.

The MVP is now much larger. As an estimate, to be firmed up in planning, 1.0 is roughly 2.2 to 2.7 times the engineering effort of Draft 3's MVP, with a central estimate of about 2.4 times (section 4.3); the 0.x build alone is about 1.3 to 1.5 times. To get working software in front of customers earlier, the plan ships a **0.x design-partner build** with the core gateway, Okta, keys, policy inheritance, IP rules, rate limits, CloudWatch and Datadog, configuration as code, and a read-only portal and catalog preview, then **1.0 GA** with every Must requirement.

---

## 1. Product shape

- **Self-hosted, perpetual license.** A firm buys it once, installs it, and can run it indefinitely. The product never phones home and works fully offline and in air-gapped networks. Updates are an optional paid maintenance entitlement, checked against a signed license file rather than a license server.
- **One Docker image, two roles.** The same image runs as a *gateway node* (the data plane that proxies traffic) or a *control plane node* (the Admin API, the web console, and the developer portal). A small install runs both roles in one container. A larger install runs several gateway containers and one or two control plane containers, all pointed at a shared PostgreSQL database.
- **The GUI and the Clojure configuration edit the same thing.** Every API, route, policy, and consumer is plain data validated by one shared schema. The web console, the Admin REST API, and the Clojure/EDN configuration files are three views of that data, so a team can start in the GUI, export to code, and move to a Git-driven workflow (or the reverse) without converting anything.
- **The hot path never touches the database.** Each gateway node holds a compiled, immutable routing table in memory, with effective policies already resolved, and swaps it atomically when configuration changes. If the database goes down, gateways keep serving traffic with the last good configuration.
- **One cluster per environment.** Dev, Test, Prod, and Sandbox are separate BeFive installs (control plane plus PostgreSQL), linked so that configuration can be promoted between them with review and audit (2.12).
- **Requirement priorities drive scope.** Must requirements in `00-customer-requirements.md` are in 1.0; the Should requirement is planned right after 1.0; the Future requirement is on the roadmap (2.20 to 2.22).

---

## 2. MVP features

Each item is tagged with the requirement IDs it satisfies. GOV-1 to GOV-6 are the six cross-cutting governance bullets of `00-customer-requirements.md`, numbered in order. "Existing" marks behavior that was already in Draft 3.

### 2.1 Domain model: APIs, versions, operations, organizations, applications
- **API** is a first-class entity with a name, domain, owner (a person or team), tags, classification (2.6), lifecycle state (2.11), description, and links to documentation. (DEV-002, DEV-004, SEC-004, GOV-4)
- An API has **versions** (for example `v1`, `v2`, or `2026-09-01`), each with its own lifecycle state, OpenAPI document, backing service, and versioning rule (2.8). (API-001, DEV-004)
- A version has **operations** (method plus path template), usually imported from OpenAPI (2.11) and editable in the console. Operations compile onto the existing routes: one operation normally becomes one route, and a hand-written route can still exist outside any API. (DEV-007, POL-002)
- **Organizations (tenants)** group consumers, for example a partner company or an internal business unit. **Consumers** belong to an organization. **Applications** belong to a consumer and hold the credentials: API keys and bound Okta OAuth client IDs (and, as before, client-certificate mappings). Subscriptions and entitlements attach to applications. (SEC-001, TRAF-001, DEV-001)
- Plans (rate limits and quotas) stay as in Draft 3 and can be assigned at organization, consumer, or application level. (TRAF-001, existing)

### 2.2 Routing, proxying, and upstreams
- Existing: routes match on host, path (exact, prefix, and parameters such as `/orders/:id`), method, and optionally headers; round-robin or least-connections load balancing; active and passive health checks with automatic recovery; per-route timeouts, retries limited to idempotent methods, path rewriting and prefix stripping; header add, remove, and rename; TLS termination with SNI; HTTP/1.1 and HTTP/2 from clients; configuration changes on all nodes within seconds with zero dropped connections. (API-004, API-006)
- Existing: **upstream connection pooling** with configurable pool sizes, idle timeouts, keep-alive, and pool metrics. (TRAF-003)
- **Large payloads and streaming.** Existing streaming of request and response bodies without full buffering and WebSocket pass-through, plus configurable request and response size limits at every policy level, Server-Sent Events and chunked-transfer pass-through with flush-through and idle timeouts, and metrics for bytes, stream durations, open streams, and limit rejections. (TRAF-005)
- **AWS Lambda upstreams.** A new upstream type invokes a function through the AWS SDK v2 asynchronous Lambda client using the task's IAM role, with an event and response mapping compatible with API Gateway's payload format 2.0, so existing Lambda functions work unchanged. It maps function errors, throttling, and timeouts to `502`, `503`, and `504` with a logged reason, and emits invocation, error, throttle, and duration metrics. Lambda Function URLs also work as ordinary HTTP upstreams. Scaling stays with Lambda; BeFive applies concurrency limits per upstream so it cannot overrun a function's reserved concurrency. (API-004, API-005)
- **Service discovery for container platforms.** Upstream targets can come from DNS A and SRV records, AWS Cloud Map, or Kubernetes EndpointSlices (read-only access), refreshed continuously, combined with health checks and load balancing across the discovered endpoints. Scaling and rolling deployments are picked up without configuration changes. (API-006, API-004)
- **Composite endpoints** and **asynchronous endpoints** are route kinds described in 2.9 and 2.10.

### 2.3 Identity and authentication (Okta first)
- Existing: pluggable authentication, several methods per route: OAuth2 and OIDC bearer tokens (JWT) with local signature validation against the issuer's JWKS, checks of `iss`, `aud`, `exp`, `nbf`, and allowed algorithms with configurable clock skew; token introspection (RFC 7662) for opaque tokens; API keys; mutual TLS. (IAM-001, IAM-002)
- Existing: the **Okta preset** derives discovery, JWKS, and introspection endpoints from the Okta domain and authorization server, and tests the connection. Generic OIDC providers work through the same mechanism. (IAM-001, IAM-002)
- **Scope and claims rules.** Policies can require scopes and any token claims, including custom claims, through a small expression language: equality, membership in a set, numeric and time comparison, presence, and `any`/`all` over array claims (for example `groups` contains any of `["payments-read", "payments-admin"]`). Expressions are data, compiled into plain functions at configuration time, with no general-purpose code on the request path. (IAM-003, GOV-3)
- **Machine-to-machine access.** OAuth client-credentials tokens from Okta are validated like any JWT and mapped to applications by client ID, so policies and rate limits apply per machine identity. API keys remain an alternative for consumers without OAuth. (IAM-006)
- **User context.** Tokens issued to an authenticated Okta user (authorization code with PKCE in the calling application) carry the user's subject, groups, and claims, which policies can evaluate and BeFive forwards upstream for delegated access. (IAM-007)
- **Centralized AuthN/AuthZ and trusted context forwarding.** BeFive performs primary authentication and authorization so upstreams do not have to. It forwards only validated context (subject, client ID, application, organization, groups, scopes, and selected claims) in configurable headers, and optionally as a short-lived **gateway-signed internal JWT** (Ed25519 or ES256, a few minutes' lifetime, audience set to the upstream) whose public keys BeFive publishes at its own JWKS endpoint so upstreams can verify it. Optional mTLS from BeFive to upstreams lets upstreams accept traffic only from the gateway. The original token can be passed through or stripped. (IAM-004, IAM-005, IAM-007)
- **Anti-spoofing.** On every inbound request, BeFive removes every header it is configured to set (identity headers, the internal JWT header, and any other `X-BeFive-*` header) before authentication; the only exception is a documented inbound control header such as the optional force-log header, which is read under its own restrictions and stripped before proxying. This means a client can never inject identity context. The removal list is derived from the configuration and cannot be disabled per route. (IAM-005, GOV-2)
- **IdP outage resilience.** JWT validation is local and does not call Okta. JWKS documents are served stale-if-error for a configurable window (default 24 hours) and the last known JWKS is persisted, so a restarted node can validate tokens during an outage. Introspection results use a bounded cache with a grace TTL. Each route chooses fail-closed (the default) or fail-open-for-cached behavior when Okta cannot be reached. Console and portal single sign-on degrade while Okta is down; a break-glass local administrator account keeps the console usable. An IdP health metric (JWKS fetch age, fetch errors, introspection errors and latency) and a default alarm and anomaly detector are included. Deliverable: a short **Okta outage runbook** in the product documentation. (IAM-008, OBS-004)
- **After 1.0 (Should):** OAuth 2.0 token exchange (RFC 8693) for upstream-specific tokens (2.20).

### 2.4 Access control and the policy hierarchy
- Existing: default deny; named, reusable policies; `401` and `403` responses with the machine-readable reason in the log, never in the response body. (IAM-003, GOV-3)
- **Hierarchy.** Policies can be attached at six levels: global, environment, API, version, path, and operation. The most specific value wins. A higher level can mark a policy **locked (enforced)**, and lower levels then cannot weaken it: for example "authentication required", "redact bodies in logs", or "internal IP ranges only" set on a Restricted API cannot be switched off on one operation. Lower levels can still tighten a locked policy. (POL-001, POL-002, GOV-3)
- **Policy kinds covered by the hierarchy:** security and authentication, IP rules, rate limits and quotas, logging and redaction, caching, size limits, and deprecation. (POL-001)
- **Compile-time resolution.** The effective policy for each operation is computed when configuration is compiled into the immutable route table, so the hierarchy adds no request-time cost. Validation rejects configurations that try to weaken a locked policy, with a message naming the lock. (POL-001, POL-002)
- **Effective policy view.** The console and the Admin API show the effective policy for any operation with the source of each value (which level set it, and whether it is locked). The same view is available from `b5ctl`. (POL-002, GOV-6)
- **IP restrictions.** CIDR allow and deny lists (IPv4 and IPv6) at any policy level, evaluated against the true client address derived from trusted proxies and `X-Forwarded-For` (existing handling in `02-architecture.md` 8.8). Typical use: internal ranges only for internal APIs, and separate internal and external listeners or hosts. (SEC-005, GOV-3)

### 2.5 API keys and secrets
- **Key lifecycle.** Issue, rotate (two active keys during rollover), revoke, and expire API keys, each associated with an application and therefore a consumer and organization, and every action audited. Keys use the `b5k_` prefix, are stored only as HMAC-SHA256 hashes with a pepper, are shown once at creation, and are never logged. (SEC-001, SEC-002, GOV-1)
- **Secrets at rest and in transit.** Existing envelope encryption for secrets (upstream credentials, client secrets, private keys, integration credentials) with a master key from an environment variable, a mounted file, or AWS KMS as the key provider. TLS protects all traffic between clients, gateways, control plane, database, and integrations. (SEC-002)
- **No exposure.** Secrets never appear in logs, portal views, console views after creation, or upstream headers, unless a route explicitly forwards a credential, which is flagged in the effective policy view and audited. (SEC-002, GOV-5)

### 2.6 Classification and the access-request workflow
- **Classification.** Every API (and optionally each version or operation) carries a classification from a configurable list; the default levels are Public, Internal, Confidential, and Restricted. Data dictionary fields can carry their own tags, such as PII (2.11). (SEC-004, GOV-4)
- **Classification drives policy.** Each level can supply default, optionally locked, policies. Shipped defaults: Restricted means no response caching, internal IP ranges only, body redaction in logs, and a stronger approval chain; Confidential means per-consumer cache partitioning and body redaction. Customers can change these defaults. (SEC-004, TRAF-004, OBS-008, GOV-4, GOV-5)
- **Access requests.** A developer requests access to an API version (and a plan) for one of their applications in the portal. BeFive routes the request for approval based on the API's classification and owner, for example API owner only for Internal, and API owner plus a security approver for Restricted. (SEC-003, SEC-004, DEV-001)
- **Built-in approvals** in the console and portal, with notifications by email, **or a ServiceNow integration** that reuses the existing ServiceNow connector (OAuth 2.0 client credentials): BeFive creates a request record, receives the approval outcome through an inbound signed webhook (with polling as a fallback), then automatically provisions the entitlement and keys, and writes the outcome back to ServiceNow and to the audit log. (SEC-003, GOV-1)
- A **generic webhook adapter** supports similar workflow systems (for example Jira Service Management): BeFive sends a signed request event and accepts a signed decision callback. (SEC-003)
- **Optional Okta OAuth app creation (off by default).** When a customer enables it, an approved access request for a machine application can create the Okta OAuth client (a client-credentials service app) through the Okta Management API and bind its client ID to the BeFive application automatically. The customer supplies its own least-privilege Okta credential (an API token created by a dedicated admin with a custom admin role limited to application management, or an OAuth 2.0 service app with the `okta.apps.manage` scope), which is stored in the encrypted secrets store; every call is audited. Created apps always use **`private_key_jwt`** client authentication, and client secrets are not used for them. By default the developer generates their own key pair and uploads only the public key (JWK or PEM) in the portal or console; BeFive validates it (RSA 2048 bits or more, or EC P-256; private material is rejected), registers it with the Okta app, and never holds the private key. Rotation is by uploading a second public key that overlaps with the first, then deactivating the old one; a compromise report deactivates a key at once. As an opt-in convenience, an install-level setting (off by default, Administrator-only) lets BeFive generate the key pair instead, keeping the private key in the secrets store only until a one-time, audited download after a fresh sign-in (the private key is deleted from the secrets store immediately after the one-time download, or after 24 hours if it is never downloaded); it is never available for Restricted APIs, and a per-API setting can disable it. BeFive never deletes Okta apps (revocation deactivates them), and APIs classified Restricted are excluded by default. Binding existing client IDs created by Okta administrators remains the default. (SEC-003, IAM-006)
- **After 1.0 (Should):** optional Okta group membership as an entitlement, through the Okta Management API (2.20).

### 2.7 Rate limiting, quotas, and caching
- Existing: token-bucket rate limits and monthly quotas, per consumer, per route, and per consumer on a route; in-memory counters on a single node and Redis for accurate cluster-wide limits; `429` on rejection. (TRAF-001, TRAF-002)
- **More dimensions.** Limits and quotas can be keyed by application (client ID), API key, user (token subject), Okta group, scope, operation, source IP, and organization (tenant), alone or combined, at any policy level. (TRAF-001)
- **Clear responses and observability.** Standard `RateLimit` and `RateLimit-Policy` headers (the IETF HTTPAPI fields, the default; BeFive tracks the final RFC wording, and `X-RateLimit-*` remains an option) and `Retry-After` on `429`, the limit decision in the access log, rate-limit metrics by dimension, and the existing security dashboard panels. Load tests verify accuracy under load in both the local and Redis modes. (TRAF-002, OBS-004)
- **Encrypted response caching.** An in-memory Caffeine cache per node with optional Redis as a shared tier. Entries are encrypted with AES-GCM using keys from the existing keyring, including in memory, so a heap dump or Redis snapshot does not expose response bodies. Cache keys are partitioned by authorization context as the caching policy says: public, per consumer (application), or per user. BeFive honors `Cache-Control` (including `private` and `no-store`), applies TTLs, and supports purge and invalidation through the Admin API and `b5ctl`. Caching is classification-aware and off for Restricted APIs by default. Cache hits, misses, stores, and purges are logged and counted without bodies. (TRAF-004, GOV-5)

### 2.8 Versioning and deprecation
- **Version routing** by path prefix, header, host, media type (for example `Accept: application/vnd.acme.v2+json`), or query parameter, configurable per API, with a default version for requests that name none. (API-001)
- **Deprecation.** Operations, versions, and APIs can be marked deprecated with a date and a sunset date. BeFive then emits the `Deprecation` header (RFC 9745), the `Sunset` header (RFC 8594), and a `Link` header pointing to migration documentation, all configurable. The catalog and portal show deprecation badges and the changelog. (API-002, DEV-004)
- **Who still calls it.** A report and console view list the consumers and applications still calling deprecated operations, with volume and last call time, so owners can contact them before retirement. (API-002, OBS-007)
- **Deprecation emails (1.0).** BeFive emails the developers and application owners whose applications called a deprecated operation, with notices at the deprecation date, before sunset (by default 30 and 7 days before), and at sunset. Notices can be configured (schedule, template text, reply-to) or turned off per API, and developers can opt out per application. Recipients come from the deprecated-usage data, so only actual callers are contacted. (API-002, DEV-004)

### 2.9 Low-code composite endpoints
- A **composite endpoint** is a route whose backend is a declarative, bounded step graph: sequential and parallel calls to existing routes or upstreams, conditional steps, and JSON mapping of requests and responses with JSON Pointer and templates. Optional expressions use SCI through the existing sandboxed script runner. (API-003)
- There are no loops; each composite has a timeout and limits on the number of steps and response size. Each step runs with the caller's validated identity and the target route's policies, so composition cannot bypass access control. (API-003, GOV-3)
- Composites are edited through a form and graph builder in the console, stored as EDN like all configuration, and testable in the route tester with a per-step trace. (API-003, POL-003)

### 2.10 Asynchronous endpoints and generated files
- An **asynchronous endpoint** answers `202 Accepted` with a job ID and a status URL. BeFive dispatches the work to the backend by HTTP with a callback URL, by asynchronous Lambda invocation, or through an Amazon SQS queue, and keeps job state in PostgreSQL. (TRAF-007)
- Clients poll the status URL or register an optional webhook callback, which BeFive signs with HMAC. (TRAF-007)
- **Results and generated files** go to object storage: Amazon S3 with SSE-KMS, an S3-compatible store such as MinIO, or a local filesystem for single-node installs. BeFive envelope encryption can be applied on top. (TRAF-006)
- **Authorized retrieval only.** Results and files can be fetched only by the job owner or a caller with a permitted group or scope, either proxied through the gateway or by a short-lived presigned URL issued after authorization. Retention and expiry are configurable per endpoint, and every retrieval is logged. (TRAF-006, GOV-3)
- Backends that implement their own asynchronous pattern (for example `202` plus `Location`) pass through unchanged, with BeFive's policies applied to both the submit and the status calls. (TRAF-007)

### 2.11 Developer portal, catalog, lifecycle, sandbox, OpenAPI, and data dictionary
- **Developer portal.** A separate ClojureScript single-page application, served by the control plane on its own hostname (for example `developer.example.com`) and sharing the malli schemas and component library with the console. It offers API discovery, documentation, interactive testing, subscriptions and access requests, and self-service key management. (DEV-001)
- **One portal for all environments.** There is one portal, hosted by the production cluster. It lists APIs from the linked environments (2.12) and shows each version's availability per environment; try-it traffic goes to the Sandbox cluster and never defaults to production (a production target is a per-API setting, off by default). (DEV-001, DEV-002, API-007)
- **0.x preview.** The 0.x build ships a thin, read-only subset of the portal: Okta login, group and user visibility, catalog search and facets, and OpenAPI-rendered documentation. It has no subscriptions, keys, try-it, or access requests; those arrive with 1.0. Partners may reach the preview from outside, so it is designed for internet exposure from 0.x: a WAF and rate limits in front of it, a strict Content Security Policy, sanitized spec content, and a dedicated security review before design partners expose it (R4, milestone 10). (DEV-001, DEV-002, DEV-003)
- **Okta login and visibility.** Developers sign in through Okta (OIDC). Each API, version, and operation can be made visible to Okta groups and to individual users, and entitlements decide who can request or use access. Internal and partner audiences are separated this way. (DEV-003, GOV-3)
- **Searchable catalog.** Full-text search (PostgreSQL full-text search, no separate search engine) with facets for domain, owner, tags, lifecycle state, classification, version, and environment availability. (DEV-002)
- **Documentation** rendered from each version's OpenAPI document, with deprecation badges, a changelog per API, and links to guides. (DEV-001, API-002)
- **Try-it console.** Interactive requests from the portal, sent to the **sandbox** environment by default, using the developer's own sandbox application credentials. Production is never the default target, and whether a production target is offered at all is a per-API setting. (DEV-001, DEV-005)
- **Self-service keys.** Developers create applications, request access, and issue, rotate, and revoke their own keys within what they are entitled to. Keys are shown once. (DEV-001, SEC-001)
- **Lifecycle tooling.** APIs and versions move through Design, Published, Deprecated, and Retired, with version management, promotion hooks into the environment pipeline (2.12), and audit of each transition. Retired versions stop routing and return `410 Gone` with a documentation link. (DEV-004, API-002)
- **Sandbox.** A dedicated Sandbox environment (its own BeFive cluster, usually pointing at test backends) and, per route, mock responses generated from OpenAPI examples, so an API can be tried before a backend exists. Sandbox access requires portal login and sandbox entitlements, has its own rate limits, and never shares keys with production. (DEV-005)
- **OpenAPI import and export.** Import OpenAPI 3.0 and 3.1 in YAML or JSON (upload, URL, or `b5ctl`) to create or update APIs, versions, and operations, with a diff preview before applying. Export or generate OpenAPI for any API version, including the security schemes, rate-limit headers, and deprecation information that BeFive adds. Optional request validation against the operation's schemas can be enabled per operation. (DEV-007, DEV-004)
- **Data dictionary.** A field and schema index built from all ingested OpenAPI schemas, searchable across APIs, showing definitions, owners, and where each field is used. Fields can carry classification tags such as PII, which feed log redaction and caching rules (2.13, 2.7). (DEV-008, OBS-008, GOV-5)
- **After 1.0 (Should):** LLM-assisted specification linting, documentation-gap detection, and draft generation (2.20). (DEV-006)

### 2.12 Environments, promotion, and configuration as code
- **One cluster per environment.** Dev, Test, Prod, and Sandbox each run their own BeFive control plane and PostgreSQL, so a mistake in one cannot affect another. (API-007)
- **Linked environments.** A registry of linked environments lets the console and `b5ctl` promote a versioned, signed configuration bundle (APIs, versions, operations, policies, and specifications) from one environment to the next, with a diff, an approval step, and audit on both sides. (API-007, POL-004)
- **Per-environment overlays** hold environment-specific values such as upstream URLs, discovery settings, and secret references, so the promoted bundle stays identical across environments. (API-007, POL-004)
- **Git and CI path.** Existing: the whole configuration is EDN validated against published schemas; a small Clojure DSL produces that data; `b5ctl validate`, `diff`, `apply`, and `export` work like Terraform against a running control plane. Draft 4 adds bundle signing and verification, OpenAPI import, and promotion commands to `b5ctl`, so pipelines can promote Dev to Test to Prod without the console. (POL-003, POL-004, API-007)
- **Everything is manageable by API.** Existing: the Admin REST API covers everything the console does, including group access, classification, traffic rules, lifecycle state, and policy bindings, documented with a generated OpenAPI specification. (POL-003)
- The single production-hosted portal shows in which linked environments each API version is available. (API-007, DEV-002)
- Existing: **custom plugins as Clojure interceptors**, loaded from a mounted plugins folder. (API-004)

### 2.13 Logging, metrics, tracing, and telemetry sinks
- Existing: one structured JSON access line per request on stdout by default with a versioned schema (`befive.access/1`), optional access-log sampling with errors, denials, rate-limit rejections, and slow requests always kept, automatic redaction of tokens, keys, cookies, and configured headers, a separate audit stream, request ID propagation, and health endpoints. Draft 4 adds the API, version, operation, organization, application, environment, and cache and policy decision fields to the schema. (OBS-001, OBS-002)
- Existing: metrics in **CloudWatch Embedded Metric Format** in per-node summary lines, exact regardless of sampling. (OBS-001, OBS-002)
- **Pluggable telemetry sinks**, all fed by the same per-node aggregators so the numbers agree across tools: CloudWatch EMF (existing); **Datadog** (DogStatsD metrics with tags, JSON logs using Datadog's reserved attributes with trace correlation through the Datadog Agent, and traces through OTLP to the Agent); generic **OTLP** for metrics and traces; and a **Prometheus** scrape endpoint. Several sinks can be active at once. (OBS-001, OBS-002, OBS-003)
- **OpenTelemetry tracing.** Existing W3C `traceparent` pass-through, plus gateway spans for authentication, policy, cache, composite steps, and upstream calls, exported through OTLP with configurable sampling. (OBS-001, OBS-003)
- **Error and latency metrics.** Separate 4xx and 5xx rates, and latency percentiles p50, p90, p95, and p99, by API, operation, consumer, and environment, over time. (OBS-005, OBS-006)
- **Redaction driven by the data dictionary.** Fields tagged as sensitive are masked in any logged body sample and in LLM context, in addition to the existing header and token redaction. (OBS-008, GOV-5)

### 2.14 Dashboard and alerting recipes (CloudWatch and Datadog)
- Existing: the CloudWatch recipe as a Terraform module and CloudFormation template: operations, security, and usage dashboards, Logs Insights saved queries, and alarms to SNS, generated from the same code as the log schema. Draft 4 adds p90, IdP health, cache, and deprecated-usage panels. (OBS-002, OBS-004, OBS-005, OBS-006)
- **Datadog recipe:** a Terraform module using the `datadog` provider with dashboards and monitors that mirror the CloudWatch recipe (latency percentiles, 4xx and 5xx rates, usage, rate-limit and authentication events, IdP health), plus a log pipeline and facets for the BeFive log schema. It is generated from the same schema code, so metric names cannot drift. (OBS-003)
- **Near-real-time console monitoring.** Existing live overview from the gateways' own counters, extended with per-API and per-operation views, policy events (denials, rate limits, IP blocks), and IdP health. (OBS-004)

### 2.15 Reporting
- **Report builder in the console** with datasets for usage, consumers, performance, errors, policy and security events, and audit events; dimensions (API, version, operation, organization, consumer, application, environment, status class, time bucket), filters, saved reports, and scheduled reports. (OBS-007)
- **Export** as CSV and JSON, and XLSX if the proposed small writer library is approved, delivered as a download, by email, or to S3. The Draft 3 monthly usage report becomes one of the shipped saved reports. (OBS-007)
- **Storage.** PostgreSQL rollups: 1-minute rollups kept briefly (default 8 days) and 1-hour rollups kept 13 months, produced by the same pipeline that already ships per-minute summaries for anomaly detection. Access to datasets follows RBAC (2.17). (OBS-007, OBS-008)

### 2.16 InfoSec alignment and audit
- **Audit everything.** Every administrative, key, secret, policy, access-request, and approval action is audited (who, what, when, before and after). The audit log is append-only and **hash-chained** (existing design, `02-architecture.md` 12.5), with chain verification in the console, the Admin API, and `b5ctl`. (OBS-008, GOV-1)
- **Retention settings** for access logs shipped by BeFive's own jobs, rollups, audit events, job results, LLM prompts, and source-IP data. (OBS-008)
- **Access control** on logs, reports, and audit data through RBAC; body samples and sensitive datasets require explicit permissions. (OBS-008, GOV-3)
- **Redaction and masking** driven by the data dictionary and by classification (2.13). (OBS-008, GOV-5)
- **Alerting** on security events (authentication-failure bursts, denials, IP blocks, key misuse) through the anomaly engine and the CloudWatch and Datadog recipes. (OBS-008, GOV-6)
- **Evidence export:** a signed archive of configuration, effective policies, audit log extracts with chain verification, key inventories (metadata only), and access-request outcomes for a chosen period, for auditors. (OBS-008, GOV-6)

### 2.17 Web console and RBAC
- Existing: manage upstreams, services, routes, policies, consumers and credentials, plans, identity providers (Okta wizard with a test button), certificates; route tester; live overview; audit log viewer; incidents and anomaly response screens; configuration as code (EDN view, export, import, diff). Draft 4 adds APIs, versions, and operations with OpenAPI import; the effective policy view; organizations and applications; classification settings; access requests and approvals; composite builder; asynchronous job monitor; cache purge; telemetry sink settings; report builder; linked environments and promotion; evidence export. (POL-002, POL-003, OBS-004, OBS-007)
- Admin sign-in through Okta (OIDC) or local accounts, including a break-glass local administrator for Okta outages. (IAM-001, IAM-008)
- **Roles.** Existing fixed roles: Administrator, Operator (routes, upstreams, policies), Consumer Manager (organizations, consumers, applications, plans), and Auditor (read-only, audit and evidence). New: **API Owner**, scoped to the APIs they own, who edits those APIs' metadata, specs, composites, caching, and lower-level policies within locks, publishes and deprecates their versions, and approves access requests for them, but cannot set locks or retire versions; **Automation Manager**, who edits anomaly response rules and scripts, enables and configures traffic actions, and manages detectors and silences and acknowledges and resolves incidents (Administrators keep all of these rights; Operators and Administrators, not Automation Managers, approve and revert traffic actions); Consumer Managers can approve access-request steps assigned to them; and a portal-side **Developer** role, which is not a console role. Roles can be mapped from Okta groups. (GOV-3, OBS-008)

### 2.18 Administration, security, and packaging
- Existing: Admin REST API with generated OpenAPI; envelope-encrypted secrets with environment, file, or AWS KMS key providers; a minimal non-root Java runtime image with SBOM and vulnerability scan; reference `docker-compose` and an Amazon ECS task definition; offline Ed25519 license activation. (SEC-002, POL-003)
- **No SOAP.** Nothing in BeFive requires SOAP services or SOAP infrastructure, and no SOAP library is in the dependency tree. (API-009)
- The gateways' only required dependency remains PostgreSQL. Redis (clustered rate limits, shared cache tier) and object storage (asynchronous results) are optional and only needed when those features are used.

### 2.19 Anomaly detection and scripted response (ServiceNow)
Draft 4.1 narrows the 1.0 scope: detection, incidents and grouping, silences, scripted response rules, ServiceNow incident create, update, and resolve, Slack and signed-webhook notifications, and two traffic actions (IP block and rate-limit tightening) are in 1.0. ServiceNow Event Management, Microsoft Teams and email anomaly notifications, and the LLM root-cause analysis follow in 1.1 (2.20); consumer block and route disable follow in 1.1 or later. The designs for the later items stay in `02-architecture.md` 14, labeled by release. BeFive watches its own traffic for anomalous patterns and responds automatically, without CloudWatch and in air-gapped installs. It also serves the operational side of OBS-004 and OBS-008 and the IdP health alarm (IAM-008).

- **Detection inside the product.** Gateways deliver compact per-minute summaries to the control plane through PostgreSQL. These are the same exact counts behind the CloudWatch metrics, so access-log sampling does not affect detection, and gateways keep serving traffic independently of the control plane. The control plane looks for anomalies per route, service, upstream, consumer, identity provider, and source IP: 5xx and 4xx rates, spikes of `401`, `403`, and `429`, latency p95 and p99, traffic drops and surges, per-consumer surges, authentication-failure bursts from one source IP, upstream health flapping, and gateway nodes that stop reporting.
- **Detectors:** fixed thresholds, rate of change, and seasonal baselines that learn what is normal for each time of the week (robust statistics over the previous four weeks, with minimum-volume guards and a warm-up period for new routes). A conservative default set is enabled on install and only notifies the console.
- **Incidents, not alert storms.** Related anomalies are grouped into one incident with an open, ongoing, and resolved lifecycle, hysteresis, deduplication, cooldowns, and silences and maintenance windows. Anomalies that follow a configuration change are linked to that change.
- **Scriptable responses.** Response rules map incident events to actions, written as EDN data or as Clojure scripts. Scripts run in a sandbox (the SCI interpreter in a separate process, with time and memory limits and no file or network access) and can only request actions, never perform them directly. Rules and scripts can be tested and replayed against recorded traffic before they are enabled. Trusted JVM code can add custom actions through the plugin SPI.
- **Built-in actions in 1.0:** create, update, and resolve ServiceNow incidents; send Slack and signed webhook notifications; and take two temporary traffic actions: block an IP (or small CIDR) and tighten a rate limit. Traffic actions are off by default. When enabled, they are time-limited with automatic revert, limited in blast radius, available in dry-run and approval-required modes, and fully audited.
- **ServiceNow integration.** Incidents through the Table API with OAuth 2.0 client credentials (basic authentication as a fallback), one BeFive incident per ServiceNow incident (deduplicated by `correlation_id`), work notes, automatic resolution, a durable outbox, and a step-by-step connection test. The same connector now also serves access requests (2.6). The Event Management target (`em_event`) follows in 1.1.
- **In 1.1:** the LLM best-guess root cause (optional and off by default; OpenAI, Azure OpenAI, Amazon Bedrock, and OpenAI-compatible endpoints including self-hosted models; redacted, minimized context; budgets; structured output; labeled as an AI-generated best guess; advisory only), ServiceNow Event Management, and Microsoft Teams and email anomaly notifications. The LLM provider integration is built once in 1.1 and serves both this analysis and DEV-006.
- **In 1.1 or later:** the consumer-block and route-disable traffic actions, which reuse the same runtime-override mechanism and safety rails.
- **Security.** Integration credentials live in the encrypted secrets store, outbound calls pass the SSRF guard, and only Administrators and Automation Managers can edit scripts or enable traffic actions; Operators can approve and revert them.

### 2.20 Planned right after 1.0 (Should items and close follow-ups)
- **AI-assisted API generation and documentation validation (DEV-006, Should).** LLM-assisted linting of OpenAPI documents, detection of documentation gaps and inconsistencies (missing descriptions, examples, error responses, inconsistent naming against the data dictionary), and draft generation of descriptions and examples. It uses the LLM provider integration that lands in the same release, with its redaction, budgets, and audit, and is advisory only: suggestions are proposals that a person accepts in the editor. Deterministic lint rules (without an LLM) ship in 1.0 as part of OpenAPI import.
- **LLM provider integration and anomaly root-cause analysis** (moved from 1.0 by Draft 4.1): the providers, redaction, budgets, and audit designed in `02-architecture.md` 14.14, shared by the root-cause analysis and DEV-006.
- **ServiceNow Event Management target** (`em_event`) and **Microsoft Teams and email anomaly notifications** (moved from 1.0 by Draft 4.1). Email as such is already in 1.0 for approvals and scheduled reports; 1.1 adds the anomaly `:email/send` action.
- **Consumer-block and route-disable traffic actions** (1.1 or a later update), on the existing runtime-override mechanism and rails.
- **OAuth 2.0 token exchange (RFC 8693)** with Okta for upstream-specific tokens, as an alternative to the gateway-signed internal JWT.
- **Okta group membership as an entitlement** through the Okta Management API, so an approved access request can add the user or service to an Okta group.

### 2.21 Deferred beyond 1.0
These are not required by `00-customer-requirements.md` and each would delay 1.0:
- Helm chart for Kubernetes (the image already runs on Kubernetes, and service discovery reads EndpointSlices).
- SAML single sign-on for the console and portal, and SCIM user provisioning.
- gRPC and HTTP/2 to upstreams.
- Safe inline policy expressions in Clojure on the ordinary request path. (The claims expression language in 2.3 is a restricted data language, and composite endpoints call the out-of-process script runner only for their optional expression steps.)
- General request and response body transformation beyond composite mapping.
- Machine-learning anomaly models, forecasting, a conversational LLM assistant, and automatic remediation beyond the four designed temporary traffic actions (two in 1.0, two after 1.0).
- Monetization and billing, multi-region configuration sync, custom RBAC roles.

### 2.22 Explicit non-requirements and the GraphQL roadmap
- **SOAP** is not supported and is not a dependency (API-009). BeFive can proxy any HTTP traffic, including SOAP envelopes, as opaque bodies, but provides no SOAP-specific features, and none are planned.
- **GraphQL roadmap (API-008, Future).** In 1.0, GraphQL endpoints can be proxied as ordinary HTTP routes with all authentication, IP, rate-limit, logging, and caching policies applied at the endpoint level. The roadmap, after 1.0 and in this order: (1) GraphQL-aware routing and limits: query depth and complexity limits, operation-name extraction for logs and metrics, and persisted queries; (2) schema registration in the catalog and data dictionary from GraphQL SDL, with portal documentation and an explorer against the sandbox; (3) field-level authorization using the same scope and claims expression language; (4) composition of REST operations into a GraphQL facade. This is a direction, not a committed date.

---

## 3. Technology stack

### 3.1 Approved stack (unchanged from Draft 3 except where noted)

| Area | Choice | Why |
|---|---|---|
| Runtime | Java 21 LTS, Clojure 1.12 | Long-term support fits a buy-once product; virtual threads simplify blocking work off the hot path. |
| Gateway HTTP server and proxy client | **Aleph** (on Netty) | Non-blocking server and client in one library, streaming bodies with backpressure, HTTP/2, WebSockets, and fine TLS control for SNI and mTLS. |
| Request pipeline | Interceptor chain (Sieppari-style) | Each feature is an ordered interceptor; customer plugins use the same interface. Draft 4 adds cache, composite, and async interceptors. |
| Routing | **reitit** | Fast compiled routers rebuilt and swapped atomically on each configuration change; also used for the Admin API. |
| Schemas and validation | **malli** | One schema set in `.cljc` files, shared by the gateway, Admin API, CLI, console, and now the portal. |
| Admin API | reitit + Ring + Muuntaja + malli coercion, served by Aleph | Standard Clojure web stack with automatic OpenAPI output. |
| System lifecycle and config | **Integrant** + **Aero** | Explicit component wiring; Aero reads EDN settings with environment-variable and secret-file references. |
| Configuration store | **PostgreSQL** via next.jdbc, HoneySQL, HikariCP, Migratus | Most enterprises already run Postgres. Changes reach gateways through `LISTEN/NOTIFY` with polling fallback. Draft 4 also uses it for the catalog search, job state, and report rollups. |
| JWT, JWKS, OIDC | **Nimbus JOSE + JWT** | Widely used and audited JOSE library. Draft 4 also uses it to sign the internal JWT and publish BeFive's JWKS. |
| Password and key hashing | buddy-hashers for admin passwords, HMAC-SHA256 for API keys | Slow hashing where needed; fast lookup for high-entropy keys. |
| Rate limiting | **Bucket4j**, in-memory plus Redis (Lettuce) for clusters | Proven token-bucket library with a distributed backend. |
| Caches | Caffeine | JWKS, introspection results, consumer lookups, and now the encrypted response cache (with Redis via Lettuce as the optional shared tier). |
| JSON | jsonista | Fast JSON for one log line per request. |
| Logging | SLF4J + Logback with a JSON encoder; a dedicated async writer for access and EMF lines | Everything lands as JSON on stdout. |
| Web console and developer portal | **ClojureScript** with shadow-cljs, **re-frame** and Reagent, Ant Design, Apache ECharts, CodeMirror 6 | Same language and schemas as the backend. The portal is a second shadow-cljs build sharing modules with the console. |
| CLI | Clojure compiled with GraalVM `native-image` | Fast startup for CI pipelines. |
| Anomaly detection | In-house statistics in a shared `.cljc` module | No statistics library needed. |
| Response scripts and composite expressions | **SCI** inside a separate GraalVM native-image runner process | Hard time and memory limits need a separate process. Composite endpoints reuse the runner for optional expression steps. |
| Integrations (ServiceNow, Okta Management API, Slack, webhooks; LLM APIs and Teams from 1.1) | Aleph's HTTP client behind the SSRF-guarded wrapper, with jsonista | Plain REST, no vendor SDKs. ServiceNow now also carries access requests; the optional Okta app creation uses the Okta Management API over plain REST. |
| AWS services | AWS SDK for Java v2 (already approved: CloudWatch Logs, S3, KMS, `bedrockruntime`, `cloudwatch`) | Draft 4 adds modules, listed in 3.2. `bedrockruntime` is first used in 1.1 (LLM analysis). |
| Email | Eclipse Angus Mail | Standard JVM SMTP library; in 1.0 used for access-request approvals and scheduled reports; anomaly email notifications from 1.1. |
| Licensing, signing | Ed25519 from the JDK | Also signs promotion bundles and evidence exports. |
| Build | deps.edn + tools.build | One uberjar. |
| Container | Multi-stage Dockerfile; `jlink`-trimmed Temurin 21 on a minimal base | Small, non-root image. |
| Testing | Kaocha, test.check, Testcontainers (Postgres, Redis, mock OIDC), an Okta developer tenant, k6 | Draft 4 adds test-only containers listed in 3.2. |
| AWS recipe | Terraform module and CloudFormation template, generated from Clojure | Now joined by a generated Datadog Terraform recipe. |
| Documentation | MkDocs Material for guides; Redoc for the Admin API reference | Shippable offline. |

### 3.2 Additions in Draft 4 (proposed, pending approval)

Only what the new requirements need. Each item is marked **proposed, pending approval**.

| Need | Proposed addition | Rationale | Where it runs |
|---|---|---|---|
| OpenTelemetry tracing and OTLP export (OBS-001, OBS-003) | OpenTelemetry Java SDK with the OTLP exporter (`io.opentelemetry:opentelemetry-sdk`, `opentelemetry-exporter-otlp`) | The standard way to emit traces to Datadog Agents and OpenTelemetry collectors. Spans are created explicitly by BeFive's interceptors; no bytecode agent. | Gateway, control plane |
| Datadog metrics (OBS-003) | `java-dogstatsd-client` (`com.datadoghq`) | Datadog's own client: tags, UDP or Unix socket, non-blocking, aggregation. Datadog logs need no library (field mapping in the JSON encoder). | Gateway, control plane |
| Prometheus scrape endpoint (OBS-001) | None; the text exposition format is rendered in-house from the existing aggregators | A few dozen lines; avoids a second metrics registry. | Gateway |
| OpenAPI parsing (DEV-007) | `io.swagger.parser.v3:swagger-parser` | Parses OpenAPI 3.0 and 3.1 in YAML and JSON and resolves `$ref`s; the de facto JVM parser. | Control plane, CLI only |
| Request validation against schemas (DEV-007, optional per operation) | networknt `json-schema-validator` | Fast, supports JSON Schema 2020-12 (OpenAPI 3.1) and the 3.0 dialect; schemas compiled at configuration time. | Gateway, control plane |
| Lambda, SQS, Cloud Map (API-005, API-006, TRAF-007) | AWS SDK v2 modules `lambda`, `sqs`, `servicediscovery` (new); `s3` with its presigner and `kms` (already approved) | Asynchronous clients with IAM-role credentials. The async HTTP client must use a Netty version compatible with Aleph's, or the AWS CRT HTTP client (risk R3). | Gateway, control plane |
| Kubernetes EndpointSlices (API-006) | No library: plain HTTP watch against the Kubernetes API with Aleph and the service-account token; fabric8 only if watch handling proves too costly | Read-only use of one resource type does not justify fabric8's dependency tree. | Gateway or control plane |
| Catalog and dictionary search (DEV-002, DEV-008) | PostgreSQL full-text search (`tsvector` with GIN indexes), optional `pg_trgm` extension for fuzzy matching | No Elasticsearch or OpenSearch, keeping PostgreSQL the only required dependency. | Control plane |
| Portal documentation rendering (DEV-001) | A custom re-frame renderer over the parsed OpenAPI document using the approved Ant Design components, with `markdown-it` and `DOMPurify` for descriptions; Redoc or Scalar embedding as the fallback option | The try-it console must use portal login, sandbox credentials, and entitlements, which is simpler in a renderer we control. Descriptions are sanitized because specs are user-supplied. | Portal |
| Composite graph builder (API-003) | React Flow (`@xyflow/react`, MIT) | A mature node-and-edge editor that works with Reagent; building one from scratch is weeks of work. | Console |
| XLSX report export (OBS-007) | `org.dhatim:fastexcel` (Apache-2.0) | Small, write-only XLSX writer; avoids Apache POI's size. Dropped if not approved, leaving CSV and JSON. | Control plane |
| Test-only | Testcontainers modules for LocalStack (Lambda, SQS, S3), MinIO, k3s, and a Datadog Agent container | Integration tests for the new backends and sinks. | CI only |

No new dependency is needed for policy compilation, the claims expression language, mock responses, the hash-chained audit log, HMAC-signed callbacks, AES-GCM cache encryption, the internal JWT, or the optional Okta app creation (plain REST calls to the Okta Management API; no Okta SDK); these use the JDK, Nimbus, and in-house code.

### 3.3 Performance goals (targets to test against, not measurements)
- Added latency from the gateway at p99 under 5 ms for a JWT-authenticated route with a warm cache, including effective-policy evaluation. (Unchanged; the policy hierarchy is resolved at compile time.)
- At least 10,000 requests per second per gateway node on 4 vCPUs for small payloads.
- Configuration changes visible on all nodes within 5 seconds.
- New in Draft 4: a cache hit served in under 1 ms of added latency at p99 for responses under 64 KB; composite endpoints add under 2 ms of gateway overhead per step, excluding the called services.

---

## 4. Releases, milestones, and effort

### 4.1 Release split
Every Must requirement is in **1.0 GA**. To put working software in front of design partners earlier, a **0.x design-partner build** comes first. It is not a general-availability release and is licensed for evaluation and pilots.

- **0.x design-partner build:** core gateway (routing, pooling, streaming, Lambda upstreams); the API, version, and operation model with basic OpenAPI import; Okta and OIDC with scopes, claims, client credentials, user context, anti-spoofing, the internal JWT, and IdP-outage resilience; API keys and secrets; the policy hierarchy with locks and the effective policy view; IP rules; rate limits and quotas with all dimensions; version routing and deprecation headers; CloudWatch and Datadog telemetry with OTLP traces and both recipes; configuration as code through the Admin API and `b5ctl`, including export, diff, and apply between environments; console basics; and a **read-only portal and catalog preview** (Okta login, group and user visibility, catalog search and facets, OpenAPI-rendered docs; no subscriptions, keys, try-it, or access requests). This covers evaluation prompts 1, 2, 3, 5, 7, 8, 9, 10, and 12, the streaming half of 11, the visibility part of 4, and the import, catalog, and documentation part of 6.
- **1.0 GA:** everything in 0.x plus the full developer portal (subscriptions, self-service keys, try-it, access requests, cross-environment catalog), lifecycle tooling, sandbox and mocks, OpenAPI export and diff preview, the data dictionary, classification and the access-request workflow (built-in, ServiceNow, and generic webhook, with optional Okta app creation), composite endpoints, service discovery, encrypted caching, asynchronous endpoints and file retrieval, linked environments with signed promotion bundles, the report builder, the InfoSec additions, and anomaly detection and scripted response in the Draft 4.1 scope (2.19). This completes all 12 evaluation prompts.
- **1.1 (planned right after 1.0):** DEV-006 with the LLM provider integration, the LLM root-cause analysis, ServiceNow Event Management, Teams and email anomaly notifications, and the other items in 2.20; consumer block and route disable in 1.1 or a later update.

### 4.2 Milestones

**0.x design-partner build**
1. **Skeleton.** Repository, build, CI, Docker image, Integrant system, migrations, health endpoints.
2. **Proxy core.** Routes, upstreams, load balancing, health checks, pooling, hot reload, structured access logs, size limits, SSE and WebSocket pass-through, Lambda upstreams.
3. **Domain model.** APIs, versions, operations, organizations, consumers, applications; basic OpenAPI import onto routes; version routing strategies and deprecation headers.
4. **Identity and policy.** API keys, JWT with JWKS, introspection, mTLS, the Okta preset, claims expressions, anti-spoofing, internal JWT, IdP-outage resilience, the policy hierarchy with locks and compile-time resolution, IP rules.
5. **Rate limiting and quotas** with all dimensions and standard headers.
6. **Admin API and CLI.** Full REST coverage, OpenAPI output, `validate`, `diff`, `apply`, `export`, effective policy queries.
7. **Telemetry.** EMF, Datadog (DogStatsD, logs, OTLP traces), OTLP, Prometheus; the CloudWatch and Datadog recipes.
8. **Console basics.** Existing Draft 3 screens plus APIs, organizations and applications, and the effective policy view.
9. **Portal and catalog preview (read-only).** The portal application shell on its own hostname, Okta login, group and user visibility, catalog search with PostgreSQL full-text search and facets, and OpenAPI-rendered documentation. Built as the first slice of milestone 11, not as throwaway work.
10. **0.x hardening.** Secrets encryption, evaluation licensing, a focused security review of identity and anti-spoofing, a **portal-preview security review for internet exposure** (external penetration test of the preview hostname, CSP and sanitizer review, WAF and rate-limit configuration, session and OIDC checks, visibility and catalog-enumeration tests), load tests of rate limiting, the Okta outage runbook.

**1.0 GA**

11. **Developer portal and developer tools.** Completes the portal on top of the preview: subscriptions, self-service keys, try-it against the Sandbox cluster, cross-environment catalog, lifecycle states, sandbox and mocks, OpenAPI diff preview and export, data dictionary.
12. **Governance workflow.** Classification and default policies, access requests, built-in approvals with email notifications, deprecation notice emails, ServiceNow request integration, generic webhook adapter, optional Okta app creation, API Owner role.
13. **Backends and traffic.** Composite endpoints with the builder; service discovery; encrypted caching; asynchronous endpoints, object storage, and authorized retrieval.
14. **Environments.** Linked environments, signed bundles, overlays, promotion with approval in console and CLI.
15. **Reporting and InfoSec.** Rollups, report builder, schedules and exports, audit coverage of all new actions, retention settings, evidence export.
16. **Anomaly detection and scripted response** (Draft 3 milestone 8, in the Draft 4.1 scope): detection, incidents, silences, rules and scripts, ServiceNow incidents, Slack and webhook notifications, IP block and rate-limit tightening, and the Automation Manager role. It depends only on the per-minute summaries from milestone 7, so it can run in parallel with milestones 11 to 15 if staffing allows.
17. **Release hardening.** Full security review including the portal, load and soak tests, documentation, packaging, and production licensing.

**1.1 (first update)**

18. **LLM and notifications.** LLM provider integration with redaction and budgets, anomaly root-cause analysis, DEV-006 spec checking and draft generation, ServiceNow Event Management, Teams and email anomaly notifications; consumer block and route disable if they are ready, otherwise in a later update.

### 4.3 Effort estimate (estimate, not a plan)
The baseline is Draft 3's MVP, anomaly detection included, counted as 100%. No code exists and there is no measured velocity, so these are relative estimates from the scope of each area, meant to be firmed up in planning.

| Area | Added effort (% of Draft 3 MVP, estimate) | Reasoning |
|---|---|---|
| Domain model (APIs, versions, operations, organizations, applications) across schema, compiler, Admin API, CLI, and console | 8–12% | New entities touch every layer, and routes must now be generated from operations. |
| Policy hierarchy, locks, compile-time resolution, effective policy view | 6–9% | A core change to the compiler that must be provably correct; property tests add effort. |
| Identity additions (claims language, internal JWT and JWKS, anti-spoofing, outage resilience, runbook) | 4–6% | Builds on Nimbus and the existing JWKS cache. |
| Key and rate-limit additions (application association, new dimensions, standard headers) | 3–5% | Mostly extensions of existing code. |
| Versioning strategies, deprecation headers, deprecated-usage report | 2–3% | Small and well specified by RFCs. |
| Classification and access-request workflow (built-in approvals, ServiceNow requests with webhook and polling, generic adapter) | 7–10% | Reuses the ServiceNow connector and outbox; the new state machine and screens appear in both console and portal. |
| Developer portal (second SPA: login, visibility, catalog, docs rendering, try-it, keys, subscriptions) | 18–25% | Comparable to Draft 3's whole console milestone. |
| OpenAPI import, diff, and export; lifecycle; sandbox and mocks; data dictionary | 10–14% | The parser is a library; mapping onto routes, diffing, generation, and the dictionary are custom. |
| Composite endpoints (engine, limits, graph builder, tester trace) | 7–10% | The builder UI is the expensive part. |
| Lambda upstream and service discovery (DNS, Cloud Map, Kubernetes) | 5–7% | Three discovery sources plus Lambda mapping, each with integration tests. |
| Encrypted caching with partitioning and purge | 3–5% | Caffeine and Redis are in place; correctness of partitioning is the work. |
| Asynchronous endpoints and files (jobs, dispatch to HTTP, Lambda, and SQS, callbacks, storage, presigned URLs, retention) | 7–10% | A small subsystem with its own state machine and storage backends. |
| Streaming additions (SSE, size limits at every level, metrics) | 1–2% | Streaming already exists. |
| Telemetry sinks (Datadog, OTLP, Prometheus) and the Datadog recipe | 6–9% | The aggregators exist; each sink and the recipe generator are new. |
| Report builder, rollups, schedules, exports | 7–10% | Reuses the summary pipeline; the builder UI and scheduling are new. |
| Linked environments, signed bundles, overlays, promotion | 6–8% | Extends the existing export, diff, and apply. |
| InfoSec (audit coverage of new actions, retention settings, evidence export, RBAC additions) | 3–5% | Mostly control-plane work on existing data; the hash-chained audit log already exists. |
| Cross-cutting: integration tests against Okta, AWS, Datadog, and ServiceNow; load and soak tests; security review; documentation for the new surface | 15–20% | The product surface roughly doubles, and so does verification and documentation. |
| **Total added (Draft 4)** | **about 118–170%** | |

Draft 4.1 adjustments (estimates, same baseline):

| Change | Effect (% of Draft 3 MVP, estimate) | Reasoning |
|---|---|---|
| Read-only portal and catalog preview in 0.x | +1–2% | Moves about 5–8% of the portal work into 0.x; the extra cost is shipping, securing, and documenting a separate subset early. |
| Optional Okta OAuth app creation through the Okta Management API | +1–2% | A small REST client, credential handling, the connection test, and integration tests against an Okta tenant. |
| Automation Manager role | +0.5–1% | One more fixed role in the RBAC matrix, its tests, and documentation. |
| LLM analysis, Event Management, Teams and email anomaly notifications moved to 1.1 | −5–7% | Part of Draft 3's anomaly scope, which the baseline includes; about three quarters of it is the LLM integration. |
| Consumer-block and route-disable actions moved after 1.0 | −1–2% | Two enforcement points, their rails, and their tests. |
| **Net Draft 4.1 change** | **about −3.5% (range −1 to −6%)** | |
| **Total added (Draft 4.1)** | **about 115–166%** | Low and high ends pair the low additions with the low deductions, and the high with the high. |

October 1, 2026 adjustments ( estimates, same baseline):

| Change | Effect (% of Draft 3 MVP, estimate) | Reasoning |
|---|---|---|
| Deprecation notice emails (1.0) | +0.5–1% | A scheduled job over existing deprecated-usage data, templates, opt-outs, and tests; email and the outbox already exist. |
| `private_key_jwt` only: public-key upload by default, opt-in BeFive-generated keys | +0.5–1% | Public-key validation and upload screens, Okta JWK registration, overlap rotation and compromise deactivation, the opt-in generated-key path with one-time download, and the optional in-browser generator; partly offset by dropping the client-secret path. Update 2 shifts work within this item rather than adding to it (estimate). |
| Internet-exposed 0.x preview: hardening and security review (0.x) | +0.5–1% | WAF and rate-limit guidance, an external penetration test, and fixes moved earlier from milestone 17. |
| IETF rate-limit headers kept as default | 0% | Already the Draft 4 design. |
| **Total added (October 1 update)** | **about 116.5–169%** | |

**Result (estimate):** 1.0 is roughly **2.2 to 2.7 times** Draft 3's MVP, with a central estimate of about **2.4 times** (Draft 4.1 before the October 1 update: 2.1 to 2.7, central 2.35; Draft 4: 2.2 to 2.7, central 2.4). If Draft 3's MVP was planned at N engineer-months, 1.0 is about 2.2N to 2.7N. The 0.x build alone is roughly **1.3 to 1.5 times** Draft 3's MVP, central about 1.4 (Draft 4: 1.2 to 1.4; the October 1 preview hardening adds about 0.01): Draft 3's core without anomaly detection (about 75–80%), plus the 0.x share of the new work, plus the portal preview (about 7–11% including its overhead and the internet-exposure review). 0.x is itself larger than the MVP Draft 3 described. The first update (1.1) grows to roughly 0.1 to 0.15 times Draft 3's MVP (LLM integration, DEV-006, Event Management, two notification channels, and possibly the two deferred traffic actions). Calendar time does not have to grow by the same factor: the portal, reporting, composite builder, and anomaly work are fairly independent and parallelize across engineers, while the domain model and the policy compiler are on the critical path and should be staffed first.

---

## 5. Decisions (flag any you disagree with)

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

---

## 6. Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | **Scope and schedule.** 1.0 is estimated at 2.2 to 2.7 times Draft 3's MVP; slips are likely if staffing stays as planned for Draft 3. | The 0.x build, parallel tracks after milestone 10, the anomaly cut points already applied in Draft 4.1, and the remaining reserve cut point (decision 11). Musts are not cut. |
| R2 | **Policy hierarchy correctness.** Inheritance with locks is easy to get subtly wrong, and a mistake could weaken security. | Compile-time resolution with property tests (test.check) over random hierarchies, a lock-violation validator, and the effective policy view for review. |
| R3 | **Netty version conflicts.** The AWS SDK's asynchronous HTTP client uses Netty, as does Aleph. | Pin compatible versions and test in CI, or use the AWS CRT HTTP client for SDK calls. |
| R4 | **The portal widens the attack surface.** Partners may reach it from the internet, already in 0.x through the preview (October 1 update). | Applies from 0.x: separate hostname, listener, session cookies, and database role; an optional portal-only control-plane task in a public subnet; a WAF in front of the portal hostname (AWS WAF managed rule groups and rate-based rules in the reference layout) plus BeFive's own per-user and per-IP rate limits; strict Content Security Policy; sanitized spec content; a portal-preview security review with an external penetration test in milestone 10, and a full portal penetration test in milestone 17. The preview is read-only, which limits what an attacker can change. |
| R5 | **ServiceNow request workflows vary by customer** (catalog items, variables, approval models). | Configurable field mapping and state mapping, the generic webhook adapter, and early testing with a design partner's instance. |
| R6 | **Cached or stored sensitive data.** Caches and async results can leak data across callers if partitioning is misconfigured. | Classification defaults (no caching for Restricted), partitioning by authorization context by default for authenticated routes, AES-GCM encryption, short presigned URL lifetimes, audit of retrievals. |
| R7 | **Composite endpoints grow into a general integration engine.** | Hard limits (no loops, step and time limits), and a documented boundary: long workflows belong in backend services or asynchronous endpoints. |
| R8 | **Datadog cost and cardinality.** Per-consumer tags can create many custom metrics. | Configurable tag sets per sink, top-k consumer tagging, and the cost estimate panel extended to Datadog. |
| R9 | **Report rollups grow in PostgreSQL.** 13 months of hourly rollups by several dimensions can be large. | Partitioned tables, per-dimension cardinality caps, and a storage estimate in the architecture document. |
| R10 | **Design-partner expectations.** Partners may judge the product on items that arrive only in 1.0 (full portal, sandbox, async) or 1.1 (LLM analysis). | The 0.x portal preview shows the portal direction early; publish the 0.x, 1.0, and 1.1 contents (4.1) and the traceability document with each build. |
| R11 | **Okta Management API credential.** The optional app creation needs a credential that can create Okta applications; misuse or leakage could create or change Okta apps. | Off by default; the customer creates a least-privilege credential (custom admin role limited to applications, or an OAuth service app with the `okta.apps.manage` scope); the credential sits in the encrypted secrets store; BeFive only creates apps it names with a fixed prefix and never deletes apps; created apps use `private_key_jwt` with developer-uploaded public keys, so by default BeFive holds neither a client secret nor a private key for them (the opt-in BeFive-generated keys are Administrator-only, never for Restricted APIs, downloadable once, and the private key is deleted from the secrets store immediately after the one-time download, or after 24 hours if it is never downloaded); every call is audited; a connection test verifies the permissions before enabling. |
| R12 | **Cross-cluster dependencies of the single portal.** The production portal reads catalog data from linked environments and proxies try-it to the Sandbox cluster, so a Sandbox or link outage degrades parts of the portal. | Catalog data from linked environments is replicated into the production cluster on promotion and on a schedule, so browsing works when a linked cluster is down; try-it shows a clear "sandbox unavailable" state; the link uses signed, scoped credentials. |

---

## 7. Open questions (need the owner's decision)

No open questions remain for this document. `02-architecture.md` 35.1 and `03-ui-design.md` 10 keep their own lists.

Resolved on October 1, 2026:

- *Client authentication for Okta-created apps* (Draft 4.1 question 1): `private_key_jwt` only, no client secrets; by default developers upload only their public key and BeFive never holds the private key, with BeFive-generated keys as an opt-in, Administrator-only setting excluded for Restricted APIs (2.6, decision 32; key custody decided in update 2). *Generated-key retention* (update 3): a generated private key is deleted from the secrets store immediately after the one-time download, or after 24 hours if it is never downloaded.
- *Reach of the 0.x portal preview* (Draft 4.1 question 2): partners may access it from outside; it is not internal-only by default; hardening and a portal-preview security review are in 0.x (2.11, R4, milestone 10).
- *Rate-limit header default* (`02` question 19): the IETF `RateLimit`/`RateLimit-Policy` headers stay the default, `X-RateLimit-*` stays an option, and BeFive tracks the final RFC wording (2.7, decision 25).
- *Deprecation emails to developers* (`02` question 18, `03` question 9): in 1.0 (2.8, decision 35).

Resolved in Draft 4.1:

- *Automation Manager role* (Draft 4 question 1): added as a fixed role; Administrators keep all rights; Operators approve and revert traffic actions (2.17, decision 9).
- *Traffic actions in 1.0* (question 2): IP block and rate-limit tightening; consumer block and route disable in 1.1 or later (2.19, decision 31).
- *Trademark check* (question 3): BeFive stays the working name; the check is a pre-launch checklist item (decision 6).
- *One portal or one per environment* (question 4): one portal hosted by the production cluster, listing APIs from linked environments, try-it to the Sandbox cluster (2.11, decision 20).
- *Okta OAuth app creation* (question 5): in 1.0 as an optional, off-by-default feature with the customer's own least-privilege credential; binding existing client IDs stays the default (2.6, decision 32).
- *0.x design-partner build* (question 6): kept, with a read-only portal and catalog preview so prompts 4 and 6 can be partly demonstrated (4.1, decision 33).
- *Anomaly scope in 1.0* (question 7): Event Management, Teams and email anomaly notifications, and LLM analysis move to 1.1 (2.19, decisions 11 and 34). Email stays in 1.0 for access-request approvals and scheduled reports.

Resolved by Draft 4: the gateway-signed identity token (`02-architecture.md` question 8 of the Draft 3 open questions, now 35.1 in Draft 4; design in 9.10) is in the MVP as an option; consumer self-service (`03-ui-design.md` 10, question 2) is provided by the portal; the environment switcher (`03-ui-design.md` 10, question 1) is replaced by linked environments; and custom roles (`03-ui-design.md` 10, question 3) stay post-1.0, with the API Owner and Automation Manager roles added.
