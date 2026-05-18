.PHONY: up down ps logs build run test test-integration bench bench-gate bench-lock bench-step5 stress-test stress-test-k6-api stress-test-k6-ai verify-infra clean

# If SDKMAN is installed, each Java target sources it and runs: sdk use java $(JAVA_VERSION)
# Override: make JAVA_VERSION=21.0.6-tem build
JAVA_VERSION ?= 21.0.5-tem
CONFIG_FILE ?= $(CURDIR)/lumebridge.yaml
PROFILE ?=
# POSIX Bash for infra/stress scripts. On Windows, `bash` often hits a broken WSL relay; default to Git Bash when installed.
ifeq ($(OS),Windows_NT)
  _GIT_BASH := $(wildcard $(PROGRAMFILES)/Git/bin/bash.exe)
  ifeq ($(_GIT_BASH),)
    _GIT_BASH := $(wildcard $(LOCALAPPDATA)/Programs/Git/bin/bash.exe)
  endif
  ifneq ($(_GIT_BASH),)
    BASH ?= $(_GIT_BASH)
  else
    BASH ?= bash
  endif
else
  BASH ?= bash
endif
# Override anytime: make stress-test BASH=/path/to/bash
# Export so Java sees CONFIG_FILE / PROFILE on Windows too (cmd.exe ignores POSIX VAR=value cmd syntax).
export CONFIG_FILE
export PROFILE

# k6 benchmarks (Mac/Linux/Windows): binary name or full path if not on PATH.
K6 ?= k6
TARGET_URL ?= http://localhost:8080

# Run with a deployment profile (works everywhere; overrides PROFILE ?= above):
#   make run PROFILE=api-pro
#   make run PROFILE=ai-pro
#   make run PROFILE=hybrid

# ── Infrastructure ──────────────────────────────────────────────

up:
	"$(BASH)" infra/compose.sh up -d

down:
	"$(BASH)" infra/compose.sh down

ps:
	"$(BASH)" infra/compose.sh ps

logs:
	"$(BASH)" infra/compose.sh logs -f

verify-infra:
	"$(BASH)" infra/verify-infra.sh

# ── Gateway (Java 21 + Virtual Threads) ────────────────────────

build:
	cd core/java && mvn -q package -DskipTests

# Pass CONFIG_FILE as absolute path so `cd core/java` still finds repo-root lumebridge.yaml.
# CONFIG_FILE and PROFILE are set via export (above) so Windows cmd.exe recipes still work.
run: build
	cd core/java && java --enable-preview -jar target/lumebridge-core.jar

test:
	cd core/java && mvn test

# test-integration:
# 	cd core/java && mvn test -Pintegration

# ── Benchmarks ──────────────────────────────────────────────────

bench: bench-gate bench-lock

bench-gate:
	@echo "=== Concurrency Gate Benchmark ==="
	$(K6) run -e TARGET_URL=http://localhost:8080 benchmarks/k6/throttler_test.js

bench-step5:
	@echo "=== Step 5 intelligence (enable dual-mode-router + intent-classifier in lumebridge.yaml) ==="
	$(K6) run -e TARGET_URL=http://localhost:8080 benchmarks/k6/step5_intelligence.js

bench-lock:
	@echo "=== Distributed Lock Benchmark ==="
	$(K6) run -e TARGET_URL=http://localhost:8080 benchmarks/k6/lock_test.js

# Portable stress runner: Bash + Python + k6 (Git Bash / WSL / macOS / Linux).
stress-test:
	@echo "=== 10k stress: api-pro + ai-pro (scripts/bench-runner.sh - needs bash, k6, python3) ==="
	"$(BASH)" "$(CURDIR)/scripts/bench-runner.sh"

# Start the gateway in another terminal, then run ONE of these (k6 only).
stress-test-k6-api:
	@echo "=== k6 stress (API payloads). Gateway must be running: make run PROFILE=api-pro ==="
	$(K6) run -e TARGET_URL=$(TARGET_URL) -e TEST_TYPE=API benchmarks/k6/stress_test.js

stress-test-k6-ai:
	@echo "=== k6 stress (AI payloads). Gateway must be running: make run PROFILE=ai-pro ==="
	$(K6) run -e TARGET_URL=$(TARGET_URL) -e TEST_TYPE=AI benchmarks/k6/stress_test.js

# ── Cleanup ─────────────────────────────────────────────────────

clean:
	cd core/java && mvn -q clean
