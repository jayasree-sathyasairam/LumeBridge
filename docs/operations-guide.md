# Operations & Installation Guide

This guide covers everything needed to deploy, scale, and maintain the LumeBridge gateway.

---

## 1. Installation & Getting Started

### Prerequisites

| Tool | Version | Purpose | Install Command (Mac) |
|---|---|---|---|
| Docker / Compose | latest | Infrastructure | `brew install --cask docker`¹ |
| Java (JDK) | 21+ | Runtime | `brew install openjdk@21` or `sdk install java 21.0.2-tem` |
| Maven | 3.9+ | Build | `brew install maven` or `sdk install maven 3.9.6` |

¹ *Note: If you get a "Malicious Software" warning on Mac, go to **System Settings > Privacy & Security > Security** and click **Open Anyway**. If integration tests still fail, run: `echo "docker.host=unix:///var/run/docker.sock" > ~/.testcontainers.properties`.*

### Quick Start

```bash
# 1. Start infrastructure
make up

# 2. Verify infrastructure health
make verify-infra

# 3. Run Unit Tests (73+ plugins)
make test

# 4. Run Integration Tests (Optional - Requires Docker)
# make test-integration

# 5. Build & Run without AI (API Gateway mode)
PROFILE=api-pro make run

# 6. Build & Run with AI (AI Gateway mode)
PROFILE=ai-pro make run

# 7. Run Performance Benchmarks (k6)
# Note: Ensure the gateway is running in another terminal first!
make bench
```

---

## 2. Deployment Profiles

LumeBridge provides pre-configured plugin sets for different use cases. Use the `PROFILE` environment variable to select one.

| Profile | Purpose | Included Plugins |
|---|---|---|
| **api-minimal** | **Secure Core API** | Auth, Semaphore, Hasher, Lock, Persistence, Dual-Mode Router |
| **ai-minimal** | **Secure Core AI** | `api-minimal` + Intelligent Model Router |
| **api-pro** | **Production API** | `api-minimal` + Quota, Normalizer, Nonce Ordering, Collision Detection, Smart Retry, Circuit Breaker, Lock Metrics, Exporter, Cleaner |

---

## 3. Sanity Checks

Once the gateway is running (`make run`), verify it from another terminal:

```bash
# 1. Test Authentication & Core Pipeline
curl -X POST http://localhost:8080/task \
  -H "X-API-Key: sk-sentinel-user123" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Hello", "nonce": 1}'

# 2. Test PII Scrubbing (AI Pro only)
curl -X POST http://localhost:8080/task \
  -H "X-API-Key: sk-sentinel-user123" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "My email is test@example.com", "nonce": 2}'
```
| **ai-pro** | **Production AI** | `ai-minimal` + Intent Classifier, Semantic Cache, Safety Guardrails, PII Scrubbing, Response Evaluator, Token Meter, Smart Retry, Circuit Breaker |
| **hybrid** | **Full Gateway** | **All 25+ plugins** (Pro API + Pro AI + DLQ + Telemetry) |

---

## 3. Plugin Management

Plugins are controlled via `lumebridge.yaml`. You can enable/disable individual plugins or override their specific settings.

### Manual Plugin Toggles
```yaml
plugins:
  smart-retry:
    enabled: true
    max_retries: 3
  safety-guardrails:
    enabled: true
```

### Viewing Active Plugins
```bash
curl -s http://localhost:8080/metrics | jq
```

---

## 4. Scaling & Resilience

### The Concurrency Gate
The `THROTTLE_PERMITS` variable controls max concurrent requests.
- **Saturation**: If `active_permits` == `max_permits`, the gate is full.
- **Horizontal Scaling**: Run multiple instances behind a Load Balancer. Redis handles the distributed locking.

### Circuit Breaker & Retries
- **Circuit Breaker**: Trips when a backend fails consistently, fast-failing subsequent requests to prevent cascade failure.
- **Smart Retry**: Handles transient errors (503, Timeouts) with exponential backoff and jitter.

---

## 5. Infrastructure Operations

### Redis (Locks, Quotas, Nonces)
- **Monitoring**: `redis-cli KEYS "lock:*"`
- **Emergency Cleanup**: `redis-cli KEYS "lock:*" | xargs redis-cli DEL`
- **Contention**: Check `lock_contention:*` keys to identify hot-spots.

### Postgres (Tasks, Cache)
- **Semantic Cache**: Requires `pgvector`. Vector similarity search is handled via SQL.
- **Stale Data Cleaner**: Periodically deletes old tasks. Check status via `/metrics`.
- **Manual Vacuum**: `psql -c "VACUUM ANALYZE tasks;"`

### Redpanda (DLQ, Telemetry)
- **DLQ**: Failed tasks are published to the `sentinel-dlq` topic.
- **Consume DLQ**: `rpk topic consume sentinel-dlq --brokers localhost:19092`

---

## 6. Build & Dependency Management

### Runtime vs Build-time
- **JAR Size**: Disabling a plugin at runtime (YAML) does **not** remove its library from the fat JAR.
- **Class Loading**: Classes for disabled plugins are typically not loaded into JVM memory unless referenced.

### Minimal Artifacts
To create a truly minimal artifact (e.g., no Kafka libraries for `api-minimal`), you must use Maven profiles or split the project into multiple modules (planned for future versions).

---

## 7. Troubleshooting & Resetting

### 🔄 Resetting the Environment
If you encounter database schema issues or need to wipe all data and start fresh:
1. `sh scripts/compose.sh down -v` (Removes containers and volumes)
2. `make up` (Starts fresh infrastructure)
3. `PROFILE=api-pro make run` (Restarts gateway)

### 🏥 Common Symptoms

| Symptom | Cause | Resolution |
|---|---|---|
| 409 Conflict | Orphaned locks | Wait for TTL (30s) or clear Redis locks. |
| High wait_ms | Gate saturation | Increase `THROTTLE_PERMITS` or scale horizontally. |
| NoClassDefFound | Missing deps | Ensure you are using the full shaded JAR. |
| DB Errors | pgvector missing | Use the `pgvector/pgvector` image in Docker Compose. |
| 400 Safety | Malicious prompt | Prompt triggered `SafetyGuardrailsPlugin`. |

---

## Related Documents
- [HLD](hld.md) -- Architecture overview
- [LLD](lld.md) -- Internal plugin logic
- [Data Flow](data-flow.md) -- Request lifecycle diagrams
- [API Spec](api-spec.md) -- Endpoint definitions
