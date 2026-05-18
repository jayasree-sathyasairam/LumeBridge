# Infrastructure Setup & Profile Verification Guide

Complete guide to setup LumeBridge infrastructure and verify all deployment profiles work correctly.

**Supports**: Windows 10 | Apple M1 Mac | Linux

---

## Part 1: Prerequisites Check

Before starting infrastructure, verify all tools are installed:

### Windows 10 (PowerShell)

```powershell
# Check Java
java -version

# Check Maven
mvn -version

# Check Docker
docker --version
docker run hello-world

# Check Make
make --version
```

### Apple M1 Mac (Bash)

```bash
# Check Java
java -version

# Check Maven
mvn -version

# Check Docker
docker --version
docker run hello-world

# Check Make
make --version
```

All four should show version information without errors.

---

## Part 2: Start Infrastructure

### Step 1: Build the Application

**Windows (PowerShell):**
```powershell
make build
```

**Mac (Bash):**
```bash
make build
```

This compiles the Java project and creates the fat JAR.

**Expected Output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: X.XXs
```

### Step 2: Start Docker Services

**Windows (PowerShell):**
```powershell
make up
```

**Mac (Bash):**
```bash
make up
```

This starts three services:
- **PostgreSQL** (pgvector) — Data persistence & semantic cache
- **Redis** — Distributed locks, quotas, nonces
- **Redpanda** — DLQ (Dead Letter Queue) & telemetry

**Expected Output:**
```
[+] Running 3/3
 ✔ Container lumebridge-postgres-1  Healthy
 ✔ Container lumebridge-redis-1     Healthy
 ✔ Container lumebridge-redpanda-1  Healthy
```

### Step 3: Verify Infrastructure Health

**Windows (PowerShell):**
```powershell
make verify-infra
```

**Mac (Bash):**
```bash
make verify-infra
```

This runs health checks on all services.

**Expected Output:**
```
✅ PostgreSQL: Connected
✅ Redis: Ping OK
✅ Redpanda: Cluster healthy
```

If any service fails, wait 30 seconds and try again:

**Windows (PowerShell):**
```powershell
Start-Sleep -Seconds 30
make verify-infra
```

**Mac (Bash):**
```bash
sleep 30
make verify-infra
```

---

## Part 3: Run Unit Tests

**Windows (PowerShell):**
```powershell
make test
```

**Mac (Bash):**
```bash
make test
```

This runs all 73+ unit tests.

**Expected Output:**
```
[INFO] BUILD SUCCESS
[INFO] Tests run: 73, Failures: 0, Errors: 0, Skipped: 0
```

If tests fail, check the error messages and refer to **Troubleshooting** section.

---

## Part 4: Run All Deployment Profiles

Each profile is a pre-configured set of plugins. Test them all to verify the infrastructure setup.

### Profile 1: api-pro (API Gateway with Quotas & Metrics)

**Terminal 1: Start the gateway**

Windows (PowerShell):
```powershell
$env:PROFILE = "api-pro"
make run
```

Mac (Bash):
```bash
PROFILE=api-pro make run
```

**Terminal 2: Test the API**

Windows (PowerShell):
```powershell
$uri = "http://localhost:8080/task"
$headers = @{
    "X-API-Key" = "sk-sentinel-user123"
    "Content-Type" = "application/json"
}
$body = '{"prompt": "Hello world", "nonce": 1}'
Invoke-WebRequest -Uri $uri -Method POST -Headers $headers -Body $body
```

Mac (Bash):
```bash
curl -X POST http://localhost:8080/task \
  -H "X-API-Key: sk-sentinel-user123" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Hello world", "nonce": 1}'
```

**Expected Response:**
```json
{
  "status": "success",
  "result": "...",
  "metadata": {
    "cache_hit": false,
    "persist_done": true,
    "throttle_time_ms": 0
  }
}
```

**Press Ctrl+C to stop**

---

### Profile 2: ai-pro (AI Gateway with Intelligent Routing)

**Terminal 1: Start the gateway**

Windows (PowerShell):
```powershell
$env:PROFILE = "ai-pro"
make run
```

Mac (Bash):
```bash
PROFILE=ai-pro make run
```

**Terminal 2: Test simple query (routes to Haiku)**

Windows (PowerShell):
```powershell
$uri = "http://localhost:8080/task"
$headers = @{
    "X-API-Key" = "sk-sentinel-user123"
    "Content-Type" = "application/json"
}
$body = '{"prompt": "List 5 fruits", "nonce": 2}'
Invoke-WebRequest -Uri $uri -Method POST -Headers $headers -Body $body
```

Mac (Bash):
```bash
curl -X POST http://localhost:8080/task \
  -H "X-API-Key: sk-sentinel-user123" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "List 5 fruits", "nonce": 2}'
```

**Expected Output:** Routes to Haiku (cost-optimized)

**Terminal 3: Test complex query (routes to Opus)**

Windows (PowerShell):
```powershell
$uri = "http://localhost:8080/task"
$headers = @{
    "X-API-Key" = "sk-sentinel-user123"
    "Content-Type" = "application/json"
}
$body = '{"prompt": "Design a distributed system for X", "nonce": 3}'
Invoke-WebRequest -Uri $uri -Method POST -Headers $headers -Body $body
```

Mac (Bash):
```bash
curl -X POST http://localhost:8080/task \
  -H "X-API-Key: sk-sentinel-user123" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Design a distributed system for X", "nonce": 3}'
```

**Expected Output:** Routes to Opus (high-quality)

**Press Ctrl+C to stop**

---

### Profile 3: hybrid (Full Gateway with all 25+ plugins)

**Terminal 1: Start the gateway**

Windows (PowerShell):
```powershell
$env:PROFILE = "hybrid"
make run
```

Mac (Bash):
```bash
PROFILE=hybrid make run
```

**Terminal 2: Test with PII**

Windows (PowerShell):
```powershell
$uri = "http://localhost:8080/task"
$headers = @{
    "X-API-Key" = "sk-sentinel-user123"
    "Content-Type" = "application/json"
}
$body = '{"prompt": "My email is test@example.com, what is AI?", "nonce": 4}'
Invoke-WebRequest -Uri $uri -Method POST -Headers $headers -Body $body
```

Mac (Bash):
```bash
curl -X POST http://localhost:8080/task \
  -H "X-API-Key: sk-sentinel-user123" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "My email is test@example.com, what is AI?", "nonce": 4}'
```

**Expected Output:**
- PII is scrubbed
- Safety guardrails evaluated
- Token meter calculated
- All plugins active

**Press Ctrl+C to stop**

---

## Part 5: Check Infrastructure Logs

**Windows (PowerShell):**
```powershell
make logs
```

**Mac (Bash):**
```bash
make logs
```

This streams logs from PostgreSQL, Redis, and Redpanda.

**Expected Entries:**
- Redis: `* DB loaded from disk`
- Postgres: `database system is ready to accept connections`
- Redpanda: `Started Redpanda`

---

## Part 6: Run Performance Benchmarks

Before implementing V2 features, establish baseline performance metrics.

### Benchmark 1: Concurrency Gate

Tests max concurrent requests with distributed locking:

**Terminal 1: Run gateway**

Windows (PowerShell):
```powershell
$env:PROFILE = "api-pro"
make run
```

Mac (Bash):
```bash
PROFILE=api-pro make run
```

**Terminal 2: Run benchmark**

Windows (PowerShell):
```powershell
make bench-gate
```

Mac (Bash):
```bash
make bench-gate
```

**Expected Output:**
```
=== Concurrency Gate Benchmark ===
     requests...: 1000 avg=45ms p(95)=120ms p(99)=180ms
     success.....: 100%
```

### Benchmark 2: Distributed Lock

Tests Redis lock contention under load:

**Terminal 1: Run gateway**

Windows (PowerShell):
```powershell
$env:PROFILE = "api-pro"
make run
```

Mac (Bash):
```bash
PROFILE=api-pro make run
```

**Terminal 2: Run benchmark**

Windows (PowerShell):
```powershell
make bench-lock
```

Mac (Bash):
```bash
make bench-lock
```

**Expected Output:**
```
=== Distributed Lock Benchmark ===
     lock_wait...: avg=2ms p(95)=8ms p(99)=15ms
     contention.: 12%
```

### Benchmark 3: Full Benchmark Suite

Runs all benchmarks:

**Terminal 1: Run gateway**

Windows (PowerShell):
```powershell
$env:PROFILE = "api-pro"
make run
```

Mac (Bash):
```bash
PROFILE=api-pro make run
```

**Terminal 2: Run all benchmarks**

Windows (PowerShell):
```powershell
make bench
```

Mac (Bash):
```bash
make bench
```

---

## Part 7: Check Container Status

**Windows (PowerShell):**
```powershell
make ps
```

**Mac (Bash):**
```bash
make ps
```

**Expected Output:**
```
lumebridge-postgres-1       RUNNING
lumebridge-redis-1          RUNNING
lumebridge-redpanda-1       RUNNING
```

---

## Part 8: Cleanup & Reset

### Stop Services (Keep Data)

**Windows (PowerShell):**
```powershell
make down
```

**Mac (Bash):**
```bash
make down
```

Data in volumes is preserved.

### Full Reset (Wipe All Data)

**Windows (PowerShell):**
```powershell
make down
make up
Start-Sleep -Seconds 30
make verify-infra
```

**Mac (Bash):**
```bash
make down
make up
sleep 30
make verify-infra
```

---

## Troubleshooting

### Issue: "docker: command not found"

**Windows:**
- Ensure Docker Desktop is running
- Check: Start Menu → Docker Desktop
- Or reinstall: `choco install docker-desktop`

**Mac:**
- Start Docker Desktop from Applications
- Or install: `brew install docker`

### Issue: "Port 5432 already in use"

Another PostgreSQL instance is running:

**Windows (PowerShell):**
```powershell
netstat -ano | findstr :5432
taskkill /PID <PID> /F
```

**Mac (Bash):**
```bash
lsof -i :5432
kill -9 <PID>
```

### Issue: "PostgreSQL connection refused"

Wait for PostgreSQL to be ready:

**Windows (PowerShell):**
```powershell
Start-Sleep -Seconds 30
make verify-infra
```

**Mac (Bash):**
```bash
sleep 30
make verify-infra
```

### Issue: "Test failures"

If `make test` fails:
1. Ensure infrastructure is healthy: `make verify-infra`
2. Check Docker logs: `make logs`
3. Reset and retry:
   - Windows: `make down && make up && Start-Sleep -Seconds 30 && make test`
   - Mac: `make down && make up && sleep 30 && make test`

### Issue: "Redis connection refused"

**Windows (PowerShell):**
```powershell
docker exec lumebridge-redis-1 redis-cli ping
```

**Mac (Bash):**
```bash
docker exec lumebridge-redis-1 redis-cli ping
```

Expected: `PONG`

### Issue: "Redpanda cluster not ready"

Wait for Redpanda to start (takes 15-20 seconds):

**Windows (PowerShell):**
```powershell
Start-Sleep -Seconds 20
make verify-infra
```

**Mac (Bash):**
```bash
sleep 20
make verify-infra
```

---

## Performance Baseline (Reference)

After successful setup, your baseline should be:

| Metric | Expected Value |
|--------|--------|
| Build time | < 30s |
| Test suite | < 20s (73 tests) |
| API response (api-pro) | 50-100ms |
| API response (ai-pro) | 80-150ms |
| Concurrent requests (gate benchmark) | 1000 req/s |
| Lock contention | < 20% |
| Cache hit rate | 15-25% (V1 baseline) |
| PII blocks (hybrid) | Functional |

---

## Next Steps After Infrastructure Verification

Once all profiles are running and benchmarks are complete:

1. ✅ Infrastructure working
2. ✅ All profiles tested
3. ✅ Baseline performance recorded
4. → **Next: Implement V2 Features**

Proceed to [V2_TECHNICAL_ROADMAP.md](V2_TECHNICAL_ROADMAP.md) for implementation priorities and detailed V2 specs.

---

## Quick Reference

| Command | Purpose |
|---------|---------|
| `make build` | Compile to JAR |
| `make up` | Start infrastructure |
| `make down` | Stop infrastructure |
| `make verify-infra` | Health checks |
| `make test` | Run unit tests |
| `make run` | Run gateway (use with PROFILE env var) |
| `make ps` | Show container status |
| `make logs` | Stream service logs |
| `make bench` | Run all benchmarks |
| `make bench-gate` | Concurrency gate benchmark |
| `make bench-lock` | Distributed lock benchmark |

Last updated: 2026-05-12
