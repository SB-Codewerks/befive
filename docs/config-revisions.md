# Configuration lifecycle, revisions, migrations and feature levels

**Relevance:** 0.x: build (milestones 1, 2 and 6). **Sources:** 02 §5.5 (revisions and concurrency), §6 (config lifecycle), §21 (migrations, rolling upgrades, feature levels, compatibility), 01 §2.12.

## 1. Write path: one place bumps revisions (02 §6.2)

1. AuthN and RBAC (02 §15.6).
2. **Coerce** the body: JSON keyword strings → keywords, guided by the spec registry. Then **apply defaults explicitly** (`befive.schema.defaults/apply-defaults`) so stored documents are explicit and diffs are stable. The source design used malli transformers here; see [validation-clojure-spec.md](validation-clojure-spec.md).
3. **Validate** the document (`s/valid?` on the entity spec plus closed-key enforcement). Errors come from `s/explain-data` mapped to `{path code message}`.
4. **Cross-entity rules** (`befive.schema.validate`, shared with the CLI and console): references exist, plugin configs match their registered specs, route conflicts are errors, shadowed routes are warnings, default deny holds, and the policy-lock check passes.
5. **Feature-level check** (§5 below). A failure returns `409 cluster-upgrade-in-progress`.
6. **One transaction:**
   1. `SELECT … FOR UPDATE` on `config_state`
   2. write rows
   3. `revision = revision + 1`
   4. `config_change` rows (with before and after)
   5. the audit event (hash-chained)
   6. `NOTIFY befive_config, '<revision>'`
7. Respond with the document, `ETag` and `X-BeFive-Revision`.

Every path (REST, `b5ctl apply`, console import, rollback, 1.0 promotion) ends in `config-service/commit!`.

## 2. Propagation (02 §6.3)

- Gateways `LISTEN befive_config` on a dedicated connection, with a 250 ms debounce. They also **poll every 10 s** as a fallback.
- On a new revision, a gateway reads the **delta** from `config_change` (revisions after its applied one). If the delta is unavailable (a gap after retention pruning), it loads a **full snapshot**.
- Delta-merge must equal full load. A property test enforces this.

## 3. Compile, swap and LKG (02 §6.4–§6.7)

- `befive.gateway.compile/compile-snapshot : model → RouteTable` is pure and runs on a virtual thread.
  - Host routers: exact, then wildcard by longest suffix, then default.
  - reitit routers per host, with candidates ordered by specificity, then priority, then id.
  - Secrets are decrypted once per revision.
  - SslContexts are cached by sha256.
  - Policies are compiled into closures.
  - Plugins are instantiated through `:compile`.
- Draft 4 steps (0.x): expand operations, resolve effective policies (with clamping), compile claims.
- **All-or-nothing.** On failure, the node keeps its current revision, sets `apply_error` in its heartbeat, logs at ERROR, and retries on the next revision or every 60 s.
- Budget: **under 200 ms for 2,000 routes and 100k credentials** on 4 vCPU.
- **Swap.** The handler derefs the atom **once per request**. Long-lived resources (pools, SslContexts, buckets) live in registries and are reaped after a grace period (pools: 60 s).
- **LKG** files are `/var/lib/befive/lkg/snapshot-<rev>.edn.gz`.
  - Write them by temp file + fsync + rename, keep the newest 3, include a sha256.
  - The header carries the wrapped DEKs. The body holds secret ciphertexts only.
  - If the DB is unreachable for 10 s at startup, load the newest valid LKG and run with `config_source=lkg` and status degraded.
  - On ECS, use EFS if LKG must survive task replacement (optional).
- **Heartbeat** every 5 s: `gateway_node` row with applied revision, status, config source, apply error, version, feature level, plugins, last seen.
  - In sync: applied revision equals the current revision.
  - Applying: behind, and the revision is less than 10 s old.
  - Behind: behind for more than 10 s, or an error is set.
  - Stale: not seen for more than 15 s.
  - Gone: not seen for more than 5 min (hidden after 24 h, purged after 7 d).
- `GET /admin/v1/config/revisions/{rev}/status` reports apply progress. `b5ctl apply --wait` uses it.

## 4. Rollback (02 §6.8)

"Roll back to revision r" computes the model at r from `config_change`, diffs it against the current model, and commits a **new forward revision** after the same confirmation flow. History is never rewritten.

**Retention of `config_change` (resolved inconsistency):**
- 02 §5.4 says "30 days and at least the last 10,000 revisions".
- 02 §33.1 (Draft 4) says `:config-change-days 365` with a minimum of 30.
- Handoff default H-11: **365 days, never fewer than the last 10,000 revisions, minimum setting 30 days.** This is the rollback window.

## 5. Concurrency (02 §5.5, §15.4)

- Per row, `version` is the ETag (`"route:orders-get:7"`). PUT, PATCH and DELETE require `If-Match`: a missing header returns 428 and a mismatch returns 412 with the current document. `If-None-Match: *` means create-only.
- **Bulk apply** uses `base-revision`. If the revision moved and the recomputed diff differs, the call returns `409 revision-conflict` with the new diff.

## 6. Migrations (02 §21.1; owner default: numbered SQL)

- Migratus SQL lives in `modules/control-plane/resources/migrations/`. **Files are sequence-numbered**: `0001-init.up.sql`, `0001-init.down.sql`, `0002-…`. The source says "numbered by timestamp"; the owner chose sequence numbers. Merge conflicts on numbers are resolved by renumbering the unmerged branch.
- Only the control plane runs them, under `pg_advisory_lock`. `BEFIVE_MIGRATE_ON_START=false` together with `java -jar befive.jar migrate` lets DBAs run them by hand.
- **Expand/contract.** Each release's migrations work with the previous minor's gateways and control plane. Additive changes ship at once (indexes `CONCURRENTLY`). Destructive changes ship one minor release later.
- Down migrations are for development only. A production rollback means restoring the backup taken before the upgrade.
- CI fails any migration that holds an exclusive lock for more than 1 s on a production-sized synthetic dataset.
- Supported PostgreSQL versions are **15, 16 and 17**. The source said 14+; the owner default is 15–17. CI tests 15 and 17, and docker-compose uses 16.

## 7. Rolling upgrades and feature levels (02 §21.2–§21.3)

- Upgrade the control plane first (it runs the migrations), then roll the gateways.
  - An N−1 gateway works with an N database and control plane.
  - Mixed clusters are supported for adjacent minors only.
- Documents carry no version field. `:befive/format 1` is additive: new optional fields and enum values only.
- **Feature level.** Each release has an integer feature level.
  - Every spec addition is tagged in the form-metadata registry: `(meta/register! :befive.route/foo {:befive/since 2})`.
  - The cluster feature level is the minimum over live gateways. A write using a newer feature gets `409 cluster-upgrade-in-progress`.
  - Gateways reject unknown keys in security-relevant positions.
- **Resolved inconsistency (handoff default H-1).** 02 §21.3 says the Draft 4 kinds "carry the 1.0 feature level; a 0.x gateway never receives them". That contradicts 01 §4.1 and §4.2, which put APIs, locks, claims, Lambda, internal JWT and the new rate-limit dimensions in 0.x. 01 wins:
  - **Feature level 1 = 0.1.0** and includes everything 0.x ships.
  - 1.0-only kinds (cache, composites, async, discovery, classification, overlays, runtime overrides, …) get the level of the release that introduces them.

## 8. Compatibility promises (02 §21.4)

These apply from 1.0, but practice them in 0.x:
- `/admin/v1` is additive only.
- Config files valid in release R stay valid.
- The log schemas `befive.access/1`, `metrics/1`, `usage/1` and `audit/1` and the EMF metric names are never renamed.
- Plugin SPI 1 stays compatible.
- CLI commands, flags and exit codes are stable.
- License format 1 stays valid.

The 0.x releases are pre-1.0, so a breaking change is allowed. It must be called out with `!` in the commit and in the release notes.
