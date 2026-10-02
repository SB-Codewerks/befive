# Environments, linked environments and promotion

**Relevance:** 0.x: partial. 0.x has the **environment name and badge**, plus **export, diff and apply between environments** with `b5ctl` and the console. Linked environments, signed bundles, overlays and the promotion wizard are **1.0**. **Sources:** 01 §2.12, decision 24. 02 §34 (linked environments, bundles, overlays, promotion), §6.10, §16.3. 03 §5.31, §6.5, §6.10, mockup `promotion-wizard.png`. 04 API-007, POL-004.

## Model (01 decision 24)

- **One BeFive cluster per environment** (Dev, Test, Prod, Sandbox). Each has its own control plane and PostgreSQL. Environments are never multi-tenant inside one database.
- `BEFIVE_ENVIRONMENT` and the `:environment` setting name the environment. The name drives the console badge color (production is red), the `{env}` part of Redis keys, the internal JWT issuer, and the EMF `Environment` dimension.

## 0.x path: config as code across environments

- `b5ctl export` from Test, commit the export to Git, then `b5ctl diff` and `b5ctl apply --env prod` against Prod.
- Environment-specific values go in `env/<env>.edn` variables (`#befive/var`), and secrets as `#befive/secret` references that must already exist in the target.
- Environment-level policy attachments are stored in the target environment's own configuration and are never exported. They are attached and locked by Operators or Administrators of that environment.
- CI templates: a GitHub Actions example that runs `b5ctl validate`, then `diff` (exit code 2 means "changes present"), then `apply --wait` with an automation token.

## 1.0: linked environments (02 §34)

- **Link registry and handshake:**
  - Uses port 9300 and the `/link/v1` routes.
  - Mutual authentication with Ed25519 link keys.
  - Each link has a role such as `promote-to`, and the links define the allowed directions: Dev → Test → Prod, and Dev → Sandbox.
  - The link also carries the catalog feed for the portal.
- **Signed bundles** (`.b5b`):
  - A deterministic zip containing `manifest.edn`, `manifest.sig` (Ed25519, signed by the link key or a trusted CI key), `config/*.edn` and `specs/<sha256>.json`.
  - Bundles never contain consumers, applications, credentials, subscriptions, secrets, certificates, runtime overrides or environment-level attachments.
  - The manifest lists the required `#befive/var` variables.
- **Overlays:**
  - `env_overlay`, one per environment, holding vars, environment-level attachments and settings.
  - Editable by Administrators and Operators.
  - Stored in `overlays/<env>.edn` in Git.
- **Promotion flow:**
  1. The source builds and signs a bundle.
  2. It sends a `POST /link/v1/proposals` to the target.
  3. The target verifies signatures, hashes, the feature level and the overlay vars, then validates and builds the diff: config diff, effective-policy diff, lock check, and spec breaking changes.
  4. Approval: one approval by default, **two for production targets**. Separation of duties applies: the bundle creator cannot approve. API Owners may request promotions but not approve them, and Operators cannot approve their own requests.
  5. The target applies the bundle as **one revision** with `source = promotion`.
  6. Rollback is the target's normal rollback.
- **CI path:** `b5ctl bundle create|sign|verify`, `b5ctl promote --bundle … --to prod`.
- **Failure handling:**
  - A queued outbox handles unreachable targets.
  - A bad signature produces `link.bundle_invalid` plus a critical audit event.
  - A feature-level mismatch is rejected.

## Sandbox (02 §26.6–§26.7, 1.0)

- A Sandbox cluster is linked to Prod and is the target of portal try-it.
- It runs with `:allow-design-routes` and OpenAPI-example mocks.
