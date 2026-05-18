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

Do this from the **repository root** (`LumeBridge/`).

### 0. Copy configuration (macOS, Linux, Git Bash, PowerShell)

Default Postgres in `infra/docker-compose.yml` is user **`lumebridge`**, password **`lumebridge`**, database **`lumebridge_nexus`** — matching `config/lumebridge.example.yaml`.

**macOS / Linux / Git Bash:**

```bash
cp config/lumebridge.example.yaml lumebridge.yaml
# Edit lumebridge.yaml if you need provider keys or plugin toggles.
```

**Windows PowerShell:**

```powershell
Copy-Item -Path "config\lumebridge.example.yaml" -Destination "lumebridge.yaml"
```

---

### 1–3. Infrastructure and tests

Makefile targets **`up`**, **`down`**, **`ps`**, **`logs`**, and **`verify-infra`** invoke **Bash** (`infra/compose.sh`, `infra/verify-infra.sh`). Use one of the approaches below.

#### macOS / Linux / Git Bash (including Windows Git Bash)

**Recommended on Windows** for `make up` / `make verify-infra` (Makefile calls Bash scripts).

```bash
make up
make verify-infra
make test
```

#### Windows PowerShell (no Bash for infrastructure)

From repo root, run Docker Compose directly:

```powershell
docker compose -p lumebridge -f infra/docker-compose.yml up -d

docker compose -p lumebridge -f infra/docker-compose.yml exec -T redis redis-cli ping
docker compose -p lumebridge -f infra/docker-compose.yml exec -T postgres pg_isready -U lumebridge -d lumebridge_nexus
docker compose -p lumebridge -f infra/docker-compose.yml exec -T redpanda rpk cluster health

cd core\java; mvn test
cd ..\..
```

PowerShell equivalents for stopping / listing / logs:

```powershell
docker compose -p lumebridge -f infra/docker-compose.yml down
docker compose -p lumebridge -f infra/docker-compose.yml ps
docker compose -p lumebridge -f infra/docker-compose.yml logs -f
```

---

### 4. Build & run (choose one profile)

**Prerequisite:** Profiles **`api-pro`**, **`ai-pro`**, and **`hybrid`** enable plugins that connect to **PostgreSQL** (and Redis) at startup. Start infra **before** `make run`:

```bash
make up
make verify-infra
```

If you skip this, you will see **`Connection to localhost:5432 refused`** until Postgres is running.

The Makefile sets **`CONFIG_FILE`** to the repo-root **`lumebridge.yaml`** automatically when you use **`make run`**.

#### macOS / Linux / Git Bash

```bash
PROFILE=api-pro make run      # API Gateway mode (no AI)
PROFILE=ai-pro make run       # AI Gateway mode (intelligent routing)
PROFILE=hybrid make run       # Full hybrid (all plugins)
```

#### Windows PowerShell

Prefer passing the profile as a **Make variable** (no PowerShell env var needed):

```powershell
make build
make run PROFILE=api-pro

make run PROFILE=ai-pro
make run PROFILE=hybrid
```

Equivalent using environment variables if you prefer:

```powershell
$env:PROFILE = "api-pro"; make run
```

#### Windows PowerShell without Make for **run** only

Still build once from repo root (`make build` or Maven below), then:

```powershell
cd core\java
$env:CONFIG_FILE = "$(Resolve-Path ..\..\lumebridge.yaml).Path"
$env:PROFILE = "api-pro"
java --enable-preview -jar target/lumebridge-core.jar
```

Adjust the `CONFIG_FILE` path if your repo root differs.

---

### Quick reference

| Step | Mac / Git Bash | Windows PowerShell (Compose native) |
|------|----------------|-------------------------------------|
| Start infra | `make up` | `docker compose -p lumebridge -f infra/docker-compose.yml up -d` |
| Health checks | `make verify-infra` | Redis / Postgres / Redpanda `docker compose ... exec` commands above |
| Tests | `make test` | `cd core\java; mvn test` |
| Run gateway | `PROFILE=api-pro make run` or `make run PROFILE=api-pro` | `make run PROFILE=api-pro` after `make build` (same form as Mac) |

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
PROFILE=hybrid make run
```

## Part 4: Build & Test Commands

| Command | Purpose |
|---------|---------|
| `make build` | Compile project to JAR |
| `make test` | Run unit tests |
| `make run` | Build and run application |
| `make clean` | Remove build artifacts |
| `make up` | Start Docker infrastructure (needs **Bash** — use Git Bash on Windows, or see Part 2 PowerShell) |
| `make down` | Stop Docker infrastructure |
| `make ps` | Show running containers |
| `make logs` | Stream container logs |
| `make verify-infra` | Health check on infrastructure |
| `make bench-gate` | k6 load test — concurrency gate (`benchmarks/k6/throttler_test.js`) |
| `make bench-lock` | k6 load test — distributed lock |
| `make bench-step5` | k6 load test — Step 5 intelligence payloads (`benchmarks/k6/step5_intelligence.js`) |
| `make bench` | Runs **`bench-gate`** then **`bench-lock`** (not **`bench-step5`**) |
| `make stress-test` | **`scripts/bench-runner.sh`** — **`api-pro`** then **`ai-pro`**, 10k k6 iterations each; needs **Bash**, **k6**, **Python** (**`python3`**, **`python`**, or Windows **`py -3`**), **make** |
| `make stress-test-k6-api` | k6-only stress (`TEST_TYPE=API`). Start gateway first: **`make run PROFILE=api-pro`** |
| `make stress-test-k6-ai` | k6-only stress (`TEST_TYPE=AI`). Start gateway first: **`make run PROFILE=ai-pro`** |

### Benchmarks (k6)

Targets **`make bench`**, **`bench-gate`**, **`bench-lock`**, **`bench-step5`**, and the **`stress-test-k6-*`** helpers invoke **[Grafana k6](https://grafana.com/docs/k6/latest/set-up/install-k6/)**. If k6 is missing, Windows **`make`** often reports **`make (e=2): The system cannot find the file specified`** because it cannot spawn the **`k6`** executable.

**`make stress-test`** runs **`"$(BASH)" "$(CURDIR)/scripts/bench-runner.sh"`**. On **Windows**, the Makefile **defaults `BASH` to Git Bash** when it finds **`%ProgramFiles%\Git\bin\bash.exe`** or **`%LOCALAPPDATA%\Programs\Git\bin\bash.exe`** — avoiding a broken **WSL** **`bash`** shim. **`make up`** / **`verify-infra`** use the same **`$(BASH)`**. Override if Git is installed elsewhere:

```bash
make stress-test BASH="C:/Program Files/Git/bin/bash.exe"
```

The script resolves the repo root, writes temp files under **`TMPDIR` / `TEMP` / `/tmp`**, exports **`CONFIG_FILE`**, runs **`PROFILE=… make run`** in the background, runs k6, and appends rows using embedded **Python** on **`--summary-export`** JSON (**`rate`**, **`p(95)`**, **`count` / `fails` / `passes`**). Output: **`reports/security_audit_report.md`**. On **Git Bash** + **Windows Python (`py -3`)**, the summary path is passed through **`cygpath -w`** so Python opens the real file (otherwise the table can show **0.00%** / zeros).

Alternatively open **Git Bash** and run **`bash scripts/bench-runner.sh`** manually. Use **`make stress-test-k6-*`** if you only need k6 with the gateway already running.

**Windows (pick one):**

```powershell
winget install GrafanaLabs.k6
```

```powershell
choco install k6 -y
```

**macOS (Homebrew):**

```bash
brew install k6
```

**Python 3** (only **`make stress-test`** / **`scripts/bench-runner.sh`** — parses k6 **`--summary-export`** JSON):

**Windows (pick one):**

```powershell
winget install Python.Python.3.12
```

(Re-open the terminal afterward.)

```powershell
choco install python3 -y
```

From **[python.org](https://www.python.org/downloads/windows/)**: run the installer and enable **Add python.exe to PATH** (optional — see below).

Verify (**note the hyphen:** **`py -3`**, not **`py 3`**):

```powershell
py -3 --version
python --version
```

Many installs expose only the **`py`** launcher; **`python`** may still be missing from **`PATH`**. **`scripts/bench-runner.sh`** tries **`python3`**, then **`python`**, then **`py -3`**, so stress-test works when **`py -3`** succeeds even if **`python`** does not.

To make **`python`** work in PowerShell, enable **Add to PATH** in the installer or **Settings → Apps → Advanced app settings → App execution aliases** (disable **`python.exe` / `python3.exe`** store stubs if they interfere).

**macOS (Homebrew):**

```bash
brew install python@3
```

**Linux:** **`sudo apt install python3`** (Debian/Ubuntu) or use your distro package manager.

Start the gateway on **`http://localhost:8080`** before running benchmarks. For **`bench-step5`**, enable the Step 5 plugins noted in the **`make`** banner (see **`lumebridge.yaml`**).

Override the k6 binary if needed:

```bash
make bench-step5 K6=/path/to/k6
```

Stress helpers use **`TARGET_URL`** (default **`http://localhost:8080`**):

```bash
make stress-test-k6-api TARGET_URL=http://127.0.0.1:8080
```

---

## Part 5: Sanity Checks

Once the gateway is running (`make run`), verify from another terminal.

The same **`POST /task`** URL and headers work for **every profile**; only the active plugins (from **`PROFILE`** + `lumebridge.yaml`) change. When **`nonce-ordering`** is enabled, use a **new `nonce`** on each request (increment or pick an unused value).

### Optional: liveness

**macOS / Linux / Git Bash:**

```bash
curl -s http://localhost:8080/healthz
```

**Windows PowerShell:**

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/healthz"
```

### Basic check (any profile)

Minimal payload to confirm auth, pipeline, and JSON response:

**macOS / Linux / Git Bash:**

```bash
curl -sS -X POST http://localhost:8080/task \
  -H "X-API-Key: sk-sentinel-user123" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Hello", "nonce": 1}'
```

**Windows PowerShell — see the full JSON body**

**`Invoke-RestMethod`** deserializes JSON into a **`PSCustomObject`**. That is convenient for properties like **`$r.payload_hash`**, but nested or long values can look incomplete in the default console view (so it may seem like you “only see the hash”).

**`Invoke-WebRequest`** returns the HTTP response wrapper; the gateway body is the **raw string** **`$response.Content`**, which always shows the **complete JSON** exactly as returned:

```powershell
$uri = "http://localhost:8080/task"
$headers = @{
  "X-API-Key"    = "sk-sentinel-user123"
  "Content-Type" = "application/json"
}
$body = '{"prompt": "Hello", "nonce": 1}'
$response = Invoke-WebRequest -Uri $uri -Method Post -Headers $headers -Body $body
$response.StatusCode   # expect 200 on success
$response.Content      # full JSON body as text
```

Pretty-print in PowerShell:

```powershell
$response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10
```

**Windows PowerShell — parsed object (`Invoke-RestMethod`)**

If you prefer typed fields, expand them explicitly:

```powershell
$headers = @{
  "X-API-Key"    = "sk-sentinel-user123"
  "Content-Type" = "application/json"
}
$r = Invoke-RestMethod -Uri "http://localhost:8080/task" -Method Post -Headers $headers -Body '{"prompt": "Hello", "nonce": 1}'
$r | Format-List *
```

**Expected on success:** HTTP **200**, **`"status": "completed"`**, plus fields such as **`payload_hash`**, **`request_id`**, **`metrics`**, and model output (e.g. **`result`** or intelligence keys — depends on profile). See [api-spec.md](api-spec.md).

### **`ai-pro`** — routing sanity (optional)

After the basic check, exercise **intent / model routing** with two different prompts (use **new nonces**):

**macOS / Linux / Git Bash:**

```bash
curl -sS -X POST http://localhost:8080/task \
  -H "X-API-Key: sk-sentinel-user123" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "List 5 fruits", "nonce": 2}'

curl -sS -X POST http://localhost:8080/task \
  -H "X-API-Key: sk-sentinel-user123" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Design a distributed system for X", "nonce": 3}'
```

**Windows PowerShell** (full bodies via **`Content`**):

```powershell
$uri = "http://localhost:8080/task"
$h = @{ "X-API-Key" = "sk-sentinel-user123"; "Content-Type" = "application/json" }
(Invoke-WebRequest -Uri $uri -Method Post -Headers $h -Body '{"prompt": "List 5 fruits", "nonce": 2}').Content
(Invoke-WebRequest -Uri $uri -Method Post -Headers $h -Body '{"prompt": "Design a distributed system for X", "nonce": 3}').Content
```

Compare routing / model metadata in each JSON response (exact shape depends on enabled intelligence plugins).

### **`hybrid`** — quick extra check

For PII scrubbing and the full plugin stack, send a prompt with synthetic PII (examples in [INFRASTRUCTURE_SETUP.md](INFRASTRUCTURE_SETUP.md)).

---

## Part 6: Plugin Management

Plugins are controlled via `lumebridge.yaml`. You can enable/disable individual plugins or override their settings.

### View Active Plugins

**macOS / Linux / Git Bash** (optional [`jq`](https://jqlang.github.io/download/)):

```bash
curl -s http://localhost:8080/metrics | jq
```

**Without jq:**

```bash
curl -s http://localhost:8080/metrics
```

**Windows PowerShell:**

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/metrics"
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

Defaults match `infra/docker-compose.yml`: user **`lumebridge`**, password **`lumebridge`**, database **`lumebridge_nexus`**.

```bash
# Interactive SQL
PGPASSWORD=lumebridge psql -h localhost -U lumebridge -d lumebridge_nexus

# Semantic cache uses pgvector — see SQL / migrations

# Manual vacuum (example)
PGPASSWORD=lumebridge psql -h localhost -U lumebridge -d lumebridge_nexus -c "VACUUM ANALYZE tasks;"
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

**Windows PowerShell:**

```powershell
$env:THROTTLE_PERMITS = "100"
$env:PROFILE = "api-pro"
make run
```

**Saturation**: If `active_permits` == `max_permits`, the gate is full. Scale horizontally or increase permits.

### Circuit Breaker & Retries

- **Circuit Breaker**: Trips when a backend fails consistently, fast-failing subsequent requests to prevent cascade failure
- **Smart Retry**: Handles transient errors (503, Timeouts) with exponential backoff and jitter

### Horizontal Scaling

Run multiple instances behind a load balancer. Redis handles distributed locking automatically.

**macOS / Linux / Git Bash:**

```bash
THROTTLE_PERMITS=50 PROFILE=api-pro make run
# Second terminal:
THROTTLE_PERMITS=50 PROFILE=api-pro make run
```

**Windows PowerShell:**

```powershell
$env:THROTTLE_PERMITS = "50"
$env:PROFILE = "api-pro"
make run
```

Repeat in a second terminal for the second instance.

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
| `DB connection refused` / `localhost:5432 refused` | Postgres not running or still starting | Run **`make up`** (or **`docker compose ... up -d`**) **before** `make run` for `api-pro` / `ai-pro` / `hybrid`. Wait ~30s, then **`make verify-infra`**. Ensure nothing else is bound to port **5432**. |
| Postgres missing from **`docker ps`** (Redis/Redpanda only) | Postgres exited on startup — often a failed **`init-db.sql`** bind mount or a bad data volume | Pull latest **`infra/docker-compose.yml`** (mounts **`migrations/init-db.sql`**). Run **`docker compose -p lumebridge -f infra/docker-compose.yml logs postgres`**. If the DB was half-initialized, **`docker compose ... down -v`** then **`up -d`** (wiping **`postgres-data`**). |
| `pgvector missing` | Postgres version issue | Use `pgvector/pgvector:latest` image in docker-compose |
| `400 Safety` | Prompt triggered safety rules | Check `lumebridge.yaml` safety-guardrails config |
| `docker not found` | Docker daemon not running | Start Docker Desktop |
| `make: command not found` | Make not installed | Install via Chocolatey (Windows) or Homebrew (Mac) — Part 1 |
| `execvpe(/bin/bash) failed` / WSL relay error when running **`make`** | **`bash`** goes through **WSL** integration but **`/bin/bash`** isn’t available in that distro | Use **`make stress-test BASH="C:/Program Files/Git/bin/bash.exe"`** (path may vary), or fix/install **WSL**; prefer **Git Bash** terminal for **`make`** |
| `execvpe failed` / Make cannot run recipes | **`make`** invokes **`bash`** (infra, **`make stress-test`**) but **`bash`** is missing | Use **Git Bash** / **WSL** from repo root, **`docker compose`** from PowerShell (Part 2), **`make … BASH="/path/to/bash.exe"`**, or **`make stress-test-k6-*`** |
| `CONFIG_FILE` / **`PROFILE`** ignored when using **`make run`** on Windows | **`cmd.exe`** does not support POSIX `VAR=value command` in Makefile recipes | The Makefile **`export`**s both variables so Java always receives them (pull latest `Makefile`) |
| Postgres authentication failed | YAML credentials ≠ Compose | Align `postgres:` in `lumebridge.yaml` with `docker-compose.yml` (defaults: `lumebridge` / `lumebridge_nexus`) |
| **`FATAL: database "lumebridge" does not exist`** in Postgres logs | PostgreSQL clients default the DB name to the **username** when **`database` / `-d`** is omitted; Compose creates **`lumebridge_nexus`**, not **`lumebridge`** | In **`lumebridge.yaml`**, set **`postgres.database: lumebridge_nexus`**. For **`psql`**, use **`-d lumebridge_nexus`**. Recreate the Postgres service after pulling the fixed **`infra/docker-compose.yml`** healthcheck (uses **`POSTGRES_DB`**). |
| **`Need python3, python, or py`** when running **`make stress-test`** | No Python on **`PATH`** | Install Python (Benchmarks section). On Windows run **`py -3 --version`** — **`bench-runner.sh`** uses **`py -3`** if **`python`** is missing |

### Resetting on Windows

Use **Git Bash** if you rely on `make down` / `make up`, or use **`docker compose`** as in Part 2.

**Git Bash — Terminal 1 (infra):**

```bash
make down
docker ps
make up
sleep 30
make verify-infra
```

**PowerShell — Terminal 2 (tests):**

```powershell
make test
```

**PowerShell — Terminal 3 (gateway):**

```powershell
make run PROFILE=api-pro
```

**PowerShell — Terminal 4 (API test):**

Same sanity checks as **Part 5**; use **`Invoke-WebRequest`** and **`$response.Content`** to print the full JSON body:

```powershell
$uri = "http://localhost:8080/task"
$h = @{ "X-API-Key" = "sk-sentinel-user123"; "Content-Type" = "application/json" }
(Invoke-WebRequest -Uri $uri -Method Post -Headers $h -Body '{"prompt": "Hello", "nonce": 1}').Content
```

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

Last updated: 2026-05-17
