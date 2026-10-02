create table befive_meta (
  id boolean primary key default true,
  constraint befive_meta_singleton check (id),
  schema_version integer not null
);

insert into befive_meta (schema_version) values (1);
