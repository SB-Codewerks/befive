# Testing strategy

**Relevance:** 0.x: build. Every milestone ships with its tests. **Sources:** 02 §22 (testing table), §20.1 and §20.5 (performance and k6), §23.7, §9.12 tests, §25.8, 03 §8.10, §8.11. Owner defaults: mock IdP plus LocalStack until real accounts exist.

## 1. Tools

- **Kaocha** runs the tests. Suites are `:unit`, `:generative`, `:integration`, `:e2e` and `:cljs`.
- **clojure.spec** with **test.check** (through `clojure.spec.gen.alpha` and `clojure.spec.test.alpha`) replaces malli generators.
- **Testcontainers:**
  - in 0.x: PostgreSQL 15/16/17, Redis, LocalStack, the mock IdP, the OTel collector and the Datadog Agent
  - in 1.0: MinIO and k3s
- **Toxiproxy** injects network faults.
- **Playwright** with **axe-core** covers the console and portal.
- **k6** runs the performance tests and **criterium** the microbenchmarks.
- Other tools: **oasdiff**, `terraform validate`, cfn-lint, promtool, Trivy, the OWASP ZAP baseline scan.

## 2. Rules of thumb

- Every public function in `schema`, `policy` and the compiler has an `s/fdef`. Test fixtures call `stest/instrument` in unit and integration suites. Never instrument in production.
- Data-shaped code gets generative tests through `stest/check` or `test.check` properties. Specs must have working generators: use `s/with-gen` for slugs, CIDRs, URLs and RE2 patterns.
- Shared `.cljc` code (schema, policy) runs the **same tests on the JVM and in Node** (shadow-cljs `:node-test`), so validation is identical everywhere.
- Inject the clock and randomness: quotas, JWT expiry, rotation timelines and TOTP all need fake time.
- Integration tests use real PostgreSQL and Redis containers. Do not mock the database.

## 3. 0.x test matrix (subset of 02 §22)

| Component | Must-have tests |
|---|---|
| schema (cljc) | A spec per entity. Generated valid docs round-trip through JSON, EDN and Transit unchanged. Diff properties: applying diff(A→B) to A gives B, and normalization is idempotent. `explain->problems` gives a stable code and path for each predicate kind. Defaults are idempotent: `(= (apply-defaults (apply-defaults d)) (apply-defaults d))`. |
| Compiler | Delta merge equals full load for random change sequences. A route-matching oracle (a slow reference matcher compared with the compiled router). Compiling 2,000 routes stays under 200 ms. |
| Pipeline | Phase order, short-circuits, sync and async mixing, a blocking-detection agent (nothing blocks the event loop) |
| Proxy | Streaming with slow readers, timeouts, retries (idempotent only, within budget), health and ejection, WebSocket, h2→h1, hop-by-hop headers, smuggling cases, ByteBuf leak detection at PARANOID, SSE flush-through, size limits |
| Lambda | LocalStack event conformance, every error row, the concurrency limiter, Netty convergence (CI) |
| AuthN | JWT vectors: every algorithm, `alg=none`, HMAC confusion, wrong `kid`, rotation, a JWKS outage with stale keys, clock skew, `aud` array and string. Introspection caches and the breaker. API keys: grace period, two-key limit, constant time. mTLS mapping. The mock IdP is a Testcontainer. |
| Okta preset | Against the mock IdP's Okta profile now. A nightly run against a real Okta developer org **once the owner provides one** |
| Anti-spoofing | Forged `X-BeFive-*` and internal JWT headers, in every case and duplicate variant, never reach the upstream |
| Internal JWT | Mint, then verify with Nimbus **and** Node `jose`. Rotation timeline with fake time. Revocation. Reuse-cache bounds. |
| IdP outage | Toxiproxy outage in steady state and across a restart (LKG plus DB copy). A new `kid` during an outage fails closed. Grace bounds. Tampered cache rejected. Break-glass audit and notification. |
| AuthZ and claims | Table tests per predicate. Property: `[:not p]` never allows anonymous callers. 401 vs 403. Compiled vs interpreted claims (generative). RE2J pathological inputs. |
| Policy hierarchy | The six properties of 02 §23.7, plus golden effective-policy JSON |
| Rate limits | Local accuracy. Redis accuracy across 3 simulated nodes. Fail-open and fail-closed when Redis is killed. The month boundary and leap years with an injectable clock. Bucket keys never contain raw subjects. Plan precedence. Refunds across 3 or more buckets. The **pre-auth IP limiter** (a flood triggers no IdP calls; exemptions hold). |
| Config sync | The NOTIFY path, poll fallback, a gap after pruning, a DB outage with LKG startup, a compile failure keeping the old revision |
| Control plane | **The RBAC matrix as data (every role × every endpoint)**, ETag and If-Match, pagination, problem details, an audit event for every mutation, hash-chain verification, OpenAPI backward compatibility (oasdiff) |
| Migrations | Up on an empty DB, up from each previous release with data, lock duration under 1 s, on PG 15 and 17 |
| Observability | Every line validates against its catalog. EMF validates against the EMF JSON schema (at most 100 values per array). Summed summaries equal the ground truth. All sinks agree with the EMF sums. Canary secrets never appear in any stream. |
| Recipes | `terraform validate`, cfn-lint, queries use only catalog fields. The nightly AWS deploy starts **once an AWS test account exists**; LocalStack until then. |
| CLI | Golden files for validate, diff and apply output. Native-binary smoke tests on each OS. |
| Licensing | Valid, tampered and unknown key IDs. The build-date coverage matrix. Evaluation limits (1 node). Trial expiry. |
| Portal preview | Preview mode: the mutating routes are absent (404). Visibility and facet-leakage properties. Hidden items return 404. A CSP and sanitizer XSS corpus. OIDC against the mock IdP. A smoke suite proving the absent features really are absent. |
| Console | Unit tests for re-frame events and subscriptions. Playwright flows 6.1, 6.2, 6.3, 6.4, 6.8 (basic) and 6.11 (mock Okta made unreachable). axe-core on every page. Visual snapshots at 1440 × 900 and 1280 × 720. |
| Security | Trivy, a ZAP baseline scan of the console, Admin API and portal, and the portal pen test in milestone 10 |

## 4. Performance (02 §20.1, §20.5)

- k6 runs the open model (`constant-arrival-rate`). Each run is a 2 min warm-up plus 10 min of measurement, repeated 3 times. Report p50, p90, p99, p99.9 and max, plus CPU and memory.
- **Added latency** is the gateway path minus the direct path, per percentile.
- **Scenarios:**
  1. public route
  2. API key
  3. JWT, warm
  4. JWT + local rate limit
  5. JWT + Redis
  6. TLS + HTTP/2 clients
  7. streaming at 64 KiB and 1 MiB
  8. WebSocket churn
  9. config churn during load
- **Targets** (hypotheses, not measurements): p99 added latency under 5 ms, at least 10k rps per 4-vCPU node. The capacity step test raises load until p99 exceeds 5 ms or errors exceed 0.1%.
- Milestone 10 adds the **rate-limit load tests** (01 §4.2). A nightly run at reduced scale catches regressions.
- Publish results per release as measurements, with the exact setup.
