#!/bin/sh
podman run -d --name befive-pg \
  -e POSTGRES_USER=befive \
  -e POSTGRES_PASSWORD=befive \
  -e POSTGRES_DB=befive \
  -p 5432:5432 \
  -v befive-pgdata:/var/lib/postgresql/data \
  docker.io/library/postgres:17
