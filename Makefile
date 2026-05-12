.PHONY: up down ps logs build run test test-integration bench bench-gate bench-lock bench-step5 verify-infra clean

# If SDKMAN is installed, each Java target sources it and runs: sdk use java $(JAVA_VERSION)
# Override: make JAVA_VERSION=21.0.6-tem build
JAVA_VERSION ?= 21.0.5-tem
CONFIG_FILE ?= $(CURDIR)/lumebridge.yaml
PROFILE ?=

# ── Infrastructure ──────────────────────────────────────────────

up:
	bash infra/compose.sh up -d

down:
	bash infra/compose.sh down

ps:
	bash infra/compose.sh ps

logs:
	bash infra/compose.sh logs -f

verify-infra:
	bash infra/verify-infra.sh

# ── Gateway (Java 21 + Virtual Threads) ────────────────────────

build:
	cd core/java && mvn -q package -DskipTests

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
	k6 run -e TARGET_URL=http://localhost:8080 benchmarks/k6/throttler_test.js

bench-step5:
	@echo "=== Step 5 intelligence (enable dual-mode-router + intent-classifier in lumebridge.yaml) ==="
	k6 run -e TARGET_URL=http://localhost:8080 benchmarks/k6/step5_intelligence.js

bench-lock:
	@echo "=== Distributed Lock Benchmark ==="
	k6 run -e TARGET_URL=http://localhost:8080 benchmarks/k6/lock_test.js

stress-test:
	@echo "=== Running Full 5-Profile 10k Stress Test ==="
	bash scripts/bench-runner.sh

# ── Cleanup ─────────────────────────────────────────────────────

clean:
	cd core/java && mvn -q clean
