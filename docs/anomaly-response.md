# Anomaly detection and scripted response

**Relevance:** **1.0: context.** Nothing here is built in 0.x. Two 0.x obligations follow from it:
1. Keep the aggregators able to emit closed per-minute generations, which the signal shipper will consume.
2. Reserve the `override.*` error codes and slot 6/13 enforcement hooks.

**Sources:** 01 §2.19, decisions 7–9, 11, 31, 34. 02 §14 (all), §15.6. 03 §5.14.3, §5.15–§5.19, mockups `incident-detail.png` and `anomaly-rules.png`.

## Scope by release (01 decisions 11, 31, 34)

- **1.0:**
  - Detection on exact per-minute summaries.
  - Incidents and grouping, silences and maintenance windows.
  - Declarative response rules and sandboxed Clojure scripts.
  - **ServiceNow incidents** (Washington DC release or later; owner default).
  - Slack and signed-webhook notifications.
  - External sources: CloudWatch alarms, SNS and the generic webhook. Alertmanager connects through the generic format.
  - Traffic actions limited to **IP block** and **rate-limit tightening**.
- **1.1:**
  - LLM root-cause analysis (advisory only, never triggers actions; provider choice is decided at 1.1).
  - ServiceNow Event Management.
  - Teams and email anomaly notifications.
- **1.1 or later:** consumer block and route disable.

## Architecture (02 §14.2–§14.4)

- Gateways write one `summary_inbox` row per minute to PG through the signal shipper. They never call the control plane.
- The control-plane **leader** holds `pg_try_advisory_lock(hashtext('befive.anomaly'))`, retried every 10 s. It merges, detects, groups and runs rules.
- **Retention:**
  - inbox: 2 h
  - per-minute signals: 8 d (consumer series: 3 d)
  - 5-min rollups: 35 d
  - **source-IP lists: 24 h by default, at most 7 d** (owner default)
  - incidents: 400 d
- The `anomaly` module is `.cljc`, so `b5ctl anomaly replay` and the console previews can run it offline.

## Detectors and lifecycle (02 §14.5–§14.8)

- **Signals:** rates from sums, latency quantiles, error codes, Space-Saving IP sketches, exemplars.
- **Detectors** are config entities:
  - signal, selection and condition (EWMA, median/MAD, seasonal baselines)
  - M-of-N confirmation, recovery hysteresis, guards, severity
- **Anomaly lifecycle:** open → resolved. Deduplication and deterministic grouping into incidents. Silences suppress actions but not records.

## Rules, scripts and actions (02 §14.9–§14.11)

- **Rules** match incident events in priority order; `:stop` ends evaluation.
- **Scripts:**
  - Define a pure `respond` function that turns an event context into action requests.
  - Run in a separate **native SCI runner** with an allow-list and no I/O, interop, clock or randomness.
  - Time and memory limits apply. A script is suspended after 5 failures.
- **Action executor:** an outbox with idempotency keys and retries. Actions run through integrations: ServiceNow, Slack, webhook, and email in 1.1.

## Traffic actions and safety rails (02 §14.12)

- **Mechanism:** a `runtime_override` entity bumps the revision and reaches gateways through normal propagation. Each gateway enforces `:expires-at` on its own clock, so auto-revert does not depend on the control plane. Overrides are excluded from export and sync.
- **Modes**, settable globally and per kind:
  - `:off` (default)
  - `:dry-run`
  - `:approval`: expires after 15 min unapproved; configurable from 5 to 60
  - `:on`
- **Rails:**
  - duration at most 240 min (manual overrides: 24 h)
  - at most 20 IP blocks and 3 rate tightenings active at once
  - at most 10 actions per hour, and one per incident per kind per 30 min
  - scope at least /24 (IPv4) or /64 (IPv6); factor at least 0.25
  - protected networks, consumers and routes are never touched
  - an impact estimate that escalates to approval (for example, an IP block affecting more than 1% of traffic)
  - optional revert on resolve
- **Approvers: Administrators and Operators.** **Automation Managers configure modes and limits but cannot approve or revert** (owner default; closes 03 §10 Q9).

## ServiceNow (02 §14.13)

- An integration of kind `:servicenow` uses OAuth client credentials and the incident target: create, update and resolve, with a `correlation_id` for deduplication.
- **Supported releases: Washington DC and later** (owner default; closes 02 §35.1 Q12).
- Event Management (`em_event`) comes in 1.1.

## Data model and bounds (02 §14.15–§14.17)

- **New kinds:** `detector`, `response_rule`, `silence`, `integration`, `runtime_override`. Gateways consume only `runtime_override`.
- **Admin API:** `/detectors`, `/response-rules` (+ `/test`), `/anomaly/simulate`, `/silences`, `/integrations`, `/incidents`, `/actions`, `/approvals`, `/runtime-overrides`.
- **CLI:** `b5ctl anomaly replay|export-signals|test-script`, `incidents`, `silence`, `overrides`.
