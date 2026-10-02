# Rate limiting, quotas and (1.0) caching

**Relevance:** rate limits and quotas are 0.x: build (milestone 5). The pre-auth per-IP limiter is 0.x (owner decision). Response caching is 1.0: context. **Sources:** 01 §2.7, decision 25. 02 §11 (rate limiting), §29 (cache), §7.4 slot list. 04 TRAF-001, TRAF-002, TRAF-004.

## 1. Where limits come from (02 §11.1, §11.6)

- **Plan limits** come from the effective plan: the **application's plan if set, else the consumer's, else the organization's**. Exactly one plan applies; plans do not stack. A plan may replace the limits for one route with `:route-overrides`.
- **Hierarchy limits:** `:rate-limit` is a policy kind at any level. Limits are unioned and lockable (a locked limit can only be lowered, never removed).
- **Route limits** (hand-written routes) with `:key` set to `:route`, `:consumer`, `:consumer-and-route` or `:client-ip`.
- **All applicable limits must allow the request.**
  - Bandwidths that share a key form one Bucket4j bucket and are consumed atomically.
  - Separate buckets are consumed in turn. If a later bucket rejects, the earlier ones are refunded.
  - On Redis, calls are pipelined.

## 2. Dimensions (02 §11.6)

`:key` is one dimension or a vector of dimensions:

| Dimension | Subject |
|---|---|
| `:application` | Application id. A request without one follows `:on-missing`: `:skip` (default), `:reject` (403 `ratelimit.dimension_missing`) or `:shared` |
| `:api-key` | Credential id (never the key itself) |
| `:user` | `base32(SHA-256(pepper, subject))[0..20]` (hashed; never used as a metrics tag) |
| `:group` / `:scope` | First match from a configured list |
| `:operation` | (API, version, operation), or the route id for hand-written routes |
| `:organization`, `:consumer`, `:route`, `:client-ip` | Client IPv6 addresses are truncated to /64 |

- Key format: `rl:{env}:{limit-id}:{dim1}={v1}|{dim2}={v2}`. Each value is at most 64 characters (longer values are hashed) and the whole key at most 256 bytes. Draft 3 keys `rl:{env}:{scope}:{limit-id}:{subject}` remain valid.
- The local map is capped at 1,000,000 keys.

## 3. Local vs Redis (02 §11.3)

| | Local (default) | Redis (`BEFIVE_REDIS_URI`) |
|---|---|---|
| Implementation | Bucket4j `LockFreeBucket` in a Caffeine map (expires after 2 × window idle) | Bucket4j `LettuceBasedProxyManager`, async, CAS Lua |
| Cluster accuracy | `:local-strategy :divide`: limit ÷ live nodes (node count from the heartbeat, refreshed every 10 s) | Exact |
| Failure | n/a | 50 ms timeout. A breaker opens for 5 s after 3 failures. `:on-backend-failure :fail-open` (default) falls back to the local divided bucket and logs `rl_decision=degraded`. `:fail-closed` → 503 `ratelimit.backend_unavailable`. Metric `RateLimitBackendDegraded`. |

## 4. Monthly quotas (02 §11.4)

- These are **calendar quotas in UTC** (owner default; per-plan time zones are not planned before 1.0). They count from 00:00 UTC on the 1st and reset at the month boundary.
- Redis: `INCR q:{env}:{consumer}:{limit-id}:{yyyy-mm}` with `EXPIREAT` set to month end + 35 d.
- Without Redis:
  - Every 5 s, flush local deltas into `quota_usage` (`ON CONFLICT DO UPDATE SET used = used + excluded.used`) and read the totals back.
  - Overshoot is bounded at about 5 s of traffic; document this.
  - The flush runs on a virtual thread and keeps accumulating while the DB is down.
- A rejection returns 429 `quota.exceeded:<id>` with `Retry-After` set to the seconds remaining until the month boundary.

## 5. Response headers (02 §11.5)

- The default is the IETF draft headers:
  ```
  RateLimit-Policy: "gold-rps";q=100;w=1, "gold-monthly";q=1000000;w=2592000
  RateLimit: "gold-rps";r=87;t=1
  ```
- `RateLimit-Policy` lists at most 4 policies, most restrictive first. A 429 also carries `Retry-After`.
- The setting is `:rate-limit-headers :ietf-draft | :x-ratelimit | :none`. Track the final RFC wording.

## 6. Pre-auth per-IP rate limiter: 0.x (owner decision; closes 02 §35.1 Q1)

The source design relied on the ALB and WAF for unauthenticated floods (02 §7.4). The owner moved a simple per-IP limiter into 0.x. **Handoff default H-3:**
- **Placement:** immediately after `:befive/ip-filter` (slot 6) and before CORS, the size limits and authentication. No IdP call, introspection or HMAC work happens for a rejected request.
- **Key:** the trusted-proxy client IP (IPv6 truncated to /64). The bucket is `rl:{env}:preauth:client-ip={ip}`.
- **Defaults:** enabled, **100 req/s per IP, with a burst of 200**, using local buckets with the `:divide` strategy (Redis if configured). Exempt: `:trusted-proxies`, load-balancer health-check sources, and `:protected-cidrs`.
- **Configurable** in Settings > Defaults & security (`:security {:preauth-ip-limit {:enabled true :rps 100 :burst 200 :exempt-cidrs [...]}}`) and overridable at environment or global level only. It is not part of the per-operation hierarchy, so it stays simple and cannot be weakened locally.
- **Response:** `429 too_many_requests` with `Retry-After`. The log reason is `ratelimit.preauth_ip`. Metric `PreAuthRateLimited` (by environment), plus a `client_ip_limited` field in the access log.
- **Tests:** a flood from one IP doesn't trigger IdP fetches; exempt CIDRs are honored; ALB XFF handling is correct.

## 7. Encrypted response caching: 1.0 (02 §29)

Not built in 0.x. Do not depend on its absence.
- Per-node Caffeine L1 with an optional Redis L2. Entries are **encrypted with AES-GCM even in memory**.
  - Keys are derived per epoch (UTC day) with HKDF from `DEK_cache`.
  - Cache keys are partitioned by authorization context (`:public`, `:per-application`, `:per-user`, `:off`).
  - Classification-aware defaults: Restricted → off.
- Honors `Cache-Control` (an RFC 9111 subset; no heuristic freshness).
- Purge: `POST /admin/v1/cache/purge` and `b5ctl cache purge`. Purges are audited.
- Metrics: `CacheHits`, `CacheMisses`, …; access field `cache_status`.
- Targets: hit under 1 ms. Slot 16 is reserved for it.
