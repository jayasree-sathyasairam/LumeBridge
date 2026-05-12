# LumeBridge Operations & Setup Guide

Complete setup and operations guide for **Windows 10** and **Apple M1 Mac**.

---

## Part 1: Setup & Installation

### System Requirements

| OS | Version | Processor | RAM | Disk Space |
|---|---|---|---|---|
| **Windows** | 10 Build 19041+ | Intel/AMD | 8 GB | 20 GB |
| **Mac** | 11 (Big Sur)+ | Apple Silicon (M1+) or Intel | 8 GB | 20 GB |

---

### Windows 10 Setup

#### Step 1: Install Package Manager (Chocolatey)

Run **PowerShell as Administrator**:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser -Force
iex ((New-Object System.Net.ServicePointManager).SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072); iex (New-Object Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1')
```

Verify installation:
```powershell
choco --version
```

#### Step 2: Install Required Tools

Run **PowerShell as Administrator**:

```powershell
choco install make git docker-desktop -y
```

This installs:
- **make** — For running Makefile targets (build, test, run)
- **git** — Version control and Git Bash
- **docker-desktop** — Containerization for infrastructure

#### Step 3: Install Java & Maven via SDKMAN

Open **Git Bash** (search for "Git Bash" in Start Menu):

```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash

# Reload shell
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 21
sdk install java 21.0.5-tem

# Install Maven
sdk install maven

# Set defaults
sdk default java 21.0.5-tem
sdk default maven
```

#### Step 4: Verify Everything

Open a **new Git Bash** window:

```bash
java -version
mvn -version
make --version
```

All three should display versions without errors.

#### Step 5: Configure Docker Desktop

1. Open **Docker Desktop** from Start Menu (takes 30 seconds to start)
2. Click **Settings** (gear icon)
3. Go to **Resources → WSL Integration**
4. Toggle **"Enable integration with my default WSL distro"**
5. Click **"Apply & Restart"**

#### Step 6: Verify Docker

```bash
docker --version
docker run hello-world
```

---

### Apple M1 Mac Setup

#### Step 1: Install Package Manager (Homebrew)

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Verify:
```bash
brew --version
```

#### Step 2: Install Required Tools

```bash
brew install make git docker
```

This installs:
- **make** — For running Makefile targets
- **git** — Version control
- **docker** — Container runtime (will prompt to install Docker Desktop)

#### Step 3: Install Java & Maven via SDKMAN

```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash

# Reload shell
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 21
sdk install java 21.0.5-tem

# Install Maven
sdk install maven

# Set defaults
sdk default java 21.0.5-tem
sdk default maven
```

#### Step 4: Verify Everything

Open a **new terminal**:

```bash
java -version
mvn -version
make --version
```

All three should display versions without errors.

#### Step 5: Start Docker Desktop

1. Open **Docker** from Applications
2. Authorize with your Mac password
3. Wait for Docker daemon to start (~30 seconds)

#### Step 6: Verify Docker

```bash
docker --version
docker run hello-world
```

---

## Part 2: Quick Start

Once setup is complete, navigate to the LumeBridge directory and run:

```bash
# 1. Start infrastructure (Postgres, Redis, Redpanda)
make up

# 2. Verify infrastructure health
make verify-infra

# 3. Run unit tests (73+ plugins)
make test

# 4. Build & run (choose one profile):

# API Gateway mode (no AI)
PROFILE=api-pro make run

# AI Gateway mode (with intelligent routing)
PROFILE=ai-pro make run

# Full hybrid mode (all plugins)
PROFILE=hybrid make run
```

---

## Part 3: Deployment Profiles

Select a profile based on your use case:

| Profile | Purpose | Included Plugins |
|---|---|---|
| **api-minimal** | Secure Core API | Auth, Semaphore, Hasher, Lock, Persistence, Dual-Mode Router |
| **ai-minimal** | Secure Core AI | `api-minimal` + Intelligent Model Router |
| **api-pro** | **Production API** | `api-minimal` + Quota, Normalizer, Nonce Ordering, Collision Detection, Smart Retry, Circuit Breaker, Lock Metrics, Exporter, Cleaner |
| **ai-pro** | **Production AI** | `ai-minimal` + Intent Classifier, Semantic Cache, Safety Guardrails, PII Scrubbing, Response Evaluator, Token Meter, Smart Retry, Circuit Breaker |
| **hybrid** | **Full Gateway** | **All 25+ plugins** (Pro API + Pro AI + DLQ + Telemetry) |

Run with a profile:
```bash
PROFILE=api-pro make run
PROFILE=hybrid make build
```

---

## Part 4: Build & Test Commands

| Command | Purpose |
|---------|---------|
| `make build` | Compile project to JAR |
| `make test` | Run unit tests |
| `make run` | Build and run application |
| `make clean` | Remove build artifacts |
| `make up` | Start Docker infrastructure |
| `make down` | Stop Docker infrastructure |
| `make ps` | Show running containers |
| `make logs` | Stream container logs |
| `make verify-infra` | Health check on infrastructure |

---

## Part 5: Sanity Checks

Once the gateway is running (`make run`), verify it from another terminal:

```bash
# Test Authentication & Core Pipeline
curl -X POST http://localhost:8080/task \
  -H "X-API-Key: sk-sentinel-user123" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Hello", "nonce": 1}'

# Expected response:
# {"status": "success", "result": "..."}
```

---

## Part 6: Plugin Management

Plugins are controlled via `lumebridge.yaml`. You can enable/disable individual plugins or override their settings.

### View Active Plugins

```bash
curl -s http://localhost:8080/metrics | jq
```

### Manual Plugin Toggles

Edit `lumebridge.yaml`:

```yaml
plugins:
  smart-retry:
    enabled: true
    max_retries: 3
  safety-guardrails:
    enabled: true
  semantic-cache:
    enabled: false
```

---

## Part 7: Infrastructure Operations

### Redis (Locks, Quotas, Nonces)

```bash
# View lock keys
redis-cli KEYS "lock:*"

# Check contention
redis-cli KEYS "lock_contention:*"

# Emergency cleanup
redis-cli KEYS "lock:*" | xargs redis-cli DEL
```

### Postgres (Tasks, Cache)

```bash
# Access database
psql -h localhost -U lumebadmin -d lumebridge

# Semantic cache requires pgvector
# Vector similarity search is handled via SQL

# Manual vacuum for performance
psql -c "VACUUM ANALYZE tasks;"
```

### Redpanda (DLQ, Telemetry)

```bash
# View DLQ topics
rpk topic list

# Consume failed tasks from DLQ
rpk topic consume sentinel-dlq --brokers localhost:19092
```

---

## Part 8: Scaling & Resilience

### The Concurrency Gate

The `THROTTLE_PERMITS` variable controls max concurrent requests:

```bash
THROTTLE_PERMITS=100 PROFILE=api-pro make run
```

**Saturation**: If `active_permits` == `max_permits`, the gate is full. Scale horizontally or increase permits.

### Circuit Breaker & Retries

- **Circuit Breaker**: Trips when a backend fails consistently, fast-failing subsequent requests to prevent cascade failure
- **Smart Retry**: Handles transient errors (503, Timeouts) with exponential backoff and jitter

### Horizontal Scaling

Run multiple instances behind a Load Balancer. Redis handles distributed locking automatically:

```bash
# Terminal 1
THROTTLE_PERMITS=50 PROFILE=api-pro make run

# Terminal 2
THROTTLE_PERMITS=50 PROFILE=api-pro make run

# Load balancer distributes requests across both instances
```

---

## Part 9: Troubleshooting & Resetting

### 🔄 Resetting the Environment

If you encounter database schema issues or need to wipe all data:

```bash
# Stop and remove containers + volumes
make down

# Start fresh infrastructure
make up

# Wait for health checks
make verify-infra

# Restart gateway
PROFILE=api-pro make run
```

### 🏥 Common Issues

| Symptom | Cause | Resolution |
|---|---|---|
| `409 Conflict` | Orphaned locks | Wait for TTL (30s) or clear Redis: `redis-cli FLUSHALL` |
| `High wait_ms` | Gate saturation | Increase `THROTTLE_PERMITS` or scale horizontally |
| `NoClassDefFound` | Missing dependencies | Ensure you're using the full shaded JAR from `make build` |
| `DB connection refused` | Database not ready | Wait 30 seconds after `make up` or run `make verify-infra` |
| `pgvector missing` | Postgres version issue | Use `pgvector/pgvector:latest` image in docker-compose |
| `400 Safety` | Prompt triggered safety rules | Check `lumebridge.yaml` safety-guardrails config |
| `docker not found` | Docker daemon not running | Start Docker Desktop from Applications |
| `make: command not found` | Make not installed | Run Chocolatey/Homebrew install (Step 2 above) |

### Resetting on Windows 10

```powershell
# Terminal 1 - Stop infrastructure
make down

# Verify containers are gone
docker ps

# Start fresh
make up
Start-Sleep -Seconds 30
make verify-infra

# Terminal 2 - Run tests
make test

# Terminal 3 - Run gateway
$env:PROFILE="api-pro"
make run

# Terminal 4 - Test API
curl -X POST http://localhost:8080/task `
  -H "X-API-Key: sk-sentinel-user123" `
  -H "Content-Type: application/json" `
  -d '{"prompt": "Hello", "nonce": 1}'
```

---

## Part 10: Runtime vs Build-time Dependencies

### JAR Size & Plugin Dependencies

- **JAR Size**: Disabling a plugin at runtime (YAML) does **not** remove its library from the fat JAR
- **Class Loading**: Classes for disabled plugins are typically not loaded into JVM memory unless referenced

### Creating Minimal Artifacts

To create a truly minimal artifact (e.g., no Kafka libraries for `api-minimal`), use Maven profiles:

```bash
mvn -q package -P api-minimal
```

This requires Maven profiles defined in `pom.xml` (planned for future versions).

---

## Part 11: Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| `PROFILE` | `api-pro` | Deployment profile (api-minimal, api-pro, ai-pro, hybrid) |
| `CONFIG_FILE` | `lumebridge.yaml` | Configuration file path |
| `THROTTLE_PERMITS` | `50` | Max concurrent requests |
| `JAVA_VERSION` | `21.0.5-tem` | Java version (override: `make JAVA_VERSION=21.0.6-tem build`) |

---

## Part 12: Related Documents

- [HLD](hld.md) — Architecture overview
- [LLD](lld.md) — Internal plugin logic
- [Data Flow](data-flow.md) — Request lifecycle diagrams
- [API Spec](api-spec.md) — Endpoint definitions

---

## Getting Help

- Check logs: `make logs`
- Verify infrastructure: `make verify-infra`
- Reset environment: `make down && make up && make verify-infra`
- For persistent issues, share error output from `make logs` and `docker ps`

Last updated: 2026-05-12
