# Roadmap: 0.x, 1.0, 1.1 and later

**Relevance:** 0.x and 1.0 planning. **Sources:** 01 §2.20–§2.22, §4.1–§4.3; 02 §35.1; 04 (traceability, release column); owner decisions of Oct 1, 2026 (decisions.md). Milestone detail is in [0x-milestones.md](0x-milestones.md).

## 1. Release split (01 §4.1)

| Release | What it is | Scope |
|---|---|---|
| **0.x design-partner build** | Not GA. Licensed for evaluation and pilots. Partners run it in their own AWS. **Focus: Okta-protected REST APIs on AWS behind an ALB.** Evaluation mode is 1 gateway node with all features and no time limit. | Core gateway (routing, pooling, streaming, Lambda upstreams) and the API, version and operation model with basic OpenAPI import. Okta and OIDC (scopes, claims, client credentials, user context, anti-spoofing, internal JWT, IdP-outage resilience). API keys and secrets. Policy hierarchy with locks and the effective policy view. IP rules. Rate limits and quotas with all dimensions, **plus the pre-auth per-IP limiter (owner, Oct 1)**. Version routing and deprecation headers. CloudWatch and Datadog telemetry with OTLP traces and both recipes. Config as code (Admin API and `b5ctl`: export, diff, apply). **TOTP for local break-glass admins (owner, Oct 1).** Console basics. A **read-only portal and catalog preview**. |
| **1.0 GA** | All Must requirements | The full portal (subscriptions, self-service keys, try-it, access requests, cross-environment catalog), lifecycle tooling, sandbox and mocks, OpenAPI export and diff, the data dictionary, classification and the access-request workflow (built-in, ServiceNow, generic webhook, optional Okta app creation), composite endpoints, service discovery, encrypted caching, async endpoints and file retrieval, linked environments with signed bundles, the report builder, the InfoSec additions, and anomaly detection and scripted response (Draft 4.1 scope) |
| **1.1** (right after 1.0) | First update | DEV-006 (AI-assisted spec checking and drafts), the LLM provider integration and root-cause analysis (**providers decided at 1.1**), ServiceNow Event Management, Teams and email anomaly notifications, consumer-block and route-disable actions (1.1 or later), RFC 8693 token exchange, Okta group membership as an entitlement (01 §2.20) |
| **Deferred beyond 1.0** (01 §2.21) | Not scheduled | Helm chart. SAML and SCIM. gRPC and upstream HTTP/2 (owner: no upstream HTTP/2 until gRPC). Inline Clojure policy expressions. General body transformation. ML anomaly models, forecasting, a conversational assistant. Monetization. Multi-region sync. Custom RBAC roles. FIPS (owner: after 1.0). Dark mode (owner: after 1.0). |
| **Roadmap** (01 §2.22) | Ordered after 1.0 | GraphQL-aware routing and limits, then later GraphQL steps (API-008). SOAP is a non-requirement (API-009). |

**Evaluation-prompt coverage** (00, "Vendor evaluation prompts"; 01 §4.1):
- 0.x covers prompts **1, 2, 3, 5, 7, 8, 9, 10, 12**, plus:
  - the streaming half of **11**
  - the visibility part of **4**
  - the import, catalog and docs part of **6**
- 1.0 completes all 12.

## 2. Requirements by release (from 04)

The release column is 04's. **0.x has 29 requirement IDs.** Several 1.0 IDs are partly delivered in 0.x, as the notes after the table explain. Requirement text is abbreviated; see 00 and 04 for full wording.

| ID | Priority | Requirement | Release (04) | Primary sources |
|---|---|---|---|---|
| IAM-001 | Must | Integrate with Okta for AuthN/AuthZ | 0.x | 01 2.3, 2.17; 02 9.1, 9.2, 9.7; 03 5.9, 6.2 |
| IAM-002 | Must | OAuth 2.0 and OIDC with Okta | 0.x | 01 2.3; 02 9.2, 9.3, 9.4, 9.7; 03 5.9 |
| IAM-003 | Must | Scope- and claims-based access control | 0.x | 01 2.3, 2.4; 02 10.1–10.5, 23.3; 03 5.6 |
| IAM-004 | Must | Centralize AuthN/AuthZ in the gateway | 0.x | 01 2.3, 2.4; 02 9.8, 9.10, 10.2; 03: to be added in Draft 4 |
| IAM-005 | Must | Forward trusted caller context; prevent header spoofing | 0.x | 01 2.3; 02 7.4 (slot 5), 9.8, 9.9, 9.10, 18.1; 03: to be added in Draft 4 |
| IAM-006 | Must | Client ID/secret or equivalent for service-to-service | 0.x | 01 2.1, 2.3; 02 5.2, 9.2, 9.6, 9.7; 03 5.7 |
| IAM-007 | Must | Execute APIs in the context of an authenticated Okta user | 0.x | 01 2.3, 2.20; 02 9.8, 9.10, 10.3, 10.5; 03: to be added in Draft 4 |
| IAM-008 | Must | Resilience during Okta/IdP outages | 0.x | 01 2.3, 2.17; 02 9.3, 9.4, 9.12 (persisted JWKS, grace cache, failure modes, break-glass, runbook), 13.2, 13.4, 14.5, 31.2; persisted JWKS, grace TTL, per-route failure mode, runbook to be added in Draft 4), 14.5; 03 5.9 (IdP health to be added in Draft 4) |
| SEC-001 | Must | API key management | 0.x | 01 2.5, 2.11; 02 9.5, 12.5, 15.2; 03 5.7, 6.3 |
| SEC-002 | Must | Secure storage and handling of keys and credentials | 0.x | 01 2.5, 2.18; 02 9.5, 9.11, 12.4, 18.2, 18.3, 18.5; 03 4.6 |
| SEC-003 | Must | Automate access requests via ServiceNow or similar | 1.0 | 01 2.6; 02 14.13, 24.3–24.7; 03: to be added in Draft 4 |
| SEC-004 | Must | Classify APIs/data to drive approval routing | 1.0 | 01 2.6; 02 24.1, 24.2, 23.3; 03: to be added in Draft 4 |
| SEC-005 | Must | Restrict access by IP range | 0.x | 01 2.4; 02 8.8, 10.1, 23.5; 03 5.4, 5.6 |
| DEV-001 | Must | Developer portal | 1.0 | 01 2.11; 02 25.1–25.4, 25.6 (0.x preview subset in 25.1); 03: to be added in Draft 4 |
| DEV-002 | Must | Searchable API catalog | 1.0 | 01 2.11; 02 25.5, 26.8; 03: to be added in Draft 4 |
| DEV-003 | Must | Portal login and access control via Okta | 1.0 | 01 2.11; 02 24.3, 24.8, 25.4, 5.6; 03: to be added in Draft 4 |
| DEV-004 | Must | Integrated lifecycle tooling | 1.0 | 01 2.8, 2.11, 2.12; 02 26.1–26.3, 26.9; 03: to be added in Draft 4 |
| DEV-005 | Must | Sandbox and testing capability | 1.0 | 01 2.11, 2.12; 02 25.7, 26.6, 26.7; 03: to be added in Draft 4 |
| DEV-006 | Should | AI-assisted API generation and doc validation | post-1.0 | 01 2.20; 02 14.14 (LLM provider integration reused in 1.1), 26.3; 03: to be added in Draft 4 |
| DEV-007 | Must | Create and ingest OpenAPI YAML | 1.0 | 01 2.1, 2.11; 02 15.7, 26.9, 16.1; 03: to be added in Draft 4 |
| DEV-008 | Must | Centralized data dictionary / API index | 1.0 | 01 2.11; 02 26.8, 25.5; 03: to be added in Draft 4 |
| API-001 | Must | Versioning and routing between versions | 0.x | 01 2.1, 2.8; 02 5.6, 6.9, 27.1, 27.2; version entity and strategies to be added in Draft 4); 03 5.4 |
| API-002 | Must | Endpoint deprecation | 0.x | 01 2.8, 2.11; 02 26.5, 27.3, 27.4, 27.5; 03: to be added in Draft 4 |
| API-003 | Must | Low-code endpoint composition | 1.0 | 01 2.9; 02 28; 03: to be added in Draft 4 |
| API-004 | Must | Function-based and microservice architectures | 0.x | 01 2.2; 02 8.1–8.5, 8.10, 17; 03 5.3 (Lambda upstream type to be added in Draft 4) |
| API-005 | Must | AWS Lambda as API backend | 0.x | 01 2.2; 02 8.10; 03: to be added in Draft 4 |
| API-006 | Must | Containerized APIs with discovery and scaling | 1.0 | 01 2.2; 02 2.4, 8.2, 8.5, 8.11; discovery to be added in Draft 4); 03 5.3 |
| API-007 | Must | Multiple environments and promotion pipelines | 1.0 | 01 2.12; 02 6.10, 16.3, 34; 03 6.5 (linked environments and bundles to be added in Draft 4) |
| API-008 | Future | GraphQL support | roadmap | 01 2.22; 02/03: roadmap only, no Draft 4 content |
| API-009 | Must | No dependency on SOAP | 0.x | 01 2.18, 2.22; 02 3.1, 3.2; 03: no change needed |
| TRAF-001 | Must | Rate limiting, throttling, quotas by many dimensions | 0.x | 01 2.7; 02 11.1–11.4, 11.6; 03 5.8 |
| TRAF-002 | Must | Reliable, observable enforcement | 0.x | 01 2.7, 2.14; 02 11.3, 11.5, 13.2, 20.5; 03 5.2 |
| TRAF-003 | Must | Connection pooling | 0.x | 01 2.2; 02 8.1 |
| TRAF-004 | Must | Encrypted, secure gateway caching | 1.0 | 01 2.7; 02 29, 24.1; 03: to be added in Draft 4 |
| TRAF-005 | Must | Large payloads and streaming | 0.x | 01 2.2; 02 8.3, 8.6, 8.9 |
| TRAF-006 | Must | Secure storage and async retrieval of generated files | 1.0 | 01 2.10; 02 30, 18.5; 03: to be added in Draft 4 |
| TRAF-007 | Must | Async processing for long-running requests | 1.0 | 01 2.10; 02 30.6, 30.7; 03: to be added in Draft 4 |
| OBS-001 | Must | Configurable logging, monitoring, alerting integrations | 0.x | 01 2.13; 02 12.1–12.3, 12.8, 31; 03 5.14.1 |
| OBS-002 | Must | Centralized logging/monitoring with external tools | 0.x | 01 2.13, 2.14; 02 12, 13; 03 5.2, 5.11 |
| OBS-003 | Must | Datadog integration | 0.x | 01 2.13, 2.14; 02 31.3, 31.6; 03: to be added in Draft 4 |
| OBS-004 | Must | Real-time monitoring | 0.x | 01 2.14; 02 12.6, 15.9; 03 5.2 (per-API views to be added in Draft 4) |
| OBS-005 | Must | Track and report 4xx and 5xx rates | 0.x | 01 2.13, 2.15; 02 12.2, 12.3.2, 13.2, 31.2; 03 5.2 |
| OBS-006 | Must | Measure and report response times | 0.x | 01 2.13, 2.14; 02 12.3.2, 13.2, 31.2; 03 5.2 |
| OBS-007 | Must | Robust, customizable reporting | 1.0 | 01 2.15; 02 13.5, 27.4, 32; 03: to be added in Draft 4 |
| OBS-008 | Must | Monitoring aligned with InfoSec | 1.0 | 01 2.16; 02 12.4, 12.5, 14, 18, 33; 03 5.12 |
| POL-001 | Must | Hierarchical configuration with inheritance | 0.x | 01 2.4; 02 10.2, 23.1–23.5; hierarchy to be added in Draft 4); 03 5.6 |
| POL-002 | Must | Policies at API, path, and endpoint levels | 0.x | 01 2.4; 02 6.4, 23.4, 23.6, 23.7; 03 5.6 (effective policy view to be added in Draft 4) |
| POL-003 | Must | Manage endpoint configuration through an API | 0.x | 01 2.12; 02 15, 16; 03 5.13 |
| POL-004 | Must | Controlled promotion across environments | 1.0 | 01 2.12; 02 6.8, 6.10, 16.1–16.3, 34.5; 03 6.5 (linked-environment promotion to be added in Draft 4) |
| GOV-1 | Governance | All auth, key, secret, and policy actions auditable | 1.0 | 01 2.5, 2.6, 2.16; 02 12.5, 15.6, 33.2; 03 5.12 |
| GOV-2 | Governance | Prevent spoofing of forwarded identity context | 0.x | 01 2.3; 02 7.4, 9.8, 9.9, 18.1 |
| GOV-3 | Governance | Least-privilege access controls at all levels | 1.0 | 01 2.3, 2.4, 2.11, 2.17; 02 10, 15.6, 23.2; 03 2 (API Owner and Developer roles to be added in Draft 4) |
| GOV-4 | Governance | Classification used in governance and approval workflows | 1.0 | 01 2.6; 02 24.3–24.7; 03: to be added in Draft 4 |
| GOV-5 | Governance | Sensitive-data handling in caching, logging, monitoring, reporting | 1.0 | 01 2.7, 2.13, 2.15, 2.16; 02 12.4, 24.1, 26.8, 29.3 |
| GOV-6 | Governance | Observable policy enforcement and operations | 0.x | 01 2.4, 2.13, 2.14, 2.16; 02 12.2, 13.2, 14; 03 5.2, 5.15 |

**Partly delivered in 0.x** (the 1.0 ID is completed in 1.0):

| ID | In 0.x | Deferred to 1.0 |
|---|---|---|
| DEV-001/002/003 | The portal and catalog preview: Okta login, group and user visibility, search and facets, rendered OpenAPI docs, basic OpenAPI import | Publication workflow, try-it, sandbox, export and diff |
| DEV-007 | Deprecation headers and the version model | Lifecycle tooling, notices |
| POL-004 | Classification as a free tag (H-13) | Default policies by classification |
| GOV-1 / GOV-3 | Audit log and RBAC for the 0.x surface | Full coverage of the 1.0 actions and evidence export |

## 3. Owner defaults that apply only from 1.0 (accepted Oct 1, 2026)

These are recorded so 0.x code doesn't paint 1.0 into a corner. Design for them, but don't build them in 0.x unless a milestone says so.

| Topic | Default | Source question |
|---|---|---|
| Quota calendar | **UTC** month and day boundaries | 02 §35.1 Q2 |
| FIPS | **After 1.0** | 02 §35.1 Q4 |
| Upstream HTTP/2 | **None until gRPC** | 02 §35.1 Q5 |
| ServiceNow | **Washington DC release and later** | 02 §35.1 Q12 |
| Source-IP data (anomaly) | **Kept 24 h by default, configurable up to 7 days** | 02 §35.1 Q14 |
| Alertmanager | **Through the generic webhook**, not a dedicated integration | 02 §35.1 Q15 |
| Dark mode | **After 1.0** | 03 §10 |
| Live metrics | **2 h at 10 s resolution, and 24 h at 1 min** | 03 §10 |
| Tablets | **Read-only and untested** | 03 §10 |
| Production script lock to Git | **Available, off by default** | 03 §10 |
| Try-it against production | **Per API, off by default** | 03 §10 |
| Portal branding | **Name, logo, accent color, footer.** The 0.x preview may ship name and logo only. | 03 §10 |
| Automation Managers | **Cannot approve traffic actions** | 03 §10 |
| LLM providers | **Decided at 1.1** | 02 §35.1 Q13 |

Two owner defaults apply to 0.x as well: **API Owners can publish and deprecate but not retire** (only Operators and Admins retire), and **navigation opens the active group plus as many groups as fit, remembered per user**.

## 4. Effort (01 §4.3, estimates only)

- **1.0** is about **2.2–2.7×** Draft 3's MVP, central about 2.4×.
- **0.x** alone is about **1.3–1.5×**, central about 1.4×.
- **1.1** is about **0.1–0.15×**.
- The domain model and the policy compiler are on the critical path; staff them first. The portal, reporting, composites and anomaly work can run in parallel.
