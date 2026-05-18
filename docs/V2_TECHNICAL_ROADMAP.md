# LumeBridge V2: Implementation Plan & Technical Roadmap

**Canonical V2 document** — priorities, matrices, **detailed feature specifications**, weekly execution plan, metrics, rollout, and checklists. Edit **this file** (`docs/V2_TECHNICAL_ROADMAP.md`) only.

Strategic framing includes impact analysis on **Performance**, **Cost**, and **Lightweight Model Optimization**.

---

## Executive Summary

**V1 Status**: Generic high-performance gateway (no LLM-specific optimizations)

**V2 Vision**: LLM orchestration platform with cost control, hallucination prevention, intelligent routing, and **production-grade platform traits**: bounded concurrency vs downstream capacity, durable async persistence where opted-in, repeatable Docker-first operations (IaC **explicitly out of scope for this phase**), and support-grade observability.

**Expected Outcomes**: 30-40% cost reduction, 40-60% improved cache hit rate, 60-80% fewer hallucinations, plus reduced operational risk from cache poisoning, unbounded backpressure, and silent persistence gaps.

---

## Strategic pillars (maps priorities → outcomes)

| Pillar | V2 focus | Primary backlog slots |
|--------|-----------|------------------------|
| **Architectural & concurrency evolution** | Virtual threads remain the default; add explicit bounds (gate vs pool vs LLM), stage timeouts, optional offload for CPU-heavy plugins, future streaming/multi-protocol adapters | P2 adaptive limiting; cross-cutting hardening in Part 5 |
| **Storage optimization & data integrity** | P0 cache correctness; pgvector/index discipline; Redis lock + idempotency story documented under load | P0 + P0 storage hardening row; P2 outbox |
| **Infrastructure & automation** | Docker Compose + `make` workflows remain primary; document secrets handling and rollout patterns without Terraform/Pulumi for now | Optional platform track (below)—no IaC |
| **Self-service & runtime observability** | Correlation IDs end-to-end, trace spans per pipeline stage, narrow internal/support tooling | P2 observability rows |

---

## Part 1: Critical Success Factors

### Performance Factors 🚀
- **Latency**: End-to-end request processing time
- **Throughput**: Requests per second
- **Cold Start**: Time to first response (GraalVM)
- **Concurrent Capacity**: Max simultaneous requests

### Cost Factors 💰
- **Model Costs**: Per-token pricing (Haiku $0.80/M → Opus $15/M)
- **Infrastructure Costs**: PostgreSQL, Redis, Redpanda
- **Token Burn Rate**: How many tokens per user request
- **Cache Hit Rate**: Higher = lower cost

### Lightweight Model Optimization 📱
- **Haiku Usage**: For simple queries (80% cost savings)
- **Query Complexity Detection**: Route to right-sized model
- **Memory Footprint**: GraalVM native = 30% less RAM
- **Cold Start**: Virtual threads + native = 40x faster

### Reliability & Operability 🛡️
- **Backpressure coherence**: Concurrency gate permits must stay aligned with Redis command capacity, Hikari pool size, and LLM provider concurrency—avoid “unlimited” virtual threads overwhelming dependencies.
- **Persistence semantics**: Any async path must define SLA (at-least-once vs effectively-once for audits) and use a **transactional outbox** where DB state and Redpanda events must match.
- **Semantic cache latency**: Nearest-neighbour queries must stay bounded at scale (ANN indexes, dimensions governance)—correctness work (P0) without storage discipline replays as p99 regressions.

---

## Part 2: Setup Checklist (Prerequisites for V2)

### ✅ Phase 0: Infrastructure Foundation (NOW)

**Status**: MUST COMPLETE FIRST

| Step | Action | Window | Verify |
|------|--------|--------|--------|
| 1 | Install tools (Java, Maven, Docker, Make) | 30min | `java -version && mvn -version && docker --version` |
| 2 | Build application | 30s | `make build` → BUILD SUCCESS |
| 3 | Start infrastructure | 2min | `make up` → 3 containers healthy |
| 4 | Verify health | 1min | `make verify-infra` → All green |
| 5 | Run unit tests | 20s | `make test` → 73 tests pass |
| 6 | Test all profiles | 15min | api-pro, ai-pro, hybrid all work |
| 7 | Run benchmarks | 10min | Get baseline metrics |

**Outcome**: Baseline performance established, infrastructure verified

---

## Part 3: V2 Priority Matrix

### P0: Critical Bug Fixes (MUST DO BEFORE FEATURES)

| Feature | Issue | Impact | Timeline | Performance | Cost | Lightweight |
|---------|-------|--------|----------|-------------|------|-------------|
| **Context-aware semantic cache** | Cache poisoning (98% similar queries return wrong answer) | Eliminates hallucination risk | 2 weeks | ⬆️ Cache hit rate | ⬇️ Reduce LLM calls | ⭕ None |
| **Multi-format payload normalization** | Only JSON works, XML/Protobuf dedup fails | 100% payload support | 1 week | ⭕ None | ⭕ None | ⭕ None |
| **Semantic cache storage hardening** | `ORDER BY embedding <=> … LIMIT 1` without production ANN discipline risks full scans as rows grow | Predictable p99 for cache lookup | 3–5 days | ⬆️ Stable p99 | ⭕ None | ⭕ None |

**Must complete P0 before P1.** Treat **semantic cache storage hardening** as part of the P0 closure milestone (migrations + load test), not an optional polish item.

---

### P1: Domain-Specific LLM Features (CORE V2 VALUE)

| Feature | Benefit | Timeline | Performance | Cost Savings | Lightweight |
|---------|---------|----------|-------------|-------------|-------------|
| **Intent Classification** | 5-intent system with adaptive TTLs | 1 week | ⬆️ Cache hit +40-60% | ⬇️ Fewer calls | ⭕ None |
| **Intelligent Model Routing** | Route simple→Haiku, complex→Opus | 2 weeks | ⬇️ Faster simple queries | 💰 30-40% savings | ✅ Lightweight routing |
| **Conversation State Mgmt** | Track tokens, prevent runaway costs | 2 weeks | ⭕ None | 💰 Budget safety | ⭕ None |
| **Hallucination Grounding** | Fact-check against context | 1-3 weeks | ⭕ None | ⭕ None | ⭕ None |

**P1 directly addresses:** Cost reduction + Lightweight optimization + Hallucination prevention

---

### P2: Operational Excellence (POLISH & SCALE)

| Feature | Benefit | Timeline | Performance | Cost | Lightweight |
|---------|---------|----------|-------------|------|-------------|
| **Async Write-Behind + transactional outbox** | Cut sync Postgres latency; **outbox** keeps DB + Redpanda consistent and replayable | 1–2 weeks | ⬆️ -5-10ms on hot path | ⭕ None | ⭕ None |
| **Adaptive Rate Limiting** | Tie concurrency gate / quotas to backend & dependency signals | 1 week | ⬆️ Optimal throughput | ⭕ None | ⭕ None |
| **Observability Dashboard** | Real-time cost/latency/token views | 3 weeks | ⭕ None | 📊 Cost visibility | ⭕ None |
| **Structured telemetry & support hooks** | Correlation ID end-to-end; OpenTelemetry-style traces (spans per pipeline stage); stable error taxonomy for L1/support | 1–2 weeks | ⭕ None | ⭕ Fewer escalations | ⭕ None |
| **GraalVM Native Image** | Fast cold start | 2 weeks | ⬆️ 40x faster start | ⭕ None | ✅ 30% less RAM |

---

### Platform track (parallel—do not block P0/P1 code merges)

Run alongside core weeks where useful. **Infrastructure as Code (Terraform/Pulumi, etc.) is not planned for this phase**—revisit when you adopt a fixed cloud account and want reproducible environments-as-repo.

| Workstream | Scope | Timeline (indicative) |
|------------|--------|------------------------|
| **Secrets & config** | Keep keys out of git; use env injection, mounted files, or your org’s secret manager when not on local Compose—document the chosen pattern | Ongoing |
| **Progressive delivery** | Optional: Helm/Kustomize manifests, canary or LB weighted routing, PDBs—maintained manually or via CI without IaC | 1–2 weeks |
| **Multi-region posture** | Doc-only or spikes: failover implications for Redis locks and Postgres; no requirement to automate regions | Doc + spikes |

---

## Detailed feature specifications

Consolidated technical depth for each backlog item. High-level timelines and priority tables are in **Part 3**; below are problem statements, approaches, and implementation sketches.

### P0 — Critical bug fixes

#### Context-aware semantic caching

**Status (V1):** Unsafe — similarity-only matching risks wrong answers.

**Problem:** Semantic cache uses cosine similarity alone → **cache poisoning**:

```
Query 1: "What's the weather in NYC today?"       [Embedding: [0.12, 0.85, ...]]
Query 2: "What was the weather in NYC yesterday?" [Embedding: [0.11, 0.84, ...]]
         -> Similarity = 0.98 (> 0.95 threshold)
         -> Returns WRONG answer (today's weather instead of yesterday's)
```

**V2 approach:** Composite cache key with context hash:

```java
String cacheKey = semanticSimilarity(query)
                  + "_" + hashContext(user_id, api_key, timestamp_bucket)
                  + "_ttl:" + getQueryFreshness(intent);
```

**Implementation:**

1. Extract **intent** from query (real-time vs historical vs user-specific).
2. Include request **context** (user_id, parameters, timestamp) in match eligibility.
3. Assign **TTL** per intent (e.g. real-time: 1 min, historical: 7 days, static: 30 days).
4. Only treat as a hit if `similarity > threshold AND context matches`.

**Timeline:** ~2 weeks · **Impact:** Eliminates cache poisoning; improves hit rate without sacrificing correctness.

---

#### Multi-format payload normalization

**Status (V1):** JSON-oriented paths break dedup for other encodings.

**Problem:** XML, Protobuf, YAML, MessagePack payloads do not hash canonically → deduplication / idempotency fail.

**V2 approach:** Pluggable format normalizers:

```java
interface PayloadNormalizer {
    CanonicalForm normalize(byte[] payload) throws UnsupportedFormatException;
}

PayloadNormalizer normalizer = detectFormat(payload);
CanonicalForm canonical = normalizer.normalize(payload);
String hash = sha256(canonical);
```

**Implementation:**

1. Detect format: `Content-Type` → body magic bytes → optional schema registry.
2. Normalizers: `JsonNormalizer` (sorted keys), `XmlNormalizer` (canonical namespaces/attrs), `ProtobufNormalizer` (deterministic field order), `FormNormalizer` (`application/x-www-form-urlencoded`).
3. Single `CanonicalForm` type feeds `payload-hasher` / locks.

**Timeline:** ~1 week · **Impact:** Dedup works across payload types.

---

### P1 — Domain-specific LLM features

#### Intent classification (query freshness)

**Current:** Binary cache-eligible flag.

**V2:** Five intent categories with different caching rules:

```java
enum QueryIntent {
    REAL_TIME,       // "What time is it?" -> TTL: 1 min
    TEMPORAL,        // "What was the weather yesterday?" -> TTL: 7 days
    STATIC,          // "What's the capital of France?" -> TTL: 30 days
    CONVERSATION,    // "Based on our earlier discussion..." -> No cache
    COMPUTATION      // "Solve this math problem" -> No cache
}
```

Example detector patterns (extend as needed): `(?i)(now|current|today|latest)` for REAL_TIME; `(?i)(yesterday|last week|2024)` for TEMPORAL; `(?i)(capital of|largest|definition)` for STATIC; default CONVERSATION.

**Timeline:** ~1 week · **Impact:** Higher cache hit rate with lower hallucination risk.

---

#### Intelligent model routing (cost optimization)

**Current:** Static single-model routing.

**V2:** Route by complexity and token budget:

```java
if (queryComplexity == LOW && tokensEstimate < 500) {
    route = HAIKU;
} else if (queryComplexity == MEDIUM && tokensEstimate < 2000) {
    route = SONNET;
} else {
    route = OPUS;
}
```

**Complexity signals:** Keywords (“design”, “analyze” → HIGH; “summarize”, “list” → LOW); approximate token estimate.

**Fallback chain:** OPUS → SONNET → HAIKU on timeouts → explicit error if all fail.

**Timeline:** ~2 weeks · **Impact:** ~30–40% cost reduction; faster simple queries.

---

#### Conversation state management

**Current:** Stateless requests.

**V2:** Redis-backed sessions with token budgets; summarize older turns when budget is exhausted; warn as consumption rises.

**Timeline:** ~2 weeks · **Impact:** Long conversations without runaway cost.

---

#### Hallucination grounding

**Current:** Naive keyword checks.

**V2:** Grounding score vs supplied context (overlap, contradiction detection, citation expectations). Example usage: low score → HTTP 206 Partial Content + warnings.

**Implementation options (pick by effort):** keyword overlap; cross-encoder NLI; LLM-as-judge (cost amortized via caching).

**Timeline:** 1–3 weeks · **Impact:** Fewer bad outputs reaching clients.

---

### P2 — Operational excellence

#### Async write-behind persistence

**Current:** Synchronous Postgres writes on the hot path (~5–10 ms).

**V2:** Acknowledge after durable enqueue to Redpanda; **combine with transactional outbox** (see Part 3 / Week 6–7) so rows and events stay consistent; batch consumer writes to Postgres.

**Timeline:** ~1 week core path · **Impact:** Latency reduction when SLA allows eventual persistence.

---

#### Adaptive rate limiting (congestion control)

**Current:** Fixed concurrency permits.

**V2:** Adjust permits from backend p99 (and optionally pool / Redis latency) — decrease on pressure, increase when healthy.

**Timeline:** ~1 week · **Impact:** Fewer cascading failures.

---

#### Real-time observability dashboard

Consume gateway / streaming metrics (e.g. WebSocket UI or Grafana): heatmaps, cache hit rate, latency percentiles, token burn, safety/PII signals.

**Timeline:** ~3 weeks · **Impact:** Faster incident detection.

---

#### GraalVM native image

Native binary for sub-100 ms cold starts and smaller footprint; validate JDBC/Jedis/reflection constraints early.

**Timeline:** ~2 weeks · **Impact:** Serverless-friendly deployments.

---

### Code quality (P2)

Reduce cyclomatic complexity in heavy plugins (`SafetyGuardrailsPlugin`, `DualModeRouterPlugin`, `ResponseEvaluatorPlugin`) via small strategy objects / evaluator lists instead of deep `if` chains.

**Timeline:** ~1 week · **Impact:** Easier tests and safer extensions.

---

## Part 4: Impact Projection (Post-V2)

### Performance Impact

**V1 Baseline** (after infrastructure setup):
```
API Response Time (api-pro):    50-100ms
Concurrent Capacity:            ~1000 req/s
Cache Hit Rate:                 15-25%
```

**V2 With Optimizations**:
```
API Response Time (api-pro):    40-80ms        (20% improvement)
API Response Time (ai-pro):     60-120ms       (40% improvement)
Concurrent Capacity:            ~2000 req/s    (2x with adaptive limiting)
Cache Hit Rate:                 55-85%         (3-4x improvement)
Cold Start Time:                50ms (GraalVM) (40x improvement)
```

### Cost Impact

**Scenario**: 10M requests/month, 80% simple queries (route to Haiku)

**V1 (Single model, no routing)**:
```
Avg tokens/request:             800
Model cost:                      $0.012/req (Sonnet)
Monthly cost:                    $120,000
```

**V2 (Intelligent routing)**:
```
Simple queries (80%):            Route to Haiku = $0.001/req
Complex queries (20%):           Route to Opus  = $0.015/req
Weighted avg:                    $0.0074/req
Monthly cost:                    $74,000
Savings:                         38% ($46,000/month)
```

### Lightweight Model Optimization Impact

**Device/Serverless Deployment**:

| Aspect | V1 | V2 (GraalVM) | Improvement |
|--------|-----|------|-------------|
| JAR Size | 45 MB | 20 MB | 55% smaller |
| Memory | 512 MB | 256 MB | 50% less |
| Startup Time | 2.1s | 50ms | 42x faster |
| Container Time | ~4s | ~200ms | 20x faster |

**Use Cases Unlocked**: Lambda, Cloud Run, Serverless K8s

---

## Part 5: Step-by-Step Implementation Sequence

### Week 1: P0 Fixes

```
Day 1-2: Context-aware semantic cache
  - Extract intent from query (or integrate with Week 2 Intent classifier stub if minimal)
  - Create composite cache key: semanticSim + contextHash + ttl bucket
  - Update SemanticCachePlugin / repository contracts as needed

Day 2-3: Semantic cache storage hardening (PostgreSQL + pgvector)
  - Add production DDL migration: ANN index appropriate for pgvector version (e.g. HNSW / IVFFlat per ops guidance)
  - Document `ANALYZE`, recall vs latency tuning; validate `SemanticCacheRepository` plans under realistic row counts
  - Optional: tenant/model partitioning strategy if multi-tenant skew appears

Day 3-5: Multi-format normalization
  - Implement PayloadNormalizer interface (see **Detailed feature specifications → P0** above)
  - Add JsonNormalizer, XmlNormalizer, ProtobufNormalizer (priority order by traffic)
  - Update hashing / dedup logic for canonical forms
  
Day 6: Testing & Benchmarking
  - Run `make test` (should pass all 73+)
  - Run `make bench` with new cache logic + verify semantic lookup p95/p99
  - Compare hit rates: before vs after (correctness first, then rate)
```

**Success Criteria**: 
- Zero cache poisoning cases (regression tests for “similar embedding, different context/temporal intent”)
- Cache hit rate ↑ from 20% → 45% **without** sacrificing correctness
- Semantic cache queries show bounded plans (no sequential scans at target scale in staging)
- All tests pass

---

### Week 2-3: P1 Features (Phase 1)

```
Day 1-2: Intent Classification
  - Implement QueryIntent enum (REAL_TIME, TEMPORAL, STATIC, etc.)
  - Create pattern-based detector
  - Add IntentPlugin

Day 3-5: Intelligent Model Routing
  - Build ComplexityDetector (keywords, token estimates)
  - Implement ModelRouter (HAIKU/SONNET/OPUS decision)
  - Add DualModeRouterPlugin enhancements

Day 6: Testing
  - Create integration tests for routing logic
  - Verify simple queries → Haiku
  - Verify complex queries → Opus
```

**Success Criteria**:
- Simple query (100 tokens) → Haiku ✓
- Complex query (2000 tokens) → Opus ✓
- Cost reduction measured: 30-40% ✓

---

### Week 4-5: P1 Features (Phase 2)

```
Day 1-3: Conversation State Management
  - ConversationSession model (Redis backend)
  - Token budget tracking
  - Auto-summarization on budget exhaust

Day 4-6: Hallucination Grounding
  - Implement GroundingChecker (fact-check)
  - Integration with ResponseEvaluatorPlugin
  - Return grounding score with warnings
```

**Success Criteria**:
- Long-running conversation (10+ turns) works
- Token budget prevents runaway costs
- Hallucination score computed accurately

---

### Week 6-7: P2 Features

```
Day 1-2: Async Write-Behind (durable)
  - Publish persistence events to Redpanda after processing
  - Implement transactional outbox pattern for rows that must stay consistent with emitted events
  - Idempotent consumer → batched Postgres writes; DLQ + replay playbook
  - Document SLA: which fields are “immediate read-your-write” vs eventual

Day 3-4: Adaptive Rate Limiting
  - Monitor backend P99 latency (and optionally Redis/Postgres pool wait)
  - Adjust concurrency gate permits based on trends (slow start / decay)
  - Prevent cascading failures and retry storms

Day 4-5: Structured telemetry & support hooks
  - Propagate correlation/request ID: client header → RequestContext → logs → outbox/DLQ metadata
  - Spans or structured stages per pipeline phase (PRE_PROCESS … POST_PROCESS) for trace viewers
  - Stable JSON error codes for nonce/collision/quota/safety/cache decisions

Day 5-7: Observability Dashboard
  - React + WebSocket frontend (or Grafana dashboards if faster)
  - Real-time metrics from Redpanda / Prometheus
  - Cost tracking, token burn rate, lock wait / contention panels
```

---

### Week 8: GraalVM & Polish

```
Day 1-3: GraalVM Native Image
  - Build native binary
  - Test cold start (target: < 100ms)
  - Reduce memory footprint
  - Validate JNI/reflection config for Jedis, JDBC driver, crypto—native image often fails here first

Day 4-5: Documentation & platform readiness
  - Update INFRASTRUCTURE_SETUP.md
  - Update operations-guide.md
  - Update production-architecture.md (deployment topology, rollout, secrets—Docker/manual K8s; no IaC in this phase)
  - Add V2 performance benchmarks

Day 6-7: Final Testing & Deployment
  - Full integration tests (include outbox consumer + replay)
  - Load testing (2000+ req/s); chaos hooks optional (slow Redis / pool contention)
  - Production readiness checklist (SLOs, runbooks, dashboard panels wired)
```

---

### Weeks 9–10 (optional): Protocol & concurrency depth

Defer if core LLM features must ship earlier; pick up when REST-only limits product adoption.

```
Streaming / multi-protocol (spike → MVP)
  - SSE or WebSocket for long LLM streams; or gRPC side-by-side with JDK HttpServer
  - Bound memory: avoid unbounded readAllBytes() patterns on large payloads where streaming applies

CPU vs I/O isolation (if profiling shows carrier starvation)
  - Small bounded executor for CPU-heavy normalization / guardrail segments
  - Stage-level timeouts and cancellation on client disconnect
```

---

## Part 6: V2 Success Metrics

### Must-Have Metrics (P0/P1 completion)

| Metric | V1 | V2 Target | Status |
|--------|-----|-----------|--------|
| Cache Hit Rate | 20% | 60%+ | 🎯 |
| Cost per Request | $0.012 | $0.007 | 💰 |
| Hallucination Rate | 3% | < 1% | 🛡️ |
| Avg Response Time | 75ms | 60ms | ⚡ |
| Simple Query Routing | N/A | 95% to Haiku | 🎯 |

### Nice-to-Have Metrics (P2 completion)

| Metric | V1 | V2 Target | Impact |
|--------|-----|-----------|--------|
| Cold Start | 2000ms | 50ms | 🚀 |
| Memory (Container) | 512MB | 256MB | 💾 |
| Max Throughput | 1000 req/s | 2000+ req/s | ⚡ |
| Dashboard Latency | N/A | < 500ms | 📊 |
| Semantic cache lookup p99 | N/A | Within SLO vs row count | ⚡ |
| Lock wait / contention | Baseline from `make bench-lock` | ≤ baseline or reduced under same gate | 🛡️ |
| Trace attribution | Partial/none | 95%+ requests with correlation ID in logs | 🔍 |

---

## Part 7: Documentation Updates Required

After each phase, update:

| Document | Update |
|----------|--------|
| `operations-guide.md` | Add V2 profiles, cost metrics |
| `INFRASTRUCTURE_SETUP.md` | Add V2 benchmarks, interpretation |
| `concepts.md` | Add or extend entries for new V2 patterns (see concept map there) |
| `lld.md` (Low-Level Design) | Architecture of new plugins |
| `hld.md` (High-Level Design) | System diagram updates |
| `production-architecture.md` | Deployment topology, secrets, progressive delivery (manual / Compose-first; IaC deferred) |
| `data-flow.md` | New flow for intelligent routing |

---

## Part 8: Rollout Strategy

### Phase 1: Canary Release (10% traffic)
```
Deploy V2 to 10% of users
Monitor:
  - Hallucination rate
  - Cache hit improvement
  - Cost savings
  - Error rate (separate client 4xx vs gateway 5xx)
  - Semantic cache lookup p95/p99 (post–ANN index)
  - Outbox / consumer lag if async persistence enabled
  - Lock wait & Redis command latency
```

### Phase 2: Ramp Up (50% traffic)
```
Increase to 50% after metrics confirm improvement
Watch for:
  - Long-tail latency (p99)
  - GraalVM native cold starts
  - Edge cases in intent detection
  - Hikari pool wait / Postgres saturation vs concurrency gate permits
```

### Phase 3: Full Release (100% traffic)
```
Complete migration
Keep V1 as fallback option for 2 weeks
```

---

## Immediate Next Steps

### 🔴 DO FIRST (Today)
1. Complete **Part 0: Infrastructure Setup** in INFRASTRUCTURE_SETUP.md
2. Run `make build`, `make up`, `make test`
3. Test all profiles (api-pro, ai-pro, hybrid)
4. Record baseline benchmarks from `make bench`

### 🟡 DO NEXT (This Week)
1. Review **Detailed feature specifications → P0** in this document (`V2_TECHNICAL_ROADMAP.md`)
2. Start Context-aware semantic cache implementation
3. Begin Multi-format payload normalization design

### 🟢 DO LATER (Weeks 2-8+)
1. Implement remaining P1 features
2. Add P2 operational features (outbox-backed async persistence, adaptive limiting)
3. Build observability dashboard + correlation IDs / traces
4. Compile GraalVM native image
5. Optional: progressive delivery runbooks or manifests (platform track—no IaC)

---

## Success Criteria Checklist

**Infrastructure Ready**:
- [ ] `make build` succeeds
- [ ] `make up` starts all 3 containers
- [ ] `make verify-infra` passes
- [ ] `make test` passes (73 tests)
- [ ] All profiles tested (api-pro, ai-pro, hybrid)
- [ ] Benchmarks recorded

**V2 P0 Complete**:
- [ ] Cache poisoning eliminated
- [ ] Cache hit rate ↑ to 45%+
- [ ] Multi-format normalization works
- [ ] Semantic cache table uses production-grade vector index / query plan validated at target scale

**V2 P1 Complete**:
- [ ] Intent classification functional
- [ ] Model routing saves 30-40% cost
- [ ] Conversation state management works
- [ ] Hallucination grounding implemented

**V2 P2 Complete**:
- [ ] Async writes reduce latency 5-10ms **with documented persistence SLA**
- [ ] Transactional outbox + idempotent consumer validated (replay tested)
- [ ] Adaptive rate limiting prevents failures under synthetic degradation
- [ ] Dashboard provides real-time visibility
- [ ] Correlation ID + stage attributions available for support triage
- [ ] GraalVM native cold start < 100ms (if native image remains in scope)

**Platform track (optional ops hardening)**:
- [ ] Secrets and provider keys never committed to the repo; documented injection path for non-Compose environments
- [ ] Canary or blue/green rollout documented and rehearsed (if applicable—may remain Compose-only)

---

## Trade-offs & positioning

**Acceptable trade-offs**

- **Memory-for-speed:** Prefer straightforward blocking code on virtual threads over rewriting the whole gateway as async — accepts JVM heap for massive concurrency vs OS threads.
- **Redis centralization:** Locks, quotas, and (in V2) conversation state rely on Redis — production needs a clear HA/failover story.
- **Grounding cost:** Stronger hallucination checks may add latency or extra model calls; mitigate with caching and tiered checks.
- **Multi-format normalization:** Small parse/canonicalization overhead (~2–5 ms typical) buys correct dedup across encodings.

**Positioning (illustrative)**

- **Tagline:** Cost-aware, hallucination-resistant AI gateway for production LLM workloads.
- **Differentiators:** Context-aware caching (not blind similarity); complexity-based routing (not one fixed model); explicit grounding and token budgets.

---

Last updated: 2026-05-17
Next review: After P0 closure (cache correctness + storage hardening)
