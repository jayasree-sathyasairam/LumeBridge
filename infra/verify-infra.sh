#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
COMPOSE="sh infra/compose.sh"

echo "== Docker Compose services =="
$COMPOSE ps

echo ""
echo "== Redis PING =="
$COMPOSE exec -T redis redis-cli ping

echo ""
echo "== Postgres readiness =="
$COMPOSE exec -T postgres pg_isready -U "${POSTGRES_USER:-sentinel}" -d "${POSTGRES_DB:-sentinel_nexus}"

echo ""
echo "== Redpanda cluster health =="
$COMPOSE exec -T redpanda rpk cluster health || true

echo ""
echo "All infrastructure checks finished."
