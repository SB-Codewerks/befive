# Telemetry, logging, metrics and recipes

**Relevance:** 0.x: build (milestone 7, with the access log from milestone 2). The audit hash chain is 0.x. Reports and InfoSec retention are 1.0. **Sources:** 01 §2.13–§2.14, decision 27. 02 §12 (observability), §13 (AWS recipe), §31 (sinks and Datadog recipe), §20.5. 04 OBS-001..006.

## 1. Log streams (02 §12.1)

All output is **JSON lines on stdout** (no files, no CloudWatch API calls). Each line has a `type`:

| type | Writer | Content |
|---|---|---|
| `access` | gateway | One line per request (and per WebSocket at close). No EMF. |
| `metrics` | gateway, CP | **EMF** route summaries every flush interval (default 60 s), plus heartbeat, upstream health and certificate expiry |
| `usage` | gateway | Per (consumer/application, route) summaries per interval. No EMF |
| `audit` | CP | One line per admin event (also stored in PG) |
| `app` | both | Logback JSON application logs |

- Access lines are built on the event loop, then go to a **bounded MPSC queue (65,536)** and a single writer thread (jsonista, buffered stdout).
- When the queue is full, drop the line and count it in `DroppedAccessLogs`. Summary lines have a priority path and are never dropped.

## 2. Access log `befive.access/1` (02 §12.2)

- The field catalog is data in `befive.schema.access-log` (`.cljc`: name, type, description, redaction rule). The log builder, the docs page and the recipes are all generated from it.
- Fields, in the order of 02 §12.2:
  - Draft 3: `schema type ts environment node request_id trace_id parent_span_id route_id service_id upstream_id upstream_target method host path path_template protocol status latency_ms upstream_latency_ms gateway_latency_ms bytes_in bytes_out client_ip user_agent tls_version sni consumer_id consumer_name plan_id client_id subject credential_id auth_method auth_result auth_reason idp_id authz_decision authz_policy authz_reason rl_decision rl_limit_id rl_remaining upstream_status attempts upstream_panic error_code ws plugin sample_rate in_sample log_reason`
  - Draft 4: `api_id api_version operation_id version_selected_by classification deprecated organization_id application_id subscription_id policy_sources ip_rule rl_dimension cache_status stream stream_duration_ms ttfb_ms limit_rejected upstream_kind lambda_request_id lambda_function_error validation_errors job_id parent_request_id composite_step client_kind stripped_headers idp_stale`
  - 1.0-only fields (`cache_status`, `job_id`, `subscription_id`, `composite_step`, `validation_errors`) are reserved in the catalog and stay absent in 0.x.
- The exact list and types are in 02 §12.2. Implement the catalog first, and test every emitted line against it.

## 3. Metrics, usage and sampling (02 §12.3)

- Metrics and usage are recorded from **every request before sampling**, so they are exact. The aggregators are two-generation, flushed at interval boundaries, and closed on shutdown.
- EMF namespace: **`BeFive`**. Route summary lines carry fixed-bucket latency histograms (about 1% quantization) and counter metrics that are used only with `Sum`. Dimensions are `[Environment]`, `[Environment, Route]`, and in Draft 4 also `Api` and `Operation`. p90 was added (31.2).
- **Sampling is optional and off by default.** It is a global rate with per-route overrides. Always-keep rules cover errors, denials, upstream failures, slow requests, listed consumers, and an opt-in force-log header. The decision is deterministic from the W3C trace ID (OTel consistent probability sampling).

## 4. Redaction (02 §12.4)

- Never log headers (except an explicit per-route allow list; `Authorization`, `Cookie` and the like can never be allow-listed).
- Never log query strings or bodies.
- Credentials appear only as `credential_id`.
- A scrubber covers JWT-shaped strings, `b5k_…`, `b5a_…`, `Bearer …` and `password=`/`secret=` pairs.
- Audit payloads replace fields marked secret in the **form-metadata registry** (`:befive/secret true`; the source says "malli schema") with `"[REDACTED]"`.
- Optional `:access-log {:subject :hash}`.
- A canary test suite asserts that no secret ever reaches any log stream.

## 5. Audit log `befive.audit/1` (02 §12.5), 0.x

- One event per mutation, written by reitit middleware. Fields: actor (type `user`, `token` or `system`), action, resource kind and id, before/after (redacted), request id, IP and outcome.
- **Hash chain:** `hash = SHA-256(prev_hash || canonical-json(event))`, written under a lock. `POST /admin/v1/audit-events/verify`.
- **Retention (resolved inconsistency).** 02 §12.5 says 400 days; 02 §33.1 says `:audit-days 2555` (7 years), with a minimum of 365. Handoff default H-10: **2555 days by default, minimum 365**. 0.x may simply not delete. The retention job and the checkpoint events of 02 §33.2 arrive in 1.0.

## 6. Live metrics (02 §12.6)

- `LongAdder` counters per route in 10 s buckets:
  - requests and status classes
  - auth failure reasons (top 20)
  - denials and rate-limited requests
  - bytes
  - a log-linear 48-bucket latency histogram (0.25 ms – 60 s)
  - per-target upstream state
- These go to the `UNLOGGED` table `node_metrics` every 10 s and are rolled to `node_metrics_1m`.
- **Retention (owner default): 2 h at 10 s and 24 h at 1 min.** Longer ranges belong to the 1.0 Reports feature.
- Per-node JSON is at `:9901/internal/metrics`.

## 7. Health (02 §12.7)

- `/healthz` checks only that the event loop is alive (within 5 s). It never checks dependencies.
- `/readyz` returns 200 when the RouteTable is loaded, the listeners are bound and the node is not draining. Degraded reasons are listed in the body (`db-unreachable`, `redis-unreachable`, `lkg-mode`, `idp-jwks-stale:<idp>`), and the node stays ready while degraded.

## 8. Traces (02 §12.8, §31.4). OTLP is **0.x**

- 02 §12.8 says "OpenTelemetry export is in 1.0", but 01 §4.2 milestone 7 puts OTLP traces in 0.x, and 01 wins (handoff default H-9).
- Without tracing: `traceparent` passes through (or is generated when missing). `X-Request-ID` is trusted only from trusted proxies (pattern `^[A-Za-z0-9._:-]{8,128}$`).
- With `:telemetry {:tracing {:enabled true}}`, the OTel Java SDK creates explicit spans (no agent): `befive.request`, `befive.authn` (only when an IdP call occurred), `befive.lambda.invoke`, and `befive.upstream` (HTTP semconv). A child `traceparent` is forwarded. Export is OTLP (gRPC or HTTP) to a collector.

## 9. Sinks (02 §31)

All sinks render the same closed aggregator generation, so the numbers agree. A property test enforces this.
- **CloudWatch EMF** (stdout, namespace `BeFive`).
- **Datadog** with `java-dogstatsd-client`:
  - Non-blocking. Prefer a UDS socket to the Agent sidecar (ECS) or DaemonSet (EKS); UDP also works.
  - Tags: env, route, api, operation, status class. Consumer tags are kept for the **top 50 consumers** and the rest become `other`. Never tag by user.
  - Datadog log attributes are mapped from access fields.
  - Send errors are counted in `TelemetrySinkErrors{sink="datadog"}` and readiness reports the sink as degraded.
- **OTLP metrics** (optional) and **Prometheus** at `:9901/internal/prometheus` (in-house text format 0.0.4, `befive_` prefix, `_total`/`_seconds`/`_bytes` suffixes, 25 buckets, a 1 s scrape cache).

## 10. Recipes (02 §13, §31.6), 0.x

- **AWS recipe** (`modules/aws-recipe`): Terraform JSON and CloudFormation JSON, generated from the catalogs.
  - Log groups: access lines may use Infrequent Access; summary lines need Standard because of EMF.
  - Dashboards: every widget is labeled exact or Logs Insights (with sampling-aware weights).
  - Saved queries live in the `BeFive/<env>` folder.
  - The alarms are in 02 §13.4. Main ones:
    - 5xx above 5% for 3 of 5 min
    - p99 above 1 s
    - `GatewayLatency` p99 above 20 ms
    - auth-failure and rate-limit anomaly bands
    - `NodeUp` below the minimum
    - unhealthy targets
    - config lag
    - dropped access logs
    - Redis degraded
    - certificate expiry under 14 d
    - IdP unavailable
    - upstream client certificate expiry
  - **Monthly usage report job** (§13.5): an EventBridge Scheduler triggers an ECS RunTask of the same image with the `report` subcommand.
- **Datadog recipe** (`modules/datadog-recipe`): Terraform JSON for the `datadog` provider, with dashboards (p50–p99, 4xx/5xx by API and consumer, usage, IdP health, deprecated usage), monitors and log facets.
- Validation: `terraform validate` and `cfn-lint` in CI. The nightly AWS deploy waits until an AWS test account exists (LocalStack until then; owner default).
- External alert sources (1.0): Prometheus Alertmanager arrives through the **generic webhook** format (a small relay), not natively (owner default).
