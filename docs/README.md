# BeFive handoff docs: index

These docs are distilled from the BeFive design set (`gateway-design/` 00–04, October 1, 2026). Each doc names its source sections and says how relevant it is to 0.x. Precedence: owner decisions (`decisions.md`) > 01 Draft 4.1 > 02 > 03 > mockups.

**Relevance legend:**
- **0.x: build** means the content is in the design-partner build.
- **0.x: partial** means part of the doc is built in 0.x and part waits for 1.0.
- **1.0: context** means the doc is not built in 0.x, but 0.x code must not block it.

| Doc | Relevance | Main sources |
|---|---|---|
| [overview.md](overview.md) | 0.x: build | 00, 01 §0–§2, 04 |
| [architecture-overview.md](architecture-overview.md) | 0.x: build | 02 §1–§4 |
| [data-plane-proxy-core.md](data-plane-proxy-core.md) | 0.x: build | 02 §7, §8 |
| [domain-model.md](domain-model.md) | 0.x: build (1.0 entities noted) | 01 §2.1, 02 §5, §6.9, §26, §27 |
| [identity.md](identity.md) | 0.x: build (Okta app creation is 1.0) | 01 §2.3, §2.5, 02 §9, §24.8 |
| [policy-hierarchy.md](policy-hierarchy.md) | 0.x: build | 01 §2.4, 02 §10, §23 |
| [rate-limiting-caching.md](rate-limiting-caching.md) | 0.x: build (caching is 1.0) | 01 §2.7, 02 §11, §29 |
| [admin-api-and-b5ctl.md](admin-api-and-b5ctl.md) | 0.x: build | 02 §15, §16, §17 |
| [config-revisions.md](config-revisions.md) | 0.x: build | 02 §6, §21 |
| [telemetry-logging.md](telemetry-logging.md) | 0.x: build | 01 §2.13–§2.14, 02 §12, §13, §31 |
| [console-ui.md](console-ui.md) | 0.x: partial | 03 §1–§9 |
| [developer-portal.md](developer-portal.md) | 0.x: partial (read-only preview) | 01 §2.11, 02 §25, 03 §5.33–§5.40 |
| [anomaly-response.md](anomaly-response.md) | 1.0: context | 01 §2.19, 02 §14, 03 §5.15–§5.19 |
| [governance-access-requests.md](governance-access-requests.md) | 1.0: context | 01 §2.6, 02 §24, 03 §5.25, §5.29 |
| [async-composites-lambda.md](async-composites-lambda.md) | 0.x: partial (Lambda in 0.x) | 01 §2.2, §2.9, §2.10, 02 §8.10, §8.11, §28, §30 |
| [environments-promotion.md](environments-promotion.md) | 0.x: partial (environment name, export/diff/apply) | 01 §2.12, 02 §34, 03 §5.31 |
| [security-infosec.md](security-infosec.md) | 0.x: build (InfoSec additions are 1.0) | 02 §18, §19, §33 |
| [deployment-docker.md](deployment-docker.md) | 0.x: build | 02 §3.2, §4, §20.3, §21 |
| [testing-strategy.md](testing-strategy.md) | 0.x: build | 02 §20, §22, 03 §8.10 |
| [validation-clojure-spec.md](validation-clojure-spec.md) | 0.x: build | Owner change (Oct 1), replacing malli in 02 §3, §5, §15, §21.3, 03 §8 |
| [clojure-style-guide.md](clojure-style-guide.md) | 0.x: build | Community Clojure Style Guide (CC BY 3.0), Simple Made Easy |
| [roadmap.md](roadmap.md) | All | 01 §2.20–§2.22, §4 |
| [0x-milestones.md](0x-milestones.md) | 0.x: build | 01 §4.2, §4.3 |
| [decisions.md](decisions.md) | All | 01 §5, 02 Appendix A, owner decisions of Oct 1 |
| [open-questions.md](open-questions.md) | All | 02 §35.1, 03 §10, 01 §7 |
| [mockups/](mockups/) | Reference | 03 §9 (14 PNGs plus `src/`) |

## Mockups (03 §9)

All mockups are 1440 × 900 PNGs with sample data. They are not pixel specs: the text wins over the images. In 0.x, the screens that need to be built are 1–5, 8, 13 and 14, and 13 and 14 only in their read-only preview form. The remaining screens are for 1.0.

| # | File | Screen (03 section) | Release |
|---|---|---|---|
| 1 | [overview.png](mockups/overview.png) | Overview dashboard (5.2) | 0.x |
| 2 | [routes-list.png](mockups/routes-list.png) | Routes list (5.4) | 0.x |
| 3 | [route-edit.png](mockups/route-edit.png) | Route edit with spec-driven validation and EDN preview (5.4) | 0.x |
| 4 | [okta-wizard.png](mockups/okta-wizard.png) | Okta connection wizard (5.9) | 0.x |
| 5 | [consumer-detail.png](mockups/consumer-detail.png) | Application detail with one-time key reveal (5.7) | 0.x (the subscriptions tab is 1.0) |
| 6 | [incident-detail.png](mockups/incident-detail.png) | Incident detail (5.16) | 1.0 |
| 7 | [anomaly-rules.png](mockups/anomaly-rules.png) | Detectors and rules (5.17) | 1.0 |
| 8 | [api-effective-policy.png](mockups/api-effective-policy.png) | Effective policy (5.22) | 0.x |
| 9 | [composite-builder.png](mockups/composite-builder.png) | Composite builder (5.26) | 1.0 |
| 10 | [access-request-approval.png](mockups/access-request-approval.png) | Access request approval (5.29) | 1.0 |
| 11 | [report-builder.png](mockups/report-builder.png) | Report builder (5.30) | 1.0 |
| 12 | [promotion-wizard.png](mockups/promotion-wizard.png) | Promotion wizard (5.31) | 1.0 |
| 13 | [portal-catalog.png](mockups/portal-catalog.png) | Portal catalog (5.36) | 0.x preview (no subscription state, side rail or environment facet) |
| 14 | [portal-api-detail.png](mockups/portal-api-detail.png) | Portal API page (5.37) | 0.x preview (docs and changelog only; no try-it) |

`mockups/src/` holds the generators: `build.py`, `screens_d4.py`, `common.py`, `console.css` and `render.sh`, which renders with headless Chrome.
