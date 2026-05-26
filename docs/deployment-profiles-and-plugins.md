# Deployment profiles and plugins

Canonical **profile → plugin wiring** lives in **`com.lumebridge.config.ConfigLoader.activateProfile`** (Java). Setting **`PROFILE`** (Make: `PROFILE=… make run`) turns plugins **on** in the merged config (`enabled: true`), overriding **`lumebridge.yaml`** defaults where the bundle requires it.

Always review **`config/lumebridge.example.yaml`** for tunable knobs (thresholds, modes, similarity, etc.) per plugin ID.

---

## Always-on pipeline core

These three plugins are **always registered** when the gateway starts (see **`LumeBridgeApp`**). Their behavior is still configured via **`lumebridge.yaml`** / **`ConfigLoader`**; deployment profiles explicitly **enable** them in **`api-minimal`** so merges stay coherent with other bundles.

| Plugin ID | Responsibility |
|---|---|
| **`concurrency-gate`** | Limits simultaneous in-flight **`POST /task`** work using a semaphore (“virtual thread gate”) so bursts do not unbounded-queue the JVM. |
| **`payload-hasher`** | Computes a **deterministic SHA-256** of the canonical payload bytes for **`payload_hash`**, correlation, dedupe keys, and lock scope. |
| **`distributed-lock`** | Acquires a **Redis-backed lock** around the guarded section so only one concurrent execution mutates overlapping state per key pattern. |

---

## Plugin catalog (brief)

Alphabetical by **plugin id** (as in **`SentinelConstants`** / YAML `plugins:` keys).

| Plugin ID | What it does |
|---|---|
| **`api-key-auth`** | Validates **`X-API-Key`** (and related headers) against Redis-stored credentials; rejects unknown keys early. |
| **`circuit-breaker`** | Tracks failure streaks and **short-circuits** calls when a backend is unhealthy, avoiding wasted retries and thundering herds. |
| **`client-quota`** | Enforces **per-client** (API key) **rate / quota** limits in Redis before expensive work runs. |
| **`collision-detection`** | Compares incoming payload/version signals with **Postgres**-backed task state to detect **static-body collisions** or drift (configurable mode). |
| **`distributed-lock`** | See [Always-on pipeline core](#always-on-pipeline-core). |
| **`dlq`** | Publishes **permanently failed** or poison tasks to a **Kafka-compatible** bus (**Redpanda** in compose) for offline replay and ops triage. |
| **`dual-mode-router`** | Classifies transport as **`rest`** vs **`mcp`** (JSON-RPC / MCP headers) and stamps routing metadata for downstream handling. |
| **`intent-classifier`** | Derives **`query_intent`** (e.g. real-time vs temporal vs static) from prompt patterns and optional JSON overrides; drives **semantic-cache** eligibility and bucket selection. |
| **`intelligent-router`** | Chooses a **model tier / route** from prompt complexity and approximate token count while preserving REST/MCP transport metadata. |
| **`lock-metrics`** | Emits **lock contention and hold-time** style metrics for Redis lock behavior. |
| **`metrics-exporter`** | Aggregates internal counters/gauges for **`/metrics`** (and related export shapes) for operators. |
| **`nonce-ordering`** | When clients send a **`nonce`**, rejects **stale or out-of-order** sequences per stream key (dormant until payloads opt in). |
| **`payload-hasher`** | See [Always-on pipeline core](#always-on-pipeline-core). |
| **`payload-normalizer`** | **Multi-format** ingestion path: validates and **canonicalizes** bodies (JSON, MsgPack, Protobuf hints, form-encoded, etc.) per **ADR-006** style rules. |
| **`pii-scrubber`** | Masks **PII patterns** (emails, cards, phones, …) in request text before logging or sending to models. |
| **`response-evaluator`** | **POST_PROCESS** checks on response shape / quality heuristics (hallucination / grounding signals as configured). |
| **`safety-guardrails`** | **PRE_PROCESS** safety: jailbreak / restricted-topic signals; may wrap user text in an XML envelope for downstream inspection. |
| **`semantic-cache`** | **pgvector** nearest-neighbour lookup and optional **cache hit short-circuit** scoped by tenant + freshness / intent bucket. |
| **`smart-retry`** | Classifies errors and applies **bounded retries** with backoff and jitter on transient failures. |
| **`stale-data-cleaner`** | **Background** trimming of old completed/failed rows in Postgres to cap table growth. |
| **`task-persistence`** | Persists task metadata and outcomes to **Postgres** for audit, idempotency-style reads, and collision/version plugins. |
| **`telemetry`** | **OpenTelemetry** wiring: trace context propagation and export as configured. |
| **`token-meter`** | Estimates **input/output token** usage for cost observability on the response path. |

### YAML-only bundles (no profile toggle today)

Some plugins ship in **`lumebridge.example.yaml`** but are **not** part of **`ConfigLoader`** profile bundles—they must be enabled explicitly in **`lumebridge.yaml`**:

| Plugin ID | Notes |
|---|---|
| **`version-guard`** | **Optimistic concurrency** using **`expected_version`** against Postgres; requires **`task-persistence`** and DB connectivity. |

---

## Profiles → enabled plugins

**Legend:** Builds are layered: each row **adds** to the baseline of the named parent column.

### Primary deployment profiles (`PROFILE=…`)

| Profile | Layers on | Plugins enabled by the profile *(plugin IDs)* |
|---|---|---|
| **`api-minimal`** | _(baseline)_ | **`api-key-auth`**, **`concurrency-gate`**, **`payload-hasher`**, **`distributed-lock`**, **`task-persistence`**, **`dual-mode-router`** |
| **`ai-minimal`** | `api-minimal` | **`intelligent-router`** |
| **`api-pro`** | `api-minimal` | **`client-quota`**, **`payload-normalizer`**, **`nonce-ordering`**, **`collision-detection`**, **`smart-retry`**, **`circuit-breaker`**, **`metrics-exporter`**, **`stale-data-cleaner`**, **`lock-metrics`** |
| **`ai-pro`** | **`ai-minimal`** *(not `api-pro`)* | **`intent-classifier`**, **`semantic-cache`**, **`safety-guardrails`**, **`pii-scrubber`**, **`response-evaluator`**, **`token-meter`**, **`smart-retry`**, **`circuit-breaker`** |
| **`hybrid`** | **`api-pro`** + **`ai-minimal`** + AI extras | Union of **`api-pro`** roster, **`intelligent-router`**, **`intent-classifier`**, **`semantic-cache`**, **`safety-guardrails`**, **`pii-scrubber`**, **`response-evaluator`**, **`token-meter`**, **`dlq`**, **`telemetry`** (**`smart-retry`** / **`circuit-breaker`** are already part of **`api-pro`**) |

**Important:** **`ai-pro`** is built from **`ai-minimal`** (which is **`api-minimal` + intelligent router**). It does **not** automatically include **`api-pro`** extras such as **`payload-normalizer`**, **`client-quota`**, **`nonce-ordering`**, or **`collision-detection`**. Use **`hybrid`** (or enable plugins in YAML) when you need **both** production API hardening **and** the full AI stack.

### Legacy / alternate profile names

Still recognized by **`ConfigLoader`** for older scripts and tests:

| Profile | Plugins turned on *(adds to whatever YAML already enables)* |
|---|---|
| **`lightweight`** | **`smart-retry`**, **`circuit-breaker`**, **`lock-metrics`**, **`stale-data-cleaner`**, **`metrics-exporter`** |
| **`standard`** | **`collision-detection`**, **`smart-retry`**, **`circuit-breaker`**, **`dlq`**, **`lock-metrics`**, **`stale-data-cleaner`**, **`metrics-exporter`**, **`telemetry`**, **`payload-normalizer`** |
| **`ai`** | **`semantic-cache`**, **`pii-scrubber`**, **`smart-retry`**, **`circuit-breaker`**, **`dlq`**, **`stale-data-cleaner`**, **`metrics-exporter`**, **`telemetry`** |

These profiles **do not** substitute for **`api-minimal`** wiring unless your **`lumebridge.yaml`** already enables auth, gate, router, and persistence.

---

## Infrastructure expectations (summary)

| Profile family | Typical dependencies |
|---|---|
| **`api-minimal`**, **`ai-minimal`** | **Redis**, **Postgres** ( **`task-persistence`** ) |
| **`api-pro`** | Redis, Postgres (+ heavier plugin surface) |
| **`ai-pro`** | Redis, Postgres with **pgvector** extension for **`semantic-cache`** |
| **`hybrid`** | Redis, Postgres (**pgvector**), **Kafka-compatible** (**Redpanda** in-repo) for **`dlq`** + telemetry export paths |

---

## Related documents

- [operations-guide.md](operations-guide.md) — **`PROFILE`** ergonomics (`make run`)
- [hld.md](hld.md) — high-level diagram and profile RSS table
- [concepts.md](concepts.md) — deeper rationale per pattern
- [api-spec.md](api-spec.md) — **`POST /task`** contracts and MCP schema
