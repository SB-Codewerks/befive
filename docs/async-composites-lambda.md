# Backends: Lambda (0.x), service discovery, composites and async jobs (1.0)

**Relevance:** Lambda upstreams and streaming are **0.x: build** (milestone 2). Service discovery, composites and async jobs are **1.0: context**. **Sources:** 01 §2.2, §2.9, §2.10, decisions 22, 23, 26. 02 §8.10 (Lambda), §8.11 (discovery), §28 (composites), §30 (async jobs). 03 §5.26, §5.28, mockup `composite-builder.png`. 04 API-003..006, TRAF-005..007.

## 1. AWS Lambda upstreams: 0.x (02 §8.10)

```clojure
{:id "orders-fn" :kind :lambda
 :lambda {:function "arn:aws:lambda:eu-west-1:123456789012:function:orders-api"
          :qualifier "live" :region "eu-west-1" :payload-format "2.0"
          :timeout-ms 29000 :max-concurrency 100 :max-pending 200
          :binary-media-types #{"application/pdf" "image/*"}}}
```

- Everything above the terminal handler (authn, policies, limits, logging) is the same as for HTTP upstreams. **Function URLs are plain `:http` upstreams.**
- **Invocation:**
  - Synchronous `LambdaClient` `Invoke` (RequestResponse) on `UrlConnectionHttpClient` (A-7, A-18). The call runs through `befive.gateway.block/off-loop`, and the transport returns a Manifold deferred.
  - Credentials come from the default chain (ECS task role, IRSA or Pod Identity). The role needs `lambda:InvokeFunction` on the qualified ARN. An `:endpoint` override uses static test credentials for LocalStack.
- **Event:** API Gateway HTTP API **payload format 2.0**:
  - `routeKey "GET /orders/{id}"`, `rawPath`, `rawQueryString`, `cookies`
  - lowercased, comma-joined headers, including the identity headers and the internal JWT
  - `pathParameters`
  - `requestContext` (stage = environment, requestId = gateway request id)
  - `authorizer.jwt.claims/scopes` plus `authorizer.befive {…}`
  - base64 bodies for binary media types or non-UTF-8 content
- **Response:**
  - An object with `statusCode` maps through as-is: `headers`, `cookies` → `Set-Cookie`, `body`, `isBase64Encoded`.
  - Anything else → `200` with a JSON body.
- **Errors:**

  | Condition | Status | Error code |
  |---|---|---|
  | Function error | 502 | `lambda.function_error` |
  | Throttling | 503 with `Retry-After: 1` | `lambda.throttled` |
  | Client timeout | 504 | `lambda.timeout` |
  | Request event over 6 MB (checked before invoking) | 413 | `lambda.payload_too_large` |
  | Bad or oversized response | 502 | `lambda.bad_response` |
  | Access denied, not found, credentials | 502 | `lambda.invoke_failed` |
  | Semaphore and queue full | 503 | `lambda.concurrency_limited` |

- **Retries:** idempotent methods only, and only for throttling or connection errors. Never retry after a function error.
- **Concurrency:** a per-node semaphore (`:max-concurrency`) with a bounded queue (`:max-pending`).
- **Metrics:** `LambdaInvocations`, `LambdaErrors`, `LambdaThrottles`, `LambdaDuration`, `LambdaConcurrencyRejected`.
- **Access fields:** `upstream_kind=lambda`, `lambda_request_id`, `lambda_function_error`.
- **Netty (01 R3):**
  - 0.x does not put the AWS SDK on Netty. The URL-connection client runs on a virtual thread (A-18), so there is no SDK event-loop group.
  - A later switch to the async client must pin one `io.netty` version across Aleph and the SDK and fail CI on non-convergence. The CRT client is the other fallback.
- **Tests:**
  - LocalStack Lambda in Testcontainers: event conformance against recorded API Gateway 2.0 events, the base64 rules, response inference, each error row, throttling and retries, the limiter and client timeouts.
  - The nightly run against real Lambda waits for the owner's AWS test account (owner default).

## 2. Streaming and large payloads: 0.x (02 §8.3, §8.9)

See [data-plane-proxy-core.md](data-plane-proxy-core.md):
- streamed bodies with backpressure
- SSE flush-through
- WebSocket splice
- the `:limits` policy kind
- the stream metrics and the fields `stream`, `stream_duration_ms`, `ttfb_ms` and `limit_rejected`

## 3. Service discovery: 1.0 (02 §8.11)

- Upstream kind `:discovered`, with three sources:
  - DNS (A/AAAA/SRV)
  - AWS Cloud Map (`servicediscovery`)
  - **Kubernetes EndpointSlices** through a plain-HTTP watch against the API server (no client library), with relist on failure
- Targets join the normal pool and health-check machinery.
- Tests use k3s and LocalStack.

## 4. Composite endpoints: 1.0 (02 §28)

- An operation backend `:composite`: a **bounded, loop-free step graph** in EDN. Steps call operations, routes or upstreams with the caller's identity. Results are mapped into one JSON response.
- **Limits:**

  | Limit | Default | Max |
  |---|---|---|
  | Steps | — | 16 |
  | Parallel steps | 8 | 16 |
  | Buffered per step | 1 MiB | 4 MiB |
  | Total buffered | 4 MiB | 16 MiB |
  | Timeout | 3 s | 30 s |

- Each step consumes its target's rate limits. Streaming targets cannot be steps.
- Optional SCI `:expr` runs in the native script-runner pool (2–4 per node) with `loop`/`recur`/`while` removed.
- The internal JWT is minted per step. Each step gets its own span.
- Errors: `composite.step_failed`, `composite.expr_failed`, `composite.timeout`.
- Target: under 2 ms of overhead per step.
- The UI uses React Flow (`@xyflow/react`).
- Reserve terminal slot 21 and do not assume a single upstream per operation in 0.x code.

## 5. Async endpoints and generated files: 1.0 (02 §30)

- An operation backend `:async`:
  - The submit returns `202 Accepted` with a job id at once. The only hot-path PG write is one job row on a dedicated pool; without PG, the submit returns 503 `async.unavailable`.
  - Dispatch by `:http-callback`, `:lambda-event` or `:sqs` (the control-plane dispatcher claims jobs with `SKIP LOCKED`).
  - The job state lives in PG.
- **Results:**
  - Stored in S3, S3-compatible storage (MinIO) or a local filesystem.
  - Retrieved through the proxy or a presigned URL.
  - **Only the owner or the `:readers` expression** may read a result (claims language plus `[:owner]`).
  - Retention defaults to 72 h, with a maximum of 90 d.
- **Client callbacks:** registered HTTPS URLs only, SSRF-guarded and verified, with Standard Webhooks signatures.
- Errors: `async.not_ready` (409), `async.expired` (410).
