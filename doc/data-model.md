# Data model

Single Postgres database. Schema migrations live in
`resources/migrations/` and are applied via Migratus.

## Tables

### `users`
Mirror of Okta identities we've seen. Populated lazily on first
successful JWT verification.

    id           uuid primary key
    okta_sub     text unique not null
    email        text not null
    created_at   timestamptz not null default now()
    last_seen_at timestamptz

### `roles`
Coarse-grained role definitions.

    id    uuid primary key
    name  text unique not null  -- e.g. 'admin', 'reader', 'svc-billing'

### `user_roles`
Many-to-many.

    user_id uuid references users(id) on delete cascade
    role_id uuid references roles(id) on delete cascade
    primary key (user_id, role_id)

### `permissions`
What a role may do. `route_pattern` matches Reitit's route name
(not raw URI) to keep befive gateway and DB in sync.

    id            uuid primary key
    role_id       uuid references roles(id) on delete cascade
    api           text not null         -- 'billing', 'inventory', ...
    route_name    text not null         -- ':api.billing/invoice-detail'
    method        text not null         -- 'GET', 'POST', ...
    unique (role_id, api, route_name, method)

### `rate_limits`
Configured limits, scoped either to a role or to an individual user.

    id          uuid primary key
    scope_type  text not null check (scope_type in ('role','user'))
    scope_id    uuid not null
    window_sec  integer not null   -- e.g. 60
    max_calls   integer not null   -- e.g. 1000
    api         text               -- nullable = applies to all APIs
    unique (scope_type, scope_id, api, window_sec)

### `rate_limit_counters`
Live counters. Window is identified by truncated timestamp.

    scope_type   text not null
    scope_id     uuid not null
    api          text not null
    window_start timestamptz not null
    count        integer not null default 0
    primary key (scope_type, scope_id, api, window_start)

A partial index on `window_start` newer than 1h supports cheap
upsert-and-read. Old rows are pruned by a scheduled job.

### `requests` (partitioned by day)
Audit log. Range-partitioned on `received_at`. Use `pg_partman`
or a cron-driven CREATE TABLE for new partitions.

    id            bigserial
    received_at   timestamptz not null
    user_id       uuid references users(id)
    api           text not null
    route_name    text
    method        text not null
    path          text not null
    upstream_url  text
    status        smallint
    duration_ms   integer
    error_type    text             -- ex-info :type when applicable
    request_id    uuid not null    -- correlation id; in mulog events too
    primary key (id, received_at)  -- partition key must be in PK

## Indexes

- `users (okta_sub)` — unique, hot path on every request
- `permissions (role_id, api, route_name, method)` — composite
  matches the lookup shape
- `requests_<date> (received_at)` — implicit via partitioning
- `requests_<date> (user_id, received_at desc)` — for per-user query
- `requests_<date> (api, route_name, received_at desc)` — for
  per-route usage reporting

## Retention

`requests` partitions older than 90 days are dropped by a nightly job.
`rate_limit_counters` rows older than the largest configured window
are deleted hourly.
