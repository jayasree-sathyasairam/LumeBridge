# LumeBridge V2: Start Here

Quick path through **infrastructure**, **baseline checks**, **k6 fixture stress**, and the **canonical V2 roadmap**.

---

## Your roadmap snapshot

You may be in **Phase 0** (infra + baselines) or already validating **landed P0 work** (semantic cache scoping, multi-format normalization—see codebase + **`reports/version2_performance.md`**).

```
┌─────────────────────────────────────────────────────────────┐
│  Phase 0: Infrastructure & baselines (start here)            │
│  ├─ Build → make build                                       │
│  ├─ Services → make up                                       │
│  ├─ Health → make verify-infra                               │
│  ├─ Tests → make test                                        │
│  ├─ Profiles → api-pro, ai-pro, hybrid                       │
│  └─ Benchmarks → make bench / optional k6 (below)           │
│                                                              │
│  P0 foundations (in repo—validate & extend)                   │
│  ├─ Tenant + freshness–scoped semantic cache (Postgres/pgvector) │
│  ├─ Multi-format payload normalization (JSON, XML, YAML,      │
│  │   form-urlencoded, MsgPack, Protobuf*, opaque binary)    │
│  └─ *Protobuf canonical path requires descriptor + message hdr │
│                                                              │
│  P1–P2 (planned detail → docs/V2_TECHNICAL_ROADMAP.md)       │
│  └─ Intent/routing, conversation quality, async writes, etc. │
└─────────────────────────────────────────────────────────────┘
```

\* Illustrative timelines in **`docs/V2_TECHNICAL_ROADMAP.md`** take precedence over this ASCII sketch.

---

## 30-minute quick start

### 1. Build

```bash
make build
```

Expect Maven **`BUILD SUCCESS`**.

### 2. Start infra

```bash
make up
```

Expect Postgres, Redis, Redpanda (see Compose).

### 3. Verify

```bash
make verify-infra
```

### 4. Tests

```bash
make test
```

Expect **all tests passing** (exact count changes over time).

### 5. Profiles

Smoke **`api-pro`**, **`ai-pro`**, and **`hybrid`** with `PROFILE=… make run` and a POST to **`http://localhost:8080/task`** with a valid **`X-API-Key`** (e.g. `sk-sentinel-…`). Use **`docs/operations-guide.md`** for fuller commands.

### 6. Performance signals

- **`make bench`** — targeted gateway benchmarks (see Makefile).
- **Fixture k6 (Version 2 harness)** — requires [k6](https://grafana.com/docs/k6/) installed:
  1. Terminal A: `make run PROFILE=ai-pro` (or `api-pro`).
  2. Terminal B: `make stress-test-k6-ai` or **`make stress-test-k6-api`**.

See **`benchmarks/k6/README.md`** (`TARGET_URL`, `TEST_TYPE`, optional **`K6_P95_MS`**). Aggregate narrative + recorded AI/API runs: **`reports/version2_performance.md`**.

**Version 1** dual-profile automation (**`scripts/bench-runner.sh`**) writes **`reports/k6_stress_audit_snapshot.md`** locally (gitignored); narrative: **`reports/lumebridge_audit_and_performance_report.md`**.

---

## Documentation order

| Order | Doc | Purpose |
|-------|-----|---------|
| 1 | **`docs/INFRASTRUCTURE_SETUP.md`** | Stack setup and verification |
| 2 | **`docs/V2_TECHNICAL_ROADMAP.md`** | Canonical priorities (P0→P2), specs, week plan |
| 3 | **`docs/operations-guide.md`** | Run, profiles, stress, ops |
| 4 | **`reports/version2_performance.md`** | Fixture k6 outcomes / thresholds notes |

Also: **`docs/adr/`** (e.g. multi-format canonicalization), root **`README.md`** for project overview.

---

## P0 at a glance (what “done” looks like in code)

| Area | Summary |
|------|---------|
| **Semantic cache** | Lookups scoped by API-key fingerprint + coarse freshness bucket; Postgres **`semantic_cache_entries`** with optional **`expires_at`**, HNSW-friendly **`scope`** partitioning—see **`SemanticCachePlugin`**, **`SemanticCacheContextBuilder`**, **`SemanticCacheRepository`**. |
| **Multi-format** | **`payload-normalizer`** → **`MultiFormatPayloadNormalizerEngine`**: JSON, XML, YAML, **`application/x-www-form-urlencoded`**, MsgPack, Protobuf (full canonicalization when configured), **`application/octet-stream`** opaque passthrough with warnings. |

Remaining roadmap items (intent polish, async write-behind, dashboards, etc.) live in **`docs/V2_TECHNICAL_ROADMAP.md`**.

---

## Pitfalls

- Do not skip **`make verify-infra`** before trusting local stacks.
- **`lumebridge.yaml`** is usually gitignored—copy from **`config/lumebridge.example.yaml`**.
- **Fixture k6**: AI vs API pools differ (e.g. multiformat scenarios are **API**); see **`reports/version2_performance.md`**.
- **Strict 1s latency SLO** in k6: pass **`K6_P95_MS=1000`**; default in **`stress_test.js`** may be higher to match typical **`ai-pro`/`api-pro`** p95 on dev hardware.

---

## If something fails

- Docker not running → start Docker Desktop (Windows/Mac).
- DB not ready → wait and retry **`make verify-infra`**.
- Stuck state → **`make down`**, wait, **`make up`**, **`make test`**.
- Profile oddities → confirm **`PROFILE`**, **`CONFIG_FILE`**, and logs (**`make logs`** where applicable).

---

## Action checklist

**Today**

- [ ] **`docs/INFRASTRUCTURE_SETUP.md`** through verification
- [ ] **`make build`**, **`make up`**, **`make verify-infra`**, **`make test`**
- [ ] Smoke **api-pro**, **ai-pro**, **hybrid**
- [ ] Optional: **`make bench`** and/or **`make stress-test-k6-ai`** / **`stress-test-k6-api`**

**This week**

- [ ] Read **`docs/V2_TECHNICAL_ROADMAP.md`**
- [ ] Align tickets with P0 shipped vs P1/P2 backlog

---

Start with **`docs/INFRASTRUCTURE_SETUP.md`**, then **`docs/V2_TECHNICAL_ROADMAP.md`**.

---

Last updated: 2026-05-17
