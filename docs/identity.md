# Identity: Okta/OIDC, API keys, mTLS, identity forwarding, IdP resilience

**Relevance:** 0.x: build (milestone 4). Okta app creation and client keys are 1.0 (§9 below). **Sources:** 01 §2.3, §2.5, decisions 4, 15, 16, 17, 32. 02 §9 (all), §15.6 (sessions), §18.4, §24.8. 03 §5.9 (Okta wizard, `mockups/okta-wizard.png`), §5.7 (application detail, `mockups/consumer-detail.png`).

## 1. Model (02 §9.1)

A route or operation's `:security :authn` lists methods (`:jwt`, `:introspection`, `:api-key`, `:mtls`) with a mode (`:any` or `:all`, required or optional). Authentication resolves an **identity**: subject, client id, scopes, groups, claims, auth method, and the consumer, application and organization. Okta is the flagship, built on generic OIDC.

## 2. JWT with JWKS (02 §9.2–§9.3)

**Validation order** (any failure → 401 with the reason in the log):
1. Token at most 8 KiB (`jwt.too_large`).
2. Nimbus header parse. Reject `none` and any `alg` outside the IdP allow-list (HMAC is never allowed for JWKS IdPs).
3. Optional `typ` = `at+jwt` check (RFC 9068).
4. `kid` → key from the JWKS cache. `kty`/`crv`/`use` must be compatible. A token without a `kid` is allowed only if the set has exactly one compatible signing key.
5. Signature check.
6. Claims: `iss` exact; `aud` intersects the route or IdP audiences; `exp` required; `nbf`/`iat` not in the future; `clock-skew-ms` 60 s default, 300 s max.
7. Map claims (subject, client-id, scopes as a set from an array or a space-delimited string, groups).
8. Look up the consumer by `(idp-id, client-id)`. An unmapped client is allowed unless the policy requires a consumer.

Verification runs on the event loop. There is no verified-token cache in the MVP.

**JWKS cache:**
- Refresh every 1 h. An unknown `kid` triggers a refetch, rate-limited to once per 30 s per issuer.
- `stale-if-error` is 24 h by default, 7 days at most.
- Every good fetch is **persisted** to `idp_jwks_cache` (with an HMAC tag) and to the LKG dir. A tampered copy is ignored and logged as a security event.

## 3. Introspection (RFC 7662) (02 §9.4)

- Positive and negative caches. The positive TTL is capped at the token's `exp`; the negative cache is short and bounded.
- Circuit breaker: 5 consecutive failures open it for 10 s, then a half-open probe. The breaker also covers JWKS fetches.
- Client authentication uses a secret reference.

## 4. API keys (02 §9.5)

- **Format:** `b5k_<8 lowercase base32 prefix>_<43 base62 secret>` (32 random bytes). Register the pattern with GitHub secret scanning and with log redaction.
- **Storage:** `HMAC-SHA256(pepper, full-key)` plus `pepper_id`. The pepper is 32 random bytes, envelope-encrypted, and shipped to gateways in the snapshot.
- **Lookup:** prefix → in-memory map → HMAC → `MessageDigest/isEqual` → status and expiry. No DB access.
- **Lifecycle:**
  - Shown once.
  - Rotation sets the old key's `expires-at` to now plus a grace of 7 d by default (range 0–90).
  - At most **two active keys** per application.
  - Revocation takes effect at the next revision.
- **Transport:** `X-API-Key` by default. The query-parameter transport is off by default; if enabled, the parameter is stripped and never logged.
- **Pepper rotation:** old keys verify with their recorded `pepper_id`.

## 5. mTLS mapping (02 §9.6)

Map the client certificate (subject DN, SAN or fingerprint) to a credential. CA bundles are Certificate entities. Errors are `mtls.no_certificate` and `mtls.unmapped`. On AWS behind an ALB, mTLS needs NLB passthrough or ALB mTLS with the certificate header from a trusted proxy. Document this; 0.x focuses on JWT and keys.

## 6. Okta preset and wizard (02 §9.7, 03 §5.9)

- The input is an Okta domain plus an authorization server ID (default `default`).
- `POST /admin/v1/identity-providers/okta/derive` derives the issuer, discovery, JWKS and introspection URLs.
- **The org authorization server is rejected for JWT validation** because its tokens are not meant for resource servers.
- **The wizard accepts any domain** (owner decision). Custom domains are allowed, not only `*.okta.com` or `*.oktapreview.com`. All fetches still go through the SSRF guard and require HTTPS.
- The connection test (`POST /identity-providers/test` and `/{id}/test`) runs discovery, JWKS, an optional sample token, and the groups claim check, and reports actionable warnings.
- Client credentials: bind Okta client IDs to applications (credential type `:oauth-client`).
- User context (IAM-007): user tokens carry `sub`, groups and claims, which are forwarded.

## 7. Identity forwarded upstream and anti-spoofing (02 §9.8–§9.9)

- **Headers** (names configurable in Settings > Defaults & security):
  - `X-BeFive-Subject`, `X-BeFive-Client-Id`, `X-BeFive-Consumer`, `X-BeFive-Application`, `X-BeFive-Organization`
  - `X-BeFive-Scopes`, `X-BeFive-Groups`, `X-BeFive-Auth-Method`, `X-BeFive-Api`, `X-BeFive-Claim-<name>` (selected claims)
  - Optional pass-through of the original token (`:forward-token`, default false).
- **Strip order (§9.9):**
  1. Slot 5 removes every configured identity header, every `X-BeFive-*` header, and the internal JWT header from the inbound request, before any plugin or authn runs.
  2. Only after authn and authz does request-transform add the trusted values.
  3. Plugins cannot set identity headers.
- **Tests:** forged headers never reach the upstream, including case and duplicate variants.

## 8. Gateway-signed internal JWT (02 §9.10), 0.x

- An optional header `X-BeFive-Identity: <JWT>` per route or policy (`:internal-jwt`).
- **EdDSA (Ed25519) by default**, ES256 optional.
- Claims: `iss` (the BeFive environment), `aud` (upstream), `sub`, `client_id`, `scope`, `groups`, application, organization, `jti`, `iat`, `exp`. Lifetime is **120 s**.
- JWKS at `/.well-known/befive/jwks.json` on the ops port (and on the data plane if enabled).
- Rotation every 90 d. A new key is published 24 h before use. Old keys stay published until their tokens expire. Revocation is supported.
- Signing keys live in the keyring and are shown in Settings > Secrets & keys.

## 9. Upstream mTLS (02 §9.11), 0.x

Upstream client certificates reference Certificate entities. The `UpstreamClientCertExpiryDays` metric and alarm apply. TLS errors map to `upstream.tls_error`.

## 10. IdP outage resilience (02 §9.12), 0.x

- **Failure mode per route** (`:idp-failure-mode`, part of the security policy kind, lockable):
  - **`:fail-closed`** (default): past `stale-if-error`, or with no fresh introspection cache entry, return `503 authn.idp_unavailable` with `Retry-After: 30`.
  - `:fail-open-for-cached`: use the last key set up to a total age of 72 h, plus an introspection grace (default 0, max 15 min, never past `exp`).
  - Negative evidence is never extended.
- **There is no mode that accepts unverified tokens.**
- **Break-glass:**
  - One designated local account, disabled by default in production templates.
  - Password plus **TOTP (required; owner decision)**.
  - Session limited to 1 h. Each login raises a critical audit event and a notification (in 0.x: an ERROR log line plus the optional signed webhook; Slack and email in 1.0; handoff default H-4).
  - The portal has no break-glass login.
- **Metrics:** `IdpJwksAgeSeconds`, `IdpJwksFetchErrors`, `IdpIntrospectionErrors`, `IdpIntrospectionLatency`, `IdpCircuitOpen`, `IdpStaleServing`, `AuthnIdpUnavailable`. The alarm fires when `IdpCircuitOpen` holds for 5 min or the JWKS age exceeds half of stale-if-error.
- **Runbook (§9.12):** detect → assess → keep traffic flowing (raise stale-if-error up to 7 d, or fail-open for non-Restricted routes through a reviewed change; locked policies cannot be relaxed) → admin access → communicate → recover. Ship this runbook in milestone 10.

## 11. Console sessions and local accounts (02 §18.4)

- Console SSO: OIDC authorization code with PKCE (S256), with `state` and `nonce` checked. The ID token is validated with the §2 rules, and IdP tokens are not kept after login.
- Cookie `__Host-befive_session`: 256-bit random, `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/`. Only its SHA-256 is stored. Idle timeout is 30 min and the absolute limit 12 h. The ID rotates at login. Mutations require an `X-CSRF-Token` per-session token.
- **Local accounts** are for first-run and break-glass use only:
  - Argon2id (buddy-hashers) with a minimum of 12 characters.
  - A breach-list check against a bundled top-100,000 list, with no online calls.
  - **Lockout for 15 min after 10 failures**. Attempts are audited.
  - Admins can disable local sign-in once SSO works, but one designated break-glass account may stay enabled.
- **TOTP is required** for local break-glass admins (owner decision, which closes 02 §35.1 Q3 and §18.4). Handoff default H-5: TOTP is required for every local account, because local accounts exist only for first-run and break-glass use.
  - RFC 6238, 6 digits, 30 s step, SHA-1 for authenticator-app compatibility, ±1 step tolerance.
  - Enrolment is forced at the first local sign-in, including the first admin created through `/setup`.
  - 10 single-use recovery codes, stored Argon2id-hashed.
  - The TOTP seed is envelope-encrypted.
  - A TOTP reset by another Administrator is audited as critical.
- **Bootstrap:** `/setup` is the only reachable page until the first admin exists. It needs a one-time bootstrap token, printed once to stdout or supplied as `BEFIVE_BOOTSTRAP_TOKEN`, and the token is invalidated after use.
- Security headers: strict CSP (`default-src 'self'`, no inline scripts, a nonce for Ant Design's runtime styles), `nosniff`, `Referrer-Policy: same-origin`, `frame-ancestors 'none'`.

## 12. Okta app creation and client keys: 1.0 only (02 §24.8, 03 §5.14.6, §5.38)

Build nothing here in 0.x. Do not design it out either.
- Optional and off by default. Uses the customer's least-privilege Okta credential (a secret reference). Every Management API call is audited. BeFive never deletes Okta apps (it deactivates the binding). There is a rate rail of 20 creations per hour.
- Client authentication is **`private_key_jwt` only**, with no client secrets.
- **Developer-uploaded public keys are the default.** Pasted private material is refused in the browser and on the server. An in-browser WebCrypto generator exists as a convenience.
- **BeFive-generated keys** are an Administrator-only opt-in (critical audit event) and are **never available for Restricted APIs**. They can be disabled per API.
  - The developer downloads the key once, after a fresh sign-in (`max_age=300`).
  - **The private key is deleted from the secrets store right after the one-time download, or after 24 h if never downloaded.** An undownloaded expired key is deactivated in Okta. The audit event is `client_key.private_deleted` with reason `downloaded` or `expired`.
  - Owner decision of Oct 1, 2026 (D-OCT1-KEYS).
