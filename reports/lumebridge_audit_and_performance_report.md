# LumeBridge: Audit, Security Snapshots & Enterprise Benchmark Report

This document is the **Version 1** narrative: product comparison, concurrency model, and how **automated dual-profile stress** (`scripts/bench-runner.sh`) records metrics **without** the JSON fixture catalog introduced for Version 2.

---

## 1. Feature comparison: LumeBridge vs. alternatives

| Feature | **LumeBridge** | **AWS Bedrock** | **LiteLLM** | **Traditional (Kong)** | Explanation |
|---|---|---|---|---|---|
| **Deduplication** | ● Full | ○ No | ◐ Partial | ○ No | Redis-backed task locks to prevent duplicate work. |
| **PII Scrubbing** | ● Full | ◐ Partial | ○ No | ○ No | Native PII pipeline in the gateway. |
| **Semantic Cache** | ● Full | ○ No | ● Full | ○ No | `pgvector` integration for intent-style matching. |
| **Concurrency** | ● Full | ◐ Partial | ◐ Partial | ● Full | Java 21 virtual threads for scalable request handling. |
| **Cloud-Native** | ● Full | ○ No | ● Full | ● Full | Stateless JAR suitable for K8s or on-prem. |

**Key:** ● Full Support | ◐ Partial Support | ○ No Support

---

## 2. Concurrency handling & resource costs

LumeBridge uses **Java 21 Project Loom** to reduce thread overhead.

### `api-pro` (throughput-oriented)

- Virtual threads per request.
- Rough footprint: ~150 MB baseline + incremental memory under very high concurrency (environment-dependent).

### `ai-pro` (intelligence & safety)

- Threads park during I/O-heavy stages.
- Higher baseline than `api-pro` when safety and semantic features are enabled.

---

## 3. Version 1 performance methodology (no fixture catalog)

Version 1 stress characterization uses **`scripts/bench-runner.sh`**, which:

1. Starts the gateway per profile (**`api-pro`** then **`ai-pro`**).
2. Runs k6 with **`--summary-export`** JSON (not **`benchmarks/k6/fixtures/scenarios.json`**).
3. Appends a compact profile summary table to **`reports/k6_stress_audit_snapshot.md`** (success rate, P95 latency, and aggregated counters such as auth rejects, safety blocks, collisions, nonce-related failures — as emitted by that script’s Python parser).

That path reflects **historical / regression-style** benchmarking: comparable rows over time, independent of the weighted fixture matrix added later.

**Illustrative interpretation** (counts vary by run and k6 version):

| Signal | Meaning |
|---|---|
| **401** | Auth rejection |
| **400** | Validation / safety / nonce-style rejection (depends on mix) |
| **409** | Collision / dedup path |

---

## 4. Version 2 (fixture-driven stress) — separate doc

Weighted scenarios in **`benchmarks/k6/fixtures/scenarios.json`**, executed by **`make stress-test-k6-ai`** / **`make stress-test-k6-api`**, are **Version 2** coverage. They were **not** part of the original Version 1 benchmark story above.

- **Fixture verification & P0 performance gate:** **`reports/Version2_P0_Performance.md`**
- **Operational commands:** **`docs/operations-guide.md`**

---

## Related documents

- **`reports/k6_stress_audit_snapshot.md`** — latest **`bench-runner.sh`** metrics table (Version 1 pipeline).
- **`reports/Version2_P0_Performance.md`** — fixture categories, AI vs API scope, multiformat notes (Version 2).
