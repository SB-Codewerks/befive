# Security architecture, licensing and InfoSec

**Relevance:** 0.x: build. The threat model, keyring, TLS, sessions, SSRF guard, supply chain, licensing and audit are built in 0.x. Retention settings, legal hold and the evidence export are 1.0. **Sources:** 01 §2.16, §2.18, decisions 5, 17. 02 §18 (security), §19 (licensing), §33 (InfoSec), §12.4–§12.5. 04 SEC-002, GOV-1..6. Owner decisions of Oct 1.

## 1. Threat model (02 §18.1)

The STRIDE summary lives in 02 §18.1. Build its mitigations in as you go:
- Spoofed identity headers are stripped in slot 5.
- Token forgery is countered by the alg allow-list, `kid` checks, and no HMAC for JWKS issuers.
- Config tampering is detected by the hash-chained audit log and RBAC.
- Secret disclosure is countered by envelope encryption and write-only APIs.
- SSRF is blocked by the safe client.
- DoS is limited by the pre-auth IP limiter, size limits, timeouts, bounded queues and the WAF.
- Portal enumeration is prevented by visibility filtering in SQL.

## 2. Secrets: envelope encryption (02 §18.2)

- **KEK source** (`BEFIVE_MASTER_KEY_SOURCE`):
  - `env`: `BEFIVE_MASTER_KEY`, 32 bytes base64.
  - `file`: `BEFIVE_MASTER_KEY_FILE`, default `/run/secrets/befive-master-key`.
  - `aws-kms`: `BEFIVE_KMS_KEY_ARN`, using `GenerateDataKey` and `Decrypt`. The key never leaves KMS. **Recommended on AWS.**
- **DEKs** are AES-256, stored wrapped in `keyring`. One is active; older ones are decrypt-only.
- **Secret values** use AES-256-GCM with a 96-bit random nonce and **AAD `"secret:" + name + ":" + version`**. Stored as `{"v":1,"dek":"dek-3","nonce":…,"ct":…}`.
- Plaintext exists only in memory, inside the objects that need it (the RouteTable, integration clients).
- **Rotation:**
  - DEK: every 90 d plus on demand (`POST /keyring/rotate`). Background re-encryption goes through ordinary config writes.
  - KEK: run with the previous and new keys side by side, then rewrap.
- **First run:** without a master key, the node **refuses to start in production mode**. In evaluation mode (`BEFIVE_EVAL=true`) it generates `/var/lib/befive/master.key` and logs a loud warning.

## 3. TLS (02 §18.3)

- TLS 1.3 and 1.2 only. On 1.2: ECDHE with AES-GCM or ChaCha20 only; no RSA key exchange, no CBC, no renegotiation.
- The JDK provider is used. BoringSSL is an optional later evaluation.
- Certificates: RSA 2048 or more, or ECDSA P-256/P-384. The upload is rejected if the certificate is expired, the key doesn't match, or the key is weak. Warn 30 d before expiry.
- HSTS is set over HTTPS. Upstream TLS verification is on by default.
- **FIPS 140-3 is after 1.0** (owner default).

## 4. Sessions and admin accounts (02 §18.4)

See identity.md §11. In short:
- `__Host-` cookies with `SameSite=Strict`, 30 min idle / 12 h absolute, and a CSRF token.
- Local accounts use Argon2id, the breach list, and a lockout of 10 failures → 15 min. **TOTP is required.**
- The bootstrap token gates first-run setup.
- Strict CSP.

## 5. SSRF guard (02 §18.5)

`befive.core.http/safe-client` is used for IdP URLs, tests, plugins, integrations and OpenAPI URL import:
- HTTPS only. Plain HTTP is allowed only for allow-listed dev hosts.
- **Resolve once and check the resolved address used for the connection**, which defeats DNS rebinding. Deny loopback, link-local (including `169.254.169.254` and `fd00:ec2::254`), unique-local and private ranges unless they appear in `:security :outbound-allow-cidrs`, plus multicast and unspecified addresses.
- No redirects for JWKS, introspection or integrations. Discovery may follow at most 3 same-host redirects.
- 1 MiB response cap and a 5 s timeout. OpenAPI URL import gets a 10 MiB cap.
- Health checks use upstream targets, which are trusted configuration, so they skip the private-range rule.
- The **Okta wizard accepts any domain** (owner decision), but every fetch still goes through the guard.

## 6. Supply chain (02 §18.6), plus the owner's builder defaults

- **Pinned exact versions** (`deps.edn` plus the npm lockfile). antq and `npm audit` reports run weekly.
- **License policy:** the product is proprietary. **CI fails on GPL or AGPL dependencies**, including transitive ones. LGPL, MPL and EPL are flagged for review. Each release ships a third-party license list (`THIRD_PARTY_LICENSES.md`) generated from the resolved tree for both JVM and npm.
- **SBOM:** CycloneDX for the uberjar and Syft for the image, published with each release.
- **Scanning:** Trivy on every image build. Fail on fixable critical findings.
- **Signing:** cosign with a **key pair** (so air-gapped customers can verify), SLSA provenance, and signed SHA-256 checksums for the CLI and tarballs.
- **Base image:**
  - A minimal glibc image with a jlink'd Temurin 21 runtime.
  - UID 10001, no shell, read-only root filesystem.
  - A `-debug` variant with a shell is published for troubleshooting.
- **Registry:** GHCR during development, then **ECR before partners** receive builds.

## 7. Licensing (02 §19)

- **Policy:** perpetual use of any build released on or before `maintenance-expires`.
  - Only the embedded **build date** is compared, never the clock.
  - An uncovered newer build refuses to start the gateway role (exit code 78) and the control plane runs in license-only mode.
- **File:** JSON `{format 1, key-id, payload (base64url JSON), signature (Ed25519 over the payload bytes)}`.
  - Payload fields: `license-id`, `customer`, `edition`, `type` (perpetual|trial), `issued-at`, `maintenance-expires`, `max-gateway-nodes`, `environments`, `features`, plus `use-expires` for trials.
  - Verified with JDK `Ed25519`. Public keys are embedded by key-id. Never contact the network.
- **Behavior:**
  - Exceeding `max-gateway-nodes` keeps serving, with a warning.
  - An expired trial puts the config into read-only mode (`403 license`).
  - A tampered file is treated like having no license.
- **Evaluation mode** (owner decision): **1 gateway node, all features, no time limit**. Extra nodes stay not-ready with a log message, and the console shows an "Evaluation - unlicensed" banner.
- 0.x milestone 10 implements and tests the license and evaluation behavior. Vendor signing tooling is a small internal CLI, `befive.license.tool`, kept out of the product jar.

## 8. Audit (02 §12.5, §33.2)

- Every mutation is audited by construction.
- The log is hash-chained, with verification through `POST /admin/v1/audit-events/verify`.
- **Actor types:** `user`, `token`, `system`. 1.0 adds `developer`, `rule` and `link`.
- **Critical events:** break-glass login, disabling local sign-in, master-key rotation, and enabling generated Okta keys (1.0).
- Retention: see telemetry-logging.md §5 (H-10).

## 9. InfoSec additions: 1.0 (02 §33)

- **Retention settings** (`:retention`):
  - audit: 2555 d, minimum 365
  - rollups: 1 m for 8 d, 1 h for 13 months
  - signals: per 02 §14.4
  - **source IP: 24 h by default, at most 7 d** (owner default)
  - job results: at most 2160 h
  - report files: 30 d
  - access requests: 1095 d
  - portal sessions: 30 d
  - config changes: 365 d
  - LLM prompts: 30 d (1.1; see open-questions.md about the 30 vs 90 d conflict)
  - The retention job runs hourly and writes one audit event per run.
- **Legal holds** (`POST /admin/v1/retention/holds`).
- **Audit checkpoints:** signed events that keep the hash chain verifiable across deletions.
- **Evidence export:**
  - Contents: manifest plus Ed25519 signature, configuration, effective policies, audit events with verification, key inventory (metadata only), access requests, roles, retention settings, security events.
  - Produced by `b5ctl evidence export|verify`.
  - The public key is published at `/.well-known/befive/evidence-key`.
- **Dictionary- and classification-driven masking** wherever bodies are recorded.

## 10. Okta client keys (1.0): the owner's custody rule

Developer-uploaded public keys are the default. Generated keys are an Admin-only opt-in, never for Restricted APIs, and **deleted from the secrets store right after the one-time download, or after 24 h if never downloaded**. See identity.md §12.
