# Open questions: status

**Relevance:** 0.x and 1.0. **Sources:** 02 §35.1 (Q1–19), 03 §10 (Q1–9 and the resolved list), 01 §7, and the owner decisions of Oct 1, 2026 ([decisions.md](decisions.md)).

Every question in the source docs is now **resolved**: by the owner on Oct 1, 2026, by an earlier draft, or by an owner default. §4 lists the items that remain pending outside the docs. If you hit a new ambiguity, follow the AGENTS.md rule: use the documented default and log an `A-n` entry in decisions.md.

## 1. 02 §35.1

| # | Question (short) | Resolution | Applies to |
|---|---|---|---|
| 1 | Per-IP pre-authentication rate limiting in the MVP? | **Yes: in 0.x** (D-OCT1-P1; design in H-3) | 0.x, M5 |
| 2 | Quota time zone | **UTC** calendar boundaries (D-OCT1-L1). A per-plan time zone is not needed. | 0.x behavior; confirmed for 1.0 |
| 3 | TOTP for local admin accounts | **Required** (D-OCT1-P2, H-5) | 0.x, M4/M8 |
| 4 | FIPS 140-3 | **After 1.0** (D-OCT1-L2). Keep the crypto provider swappable. | post-1.0 |
| 5 | Upstream HTTP/2 | **None until gRPC** (D-OCT1-L3) | post-1.0 |
| 6 | Evaluation mode limits | **1 gateway node, all features, no time limit** (D-OCT1-P3) | 0.x, M10 |
| 7 | Okta custom domains in the wizard | **Accept any domain** (D-OCT1-P4), through the SSRF guard and HTTPS (H-14) | 0.x, M4 |
| 8 | Internal JWT | Resolved in Draft 4: optional, in 1.0. Built in 0.x per 01 §4.1. | 0.x |
| 9 | Product name | Resolved in Draft 4.1: BeFive. **The trademark check is a pre-launch item** (§4). | — |
| 10 | Automation Manager role | Resolved in Draft 4.1 | 1.0 |
| 11 | Traffic actions in 1.0 | Resolved in Draft 4.1: IP block and rate-limit tightening in 1.0; the others in 1.1+ | 1.0 |
| 12 | ServiceNow releases | **Washington DC and later** (D-OCT1-L4) | 1.0 |
| 13 | LLM defaults and providers | **Providers decided at 1.1** (D-OCT1-L14). Prompt retention conflicts (30 vs 90 days, H-18); decide at 1.1. | 1.1 |
| 14 | Source-IP retention | **24 h default, up to 7 days configurable** (D-OCT1-L5) | 1.0 |
| 15 | Alertmanager format | **Through the generic webhook** (D-OCT1-L6) | 1.0 |
| 16 | Okta app client auth | Resolved Oct 1: `private_key_jwt` only, with public-key upload by default. **Extended Oct 1:** generated private keys are deleted after the one-time download, or after 24 h (D-OCT1-KEYS). | 0.x (keys), 1.0 (app creation) |
| 17 | Preview reach | Resolved Oct 1: partners may reach it from outside. Internet-exposure review in M10. | 0.x |
| 18 | Deprecation emails | Resolved Oct 1: 1.0 | 1.0 |
| 19 | Rate-limit headers | Resolved Oct 1: the IETF headers are the default | 0.x |

## 2. 03 §10

| # | Question (short) | Resolution | Applies to |
|---|---|---|---|
| 1 | Dark mode | **After 1.0** (D-OCT1-L7). Use design tokens so it can be added later. | post-1.0 |
| 2 | Live metrics retention split | **Accepted: 2 h at 10 s, and 24 h at 1 min.** Longer ranges come from Reports (D-OCT1-L8). | 0.x console (live overview); Reports in 1.0 |
| 3 | Tablet support | **Read-only and untested** (D-OCT1-L9) | 1.0 |
| 4 | Lock scripts and rules to Git in prod | **Available, off by default** (D-OCT1-L10) | 1.0 (M16) |
| 5 | Navigation default state | **Active group plus as many as fit, remembered per user** (D-OCT1-P7) | 0.x, M8 |
| 6 | API Owner and Retire | **API Owners publish and deprecate but do not retire.** Operators and Admins retire (D-OCT1-P6). | 0.x RBAC; 1.0 role UI |
| 7 | Try-it against production | **Per API, off by default** (D-OCT1-L11) | 1.0 |
| 8 | Portal branding depth | **Name, logo, accent color, footer.** No custom CSS or pages (D-OCT1-L12). The 0.x preview may ship name and logo (H-15). | 0.x/1.0 |
| 9 | Who approves traffic actions | **Automation Managers cannot approve.** Administrators and Operators approve (D-OCT1-L13). | 1.0 |

## 3. 01 §7

No 01 questions remain open; 01 §7 records the Oct 1 resolutions, including the generated-key retention entry added in update 3.

## 4. Still pending (not design questions; tracked here)

| ID | Item | Default until resolved | Owner action |
|---|---|---|---|
| P-1 | **A real Okta developer org** for nightly integration tests | Mock IdP with an Okta profile (D-OCT1-BUILD-11) | Provide the org and a least-privilege API token through CI secrets |
| P-2 | **An AWS test account** (ECS/ALB deploy, KMS, Lambda, CloudWatch recipes) | LocalStack; the recipes are validated statically | Provide the account and an OIDC role for GitHub Actions |
| P-3 | **Trademark and search-confusion check** for "BeFive" (01 decision 6) | Keep the name. Derived names (`b5ctl`, `b5k_`, `X-BeFive-*`, `BEFIVE_*`, `befive.*`) are centralized so they can be changed mechanically. | Before launch |
| P-4 | **LLM prompt retention** (30 vs 90 days) | Not built before 1.1 | Decide at 1.1 |
| S-2 | **Spec → JSON Schema/OpenAPI:** spec-tools (via `reitit.coercion.spec`) or the in-house `befive.schema.json-schema` | Start with `reitit.coercion.spec` and spec-tools. Replace with the in-house mapper only if spec-tools' output or maintenance is inadequate, and log an A-n entry. | Optional review |
| P-5 | **malli references in the source design docs** (01, 02, 03) are not yet rewritten to clojure.spec. The handoff docs supersede them (D-OCT1-SPEC). | Follow the handoff docs | Optionally have the design docs edited |
| P-6 | **BoringSSL / netty-tcnative evaluation** (02 Appendix A 19) | JDK TLS provider | Evaluate after 0.x load tests |
| P-7 | **LGPL and MPL dependency policy.** The owner banned only GPL and AGPL. | Allowed, but flagged in the PR and listed in the third-party list | Confirm with counsel |
