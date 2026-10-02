#!/usr/bin/env bash
# Create the local BeFive network and start the design-partner stack:
# gateway, PostgreSQL 16, Redis, LocalStack, Toxiproxy, the mock IdP,
# and the echo upstream.
set -euo pipefail

cd "$(dirname "$0")"

exec docker compose -p befive -f docker/docker-compose.yml up "$@"
