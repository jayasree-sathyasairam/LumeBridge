.PHONY: up down ps logs build run test test-integration bench bench-gate bench-lock bench-step5 verify-infra clean

# If SDKMAN is installed, each Java target sources it and runs: sdk use java $(JAVA_VERSION)
# Override: make JAVA_VERSION=21.0.6-tem build
JAVA_VERSION ?= 21.0.5-tem

# ── Infrastructure ──────────────────────────────────────────────

up:
	sh infra/compose.sh up -d

down:
	sh infra/compose.sh down

ps:
	sh infra/compose.sh ps

logs:
	sh infra/compose.sh logs -f

verify-infra:
	sh infra/verify-infra.sh

# ── Gateway (Java 21 + Virtual Threads) ────────────────────────

build:
	@bash -c 'set -e; \
	  if [ -f "$$HOME/.sdkman/bin/sdkman-init.sh" ]; then . "$$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java "$(JAVA_VERSION)"; fi; \
	  cd "$(CURDIR)/core/java" && mvn -q package -DskipTests'

run: build
	@bash -c 'set -e; \
	  if [ -f "$$HOME/.sdkman/bin/sdkman-init.sh" ]; then . "$$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java "$(JAVA_VERSION)"; fi; \
	  export CONFIG_FILE="$(CURDIR)/lumebridge.yaml"; \
	  export PROFILE="$(PROFILE)"; \
	  cd "$(CURDIR)/core/java" && java --enable-preview -jar target/lumebridge-core.jar'

test:
	@bash -c 'set -e; \
	  if [ -f "$$HOME/.sdkman/bin/sdkman-init.sh" ]; then . "$$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java "$(JAVA_VERSION)"; fi; \
	  export CONFIG_FILE="$(CURDIR)/lumebridge.yaml"; \
	  cd "$(CURDIR)/core/java" && mvn test'

# test-integration:
# 	@bash -c 'set -e; \
# 	  if [ -f "$$HOME/.sdkman/bin/sdkman-init.sh" ]; then . "$$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java "$(JAVA_VERSION)"; fi; \
# 	  export DOCKER_HOST="unix:///Users/ramkiranbalaji/.docker/run/docker.sock"; \
# 	  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/Users/ramkiranbalaji/.docker/run/docker.sock"; \
# 	  export TESTCONTAINERS_RYUK_DISABLED=true; \
# 	  export TESTCONTAINERS_CHECKS_DISABLE=true; \
# 	  cd "$(CURDIR)/core/java" && mvn test -Pintegration'

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
	./scripts/bench-runner.sh

# ── Cleanup ─────────────────────────────────────────────────────

clean:
	@bash -c 'if [ -f "$$HOME/.sdkman/bin/sdkman-init.sh" ]; then . "$$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java "$(JAVA_VERSION)"; fi; \
	  cd "$(CURDIR)/core/java" && mvn -q clean 2>/dev/null || true'
