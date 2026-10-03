create table api (
  id text primary key,
  domain text,
  classification text,
  owners text[] not null default '{}',
  tags text[] not null default '{}',
  doc jsonb not null,
  version bigint not null default 1,
  revision bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table api_spec (
  sha256 text primary key,
  format text not null,
  openapi_version text not null,
  original bytea not null,
  normalized jsonb,
  source text,
  imported_at timestamptz not null default now()
);

create table api_version (
  api_id text not null references api (id) on delete cascade,
  version_id text not null,
  state text not null default 'design',
  service_id text not null,
  spec_sha256 text references api_spec (sha256),
  doc jsonb not null,
  version bigint not null default 1,
  revision bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (api_id, version_id)
);

create table operation (
  api_id text not null,
  version_id text not null,
  operation_id text not null,
  method text not null,
  path_template text not null,
  backend_kind text not null default 'proxy',
  doc jsonb not null,
  version bigint not null default 1,
  revision bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (api_id, version_id, operation_id),
  unique (api_id, version_id, method, path_template),
  foreign key (api_id, version_id)
    references api_version (api_id, version_id) on delete cascade
);

create table organization (
  id text primary key,
  name text not null,
  status text not null default 'active',
  plan_id text,
  doc jsonb not null,
  version bigint not null default 1,
  revision bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table consumer (
  id text primary key,
  organization_id text not null references organization (id),
  name text not null,
  status text not null default 'active',
  doc jsonb not null,
  version bigint not null default 1,
  revision bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table application (
  id text primary key,
  consumer_id text not null references consumer (id),
  name text not null,
  status text not null default 'active',
  environment_scope text not null default 'production',
  plan_id text,
  doc jsonb not null,
  version bigint not null default 1,
  revision bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table credential (
  id text primary key,
  application_id text not null references application (id),
  consumer_id text references consumer (id),
  kind text,
  doc jsonb not null,
  version bigint not null default 1,
  revision bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
