# Product overview

**Relevance:** 0.x: build. **Sources:** 00 (customer requirements), 01 §0–§2, §2.22, 04 §1–§4.

## What BeFive is

BeFive is a commercial, self-hosted enterprise API gateway written in Clojure. It is sold under a perpetual license with optional maintenance, and the license is verified offline (01 decision 5). It competes with WSO2 API Manager, Red Hat/IBM 3scale, Kong and Apigee for buyers who need the following (00):
- Okta-first identity: OAuth 2.0/OIDC, scopes and claims, client credentials, user context, resilience during an IdP outage.
- Centrally enforced, hierarchical policy (global → environment → API → version → path → operation) with locks.
- Rate limits and quotas on many dimensions, with standard headers.
- A developer portal and catalog, OpenAPI lifecycle, sandbox, data dictionary (1.0).
- Governance: classification-driven access requests through ServiceNow, audit, evidence (1.0).
- Observability into CloudWatch and Datadog, plus OTLP and Prometheus.
- AWS Lambda and container backends, streaming and large payloads, async jobs (1.0).
- Config as code with promotion across environments.

## Personas (03 §2.1)

| Persona | Role |
|---|---|
| Priya, platform admin | Administrator |
| Marco, API operator | Operator |
| Kim, API owner | API Owner (scoped to owned APIs) |
| Dana, partner manager | Consumer Manager |
| Sam, security auditor | Auditor |
| Omar, on-call engineer | Operator |
| Lee, automation engineer | Automation Manager (1.0 anomaly) |
| Devon, developer | Portal developer (not a console role) |

## Requirements

Source 00 lists **49 requirement IDs**: 47 Must, 1 Should (DEV-006) and 1 Future (API-008 GraphQL), plus six governance expectations (GOV-1..6). Every Must is in 1.0 or earlier. **29 are complete in 0.x.** DEV-001, DEV-002, DEV-003, DEV-007, POL-004, GOV-1 and GOV-3 are partly in 0.x (04 §4). The full ID-by-release table is in [roadmap.md](roadmap.md).

## Design-partner focus (0.x)

0.x targets design partners running **Okta-protected REST APIs on AWS behind an ALB** (owner decision, Oct 1, 2026). Optimize defaults, docs and the CloudWatch/Datadog recipes for that path first: ECS/EKS, ALB trusted proxies, Okta custom authorization servers, Lambda as an option.

The demo script (04 §3) is the 0.x acceptance theatre. These evaluation prompts must run end to end on 0.x:
1. Okta scopes and claims on an operation
2. Client credentials
3. User context and anti-spoofing
5. API key create, rotate, revoke and audit
7. Version routing and deprecation headers
8. IP rules, rate limits and quotas with logs and metrics
9. Policy inheritance with locks and config as code
10. Datadog
11. The streaming half of this prompt
12. Okta outage

Prompts 4 and 6 are partial, through the read-only portal preview and basic OpenAPI import.

## Product principles (02 §1, 03 §1)

- PostgreSQL is the single source of truth. The data-plane hot path never touches it.
- Config is plain EDN data with one shared spec set. The console, the CLI and the server validate and diff identically.
- Fail static: keep serving the last good configuration.
- Everything the UI can do, the API can do. The console uses only the public Admin API.
- Show where a policy comes from: the effective policy view gives provenance and locks.
- Ship what is ready and label what is next. In 0.x, 1.0-only features are absent, not disabled.
- No external calls from the browser. The product works offline and air-gapped.

## Explicit non-requirements (01 §2.22)

- SOAP is not supported (API-009 is satisfied by not depending on it).
- GraphQL is roadmap only.
- Monetization, multi-region and custom RBAC are deferred.
