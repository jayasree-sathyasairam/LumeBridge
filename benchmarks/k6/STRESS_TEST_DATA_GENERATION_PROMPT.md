# Meta-Prompt: LumeBridge Stress / Regression Test Data Generation

Copy everything inside **META PROMPT (below)** into ChatGPT / Claude / an internal generator tool when you need new payloads for **`benchmarks/k6/stress_test.js`** or a successor harness (`POST /task`).

---

## META PROMPT (START COPYING HERE)

### Role

You are a **Principal QA & Automation Architect** generating **production-grade, reproducible test payloads** for **LumeBridge**, a **Java 21** middleware gateway using an ordered **plugin pipeline** on **`POST /task`** (also mirrored at **`/gate`** and **`/lock`** for benchmarks).

Your output must be structured JSON/YAML/XML/text snippets suitable for **k6**, **artillery**, or static fixtures—with explicit **`tags`**, **`kind`** (`stress`|`negative`|`contract`|`future-gap`), **`plugins_under_test`**, and **`expected_http_status`** when assertions apply.

---

### Primary Ingress Reality Check

1. **Main endpoint**: **`POST /task`** with raw HTTP body bytes captured into pipeline **`RequestContext`**.  
2. **Documented contract** (`docs/api-spec.md`): clients commonly send **`Content-Type: application/json`**.  
3. **MCP-style routing**: JSON body containing **`jsonrpc`** **OR** header **`MCP-Protocol-Version`** triggers **`dual-mode-router`** route **`mcp`** (when plugin enabled).  
4. **Not implemented as native listeners today**: standalone SOAP endpoint, GraphQL endpoint, WebSocket MCP, SSE MCP. For regression/stress **until adapters land**, model SOAP/XML/GraphQL as **`embedded strings`** inside JSON (`soap_envelope`, `graphql_query`) **OR** alternate **`Content-Type`** bodies—but tag fixtures **`future-gap`** or **`multi-protocol-shell`** and set **`expected_http_status`** to **`implementation_defined`** or **`policy_dependent`** when strict rejects do not exist.

---

### Implemented Plugins You MUST Cover

Generate vectors grouped under each **`plugin_id`** (YAML `plugins.<id>` names—must match `SentinelConstants` / `LumeBridgeApp`):

| plugin_id | Purpose (what to stress) |
|-----------|---------------------------|
| **`payload-normalizer`** | JSON / XML / YAML / form-urlencoded / **MessagePack** / **Protobuf** paths; canonical hashing inputs; **`Content-Type` mismatch**; warnings (`payload_normalizer_skipped:`). Protobuf needs **`X-Protobuf-Message`** + deployed **`protobuf_descriptor_path`**. |
| **`pii-scrubber`** | Emails, phones, PANs, SSN-like patterns in `prompt` / text fields; assert **`pii_scrub_count`** or metadata when enabled. |
| **`concurrency-gate`** | Backpressure under load; permit exhaustion (profile-dependent). |
| **`payload-hasher`** | Empty body → **400**; stable hash driving lock + nonce keys. |
| **`distributed-lock`** | Thundering herd: identical bodies → **409** with metadata **`lock_key`**, **`lock_holder`**. |
| **`version-guard`** | **`expected_version`** stale vs DB → **409** `stale_version` metadata. |
| **`nonce-ordering`** | Monotonic **`nonce`** per **`payload_hash`**. Replay / decrease → **409** `nonce_rejected` (`cached_nonce` / `incoming_nonce`). Omit `nonce` → plugin skips. |
| **`collision-detection`** | Same hash, divergent semantic outcome per mode (`disabled` / `manual` / `auto_reprocess`); **409** collision metadata when manual. |
| **`task-persistence`** | Durable task writes—stress ordering with persistence enabled + Postgres. |
| **`dual-mode-router`** | REST vs MCP detection (`jsonrpc`, MCP header). |
| **`semantic-cache`** | pgvector NN + **`scope`** (tenant fingerprint + freshness bucket); **`similarity_threshold`**, **`embedding_dimensions`**; paraphrase pairs; **today vs yesterday** freshness traps; optional **`cache_bypass`**. |
| **`intent-classifier`** | Intent metadata driving cache eligibility flags (`META_INTENT`, cache-eligible vs must-execute). |
| **`circuit-breaker`** | After failures → **503** `circuit_open`. |
| **`smart-retry`** | Transient categories—pairs with breaker/upstream simulation labels (often harness-dependent **502** / retries). |
| **`dlq`** | Failures producing Kafka records—tag payloads **`expects_dlq_record`** when **5xx** / qualifying **400**. |
| **`api-key-auth`** | **`X-API-Key`** missing / wrong prefix → **401**. Valid prefix: **`sk-sentinel-*`**. |
| **`safety-guardrails`** | Jailbreak / policy-breaking **`prompt`** → often **400** when blocking (`stress_test.js` expects `"blocked 400"` for violations—confirm profile). |
| **`client-quota`** | Burst same identity → **429**. |
| **`response-evaluator`** | Evaluation payload shapes affecting hallucination flags in responses. |
| **`lock-metrics`** | Side-channel metrics—not HTTP-blocking but generates richer **`GET /metrics`**. |
| **`stale-data-cleaner`** | Maintenance semantics—optional **`metrics`** shape extensions. |
| **`metrics-exporter`** | Enrich **`GET /metrics`** response (`metrics.exporter` subtree when plugin enabled)—Prometheus **text** exposition may exist only in docs/spec evolution; tag **`policy_dependent`** if hitting scrape endpoints outside `LumeBridgeApp`. |
| **`token-meter`** | Token accounting fields in response (`tokens_input` / `tokens_output` metadata paths). |
| **`intelligent-router`** | Payload size vs **`MAX_PAYLOAD_SIZE` (5000 bytes)** routing tier signal; large payload routing scenarios. |
| **`telemetry`** | Correlation / logging—pair all scenarios with **`X-Trace-ID`**, **`X-Span-ID`**, **`traceparent`**, optional **`X-Sequence-Number`**. |

**Always-on core** (registered unless refactored): **`concurrency-gate`**, **`payload-hasher`**, **`distributed-lock`** (`LumeBridgeApp` registers after optional PRE_PROCESS plugins).

---

### V2 Roadmap Coverage (from `docs/V2_TECHNICAL_ROADMAP.md`)

Tag fixtures so backlog visibility survives CI filtering:

| Tag | Roadmap theme | Examples |
|-----|-----------------|----------|
| **`@v2-p0-context-cache`** | Context-aware semantic cache | Tenant isolation + freshness buckets + conflicting near-duplicates (“today” vs “yesterday”). |
| **`@v2-p0-multiformat`** | Multi-format normalization | JSON/XML/YAML/form/msgpack/protobuf/octet-stream combinations per ADR-006. |
| **`@v2-p0-storage-hardening`** | pgvector ANN discipline | High-cardinality synthetic embeddings batch seed scripts + expectation **`explain_bounded`** (no sequential scan at N rows)—often offline validation, not single HTTP body. |
| **`@v2-p1-intent`** | 5-intent + adaptive TTL | Payloads naming intents explicitly until classifier upgraded—mark **`partial_implementation`** if still binary cache-eligible. |
| **`@v2-p1-routing`** | Intelligent routing / fallback | Upstream failure simulation headers (`X-Simulate-Upstream-503`) **future** unless harness injects. |
| **`@v2-p1-conversation`** | Conversation state / budgets | Multi-turn **session id** fields—tag **`future-gap`** if gateway ignores today. |
| **`@v2-p1-grounding`** | Hallucination grounding | Retrieval-augmented payloads—tie to **`response-evaluator`** when present else **`future-gap`**. |
| **`@v2-p2-outbox`** | Transactional outbox | Idempotent CRM sync mutations—**no HTTP assertion** until implemented; tag **`future-gap`**. |
| **`@v2-p2-adaptive-limiting`** | Adaptive rate limiting | Signal injection (`X-Backend-Pressure`)—**`future-gap`**. |
| **`@v2-p2-observability`** | OTEL spans / dashboards | Span context propagation-only fixtures. |
| **`@v2-p2-graal`** | Native image | Cold-start soak metadata—**`future-gap`**. |

---

### Features NOT Fully Available — Still REQUIRED In Dataset

For each item, emit explicit **`expectation_mode`**:

- **`expect_strict`** — gateway must enforce (implemented plugin).  
- **`expect_warning`** — may succeed with `warnings[]`.  
- **`policy_dependent`** — passes today; document risk.  
- **`future_gap`** — placeholder contract / adapter readiness.

Include fixtures for:

1. **Native SOAP / GraphQL / MCP-over-WebSocket / MCP-over-SSE** transports.  
2. **HTTP 413** enforced at ingress for multi-megabyte bodies (today: reader loads full body—stress memory / OOM risk).  
3. **Standard `Idempotency-Key`** semantics beyond Redis lock dedupe (custom headers only unless plugin added).  
4. **WAF-style rejection** of SQLi/XSS substrings at edge (today may pass to safety / scrubbers).  
5. **Streaming responses** / chunked upload from clients.  
6. **Multi-region failover** headers.  
7. **Automatic protobuf schema registry fetch** (today: local `FileDescriptorSet` file path).

---

### Original Scenario Requirements (Must Preserve)

1. **Sequential entity mutations** using **`nonce`**, **`Transaction-ID`**, **`Idempotency-Key`**, **`X-Sequence-Number`**: CREATE → UPDATE → DELETE with **intentional gaps** (nonce 5 before 4) and **replays**.  
2. **Tiered priorities**: **`p0_core_security`**, **`p1_integrity_compliance`**, **`p2_intelligence_ops`**.  
3. **Adversarial**: SQLi, XSS, jailbreak / prompt injection, oversized fields, integer overflow JSON numbers, **`Content-Type` lies**.  
4. **Thundering herd**: duplicate concurrent bodies + shared correlation IDs.  
5. **PII density** for scrubber.  
6. **Semantic cache**: paraphrase pairs + anti-poisoning freshness contrasts.  
7. **Routing / fallback**: simulated upstream failure annotations (harness interprets).  

---

### Stress Test Harness Constraints (k6)

Align with **`benchmarks/k6/stress_test.js`** patterns:

- URL: **`${TARGET_URL}/task`**.  
- **`TEST_TYPE`**: `AI` vs `API` switches scenario blend (prompt-heavy vs method/id style).  
- **`X-API-Key`**: `sk-sentinel-stress-${VU}` for happy paths; inject **`invalid-key-*`** for auth soak.  
- **`nonce`**: derive from **`Date.now() * 100000 + VU * 1000 + iter`** for monotonicity unless testing **`collision`** (`nonce: 9999`) or **`nonce_violation`**.  
- Thresholds: **`errors` rate < 5%** allows intentional branch failures—tag intentional **`expect_failure`** payloads so k6 checks **do not** count them as errors incorrectly.

Every generated scenario SHOULD declare **`vu_scaling_notes`** (e.g. collision scenarios need shared static payload hash).

---

### Output Shape You MUST Produce

Emit **JSON array** of scenarios:

```json
{
  "id": "LB-STRESS-00042",
  "tags": ["@p1", "@distributed-lock", "@herd"],
  "priority_tier": "p1_integrity_compliance",
  "plugins_under_test": ["distributed-lock", "payload-hasher"],
  "expectation_mode": "expect_strict",
  "expected_http_status": 409,
  "headers": { "Content-Type": "application/json", "X-API-Key": "sk-sentinel-stress-${VU}" },
  "body": {},
  "k6_check_expression_hint": "r.status === 409 && JSON.parse(r.body).lock_key !== undefined",
  "notes": "Concurrent iterations share identical JSON.stringify(body) for lock contention."
}
```

Also produce **`payload_mix_weights`** recommendation block matching **`stress_test.js`** percentages (`auth_failure` 5%, `safety_violation` 10%, etc.) when asked for **scenario matrices**.

---

### Deliverables Checklist

When answering the user’s generation request, always produce:

1. ≥ **40** distinct scenarios spanning **all implemented plugins** (disable-aware subsets OK).  
2. ≥ **10** scenarios tagged **`future_gap`** / **`multi-protocol-shell`**.  
3. ≥ **8** sequential mutation chains (CREATE/UPDATE/DELETE / out-of-order).  
4. ≥ **6** semantic-cache pairs (hit expected / anti-hit freshness).  
5. Multiformat bodies: **JSON**, **XML-as-body**, **YAML-as-body**, **msgpack** (base64 optional), **protobuf** (hex placeholder).  
6. Correlation headers on ≥ **80%** of scenarios.

---

### META PROMPT (STOP COPYING HERE)

---

## Repo pointers

| Artifact | Path |
|----------|------|
| k6 stress script | `benchmarks/k6/stress_test.js` |
| HTTP contract | `docs/api-spec.md` |
| V2 backlog | `docs/V2_TECHNICAL_ROADMAP.md` |
| Payload protobuf/msgpack ADR | `docs/adr/006-multi-format-payload-canonicalization.md` |
| Plugin wiring order | `core/java/src/main/java/com/lumebridge/LumeBridgeApp.java` |

### Implemented in-repo

1. **Generator:** `benchmarks/k6/scripts/generate-scenarios.mjs` — run  
   `node benchmarks/k6/scripts/generate-scenarios.mjs`  
   (from repo root or `benchmarks/k6`) to refresh **`benchmarks/k6/fixtures/scenarios.json`**.
2. **Runner:** `benchmarks/k6/stress_test.js` loads fixtures via **`SharedArray`** + **`open("./fixtures/scenarios.json")`** when **`STRESS_MODE=fixtures`** (default). Use **`STRESS_MODE=legacy`** for the previous inline random mix.
3. **Make:** `make stress-test-k6-api` / `make stress-test-k6-ai` invoke k6 against committed `fixtures/scenarios.json`. Regenerate JSON only when needed: `make stress-k6-regenerate-fixtures` (needs Node) or `node scripts/generate-scenarios.mjs` from `benchmarks/k6`.

Optional env: **`VUS`**, **`ITERATIONS`**, **`MAX_DURATION`**, **`SLEEP_MS`**, **`TARGET_URL`**.
