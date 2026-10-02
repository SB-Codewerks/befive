# Deployment, Docker and settings

**Relevance:** 0.x: build (milestone 1, then hardened in milestone 10). Helm charts come after 1.0. **Sources:** 01 §1, §2.18, decision 3. 02 §2 (topologies), §3.2 (build outputs), §4.1 (settings), §4.3–§4.4 (startup and shutdown), §20.3 (JVM), §21 (upgrades), §13.5 (report job).

## 1. Image and artifacts (02 §3.2)

- **Dockerfile** (multi-stage):
  1. Clojure CLI plus Node build the uberjar and the console and portal bundles.
  2. `jlink` builds a trimmed **Temurin 21** runtime.
  3. Minimal glibc base (Ubuntu Chiseled or Distroless `base`), UID 10001, read-only root filesystem. Writable paths: `/var/lib/befive/lkg`, `/tmp`.
  - The native `befive-script-runner` goes in `/opt/befive/bin/` (1.0).
  - Image tags: `befive:<semver>` and `befive:<semver>-debug`.
- **Artifacts:**
  - `befive-<ver>.jar`, AOT-compiled with direct linking.
  - `b5ctl` native binaries for linux-amd64, linux-arm64, macOS arm64 and Windows amd64, plus `b5ctl.jar`.
  - `plugin-api-<ver>.jar`.
  - The AWS recipe (Terraform and CloudFormation JSON) and the Datadog recipe.
  - SBOMs, cosign signatures and SLSA provenance.
  - The offline kit (1.0; optional in 0.x).
- **Registries:** GHCR first; push to ECR before partners get access.

## 2. Settings (02 §4.1)

Node-local settings live in Aero EDN (`resources/befive/settings.edn`). Environment variables and mounted `*_FILE` secrets override them. Everything else is configuration in the database.
- **An invalid setting exits with code 78.**
- Settings are validated with **spec** (`:befive.settings/settings`), and `explain-data` is printed in human form.

| Variable | Meaning |
|---|---|
| `BEFIVE_ROLE` | `all` (default), `gateway`, `control-plane` |
| `BEFIVE_NODE_ID` | Defaults to hostname plus a random suffix |
| `BEFIVE_ENVIRONMENT` | Environment name (dev, test, production, sandbox, …) |
| `BEFIVE_DB_URL`, `BEFIVE_DB_USER`, `BEFIVE_DB_PASSWORD` / `BEFIVE_DB_PASSWORD_FILE` | PostgreSQL 15–17 |
| `BEFIVE_MASTER_KEY_SOURCE` (`env`/`file`/`aws-kms`), `BEFIVE_MASTER_KEY` / `BEFIVE_MASTER_KEY_FILE`, `BEFIVE_KMS_KEY_ARN` (+ `_PREVIOUS` variants for rotation) | Keyring |
| `BEFIVE_LICENSE_FILE` | License file (optional) |
| `BEFIVE_EVAL` | Evaluation mode (allows a generated master key; 1 gateway node) |
| `BEFIVE_TRUSTED_PROXIES` | CIDRs of the ALB/NLB, for the XFF walk |
| `BEFIVE_REDIS_URI` | Optional Redis for rate limits |
| `BEFIVE_CACHE_REDIS_URI` | Optional cache L2 (1.0) |
| `BEFIVE_PLUGINS_DIR` | Plugin directory |
| `BEFIVE_HOOKS_ENABLED` | External anomaly hooks (1.0) |
| `BEFIVE_CP_SURFACES` | For example `portal` (a portal-only CP task) |
| `BEFIVE_BOOTSTRAP_TOKEN` | First-run token (otherwise printed once) |
| `BEFIVE_MIGRATE_ON_START` | Default `true` |
| `BEFIVE_JAVA_OPTS` | Extra JVM flags |
| `BEFIVE_TOKEN`, `BEFIVE_SERVER` | b5ctl only |

Ports are listed in architecture-overview.md.

## 3. Local development stack (owner default: mock IdP + LocalStack)

`docker/docker-compose.yml` runs:
- `befive` (`BEFIVE_ROLE=all`, `BEFIVE_EVAL=true`)
- `postgres:16` (CI also runs 15 and 17)
- `redis:7` (optional profile)
- **a mock OIDC issuer**: a small Clojure service in `test/support/mock-idp` that issues tokens, serves discovery and JWKS, offers introspection, and can rotate keys and go "down" through a control endpoint
- **LocalStack** (Lambda, KMS; SQS and S3 in 1.0)
- an echo upstream (httpbin-style)
- optionally Toxiproxy, the OTel collector and the Datadog Agent

Real Okta developer-org and AWS sandbox jobs are added once the owner provides those accounts. Track this in open-questions.md.

## 4. AWS reference deployment (02 §2.4). This is the design-partner focus

- ECS on Fargate (EKS is analogous).
- **The public ALB → gateway service** (8080; TLS terminates at the ALB, or 8443 for end-to-end TLS). Health check: `/readyz` on 9901.
- An internal ALB → the control-plane service (9000).
- The portal hostname → its own ALB listener with **AWS WAF** → 9100. This can be a portal-only CP task in a public subnet.
- RDS PostgreSQL Multi-AZ (15–17), optional ElastiCache Redis, KMS for the master key, Secrets Manager for `BEFIVE_DB_PASSWORD` through ECS `secrets`.
- Gateways need to reach only PG, Redis, the IdPs, the upstreams and Lambda. **They do not need the control plane.**
- `BEFIVE_TRUSTED_PROXIES` = the ALB subnets.
- ECS `stopTimeout` of 60 s. Optional EFS for LKG across task replacement.
- The AWS recipe (Terraform/CloudFormation) creates log groups, dashboards, alarms, saved queries and the usage-report schedule (EventBridge Scheduler → ECS RunTask `report`).

## 5. JVM defaults (02 §20.3–§20.4)

```
-XX:+UseZGC -XX:+ZGenerational  -XX:MaxRAMPercentage=70  -XX:MaxDirectMemorySize=1g
-XX:+ExitOnOutOfMemoryError  -XX:+AlwaysPreTouch  -Dio.netty.allocator.type=pooled
-Dio.netty.leakDetection.level=disabled   (paranoid in CI)   -Xlog:gc*:stderr:time,level,tags
```

- Sizing guidance, to be confirmed by benchmarks: gateway 4 vCPU / 8 GiB, control plane 1 vCPU / 2 GiB. `BEFIVE_JAVA_OPTS` appends to or overrides these flags.
- Use virtual threads only off the hot path: LISTEN, compile, LKG, heartbeat, quota flush, Admin API handlers and KMS calls.

## 6. Upgrades (02 §21)

- Upgrade the control plane first, then roll the gateways. See config-revisions.md §6–§7.

## 7. Not in scope

- Deferred beyond 1.0 (01 §2.21):
  - the Helm chart (the image already runs on Kubernetes)
  - SAML and SCIM
  - gRPC and upstream HTTP/2
  - multi-region sync
  - custom RBAC roles
  - monetization
