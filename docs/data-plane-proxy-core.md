# Data plane and proxy core

**Relevance:** 0.x: build (milestones 2, 4 and 5). **Sources:** 02 §7 (request lifecycle), §8 (proxying), §20.2 (hot-path rules), 01 §2.2.

## Connection handling and executor (02 §7.1–§7.3)

- Aleph listeners on 8080 and 8443. TLS 1.2/1.3 with SNI from the RouteTable certificates. Client-side HTTP/2 through ALPN. Upstreams are **HTTP/1.1 only** (no upstream HTTP/2 until gRPC; owner default).
- An **in-house Sieppari-style interceptor executor**: each interceptor has `:enter`, `:leave` and `:error`, and may return a value or a Manifold deferred. Sync and async can be mixed. Nothing blocks the event loop; a blocking-detection agent runs in tests.
- The context map (02 §7.3) carries the request, the matched route and operation, identity, the effective policy (compiled closures), the timings, and the access-log record under construction.

## Phase order: 21 slots, normative (02 §7.4)

| # | Slot | Notes |
|---|---|---|
| 1 | `:befive/access-log` | Opens the record and writes it on leave |
| 2 | `:befive/request-id` | Trusted inbound ID or a new one (UUIDv7) |
| 3 | `:befive/client-ip` | Trusted-proxy XFF walk (§8.8) |
| 4 | `:befive/error-mapper` | Maps exceptions to the fixed error bodies |
| 5 | `:befive/strip-inbound` | Removes identity headers and the internal JWT header (identity.md §anti-spoofing) |
| 6 | `:befive/ip-filter` | Effective IP allow/deny; also enforces runtime overrides (1.0) |
| — | **pre-auth per-IP limiter** | **0.x owner decision.** Runs immediately after `ip-filter` and before any auth work (see rate-limiting-caching.md). It belongs to the same slot group as `ip-filter`, so the slot numbering is unchanged. |
| 7 | `:befive/cors` | |
| 8 | `:befive/limits` | Size and header limits (§8.9) |
| 9 | `:befive/lifecycle` | Retired version → 410 problem+json; deprecation headers on leave |
| 10 | pre-auth plugins | |
| 11 | `:befive/authn` | JWT, introspection, API key, mTLS; resolves consumer and application |
| 12 | `:befive/authz` | Compiled rule tree; 401 vs 403 |
| 13 | `:befive/rate-limit` | All dimensions and quotas (rate-limiting-caching.md) |
| 14 | post-auth plugins | |
| 15 | `:befive/validate` | Optional OpenAPI request validation (1.0, 02 §26.4) |
| 16 | `:befive/cache` | 1.0 |
| 17 | `:befive/request-transform` | Including identity forwarding headers and the internal JWT |
| 18 | pre-proxy plugins | |
| 19 | response plugins | |
| 20 | `:befive/response-transform` | |
| 21 | terminal | `:befive/proxy`, `:befive/lambda`, (1.0: `:befive/composite`, `:befive/async`, `:befive/mock`) |

## Error responses (02 §7.6)

Security errors reveal nothing. The body is `{"error": "<code>", "request_id": "..."}` and the reason goes only to the access log (`auth_reason`, `authz_reason`, `error_code`):

| Status | error | Example log reasons |
|---|---|---|
| 400 | bad_request | request.malformed, request.invalid_host, version.unknown |
| 401 | unauthorized | jwt.missing/expired/bad_signature/unknown_kid/alg_not_allowed/aud_mismatch, introspection.inactive, api_key.unknown/expired, mtls.no_certificate/unmapped |
| 403 | forbidden | ip.denied, authz.no_policy, authz.scope_missing:<scope>, authz.group_missing, authz.method_denied, authz.claim_type_mismatch, consumer.suspended, ratelimit.dimension_missing |
| 404 / 405 | not_found / method_not_allowed | route.not_found, route.method_not_allowed |
| 406 | not_acceptable | version.unknown_media_type |
| 410 | gone | version.retired |
| 413 | payload_too_large | limits.body_too_large, lambda.payload_too_large |
| 429 | too_many_requests | ratelimit.exceeded:<id>, quota.exceeded:<id>, plus `ratelimit.preauth_ip` (0.x pre-auth limiter; new code, handoff default H-3 in decisions.md) |
| 502 | bad_gateway | upstream.connect_failed/reset/tls_failed/tls_error/protocol_error, limits.response_too_large, lambda.function_error/bad_response/invoke_failed |
| 503 | service_unavailable | upstream.no_healthy_targets/pool_exhausted, authn.idp_unavailable, ratelimit.backend_unavailable, lambda.throttled/concurrency_limited |
| 504 | gateway_timeout | upstream.connect_timeout/read_timeout/total_timeout, lambda.timeout |

- Resource-state errors (410 retired, 406/400 unknown version) use `application/problem+json` with a docs link.
- A 401 carries `WWW-Authenticate: Bearer realm="api"` (plus `error="invalid_token"` for JWT failures).

## Proxying (02 §8)

- **Pools (§8.1):** one Aleph pool per upstream (keyed by a hash of the pool settings), with max connections, idle timeout and keep-alive. Pool exhaustion → 503 `upstream.pool_exhausted`.
- **Load balancing (§8.2):** smooth weighted round robin (default) and least-connections. **Panic routing:** if the healthy share falls below the threshold, route to all targets.
- **Streaming and backpressure (§8.3):** request and response bodies stream through Manifold streams and are never fully buffered (except where a feature needs buffering: composites, cache, validation).
- **Timeouts and retries (§8.4):** connect, read and total timeouts. Retries only for idempotent methods (or with an explicit opt-in), within a **retry budget of 20%** of requests, with jittered backoff.
- **Health checks (§8.5):** active HTTP probes plus passive ejection on consecutive failures.
- **WebSocket (§8.6):** upgrade and splice, with idle timeout and close-frame handling on drain.
- **Hop-by-hop headers (§8.7):** removed per RFC 9110. HTTP/2 from clients is translated to HTTP/1.1 upstream.
- **Client IP (§8.8):** walk `X-Forwarded-For` from the right, skipping `BEFIVE_TRUSTED_PROXIES` (the ALB subnets). Set `X-Forwarded-For/Proto/Host` upstream.
- **SSE, chunked and size limits (§8.9):** SSE is flushed through per event, with no buffering. `:limits` is a **policy kind** (max request body, header size, response body); oversize → 413/502. Stream metrics are kept per route.
- **Lambda (§8.10):** see [async-composites-lambda.md](async-composites-lambda.md). API Gateway payload format 2.0 mapping and an error table. This is 0.x.
- **Netty:** pin one Netty version across Aleph and the AWS SDK. Use the AWS CRT HTTP client as a fallback if convergence fails. CI runs a dependency-convergence check.

## Hot-path engineering rules (02 §20.2)

- No reflection (`*warn-on-reflection*` is an error).
- No PG and no blocking I/O on the event loop.
- Precompile everything at compile time: routers, claims closures, regexes (RE2J), header name sets.
- Avoid per-request allocation where it is measurable.
- The access log goes through a bounded queue. Drop and count when full (`DroppedAccessLogs`), but never drop summary lines.

## Performance targets (02 §20.1, 01 §3.3)

These are to be verified with k6 and are not measurements:
- p99 added latency under 5 ms
- 10k rps or more per 4 vCPU
- config live within 5 s

JVM: ZGC (generational) and container-aware heap flags in the image (02 §20.3). Virtual threads only off the hot path (§20.4).
