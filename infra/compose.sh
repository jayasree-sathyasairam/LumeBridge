#!/usr/bin/env bash
# Run Docker Compose v2 from repo root: prefer `docker compose`, else `podman compose`.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if command -v docker >/dev/null 2>&1; then
  exec docker compose -p lumebridge -f infra/docker-compose.yml "$@"
elif command -v podman >/dev/null 2>&1; then
  exec podman compose -p lumebridge -f infra/docker-compose.yml "$@"
else
  echo "Neither 'docker' nor 'podman' was found on your PATH." >&2
  echo "Install one of:" >&2
  echo "  • Docker Desktop — https://docs.docker.com/get-docker/" >&2
  echo "  • Podman (Compose v2) — https://podman.io/docs/installation" >&2
  exit 1
fi
