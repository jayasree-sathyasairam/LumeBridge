# LumeBridge: Master Architecture & Data Flow

This document is the primary reference for the LumeBridge architecture. It combines high-level data flow diagrams (DFD), per-profile sequence diagrams, and database forensic details into a single source of truth.

---

## 1. High-Level Data Flow (Gane-Sarson Style)

This diagram shows how data flows between external entities, core processes, and persistent data stores.

```mermaid
flowchart TD
    %% Entities
    Client[[Client]]
    AIProvider[[AI Provider]]

    %% Data Stores
    Redis[(Redis\nLocks/Quotas)]
    Postgres[(Postgres\nTasks/Cache)]

    %% Processes
    P1(fa:fa-shield-halved 1. Secure & Normalize)
    P2(fa:fa-fingerprint 2. Deduplicate & Verify)
    P3(fa:fa-route 3. Classify & Cache)
    P4(fa:fa-bolt 4. Execute & Protect)
    P5(fa:fa-chart-line 5. Evaluate & Meter)

    %% Flow
    Client -- "JSON Payload + API Key" --> P1
    P1 -- "Clean Payload" --> P2
    P2 -- "Payload Hash" --> Redis
    Redis -- "Lock/Quota/Nonce Status" --> P2
    P2 -- "Verified Payload" --> P3
    P3 -- "Intent / Similarity" --> Postgres
    Postgres -- "Cached Result" --> P3
    P3 -- "Target Route" --> P4
    P4 -- "Validated Request" --> AIProvider
    AIProvider -- "AI Response" --> P4
    P4 -- "Raw Result" --> P5
    P5 -- "Evaluated Response" --> Client
    P5 -- "Usage & Tokens" --> Postgres
    P5 -- "Failure Events" --> Kafka[(Redpanda\nDLQ)]
    
    %% Styling
    style P1 fill:#ff9966,stroke:#333,stroke-width:2px
    style P2 fill:#ff9966,stroke:#333,stroke-width:2px
    style P3 fill:#ff9966,stroke:#333,stroke-width:2px
    style P4 fill:#ff9966,stroke:#333,stroke-width:2px
    style P5 fill:#ff9966,stroke:#333,stroke-width:2px
    style Redis fill:#fff,stroke:#333,stroke-width:2px
    style Postgres fill:#fff,stroke:#333,stroke-width:2px
```

---

## 2. Profile-Specific Request Lifecycles

Different profiles activate different "stops" along the data highway. 

````carousel
### 1. api-minimal (Core API)
```mermaid
sequenceDiagram
    participant C as Client
    participant P as Pipeline
    participant R as Redis
    participant DB as Postgres

    C->>P: POST /task (JSON + X-API-Key)
    Note over P: STAGE: PRE_PROCESS
    P->>R: API Key Auth
    Note over P: STAGE: DEDUP
    P->>P: Hash Payload
    P->>R: Acquire Distributed Lock
    P->>DB: Idempotency Lookup
    Note over P: STAGE: EXECUTE
    P->>P: Process Request
    Note over P: STAGE: POST_PROCESS
    P->>R: Release Lock
    P-->>C: 200 OK
```
<!-- slide -->
### 2. ai-minimal (Core AI)
```mermaid
sequenceDiagram
    participant C as Client
    participant P as Pipeline
    participant R as Redis
    participant DB as Postgres
    participant AI as AI Provider

    C->>P: POST /task (JSON + X-API-Key)
    Note over P: STAGE: PRE_PROCESS
    P->>R: API Key Auth
    Note over P: STAGE: DEDUP
    P->>P: Hash Payload
    P->>R: Acquire Distributed Lock
    P->>DB: Idempotency Lookup
    Note over P: STAGE: ROUTE
    P->>P: Intelligent Router
    Note over P: STAGE: EXECUTE
    P->>AI: Call LLM
    Note over P: STAGE: POST_PROCESS
    P->>R: Release Lock
    P-->>C: 200 OK
```
<!-- slide -->
### 3. api-pro (Production API)
```mermaid
sequenceDiagram
    participant C as Client
    participant P as Pipeline
    participant R as Redis
    participant DB as Postgres

    C->>P: POST /task (JSON + X-API-Key)
    Note over P: STAGE: PRE_PROCESS
    P->>R: [ApiKeyAuth]
    P->>R: [ClientQuota]
    P->>P: [PayloadNormalizer]
    Note over P: STAGE: DEDUP
    P->>P: [PayloadHasher]
    P->>R: [DistributedLock]
    P->>DB: [Idempotency]
    P->>R: [NonceOrdering]
    P->>DB: [CollisionDetection]
    Note over P: STAGE: ROUTE
    P->>P: [DualModeRouter] (REST/MCP)
    Note over P: STAGE: EXECUTE
    P->>P: [CircuitBreaker]
    P->>P: [SmartRetry]
    Note over P: STAGE: POST_PROCESS
    P->>P: [MetricsExporter]
    P->>R: [LockMetrics]
    P->>R: Release Lock
    P-->>C: 200 OK
```
<!-- slide -->
### 4. ai-pro (Production AI)
```mermaid
sequenceDiagram
    participant C as Client
    participant P as Pipeline
    participant R as Redis
    participant DB as Postgres
    participant AI as AI Provider

    C->>P: POST /task (JSON + X-API-Key)
    Note over P: STAGE: PRE_PROCESS
    P->>R: [ApiKeyAuth]
    P->>P: [SafetyGuardrails]
    P->>P: [PIIScrubber]
    Note over P: STAGE: DEDUP
    P->>P: [PayloadHasher]
    P->>R: [DistributedLock]
    Note over P: STAGE: ROUTE
    P->>P: [IntentClassifier]
    P->>DB: [SemanticCache] Search
    P->>P: [IntelligentRouter]
    Note over P: STAGE: EXECUTE
    P->>AI: Call LLM
    Note over P: STAGE: POST_PROCESS
    P->>P: [ResponseEvaluator]
    P->>P: [TokenMeter]
    P->>R: Release Lock
    P-->>C: 200 OK
```
<!-- slide -->
### 5. hybrid (Full Suite)
```mermaid
sequenceDiagram
    participant C as Client
    participant P as Pipeline
    participant R as Redis
    participant DB as Postgres
    participant AI as AI Provider
    participant K as Redpanda

    C->>P: POST /task (Full Security)
    Note over P: PRE_PROCESS
    P->>R: Auth & Quota
    P->>P: Normalizer
    P->>P: Safety & PII
    Note over P: DEDUP
    P->>P: Hash & Lock
    P->>R: Nonce Order
    P->>DB: Collision Check
    Note over P: ROUTE
    P->>DB: Semantic Cache
    P->>P: Intelligent Router
    Note over P: EXECUTE
    P->>AI: Call Backend
    Note over P: POST_PROCESS
    P->>P: Evaluation & Token Meter
    P->>K: DLQ & Telemetry
    P-->>C: 200 OK
```
````

---

## 3. Internal Data State (The "Forensic" View)

As data flows through the gateway, it is persisted in Postgres and Redis.

### Postgres: `tasks` Table
| Column | Data Type | Production Value Example | Rationale |
|---|---|---|---|
| `payload_hash` | TEXT | `b3685ae...` | Used for instant deduping and caching. |
| `payload` | TEXT | `<user_input>My email is [SHA256]...</user_input>` | Stores the **guarded** and **scrubbed** version. |
| `result` | JSONB | `{"content": "Hello!"}` | The cached response from the model. |
| `metrics` | JSONB | `{"gate_wait_ms": 2}` | Used for cost and performance analysis. |

### Redis: Global State
| Key Pattern | Data Store | Usage |
|---|---|---|
| `lock:{hash}` | Distributed Lock | Prevents multiple instances from processing the same task. |
| `quota:{user}:{min}` | Rate Limiter | Enforces per-client quotas across the cluster. |
| `nonce:{user}` | Nonce Manager | Ensures strictly increasing request order. |

---

## 4. Clustered Production Deployment

LumeBridge is designed for a **Cloud-Native Clustered Architecture**:

1.  **Ingress (Stateless)**: Multiple gateway instances handle traffic behind a Load Balancer.
2.  **Shared State (Redis)**: Ensures consistency for locks and quotas across all instances.
3.  **Durable Audit (Postgres)**: Single source of truth for task history and vector embeddings.
4.  **Telemetry (Redpanda)**: Async streaming of logs and errors for real-time monitoring.

---

## 5. Summary Table: Profile Capabilities

| Feature | `api-minimal` | `ai-minimal` | `api-pro` | `ai-pro` | `hybrid` |
|---|---|---|---|---|---|
| **Deduplication** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **PII Scrubbing** | ❌ | ❌ | ❌ | ✅ | ✅ |
| **Safety Guardrails** | ❌ | ❌ | ❌ | ✅ | ✅ |
| **Intelligent Routing**| ❌ | ✅ | ❌ | ✅ | ✅ |
| **DLQ / Telemetry** | ❌ | ❌ | ❌ | ❌ | ✅ |

---

## 6. Internal Data Structure: `RequestContext`

The `RequestContext` is the primary object passed between plugins. It is mutable and accumulates state as the request progresses.

| Field | Description | Set By |
|---|---|---|
| `auth_user_id` | Identified user/service | `ApiKeyAuthPlugin` |
| `payload_hash` | Unique request fingerprint | `PayloadHasherPlugin` |
| `route` | Target backend/model | `IntelligentRouterPlugin` |
| `tokens_input` | Estimated prompt tokens | `TokenMeterPlugin` |
| `safety_violation`| Flag if prompt was malicious | `SafetyGuardrailsPlugin` |

---

## 7. Background Maintenance Operations

Independent of the request flow, the following processes maintain system health.

```mermaid
flowchart LR
    Cleaner["StaleDataCleaner"] -- "DELETE older than 24h" --> DB[(Postgres)]
    Exporter["MetricsExporter"] -- "Collect Stats" --> Plugins["All Plugins"]
    Exporter -- "Expose /metrics" --> Prometheus["Prometheus/Grafana"]
    Plugins -- "Record Contention" --> Redis[(Redis)]
```
