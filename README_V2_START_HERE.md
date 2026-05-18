# 🚀 LumeBridge V2: Start Here

**Quick start guide for infrastructure setup, baseline testing, and V2 implementation**

---

## Your Current Task: Foundation Phase (THIS WEEK)

You are here: **Getting infrastructure running & establishing baseline metrics**

```
┌─────────────────────────────────────────────────────────────┐
│  Phase 0: Infrastructure Setup (NOW)                        │
│  ├─ Install tools ✓                                          │
│  ├─ Build application → make build                           │
│  ├─ Start services → make up                                 │
│  ├─ Verify health → make verify-infra                       │
│  ├─ Run tests → make test                                    │
│  ├─ Test profiles → api-pro, ai-pro, hybrid                 │
│  └─ Get benchmarks → make bench                             │
│                                                              │
│  Phase 1: V2 Bug Fixes (Next 2 weeks)                        │
│  ├─ Context-aware semantic cache (P0)                       │
│  └─ Multi-format normalization (P0)                         │
│                                                              │
│  Phase 2: V2 Features (Weeks 3-5)                           │
│  ├─ Intent classification (P1)                              │
│  ├─ Intelligent routing → 30-40% cost savings              │
│  ├─ Conversation state (P1)                                 │
│  └─ Hallucination grounding (P1)                            │
│                                                              │
│  Phase 3: Optimization (Weeks 6-8)                          │
│  ├─ Async writes → -5-10ms latency                         │
│  ├─ Adaptive rate limiting (P2)                             │
│  ├─ Dashboard (P2)                                          │
│  └─ GraalVM native → 40x cold start improvement            │
└─────────────────────────────────────────────────────────────┘
```

---

## 📋 WHAT TO DO NOW: 30-Minute Quick Start

### Step 1: Build the Application (30 seconds)

```bash
make build
```

**Expected**: `BUILD SUCCESS`

### Step 2: Start Infrastructure (2 minutes)

```bash
make up
```

**Expected**: 3 containers running (PostgreSQL, Redis, Redpanda)

### Step 3: Verify Everything Works (1 minute)

```bash
make verify-infra
```

**Expected**: All green ✅

### Step 4: Run Tests (20 seconds)

```bash
make test
```

**Expected**: `Tests run: 73, Failures: 0, Errors: 0`

### Step 5: Test All Profiles (10 minutes)

**Profile 1: api-pro** (API Gateway)

Terminal 1:
```bash
PROFILE=api-pro make run
```

Terminal 2 (test):
```bash
# Windows (PowerShell)
$headers = @{"X-API-Key" = "sk-sentinel-user123"; "Content-Type" = "application/json"}
Invoke-WebRequest -Uri "http://localhost:8080/task" -Method POST -Headers $headers -Body '{"prompt":"Hello","nonce":1}'

# Mac (Bash)
curl -X POST http://localhost:8080/task \
  -H "X-API-Key: sk-sentinel-user123" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Hello","nonce":1}'
```

**Expected**: `"status":"success"` ✓

Stop with `Ctrl+C`, then test Profile 2:

**Profile 2: ai-pro** (AI Gateway with intelligent routing)

Terminal 1:
```bash
PROFILE=ai-pro make run
```

Terminal 2: Test simple query (should route to Haiku):
```bash
# See Step 5 above, use prompt: "List 5 fruits"
```

Terminal 3: Test complex query (should route to Opus):
```bash
# Same as above, use prompt: "Design a distributed system for X"
```

**Expected**: Smart routing working ✓

Stop with `Ctrl+C`, then test Profile 3:

**Profile 3: hybrid** (Full gateway, all 25+ plugins)

Terminal 1:
```bash
PROFILE=hybrid make run
```

Terminal 2: Test with PII:
```bash
# Same curl/PowerShell as above
# Prompt: "My email is test@example.com, what is AI?"
```

**Expected**: PII scrubbed, all plugins active ✓

### Step 6: Get Baseline Performance (5 minutes)

Terminal 1: Run gateway
```bash
PROFILE=api-pro make run
```

Terminal 2: Run benchmarks
```bash
make bench
```

**Record these values** (you'll compare against after V2):
- Requests/sec
- Avg latency
- Cache hit rate
- Lock contention

---

## 📊 What You'll See: Expected Results

After completing the 30-minute setup:

```
✅ Infrastructure
   PostgreSQL: healthy
   Redis: healthy
   Redpanda: healthy

✅ Application
   Build: 25 seconds
   Tests: 73 passed in 15 seconds
   
✅ Profiles Working
   api-pro: Responding in 50-80ms
   ai-pro: Routing queries correctly
   hybrid: All 25+ plugins active
   
✅ Baseline Metrics (save these!)
   Cache hit rate: 20%
   Avg response: 75ms
   Max throughput: 1000 req/s
   Lock contention: 12%
```

---

## 🎯 V2 Implementation: What's Next

After infrastructure is verified, you'll implement:

### P0: Critical Bug Fixes (Weeks 1-2)
| Feature | Benefit |
|---------|---------|
| **Context-aware semantic cache** | Eliminate cache poisoning, ↑ hit rate from 20% → 60% |
| **Multi-format normalization** | Support XML, Protobuf, JSON (not just JSON) |

### P1: Cost Reduction & Quality (Weeks 3-5)
| Feature | Benefit |
|---------|---------|
| **Intent Classification** | Route queries by freshness (real-time, temporal, static) |
| **Intelligent Model Routing** | Simple→Haiku (80% cheaper), Complex→Opus (quality) = **30-40% cost savings** |
| **Conversation State** | Prevent token budget surprises |
| **Hallucination Grounding** | Reduce hallucinations 60-80% |

### P2: Performance & Scale (Weeks 6-8)
| Feature | Benefit |
|---------|---------|
| **Async Writes** | -5-10ms latency improvement |
| **GraalVM Native** | 40x faster cold start (2s → 50ms) |
| **Observability Dashboard** | Real-time cost tracking |
| **Adaptive Rate Limiting** | Prevent cascading failures |

---

## 📈 Expected V2 Impact

**Performance**:
```
V1: 75ms avg response → V2: 60ms avg response (-20%)
V1: 1000 req/s max → V2: 2000+ req/s max (2x)
V1: 2s cold start → V2: 50ms cold start (40x improvement)
```

**Cost** (10M req/month scenario):
```
V1: $120,000/month (single model)
V2: $74,000/month (intelligent routing)
────────────────────
Savings: $46,000/month (38% reduction)
```

**Hallucination**:
```
V1: 3% hallucination rate
V2: <1% hallucination rate (-67%)
```

---

## 📚 Documentation Structure

After you complete Phase 0 (infrastructure), follow these docs in order:

1. **INFRASTRUCTURE_SETUP.md** ← YOU ARE HERE
   - Complete all steps in Part 1-7
   - Record baseline metrics from Part 6

2. **V2_TECHNICAL_ROADMAP.md** ← SINGLE CANONICAL V2 DOC
   - Priorities (P0 → P1 → P2), impact analysis, **detailed feature specs**, week-by-week plan, metrics, rollout, checklists

3. **operations-guide.md** ← FOR DEPLOYMENT
   - Updated with V2 profiles
   - Cost tracking
   - Performance benchmarks

---

## ⚠️ Common Pitfalls (Avoid These!)

### ❌ Don't Start V2 Implementation Without Phase 0
You need baseline metrics to measure V2's impact. Skip Phase 0 and you won't know if V2 actually helped.

### ❌ Don't Implement Features Out of Order
- **Must do P0 first** (cache poisoning fix) or hallucination will still happen
- **Then do P1** (cost reduction + quality)
- **Then do P2** (scale & observation)

### ❌ Don't Assume Docker Will Work Without Verification
Run `make verify-infra` before moving on. Docker requires:
- Docker Desktop running (Windows/Mac)
- Internet connection (to pull images)
- 20GB free disk space

### ❌ Don't Test One Profile Only
Test all three:
- **api-pro** = basic gateway functionality
- **ai-pro** = intelligent routing (critical for V2)
- **hybrid** = all plugins (quality checks)

---

## 🔧 If Something Goes Wrong

### "Docker not found"
```bash
# Windows: Start Docker Desktop from Start Menu
# Mac: Start Docker Desktop from Applications
```

### "PostgreSQL not ready"
```bash
# Wait 30 seconds and try again
sleep 30 && make verify-infra
```

### "Tests failing"
```bash
# Reset everything
make down
sleep 10
make up
sleep 30
make test
```

### "Profile not responding"
```bash
# Check if gateway is still running (Terminal 1)
# Check logs: make logs
# Restart with Ctrl+C and PROFILE=xxx make run again
```

---

## 📞 Success Criteria

After completing all steps, you should have:

- [x] Infrastructure running (3 containers healthy)
- [x] Tests passing (73/73)
- [x] All profiles working (api-pro, ai-pro, hybrid)
- [x] Baseline metrics recorded (cache %, latency, throughput)
- [x] Understanding of V2 priorities and impacts
- [x] Ready to implement Phase 1 (P0 bug fixes)

Once you check all boxes → You're ready to start V2 implementation!

---

## 🎬 Your Action Items

**Today:**
- [ ] Complete INFRASTRUCTURE_SETUP.md Part 1-7
- [ ] Run `make build`
- [ ] Run `make up` & `make verify-infra`
- [ ] Run `make test`
- [ ] Test all 3 profiles
- [ ] Run `make bench` and save results

**This Week:**
- [ ] Review **V2_TECHNICAL_ROADMAP.md**
- [ ] Understand P0, P1, P2 priorities
- [ ] Plan V2 bug fix implementation

**Next Week:**
- [ ] Start P0: Context-aware semantic cache
- [ ] Start P0: Multi-format normalization

---

**Ready to get started? Go to INFRASTRUCTURE_SETUP.md and complete Part 0-7!**

---

Last Updated: 2026-05-12
Next Review: After infrastructure setup complete
