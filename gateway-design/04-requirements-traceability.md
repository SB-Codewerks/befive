# BeFive: Requirements Traceability

Draft 4 companion, September 28, 2026. Maps every requirement in `00-customer-requirements.md` to the BeFive release that satisfies it, how, and where the design documents cover it. Section references to `01` are to `01-mvp-scope-and-stack.md` Draft 4. References to `02-architecture.md` and `03-ui-design.md` point to their Draft 3 sections (renamed to BeFive); "to be added in Draft 4" marks content the next pass must write.

**Update (October 1, 2026).** Applies the owner's answers recorded in `01` (Draft 4.1 update): deprecation emails to developers in 1.0 (API-002), `private_key_jwt` only for Okta apps BeFive creates, with developer-uploaded public keys by default (SEC-003; key custody decided later on October 1), the 0.x portal preview reachable by partners from outside (DEV-001), and IETF `RateLimit` headers as the default (TRAF-002). No release placement or coverage count changes.

**Update 2 (October 1, 2026).** Generated-key retention (owner decision, SEC-003): a BeFive-generated Okta client private key is deleted from the secrets store immediately after the one-time download, or after 24 hours if it is never downloaded. No release placement or coverage count changes.

**Release values.** 0.x is the design-partner build; 1.0 is general availability and contains every Must; post-1.0 means planned right after 1.0 (1.1); roadmap means documented direction without a committed date; N/A means not applicable. A requirement is listed under the release in which it is complete; where part of it arrives in 0.x, the text says so.

**Design coverage column (last).** C: the Draft 3 architecture and UI documents already cover it; P: partly covered, changes needed in Draft 4; N: new content needed in Draft 4; N/A: roadmap only.

## 1. Requirements

| ID | Priority | Short requirement | BeFive release | How BeFive satisfies it | 01 references | 02/03 design references | 02/03 coverage (Draft 3) |
|---|---|---|---|---|---|---|---|
| IAM-001 | Must | Integrate with Okta for AuthN/AuthZ | 0.x | Okta is a first-class preset over generic OIDC: BeFive derives discovery, JWKS, and introspection endpoints, tests the connection, and maps Okta scopes, groups, and claims into gateway policies. | 01 2.3, 2.17 | 02 9.1, 9.2, 9.7; 03 5.9, 6.2 | C |
| IAM-002 | Must | OAuth 2.0 and OIDC with Okta | 0.x | Local JWT validation with issuer, audience, expiry, algorithm checks and a rotating JWKS cache, plus RFC 7662 introspection for opaque tokens; scopes and claims are available to policies. | 01 2.3 | 02 9.2, 9.3, 9.4, 9.7; 03 5.9 | C |
| IAM-003 | Must | Scope- and claims-based access control | 0.x | Policies require scopes and claims, including custom claims, through a small compiled expression language (equality, membership, comparisons, any/all over arrays), attachable at any policy level. | 01 2.3, 2.4 | 02 10.1–10.5, 23.3; 03 5.6 | P |
| IAM-004 | Must | Centralize AuthN/AuthZ in the gateway | 0.x | BeFive performs primary authentication and authorization with default deny and forwards validated context, so upstreams need no primary auth logic; an optional gateway-signed internal JWT and upstream mTLS let upstreams trust that context. | 01 2.3, 2.4 | 02 9.8, 9.10, 10.2; 03: to be added in Draft 4 | P |
| IAM-005 | Must | Forward trusted caller context; prevent header spoofing | 0.x | Only validated subject, client, application, organization, groups, scopes, and selected claims are forwarded, as headers and optionally a signed internal JWT; every configured identity header is stripped from inbound requests. | 01 2.3 | 02 7.4 (slot 5), 9.8, 9.9, 9.10, 18.1; 03: to be added in Draft 4 | P |
| IAM-006 | Must | Client ID/secret or equivalent for service-to-service | 0.x | Okta client-credentials tokens are validated like any JWT and mapped to applications by client ID for policies and limits; API keys and mTLS remain alternatives. | 01 2.1, 2.3 | 02 5.2, 9.2, 9.6, 9.7; 03 5.7 | C |
| IAM-007 | Must | Execute APIs in the context of an authenticated Okta user | 0.x | User tokens carry subject, groups, and claims that policies evaluate and BeFive forwards upstream as headers or in the internal JWT for delegated access; RFC 8693 token exchange follows after 1.0. | 01 2.3, 2.20 | 02 9.8, 9.10, 10.3, 10.5; 03: to be added in Draft 4 | P |
| IAM-008 | Must | Resilience during Okta/IdP outages | 0.x | Local validation, stale-if-error JWKS (default 24 h) with a persisted last-known copy, bounded introspection cache with grace TTL, per-route fail-closed or fail-open-for-cached, break-glass admin, IdP health metric and alarm, and an Okta outage runbook. | 01 2.3, 2.17 | 02 9.3, 9.4, 9.12 (persisted JWKS, grace cache, failure modes, break-glass, runbook), 13.2, 13.4, 14.5, 31.2; persisted JWKS, grace TTL, per-route failure mode, runbook to be added in Draft 4), 14.5; 03 5.9 (IdP health to be added in Draft 4) | P |
| SEC-001 | Must | API key management | 0.x | Issue, rotate with two active keys, revoke, expire, associate with applications, consumers, and organizations, and audit every action, in the console, Admin API, CLI, and (1.0) portal self-service. | 01 2.5, 2.11 | 02 9.5, 12.5, 15.2; 03 5.7, 6.3 | C |
| SEC-002 | Must | Secure storage and handling of keys and credentials | 0.x | Keys stored only as peppered HMAC hashes and shown once; secrets envelope-encrypted with env, file, or AWS KMS key providers; TLS in transit; redaction keeps secrets out of logs, portal, and upstream headers. | 01 2.5, 2.18 | 02 9.5, 9.11, 12.4, 18.2, 18.3, 18.5; 03 4.6 | C |
| SEC-003 | Must | Automate access requests via ServiceNow or similar | 1.0 | Portal access requests are routed by classification and owner to built-in approvals or to ServiceNow (request record, webhook plus polling for the outcome), then keys and entitlements are provisioned and the outcome written back and audited; a generic webhook adapter covers similar systems; optional, off-by-default Okta OAuth app creation through the Okta Management API completes machine onboarding (created apps use `private_key_jwt` with developer-uploaded public keys by default; BeFive-generated keys are an Administrator opt-in, excluded for Restricted APIs, and a generated private key is deleted from the secrets store right after its one-time download or after 24 hours if never downloaded). | 01 2.6 | 02 14.13, 24.3–24.7; 03: to be added in Draft 4 | N |
| SEC-004 | Must | Classify APIs/data to drive approval routing | 1.0 | Configurable classification levels on APIs, versions, and operations, and tags on dictionary fields, drive approval chains and default (optionally locked) policies for caching, IP ranges, and redaction. | 01 2.6 | 02 24.1, 24.2, 23.3; 03: to be added in Draft 4 | N |
| SEC-005 | Must | Restrict access by IP range | 0.x | CIDR allow and deny rules (IPv4 and IPv6) at any policy level, evaluated on the client address resolved through trusted proxies and X-Forwarded-For. | 01 2.4 | 02 8.8, 10.1, 23.5; 03 5.4, 5.6 | P |
| DEV-001 | Must | Developer portal | 1.0 | One portal application, hosted by the production cluster on its own hostname, offers discovery across linked environments, OpenAPI-rendered docs, try-it against the Sandbox cluster, subscriptions and access requests, and self-service key management. Partly in 0.x: a read-only preview with discovery and OpenAPI-rendered docs (no subscriptions, keys, try-it, or access requests), which partners may reach from outside, hardened and security-reviewed for internet exposure in 0.x. | 01 2.11 | 02 25.1–25.4, 25.6 (0.x preview subset in 25.1); 03: to be added in Draft 4 | N |
| DEV-002 | Must | Searchable API catalog | 1.0 | PostgreSQL full-text search with facets for domain, owner, tags, lifecycle state, classification, version, and environment availability. Partly in 0.x: the preview has search and all facets except environment availability, which needs linked environments (1.0). | 01 2.11 | 02 25.5, 26.8; 03: to be added in Draft 4 | N |
| DEV-003 | Must | Portal login and access control via Okta | 1.0 | Portal sign-in through Okta OIDC; visibility and request rights per API, version, and operation by Okta group and by individual user. Partly in 0.x: Okta login and group and user visibility in the preview; request rights arrive with access requests in 1.0. | 01 2.11 | 02 24.3, 24.8, 25.4, 5.6; 03: to be added in Draft 4 | N |
| DEV-004 | Must | Integrated lifecycle tooling | 1.0 | Design, Published, Deprecated, and Retired states for APIs and versions, OpenAPI import, testing in the sandbox, version management, promotion hooks, deprecation headers, and retirement with 410 responses. | 01 2.8, 2.11, 2.12 | 02 26.1–26.3, 26.9; 03: to be added in Draft 4 | N |
| DEV-005 | Must | Sandbox and testing capability | 1.0 | A dedicated Sandbox environment and per-route mock responses from OpenAPI examples, reachable only with portal login and sandbox entitlements and credentials separate from production. | 01 2.11, 2.12 | 02 25.7, 26.6, 26.7; 03: to be added in Draft 4 | N |
| DEV-006 | Should | AI-assisted API generation and doc validation | post-1.0 | Planned for 1.1: LLM-assisted spec linting, documentation-gap detection, and draft generation reusing the existing LLM integration, advisory only; deterministic lint rules can ship in 1.0. | 01 2.20 | 02 14.14 (LLM provider integration reused in 1.1), 26.3; 03: to be added in Draft 4 | N |
| DEV-007 | Must | Create and ingest OpenAPI YAML | 1.0 | Import OpenAPI 3.0/3.1 YAML or JSON to create or update APIs, versions, and operations with a diff preview; export or generate OpenAPI for any API including gateway-added security schemes (basic import already in 0.x). | 01 2.1, 2.11 | 02 15.7, 26.9, 16.1; 03: to be added in Draft 4 | N |
| DEV-008 | Must | Centralized data dictionary / API index | 1.0 | A searchable field and schema index built from ingested OpenAPI schemas, with definitions, owners, usage across APIs, and classification tags that feed redaction and caching. | 01 2.11 | 02 26.8, 25.5; 03: to be added in Draft 4 | N |
| API-001 | Must | Versioning and routing between versions | 0.x | APIs have versions routed by path, header, host, media type, or query parameter, with a default version per API. | 01 2.1, 2.8 | 02 5.6, 6.9, 27.1, 27.2; version entity and strategies to be added in Draft 4); 03 5.4 | P |
| API-002 | Must | Endpoint deprecation | 0.x | Operations, versions, and APIs can be deprecated with dates; BeFive emits Deprecation (RFC 9745), Sunset (RFC 8594), and Link headers, shows badges in the catalog (1.0), and reports who still calls deprecated endpoints. From 1.0, developers and application owners who call a deprecated operation also get email notices at deprecation, before sunset, and at sunset, configurable or turned off per API. | 01 2.8, 2.11 | 02 26.5, 27.3, 27.4, 27.5; 03: to be added in Draft 4 | N |
| API-003 | Must | Low-code endpoint composition | 1.0 | Composite endpoints: bounded, loop-free step graphs of sequential, parallel, and conditional calls with JSON mapping and optional sandboxed SCI expressions, built in a console graph builder and stored as EDN. | 01 2.9 | 02 28; 03: to be added in Draft 4 | N |
| API-004 | Must | Function-based and microservice architectures | 0.x | Microservices are HTTP upstreams with pooling, balancing, health checks, and plugins; functions are Lambda upstreams or Function URLs; all get the same policies. | 01 2.2 | 02 8.1–8.5, 8.10, 17; 03 5.3 (Lambda upstream type to be added in Draft 4) | P |
| API-005 | Must | AWS Lambda as API backend | 0.x | A Lambda upstream type invokes functions with the AWS SDK v2 and the task IAM role, using API Gateway payload 2.0 mapping, error mapping, timeouts, concurrency limits, and metrics. | 01 2.2 | 02 8.10; 03: to be added in Draft 4 | N |
| API-006 | Must | Containerized APIs with discovery and scaling | 1.0 | Targets come from DNS A/SRV, AWS Cloud Map, or Kubernetes EndpointSlices with health checks and load balancing, so scaling and rolling deployments need no configuration changes. | 01 2.2 | 02 2.4, 8.2, 8.5, 8.11; discovery to be added in Draft 4); 03 5.3 | P |
| API-007 | Must | Multiple environments and promotion pipelines | 1.0 | One BeFive cluster per environment, linked for promotion of signed, versioned bundles with diff, approval, and audit, per-environment overlays, and a Git/CI path through b5ctl. | 01 2.12 | 02 6.10, 16.3, 34; 03 6.5 (linked environments and bundles to be added in Draft 4) | P |
| API-008 | Future | GraphQL support | roadmap | GraphQL endpoints are proxied as HTTP routes with all endpoint-level policies in 1.0; a documented roadmap adds GraphQL-aware limits, SDL in the catalog, field-level authorization, and a REST-to-GraphQL facade. | 01 2.22 | 02/03: roadmap only, no Draft 4 content | N/A |
| API-009 | Must | No dependency on SOAP | 0.x | No SOAP services, libraries, or infrastructure are used anywhere in BeFive; SOAP traffic can only be proxied as opaque HTTP. | 01 2.18, 2.22 | 02 3.1, 3.2; 03: no change needed | C |
| TRAF-001 | Must | Rate limiting, throttling, quotas by many dimensions | 0.x | Token-bucket limits and quotas keyed by application, key, user, Okta group, scope, operation, IP, and organization, alone or combined, at any policy level. | 01 2.7 | 02 11.1–11.4, 11.6; 03 5.8 | P |
| TRAF-002 | Must | Reliable, observable enforcement | 0.x | Local or Redis-backed buckets verified by load tests, with standard IETF RateLimit and RateLimit-Policy headers by default (X-RateLimit-* as an option) and Retry-After, decisions in access logs, and rate-limit metrics and dashboards. | 01 2.7, 2.14 | 02 11.3, 11.5, 13.2, 20.5; 03 5.2 | P |
| TRAF-003 | Must | Connection pooling | 0.x | Configurable upstream connection pools with keep-alive, idle timeouts, limits, and pool metrics. | 01 2.2 | 02 8.1 | C |
| TRAF-004 | Must | Encrypted, secure gateway caching | 1.0 | Caffeine cache with optional Redis, entries encrypted with AES-GCM keyring keys, keys partitioned by authorization context, Cache-Control, TTL, purge via Admin API, classification-aware defaults, and body-free cache logging. | 01 2.7 | 02 29, 24.1; 03: to be added in Draft 4 | N |
| TRAF-005 | Must | Large payloads and streaming | 0.x | Streaming without full buffering, WebSocket, SSE, and chunked pass-through, configurable size limits at every policy level, and stream metrics. | 01 2.2 | 02 8.3, 8.6, 8.9 | P |
| TRAF-006 | Must | Secure storage and async retrieval of generated files | 1.0 | Results and files go to S3 with SSE-KMS, S3-compatible storage, or local disk, optionally envelope-encrypted, and are retrievable only by the job owner or permitted groups or scopes via gateway proxy or short-lived presigned URL, with retention. | 01 2.10 | 02 30, 18.5; 03: to be added in Draft 4 | N |
| TRAF-007 | Must | Async processing for long-running requests | 1.0 | Asynchronous endpoints return 202 with a job ID and status URL, dispatch by HTTP callback, Lambda async invoke, or SQS, keep state in PostgreSQL, and support polling and HMAC-signed callbacks; backend-native async patterns pass through. | 01 2.10 | 02 30.6, 30.7; 03: to be added in Draft 4 | N |
| OBS-001 | Must | Configurable logging, monitoring, alerting integrations | 0.x | Structured access, audit, and app logs plus pluggable sinks (CloudWatch EMF, Datadog, OTLP, Prometheus) and OpenTelemetry traces, configured per install. | 01 2.13 | 02 12.1–12.3, 12.8, 31; 03 5.14.1 | P |
| OBS-002 | Must | Centralized logging/monitoring with external tools | 0.x | All gateway nodes and environments report through the same schema and sinks into CloudWatch or Datadog recipes with dashboards and alarms. | 01 2.13, 2.14 | 02 12, 13; 03 5.2, 5.11 | C |
| OBS-003 | Must | Datadog integration | 0.x | DogStatsD metrics with tags, JSON logs with Datadog reserved attributes and trace correlation, traces via OTLP to the Agent, and a Terraform datadog provider recipe for dashboards and monitors. | 01 2.13, 2.14 | 02 31.3, 31.6; 03: to be added in Draft 4 | N |
| OBS-004 | Must | Real-time monitoring | 0.x | Live console views from gateway counters by API and operation, policy events, and IdP health, plus near-real-time sink metrics. | 01 2.14 | 02 12.6, 15.9; 03 5.2 (per-API views to be added in Draft 4) | P |
| OBS-005 | Must | Track and report 4xx and 5xx rates | 0.x | Separate 4xx and 5xx rates by API, operation, consumer, environment, and time, in metrics, dashboards, and reports. | 01 2.13, 2.15 | 02 12.2, 12.3.2, 13.2, 31.2; 03 5.2 | P |
| OBS-006 | Must | Measure and report response times | 0.x | Latency p50, p90, p95, and p99 (total and upstream) by API, operation, consumer, and environment. | 01 2.13, 2.14 | 02 12.3.2, 13.2, 31.2; 03 5.2 | P |
| OBS-007 | Must | Robust, customizable reporting | 1.0 | Console report builder over PostgreSQL rollups for usage, consumers, performance, errors, policy and security events, and audit, with saved and scheduled reports exported as CSV, JSON, or XLSX to download, email, or S3. | 01 2.15 | 02 13.5, 27.4, 32; 03: to be added in Draft 4 | N |
| OBS-008 | Must | Monitoring aligned with InfoSec | 1.0 | Retention settings, RBAC on logs and reports, hash-chained audit log, dictionary- and classification-driven redaction, security alerting, and a signed evidence export. | 01 2.16 | 02 12.4, 12.5, 14, 18, 33; 03 5.12 | P |
| POL-001 | Must | Hierarchical configuration with inheritance | 0.x | Security, IP, rate-limit, logging and redaction, caching, size-limit, and deprecation policies inherit from global to environment, API, version, path, and operation, with locks that stop lower levels from weakening them. | 01 2.4 | 02 10.2, 23.1–23.5; hierarchy to be added in Draft 4); 03 5.6 | P |
| POL-002 | Must | Policies at API, path, and endpoint levels | 0.x | Most specific level wins, precedence is resolved at compile time, locks are validated, and an effective policy view shows the origin of every value. | 01 2.4 | 02 6.4, 23.4, 23.6, 23.7; 03 5.6 (effective policy view to be added in Draft 4) | P |
| POL-003 | Must | Manage endpoint configuration through an API | 0.x | The Admin API, EDN files, Clojure DSL, and b5ctl manage group access, classification, traffic rules, lifecycle state, and policy bindings as configuration as code. | 01 2.12 | 02 15, 16; 03 5.13 | C |
| POL-004 | Must | Controlled promotion across environments | 1.0 | Versioned, signed bundles promoted Dev to Test to Prod with diff, approval, and audit through the console or CI, with per-environment overlays (export, diff, and apply between environments already in 0.x). | 01 2.12 | 02 6.8, 6.10, 16.1–16.3, 34.5; 03 6.5 (linked-environment promotion to be added in Draft 4) | P |

## 2. Cross-cutting governance expectations

GOV-1 to GOV-6 are the six bullets of the section "Cross-cutting security and governance expectations" in `00-customer-requirements.md`, in order.

| ID | Priority | Short requirement | BeFive release | How BeFive satisfies it | 01 references | 02/03 design references | 02/03 coverage (Draft 3) |
|---|---|---|---|---|---|---|---|
| GOV-1 | Governance | All auth, key, secret, and policy actions auditable | 1.0 | The hash-chained audit log already covers admin, credential, and session actions; Draft 4 extends it to access requests, approvals, promotions, cache purges, and file retrievals (core in 0.x). | 01 2.5, 2.6, 2.16 | 02 12.5, 15.6, 33.2; 03 5.12 | P |
| GOV-2 | Governance | Prevent spoofing of forwarded identity context | 0.x | All configured identity headers are stripped from inbound requests, and upstreams can verify a gateway-signed internal JWT or require gateway mTLS. | 01 2.3 | 02 7.4, 9.8, 9.9, 18.1 | P |
| GOV-3 | Governance | Least-privilege access controls at all levels | 1.0 | Default deny, policies at every level for users, groups, clients, and machine identities, portal visibility by group and user, and console RBAC with a scoped API Owner role and an Automation Manager role for rules, scripts, detectors, silences, and traffic-action settings (core in 0.x, including preview visibility). | 01 2.3, 2.4, 2.11, 2.17 | 02 10, 15.6, 23.2; 03 2 (API Owner and Developer roles to be added in Draft 4) | P |
| GOV-4 | Governance | Classification used in governance and approval workflows | 1.0 | Classification drives approval chains and default, lockable policies; it is not passive metadata. | 01 2.6 | 02 24.3–24.7; 03: to be added in Draft 4 | N |
| GOV-5 | Governance | Sensitive-data handling in caching, logging, monitoring, reporting | 1.0 | Classification and dictionary tags drive cache defaults and partitioning, log and LLM-context redaction, and access to report datasets. | 01 2.7, 2.13, 2.15, 2.16 | 02 12.4, 24.1, 26.8, 29.3 | P |
| GOV-6 | Governance | Observable policy enforcement and operations | 0.x | Policy decisions are in every access line, metrics and dashboards cover denials, limits, and IP blocks, the effective policy view explains configuration, and the evidence export (1.0) packages it for auditors. | 01 2.4, 2.13, 2.14, 2.16 | 02 12.2, 13.2, 14; 03 5.2, 5.15 | C |

## 3. Vendor evaluation prompts: demo script outline

Each line is what to show in BeFive. The release column says which build can run the demo. Demos use sample data and a customer-provided or BeFive-owned Okta tenant.

| # | Evaluation prompt (short) | Release needed | What to show in BeFive |
|---|---|---|---|
| 1 | Okta OAuth/OIDC with scope and claims enforcement on an endpoint | 0.x | Run the Okta wizard, import an OpenAPI spec, set a claims rule (for example `groups` any-of plus a custom `department` claim) on one operation, then show allowed and `403` requests and the decision in the access log and route tester. |
| 2 | Service-to-service via client credentials | 0.x | Create an Okta client-credentials app, bind its client ID to a BeFive application, call an API with the machine token, and show per-application rate limits and log fields. |
| 3 | Propagate Okta user context downstream, with anti-spoofing | 0.x | Call with a user token and show the forwarded headers and the verified internal JWT at an echo upstream; resend with forged `X-BeFive-Subject` and internal JWT headers and show they are stripped. |
| 4 | Portal access via Okta; visibility by group and by user | 0.x visibility (preview); 1.0 access | 0.x: log in to the read-only portal preview as two Okta users; show an API visible to one Okta group, another shared with a single named user, and hidden from the second user. 1.0 adds the access half: the entitled user requests access and receives a key, while the other user cannot request it. |
| 5 | Create, rotate, revoke, audit an API key | 0.x (portal self-service in 1.0) | Issue a `b5k_` key for an application, call with it, rotate (both keys work), revoke the old one (`401`), and show the audit trail and chain verification. |
| 6 | OpenAPI ingestion, catalog publication, interactive testing, sandbox | 0.x ingestion, catalog, docs (preview); 1.0 testing and sandbox | 0.x: import a YAML spec with `b5ctl` or the console, publish the version, find it by facet in the preview catalog, and read the rendered documentation. 1.0: re-import with the diff preview, see availability per environment, and use try-it against the Sandbox cluster, including a mock response generated from examples. |
| 7 | Version routing and deprecation with the Deprecation header | 0.x | Route v1 and v2 by path and by media type, deprecate v1, and show the `Deprecation`, `Sunset`, and `Link` headers and the deprecated-usage report by consumer. |
| 8 | Per-endpoint IP restrictions, rate limits, throttles, quotas, with logs and metrics | 0.x | Set an internal-only CIDR rule on one operation and a per-user limit and monthly quota on another; drive traffic and show `403`, `429` with `RateLimit` and `Retry-After`, log lines, and dashboard panels. |
| 9 | API/path/endpoint policy inheritance and config deployment via API or code | 0.x | Set a locked auth policy at API level and an override at operation level, show the effective policy view and a rejected attempt to weaken the lock, then change it in Git and deploy with `b5ctl diff` and `apply`. |
| 10 | Datadog: latency, 4xx/5xx, usage, alerts, security audit data | 0.x | Show the Terraform-provisioned Datadog dashboard (p50 to p99, 4xx and 5xx by API and consumer, usage), trigger a monitor, follow a trace from the log, and show audit and denial events in Datadog logs. |
| 11 | Large payload or streaming, async long-running request, secure file retrieval | 0.x streaming; 1.0 async | Stream a large download and an SSE feed with size-limit rejection; submit an async report job (`202`, status polling, signed callback) and retrieve the file as the owner, then show a `403` for another user and an expired presigned URL. |
| 12 | Safe behavior during an Okta outage | 0.x | Block Okta egress: JWT routes keep working on the persisted JWKS, a fail-closed introspection route returns `503` after its grace TTL, the IdP health alarm fires, and the break-glass admin signs in to the console; walk through the runbook. |

## 4. Coverage summary

**Requirement IDs:** 49 in total: 47 Must, 1 Should, 1 Future / Roadmap. Every Must is in 1.0 or earlier.

| Status | Requirement IDs | Of which Must | Governance items (GOV) |
|---|---|---|---|
| 0.x | 29 | 29 | 2 |
| 1.0 | 18 | 18 | 4 |
| post-1.0 | 1 | 0 | 0 |
| roadmap | 1 | 0 | 0 |
| N/A | 0 | 0 | 0 |
| **Total** | **49** | **47** | **6** |

By the end of 1.0, 47 of 49 requirement IDs are complete (29 of them already in the 0.x build), DEV-006 follows in 1.1, and API-008 is on the roadmap. All six governance items are met by 1.0.

Draft 4.1 changed no release placement at the level of complete requirements, so the counts above are unchanged. It adds partial 0.x delivery for DEV-001, DEV-002, and DEV-003 through the read-only portal preview. Requirements complete in 1.0 with a part already in 0.x are now: DEV-001, DEV-002, DEV-003, DEV-007, POL-004, GOV-1, and GOV-3. The anomaly items moved to 1.1 (LLM analysis, ServiceNow Event Management, Teams and email anomaly notifications) and the two traffic actions moved after 1.0 are not needed by any requirement ID; the IdP health alarm for IAM-008 is also provided by the CloudWatch and Datadog recipes.

The design coverage table below and the "02/03 coverage (Draft 3)" column describe the Draft 3 starting point. With `02` Draft 4, all `02` content for every requirement except API-008 (roadmap) is written; see section 5.

| Design coverage in 02/03 (Draft 3) | Requirement IDs | Governance items |
|---|---|---|
| Already covered | 9 | 1 |
| Partly covered, changes needed | 21 | 4 |
| New content needed | 18 | 1 |
| Roadmap only | 1 | 0 |

## 5. Draft 4 work list for `02-architecture.md` and `03-ui-design.md`

The rename is already applied to both documents. The following sections need new or changed content so that the architecture and UI match `01` Draft 4. Requirement IDs in parentheses show why.

**Status (October 1, 2026): every `02` item in 5.1 and 5.2 is done in `02-architecture.md` Draft 4.** The status of the `03` items (5.3, 5.4) is tracked separately by the UI design. The "02/03 design references" column in sections 1 and 2 now cites `02` Draft 4 section numbers; its `03` part is unchanged here. `02` Draft 4 keeps chapters 1 to 22, adds chapters 23 to 34, and renumbers the former chapter 23 (open questions and risks) to 35.

### 5.1 `02-architecture.md`: changes to existing sections

| Section | Change needed |
|---|---|
| Header, 1 | Draft 4 changelog; summary of the new subsystems (portal, jobs, reports, telemetry sinks, promotion). |
| 2.1, 2.4, 2.5 | System context and AWS layout with Datadog Agent, OTLP collector, ServiceNow request flow, S3 and SQS, Lambda, Cloud Map, Kubernetes API, linked environments, and the portal hostname; component view with portal, job dispatcher, report scheduler, and sinks. (OBS-003, TRAF-006, API-005, API-006, API-007, DEV-001) |
| 3.1, 3.2 | Module layout for the portal build and new namespaces (`befive.openapi`, `befive.policy`, `befive.composite`, `befive.jobs`, `befive.cache`, `befive.reports`, `befive.promotion`, `befive.telemetry.*`). |
| 4.1, 4.2 | New settings and Integrant components (sinks, cache, discovery, job workers, report scheduler, portal server, bundle signer). |
| 5.2, 5.3, 5.4 | Entities, malli schemas, and tables for API, version, operation, organization, application, subscription and entitlement, access request, classification level, policy attachment (with level and lock), composite, async job, stored object, report definition and schedule, rollups, linked environment, dictionary field. (DEV-*, SEC-003, SEC-004, POL-001, TRAF-006, OBS-007) |
| 6.4 | Compile step: operations to routes, effective policy resolution with locks, lock-violation validation. (POL-001, POL-002) |
| 6.8 | Rollback interplay with promoted bundles. (POL-004) |
| 7.4 | Phase order: identity-header stripping before authentication, cache lookup and store, deprecation headers, composite and async handlers, internal JWT minting. (IAM-005, TRAF-004, API-002, API-003, TRAF-007) |
| 8.2, 8.3, 8.6 | Discovered targets in load balancing; per-level size limits, SSE and chunked pass-through, stream metrics. (API-006, TRAF-005) |
| 9.3, 9.4 | Persisted last-known JWKS, introspection grace TTL, per-route fail-closed or fail-open-for-cached. (IAM-008) |
| 9.8 | Replace "post-MVP candidate" with the gateway-signed internal JWT and BeFive JWKS endpoint; forwarded organization, application, and claims; the strip list. (IAM-004, IAM-005, IAM-007) |
| 10.1–10.4 | Claims expression language; policy levels and locks; IP rules at every level; classification default policies. (IAM-003, POL-001, POL-002, SEC-005, SEC-004) |
| 11.1–11.5 | New limit dimensions (user, group, scope, operation, IP, organization); `RateLimit`/`RateLimit-Policy` and `Retry-After` headers. (TRAF-001, TRAF-002) |
| 12.2 | Access log fields for API, version, operation, organization, application, environment, cache and policy decisions. (OBS-001, OBS-005) |
| 12.3 | Telemetry sink architecture: Datadog (DogStatsD, reserved log attributes, trace correlation), OTLP, Prometheus; p90; API and operation dimensions. (OBS-001, OBS-003, OBS-006) |
| 12.4 | Data-dictionary- and classification-driven redaction. (OBS-008, GOV-5) |
| 12.5 | Audit actions for access requests, approvals, promotions, cache purges, job and file retrievals. (GOV-1) |
| 12.6, 12.8 | Live metrics per API and operation, policy events, IdP health; OpenTelemetry spans and export. (OBS-004, OBS-001) |
| 13.2, 13.4, 13.5 | p90, IdP health, cache, and deprecated-usage panels and alarms; monthly report becomes a saved report. (OBS-006, IAM-008, OBS-007) |
| 14.13 | ServiceNow connector reused for access-request records, inbound decision webhook, and polling. (SEC-003) |
| 15.2, 15.6, 15.7 | New Admin API resources; API Owner role and permissions; OpenAPI export for customer APIs (distinct from the Admin API's own spec). (POL-003, GOV-3, DEV-007) |
| 16.1–16.3 | `b5ctl` commands for OpenAPI import, bundle sign and verify, promote, cache purge, effective policy; file layout with overlays. (POL-004, API-007, DEV-007) |
| 18.1, 18.5 | Threat model for the portal, cache, presigned URLs, composite steps, and callbacks; SSRF rules for Lambda, discovery, and callbacks. (SEC-002, TRAF-004, TRAF-006) |
| 20.1 | Performance budgets for cache hits and composite steps. |
| 22 | Integration tests with LocalStack, MinIO, k3s, a Datadog Agent, and a ServiceNow test instance; property tests for policy resolution. |
| 35.1, 35.2 (were 23.1, 23.2) | Mark question 8 resolved; add the new owner questions and risks from `01` sections 6 and 7. |
| Appendix A | Add Draft 4 decisions. |

All rows above: **done in 02 Draft 4** (see the 02 changelog for the full list, including 12.2–12.6, 12.8, 13.2, 13.4, 13.5, 14, 15.9, 18.4, and 21.3).

### 5.2 `02-architecture.md`: new sections

All items: **done in 02 Draft 4**, at the sections given.

1. Policy hierarchy and effective policy resolution (POL-001, POL-002) **Done: 02 23.**
2. IdP outage behavior and the Okta outage runbook (IAM-008) **Done: 02 9.12.**
3. Classification and the access-request workflow, including ServiceNow and the generic webhook adapter (SEC-003, SEC-004, GOV-4) **Done: 02 24.**
4. Developer portal architecture: hosting, sessions, Okta login, visibility model, try-it proxying to the sandbox (DEV-001, DEV-003, DEV-005) **Done: 02 25.**
5. Catalog search and the data dictionary (DEV-002, DEV-008) **Done: 02 25.5, 26.8.**
6. OpenAPI import, diff, export, lifecycle states, and mock responses (DEV-004, DEV-005, DEV-007, API-002) **Done: 02 26.**
7. Versioning strategies and deprecation headers (API-001, API-002) **Done: 02 27.**
8. Composite endpoints: step graph model, limits, execution, SCI steps (API-003) **Done: 02 28.**
9. Lambda upstreams and service discovery (API-004, API-005, API-006) **Done: 02 8.10, 8.11.**
10. Response caching: encryption, partitioning, invalidation (TRAF-004) **Done: 02 29.**
11. Asynchronous endpoints and object storage (TRAF-006, TRAF-007) **Done: 02 30.**
12. Linked environments, signed bundles, overlays, and promotion (API-007, POL-004) **Done: 02 34.**
13. Reporting: rollups, report builder backend, schedules, exports (OBS-007) **Done: 02 32.**
14. InfoSec: retention settings and the evidence export (OBS-008) **Done: 02 33.**
15. Datadog recipe generator (OBS-003) **Done: 02 31.6.**

### 5.3 `03-ui-design.md`: changes to existing sections

| Section | Change needed |
|---|---|
| Header | Draft 4 changelog. |
| 2 | Personas and roles for API Owner and portal Developer. (GOV-3) |
| 3.1, 3.2 | Navigation and URLs for APIs, Organizations, Access requests, Composites, Jobs, Reports, Environments, Data dictionary. |
| 5.2 | Per-API and per-operation live views, policy events, IdP health, p90. (OBS-004, OBS-006) |
| 5.3, 5.4 | Lambda upstream type and discovery sources; route links to operations, version rule, deprecation, cache, and size-limit fields. (API-005, API-006, API-002, TRAF-004, TRAF-005) |
| 5.5 | Route tester shows the effective policy and composite step traces. (POL-002, API-003) |
| 5.6 | Policies at six levels with locks; effective policy view with value origins. (POL-001, POL-002) |
| 5.7 | Organizations, consumers, and applications with keys and bound client IDs. (SEC-001, IAM-006) |
| 5.9 | IdP outage settings, IdP health, internal JWT signing keys. (IAM-004, IAM-008) |
| 5.12 | Evidence export. (OBS-008) |
| 5.13 | Promotion from linked environments. (POL-004) |
| 5.14 | Settings for telemetry sinks (Datadog, OTLP, Prometheus), classification levels and defaults, retention. (OBS-003, SEC-004, OBS-008) |
| 6.5 | Promotion flow through linked environments instead of manual export and import. (API-007, POL-004) |
| 8 | Portal as a second shadow-cljs build sharing modules; how the control plane serves it on its own hostname. (DEV-001) |
| 9 | New mockups needed (APIs and OpenAPI import, effective policy, portal catalog and API page, access request); coordinate with the mockup work. |
| 10 | Mark questions 1 and 2 resolved and question 3 partly resolved; add new owner questions. |

### 5.4 `03-ui-design.md`: new sections

1. Console screens: APIs and versions (with OpenAPI import diff), Data dictionary, Access requests and approvals, Composite builder, Async jobs, Cache purge, Report builder, Linked environments and promotion.
2. Developer portal UI: sign-in, catalog with facets, API page with docs and deprecation badges, try-it, applications and keys, access requests, changelog.
3. Key flows: onboard an API from an OpenAPI file; request access through ServiceNow; promote Dev to Test to Prod; operate during an Okta outage; build a composite endpoint; run and retrieve an async job.
