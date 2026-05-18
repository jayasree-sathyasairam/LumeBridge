#!/usr/bin/env bash
set -euo pipefail

POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-lumebridge}"
POSTGRES_DB="${POSTGRES_DB:-lumebridge_nexus}"

export PGPASSWORD="${POSTGRES_PASSWORD:-lumebridge}"

echo "Seeding sample tasks..."

psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<'SQL'
INSERT INTO tasks (payload_hash, payload, status, version) VALUES
  ('sample-hash-001', '{"type": "test", "data": "hello world"}', 'completed', 1),
  ('sample-hash-002', '{"type": "test", "data": "benchmark payload"}', 'pending', 1),
  ('sample-hash-003', '{"type": "ai", "query": "What is a circuit breaker?"}', 'pending', 1)
ON CONFLICT (payload_hash) DO NOTHING;
SQL

echo "Seed complete. $(psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c "SELECT COUNT(*) FROM tasks;") tasks in database."
