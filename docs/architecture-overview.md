# Architecture overview

**Relevance:** 0.x: build. **Sources:** 02 §1 (summary and five rules), §2 (topologies), §3 (modules), §4 (Integrant system), 01 §1, §3.

## One image, three roles (02 §1)

There is one Clojure 1.12 application on Java 21, in one uberjar and one Docker image. `BEFIVE_ROLE` selects the Integrant components:

| Role | Runs | 0.x notes |
|---|---|---|
| `gateway` | Aleph/Netty listeners, compiled RouteTable, interceptor pipeline, upstream pools, health checks, rate limiter, access-log writer, metric and usage aggregators (EMF), telemetry sinks, heartbeat, Lambda invocation, internal JWT minting | 1.0 adds the cache, composites, discovery, the signal shipper, async submit and runtime overrides |
| `control-plane` | Admin API `/admin/v1`, console assets, OIDC console sign-in, audit, migrations, cluster status, route tester, portal listener (preview) | 1.0 adds anomaly (on the leader), access workflow, jobs, reports, promotion and evidence |
| `all` | Both in one JVM | Evaluation mode (1 gateway node, all features, no time limit) and small installs |

## Five rules (02 §1). Do not break these.

1. **PostgreSQL is the single source of truth** for config, consumers, credential hashes, admin users, audit and cluster status.
2. **The hot path never touches PostgreSQL or blocks a Netty event-loop thread.** Config is compiled into an immutable `RouteTable` and swapped atomically (`reset!` on an atom). Blocking work (JDBC, disk, AWS SDK) runs on virtual threads. The single documented exception (1.0) is the async job submit (02 §30.2).
3. **One global, monotonically increasing config revision.** Each committed change bumps it exactly once. A node's state is "which revision I applied".
4. **Config is plain data with one schema set.** The shared `.cljc` **clojure.spec** registry (the original says malli; see [validation-clojure-spec.md](validation-clojure-spec.md)) validates the same documents in the Admin API, compiler, CLI and console. JSON, EDN and Transit use identical (kebab-case, unqualified) key names.
5. **Fail static.** DB, control-plane or Redis outages degrade freshness or accuracy, never the availability of already-configured traffic. Security checks still fail closed.

## Topologies (02 §2)

- **A, evaluation:** one container with `BEFIVE_ROLE=all`, plus `postgres:16` and an echo upstream (docker-compose). Rate limits are in memory.
- **B, cluster:** N stateless gateways, one or two control planes, shared PostgreSQL (RDS Multi-AZ) and optional Redis (ElastiCache). **Gateways and control plane never talk to each other directly.** All coordination goes through PostgreSQL (LISTEN/NOTIFY, heartbeat tables).
- **C, AWS reference:** ECS on Fargate (EKS is analogous) behind an **ALB**, which is the 0.x design-partner focus. Gateways sit behind the public ALB. The control plane sits behind an internal ALB. The portal (preview) has its own hostname behind WAF.

### Ports (02 §2.2)

| Port | Role | Purpose |
|---|---|---|
| 8080 | gateway | Plain HTTP data plane (behind the TLS-terminating ALB) |
| 8443 | gateway | TLS data plane (SNI, mTLS, HTTP/2 via ALPN, client side only) |
| 9000 | control-plane | Admin API, console, OIDC callback |
| 9901 | both | Ops: `/healthz`, `/readyz`, `/internal/metrics`, `/internal/info`, `/internal/prometheus`, BeFive JWKS. Never public. |
| 9100 | control-plane | Developer portal (off until configured; preview mode in 0.x) |
| 9200 | control-plane | Job callbacks (1.0) |
| 9300 | control-plane | Link listener (1.0) |

## Modules (02 §3.1)

See AGENTS.md §5 for the tree and the dependency rules. The key separations are:
- `schema`, `policy` and `anomaly` are `.cljc`, so the same code runs in the server, the CLI and the browser.
- `gateway` and `control-plane` are independent. The route tester reaches the pipeline through `befive.gateway.embed`.

## Integrant system (02 §4.2)

- **Shared keys:** `:befive/settings`, `:befive/logging`, `:befive/license`, `:befive/db` (on a gateway, does not fail startup if the DB is down; on a control plane, fails after 60 s), `:befive/keyring`, `:befive/plugins`, `:befive/schema-registry` (the spec registry plus plugin specs), `:befive/ops-server` (9901), `:befive/health`.
- **Gateway keys (0.x):** `lkg-store`, `runtime` (the RouteTable atom), `system-http` (SSRF-guarded client for IdP and health), `upstream-pools`, `jwks-cache`, `introspection-cache`, `rate-limiter` (plus a pre-auth IP limiter), `quota-store`, `access-log` (a bounded MPSC queue with one writer thread), `aggregators`, `counters`, `compiler`, `config-sync`, `health-checker`, `http-server`, `heartbeat`, `aws-clients` (Lambda), `telemetry-sinks`.
- **Control-plane keys (0.x):** `migrations` (advisory lock), `repo`, `keyring-admin`, `audit`, `config-service`, `cluster`, `events` (SSE), `sessions`, `tester`, `admin-server`, `portal-server` (preview).
- **1.0 keys:** response-cache, discovery, composite-runners, jobs, signal-shipper, leader, anomaly/response engines, outbox, access workflow, reports, promotion, evidence.

**Gateway startup (02 §4.3):**
1. settings, logging, license (fail fast; an invalid setting exits with code 78)
2. DB pool (retries in the background)
3. keyring (from the DB, or from the LKG header)
4. plugins
5. snapshot from the DB (up to 10 s), else the LKG file; compile
6. bind listeners; `/readyz` returns 200 only when a RouteTable exists and the listeners are bound
7. heartbeat

A gateway with no DB and no LKG stays not-ready (it serves only 9901) and does not exit.

**Graceful shutdown (02 §4.4):**
1. `/readyz` → 503; the node is marked draining
2. keep serving for `drain.delay-ms` (10 s)
3. stop accepting new connections
4. wait for in-flight requests up to `drain.timeout-ms` (30 s); WebSockets get close frame 1001
5. flush the aggregators, the access log and the quotas

Set the ECS `stopTimeout` to 60 s.

## Request path at a glance

`ALB → gateway listener → pipeline (21 slots, see data-plane-proxy-core.md) → upstream pool / Lambda → streaming response → access log + aggregators`. The control plane is never on the request path.

## Config path at a glance

`console/b5ctl → Admin API → spec validation + cross-entity checks + lock validation → one PG transaction (bump revision, config_change rows, audit) → NOTIFY befive_config → gateways fetch the delta → pure compile → atomic swap → heartbeat reports the applied revision`. Details: [config-revisions.md](config-revisions.md).
