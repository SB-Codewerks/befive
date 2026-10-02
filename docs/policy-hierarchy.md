# Policy hierarchy, authorization and the claims language

**Relevance:** 0.x: build (milestone 4, with the effective policy view in milestones 6 and 8). Classification defaults (level 2a) are 1.0, but the resolver must accept them. **Sources:** 01 §2.4, decisions 14 and 19. 02 §10 (authorization), §10.5 (claims language), §23 (hierarchy). 03 §4.9 (policy sources and locks UI), §5.22 (effective policy screen, `mockups/api-effective-policy.png`). 04 POL-001, POL-002, SEC-005.

## 1. Rule trees (02 §10.1–§10.4)

- A policy is a named EDN rule tree:
  - Combinators: `[:all …]`, `[:any …]`, `[:not r]`.
  - Predicates: `[:authenticated]`, `[:auth-method :jwt :mtls]`, `[:scope "x"]`, `[:scopes :all|:any …]`, `[:group "g"]`, `[:groups :any …]`, `[:claim [path] op value]`, `[:client-id …]`, `[:consumer …]`, `[:consumer-group "g"]`, `[:ip cidr…]`.
  - Draft 4 adds `[:application …]`, `[:organization …]`, `[:classification level]`, `[:any-item path rule]` and `[:every-item path rule]`.
- Limits: depth at most 8, at most 64 predicates, at most 32 values per set. Referenced groups must exist.
- **Default deny (§10.2).** `:access` is required: either `{:public true}` or `{:policy id :methods {…}}`. Per-method overrides are allowed, and `:deny` → `403 authz.method_denied`. A route without an access decision is invalid at write time. If one reaches a gateway anyway, the gateway rejects every request to it.
- **Compile, don't interpret (§10.4).** Rule trees compile to closure trees once per revision. Evaluation short-circuits and returns a decision record, for example `{:result :deny :policy "orders-write" :reason "scope_missing:orders:write"}`.
- **401 vs 403:** when there is no identity and the policy has identity predicates, return 401. `:not` over identity predicates with no identity is a deny. A suspended consumer gets 403 before the policy is evaluated.
- Decisions go to the access log (`authz_decision`, `authz_policy`, `authz_reason`). The response body only says `forbidden`.

## 2. Claims expression language (02 §10.5)

- Operators:
  - `:=` and `:not=`; `:in`; `:exists`
  - `:any-of`, `:all-of`, `:none-of` (arrays or space-delimited strings)
  - `:>`, `:>=`, `:<`, `:<=`
  - `:within` with an ISO duration, for `auth_time` freshness
  - `:matches`, which uses **RE2J** (linear time; pattern length at most 256)
  - `:contains` is kept as an alias of `:any-of` with one value.
- Paths are vectors of strings and indexes. `[:item …]` is used inside item quantifiers, which scan at most 256 elements (more → false, reason `claim_array_too_large`).
- The only dynamic value allowed is `[:request :header|:query|:path-param|:method name]`, compared as a string.
- Compilation in `befive.policy.claims/compile`:
  1. Validate at write time with **spec** (this replaces the malli step in the source).
  2. Run static type checks against the IdP's declared claim types (the Okta preset declares `scp` as an array, `auth_time` as a number, and so on). An undeclared claim with a type mismatch at runtime evaluates to false with `authz.claim_type_mismatch`.
  3. Pre-resolve paths and build HashSets.
  4. Short-circuit as in §1.
- Target: under 5 µs for 20 predicates.
- Test: generative tests that compare the compiled closure with a straightforward interpreter.

## 3. Levels (02 §23.1)

| Order | Level | Where it lives |
|---|---|---|
| 1 | Global | Promoted config |
| 2 | Environment | Environment overlay (in 0.x, the environment's own config; overlays come in 1.0) |
| 2a | Classification defaults (1.0) | Injected at the level that declares the classification |
| 3 | API | `policy_attachment` with scope api |
| 4 | Version | Scope version |
| 5 | Path | Prefix match on the normalized template, at segment boundaries |
| 6 | Operation | Scope operation, or inline in the operation document (setting both is a validation error) |

Hand-written routes resolve as global → environment → route.

## 4. Attachments and locks (02 §23.2)

- `{:id "pa_…" :scope {:level :api :api "payments"} :kind :ip :value {…} :locked true :reason "…"}`.
- Table `policy_attachment`, with at most one attachment per (scope, kind). See 02 §23.2 for the DDL.
- **Who may attach and lock** (02 §15.6, which is authoritative over 03 §2.2):
  - **Global attachments and global locks:** Administrator only.
  - Environment level and below: Operators may attach and lock.
  - **API Owners:** unlocked attachments at API, version, path or operation level, on their own APIs only, and within existing locks. They can never set or remove locks.

## 5. Kinds and strength order (02 §23.3)

| Kind | Fields | Merge | Stronger means |
|---|---|---|---|
| `:security` | `:authn`, `:access`, `:idp-failure-mode`, `:forward-token`, `:internal-jwt` | Most specific wins; a locked `:access` is conjoined `[:all locked lower]` | required ⊃ optional; `:all` ⊃ `:any`; fewer methods; fail-closed > fail-open; forward-token false |
| `:ip` | `:allow`, `:deny` CIDR sets | Allow sets intersect when locked; deny sets union | Smaller allow set, larger deny set (computed with the in-house `befive.net.cidr` range sets) |
| `:rate-limit` | Vector of limits | Union; a locked limit cannot be removed | Lower rate or capacity for the same limit id |
| `:logging` | `:bodies`, `:redact-fields`, `:sampling`, `:force-log` | Most specific; redact-fields union | `:off` > `:redacted` > `:on` |
| `:cache` (1.0) | `:mode`, `:ttl-s`, `:vary`, … | Most specific | `:off` > `:per-user` > `:per-application` > `:public`; shorter TTL |
| `:limits` | Size limits (02 §8.9) | Most specific | Lower values |
| `:deprecation` | Dates, link | Most specific | Not lockable |

Strength is a partial order per field, implemented as `meet` and `<=` functions in `befive.policy`.

## 6. Resolution (02 §23.4)

The same pure function, `befive.policy.resolve/effective`, runs in the control plane (validation and previews) and in every gateway compile.
- The chain runs from least to most specific.
- **A weaker value under a lock is a validation error.** Example: `policy.lock_violation: operation payments/v2/refund sets :ip :allow to 0.0.0.0/0, but API payments locks :ip :allow (reason: …)`. Writes, imports, OpenAPI imports and promotions all run this check.
- **Defense in depth.** The compiler applies `meet` even after validation passes. A clamp is logged at WARN.
- Provenance records the source level and attachment id for every field, plus the lock source.
- Budget: 50 ms of the 200 ms compile target for 2,000 operations, with memoization per (API, version, prefix).

## 7. Effective policy API and tooling (02 §23.6)

- `GET /admin/v1/apis/{api}/versions/{v}/operations/{op}/effective-policy`, `GET /admin/v1/routes/{id}/effective-policy`, `GET /admin/v1/apis/{api}/effective-policy?version=v2`.
- `POST /admin/v1/effective-policy/preview` takes a proposed change set and returns before/after values and any violations.
- The response includes `flags` such as `forwards-credential` and public methods.
- CLI: `b5ctl policy effective <op-or-route> [--at-revision N]`, `b5ctl policy explain`.
- Console (03 §4.9, §5.22):
  - A source chip per level. Each level has its own color, and the text always shows the level name.
  - Lock icon with the lock source.
  - Inherited placeholder with "Override here" and "Reset to inherited".
  - Struck-through overridden values.
  - The edit-at-this-level panel offers only tighter options.

## 8. IP rules (02 §23.5, SEC-005)

CIDR allow and deny rules at every level, matched against the trusted-proxy client IP (data-plane-proxy-core.md §client IP). IPv6 addresses are handled with 128-bit ranges.

## 9. Required property tests (02 §23.7)

Run these with test.check over generated hierarchies of up to 6 levels:
1. lock monotonicity
2. most-specific-wins
3. order independence
4. validator soundness (the clamp is a no-op on accepted configs)
5. agreement with a naive oracle
6. delta equals full

Also add a golden effective-policy JSON test and example tests per kind, including IPv6 intersection.
