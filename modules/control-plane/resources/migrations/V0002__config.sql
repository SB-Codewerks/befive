create table config_snapshot (
  revision bigint primary key,
  document text not null,
  created_at timestamptz not null default now()
);

create table config_change (
  revision bigint not null,
  entity text not null,
  entity_id text not null,
  after_doc text,
  primary key (revision, entity, entity_id)
);

create table gateway_node (
  node_id text primary key,
  applied_revision bigint,
  status text,
  config_source text,
  apply_error text,
  version text,
  feature_level integer not null default 1,
  last_seen timestamptz
);
