# Web console

**Relevance:** 0.x: partial (milestone 8, "console basics"). Screens marked 1.0 are **absent** in 0.x. **Sources:** 03 §1–§9 (principles, roles, IA, patterns, screens, flows, visual design, frontend architecture, mockups). 01 §2.17, decision 29. 02 §15.6 (RBAC is authoritative).

## 1. Principles (03 §1)

- **One object, three views.** Every object has a form, an EDN view and a JSON view. The EDN is exactly what the CLI uses.
- **Show the effect.** Show live traffic, node rollout and usage counts next to settings.
- **Safe by default, fast when sure.** Use the confirmation levels in §5.
- **Verify before you ship.** The route tester, IdP test and config diff are first-class.
- **Dense but calm.** Color carries status only.
- **Public Admin API only.** No hidden endpoints.
- **No dead ends.** Empty states always point to the next step.
- **Works air-gapped.** No CDNs, web fonts, analytics or external calls.
- **Show where a policy comes from.** Source chips and locks.
- **Ship what is ready.** In 0.x, 1.0 screens are absent. In 1.0, 1.1 features show as muted "Available in 1.1" placeholders.

## 2. Roles in the UI (03 §2)

- Personas are listed in overview.md. Permissions are the **union of roles**, fetched once per session from `GET /admin/v1/session`.
- Navigation shows every section to every role (read access is universal). API Owners get a default "My APIs | All" filter.
- Actions a user cannot perform are **hidden**. On detail pages, show a disabled button with a tooltip naming the missing role or scope.
- Forms open read-only for users without edit rights. Locked policy fields are read-only except at the level that set the lock.
- **Conflict resolved:** 03 §2.2 shows Operators editing "Policies at global and environment level". 02 §15.6 and §23.2 restrict **global** attachments and locks to Administrators. The API is the enforcement point, so 02 wins: Operators edit at environment level and below.
- Owner decision: **API Owners publish and deprecate, but do not retire.** Retire is Operator or Administrator only (closes 03 §10 Q6).

## 3. Information architecture (03 §3)

- **Navigation groups:** Overview, then **APIs**, **Traffic**, **Access & governance**, **Anomalies** and **Operations** (with Settings inside Operations).
  - The 03 §3.3 text says "28 entries in six groups"; the map shows Overview plus five groups. Treat Overview as the sixth group.
  - Left sider: navy `#0b1f33`, 224 px, collapsible to 64 px.
- **Group collapse (owner decision; closes 03 §10 Q5):**
  - The group containing the current page is always open, plus **as many other groups as fit** in the viewport height.
  - The open/closed state is **remembered per user**: local storage keyed by user id; a server-side preference can come later.
  - A collapsed group shows the sum of its badges.
- **0.x navigation:**

| Group | 0.x entries | 1.0 adds |
|---|---|---|
| Overview | Overview | — |
| APIs | APIs (list/detail, versions, operations, basic OpenAPI import, effective policy, claims-rule editor) | Composites, Data dictionary, Classification |
| Traffic | Services & upstreams (HTTP, Lambda), Routes, Policies (global/environment), Route tester | Caching, Async jobs, discovery |
| Access & governance | Organizations → Consumers → Applications (keys, client IDs), Plans, Identity providers (Okta wizard, IdP health), Certificates | Access requests, subscriptions |
| Anomalies | (absent) | Incidents, Detectors & rules, Silences, Approvals & overrides |
| Operations | Gateway nodes, Audit log (with verification), Config as code (export/import/diff/apply/history/rollback), Environments (badge only), Settings | Reports, Environments (linked + promotion), Evidence export |
| Settings sections (0.x) | Admin users & API tokens, Single sign-on & role mapping (Okta preset), License, Secrets & keys (incl. internal JWT keys), Logging & EMF, Telemetry sinks, Defaults & security (incl. pre-auth IP limit, rate-limit header style), Developer portal (Off / Preview) | Integrations, Anomaly & automation, Retention & evidence, Okta app creation |

- **URLs (03 §3.2):** `reitit-frontend`, HTML5 history. Examples:
  - `/apis/:api/:tab`
  - `/apis/:api/versions/:version/operations/:op/:tab` (tabs `overview`, `effective-policy`, `policies`, `schema`, `traffic`, `consumers`, `edn`)
  - `/routes/:id/edit`, `/tester?operation=…`, `/identity-providers/new/okta`, `/config/revisions/:rev`, `/settings/:section`, `/setup`, `/login`
  - Filters live in query params, coerced and validated with **spec**.
- **Top bar (03 §3.3):**
  - Environment badge (red = production, amber = test, blue = dev/sandbox). The badge color is repeated in every confirmation.
  - Cluster status ("6 of 6 nodes in sync · revision 1843").
  - Global search (`/`).
  - Notifications and user menu.
- **System banners:** DB unavailable, Okta unreachable with the JWKS age, license warnings.
- **Shortcuts:** `/` search, `g o` / `g p` / `g r` / `g c` / `g a` go to sections, `n` new, `e` edit, `Ctrl/Cmd+S` save, `Ctrl/Cmd+Enter` run the tester, `?` help.

## 4. Shared patterns (03 §4)

- **Lists:** Ant Design `Table size="small"`, sticky header, server cursor pagination, a debounced filter bar, bulk actions through `POST /config/apply` (one revision), column chooser, live columns from SSE.
- **Forms:**
  - Validated as the user types (200 ms debounce) **with the same shared clojure.spec specs as the server**. Messages come from `explain-data` mapped through the message table. Server 422 errors map back by path. No rule is ever written twice. The source says "malli"; see validation-clojure-spec.md.
  - Defaults show as placeholders ("30000 (default)"), from the form-metadata registry.
  - An "Unsaved changes" tag.
  - Saves send `If-Match`. A 412 opens a conflict dialog: Review differences, Overwrite or Discard.
  - After a save, a toast says "Saved as revision N · applying to M nodes".
- **EDN tab:** CodeMirror 6 with live parse and validation. "Apply to form" writes the EDN back into the form.
- **Confirmations:**
  - Inline: reversible changes.
  - `Popconfirm`: low-impact changes.
  - `Modal.confirm` with an impact summary: delete, revoke or suspend.
  - **Type-to-confirm:** high impact, such as deletions applied to production or disabling local sign-in.
  - Deleting a referenced object is **blocked**, not confirmed.
- **States:** skeletons on load and never a blank page. Every error carries the request ID.
- **Secrets:** write-only ("Stored encrypted · last changed …"). One-time values (API keys, tokens) appear in a modal that cannot be dismissed until the user confirms they stored the value. They live in component-local state only, never in app-db.
- **Policy display (03 §4.9):** source chips, lock icon with the lock source, inherited placeholders, struck-through overrides.

## 5. 0.x screens (03 §5) and mockups

| Screen | 03 § | Mockup |
|---|---|---|
| Sign-in (SSO; local sign-in collapsed, visible only with `?local=1` when disabled; **TOTP step for local accounts**) | 5.1 | — |
| First-run setup (bootstrap token → license or evaluation → security check → first admin + **TOTP enrolment** → SSO → done) | 6.1 | — |
| Overview (KPIs, traffic, latency p50/p90/p95/p99, top routes, node sync, recent changes, IdP health; ranges up to 2 h at 10 s, 24 h at 1 min) | 5.2 | [overview.png](mockups/overview.png) |
| Services & upstreams (HTTP, Lambda) | 5.3 | — |
| Routes list and edit (anchored sections, spec-driven validation, live EDN preview, conflicts row) | 5.4 | [routes-list.png](mockups/routes-list.png), [route-edit.png](mockups/route-edit.png) |
| Route tester | 5.5 | — |
| Policies (global/environment levels) | 5.6 | — |
| Organizations, consumers, applications (credentials, client IDs; one-time reveal) | 5.7 | [consumer-detail.png](mockups/consumer-detail.png) (ignore the Subscriptions tab in 0.x) |
| Plans | 5.8 | — |
| Identity providers + Okta wizard (5 steps, derive, test, mapped identity; **any domain accepted**) | 5.9 | [okta-wizard.png](mockups/okta-wizard.png) |
| Certificates | 5.10 | — |
| Gateway nodes | 5.11 | — |
| Audit log (verification; evidence export is 1.0) | 5.12 | — |
| Config as code | 5.13 | — |
| Settings (0.x sections above) | 5.14 | — |
| APIs list and detail (versions, operations, docs, lifecycle, deprecation; publish and deprecate for owners, retire for Op/Admin) | 5.20 | — |
| OpenAPI import (basic in 0.x; the full diff preview is 1.0) | 5.21 | — |
| Operation detail and effective policy | 5.22 | [api-effective-policy.png](mockups/api-effective-policy.png) |
| Claims-rule editor | 5.23 | — |

The 1.0 screens are 5.15–5.19 (anomaly), 5.24–5.32 and their mockups 6, 7, 9, 10, 11 and 12. Flows: 6.1, 6.2, 6.3, 6.4, 6.8 (basic) and 6.11 apply to 0.x.

## 6. Visual design (03 §7)

- Ant Design 5 tokens. Fonts are served locally (Inter). Light mode only: **dark mode comes after 1.0** (owner default), and the design is token-driven so it can be added later.
- **WCAG 2.1 AA:**
  - Contrast of 4.5:1, focus rings, full keyboard support.
  - `aria-live` regions.
  - Charts offer "View as table".
  - Respect `prefers-reduced-motion`.
  - Usable at 200% zoom.
  - axe-core in Playwright.
- **Responsive:**
  - Minimum 1280 × 720, designed at 1440 × 900.
  - Below 1024 px, read-only screens still work. **Tablets: read-only, untested** (owner default).
  - Phones are not a target for the console.
- **Brand:**
  - The "transit" mark. Assets are in `/workspace/befive-logo/concept-3/` (`befive-brand.zip`) and are not part of this package. The mockup copies are in `mockups/src/assets/`.
  - Navy `#0b1f33`. Teal `#08979c` / `#13c2c2` is used for the mark only. The UI primary is Ant Design blue.

## 7. Frontend architecture (03 §8)

- **Stack:**
  - shadow-cljs, re-frame/Reagent (React 18), Ant Design 5 through thin `befive.console.ui.*` wrappers, ECharts, and CodeMirror 6 (`@nextjournal/lang-clojure`).
  - Shared `.cljc` from `schema`: **specs, message table, form-metadata registry, defaults, the cross-entity validator, the diff engine, normalization**.
- **Code splitting:**
  - 0.x modules: `shell`, `charts`, `editor`, `tester`, `apis` and `governance`.
  - 1.0 modules: `automation`, `incidents`, `composite`, `traffic-extra`, `reports` and `environments`.
  - Target: `shell` under 900 KB gzip.
- **app-db (03 §8.3):** `:session :route :entities :lists :forms :cluster :live :tester :config :effective :ui`. Server documents are normalized under `:entities`. Forms hold drafts separately from entities.
- **Forms (03 §8.5, rewritten for spec):** a form is `{:spec <spec-key> :original doc :draft doc}`. Fields bind by path. Validation runs `s/explain-data` → `befive.schema.errors/explain->problems`, stored by path. Generated forms walk the spec through the **form-metadata registry**. Defaults come from `befive.schema.defaults`. Details in validation-clojure-spec.md §6.
- **API client:**
  - Fetch with Transit, `same-origin`, `X-CSRF-Token`, `X-Request-ID`, `If-Match` and a 30 s timeout.
  - Central handling: 401 → login, 403 → toast, 412 → conflict dialog, 409 → refresh the diff, 422 → field errors, 503 → banner.
  - GETs retry with backoff.
- **SSE:** one `EventSource` per tab. After 3 failures, fall back to polling.
- **i18n-ready:** `(tr :key {...})` with EDN dictionaries. Ships `en-US` only.
- **Serving:**
  - Hashed assets are immutable.
  - `index.html` is rendered per request with a CSP nonce and the environment name.
  - Strict CSP, `frame-ancestors 'none'`.
- **Tests:**
  - Unit tests for events and subscriptions.
  - Shared spec tests run on the JVM and in Node.
  - Playwright flows run against a `BEFIVE_ROLE=all` container with a mock IdP, plus axe-core.
  - Visual snapshots at 1440 × 900 and 1280 × 720.
