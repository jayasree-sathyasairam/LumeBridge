The following diagram details the internal logic of the `Pipeline.execute()` method as it processes a request through all 10 feature steps.

![LumeBridge Master Flow](/Users/ramkiranbalaji/.gemini/antigravity/brain/b1b5c777-cd26-400c-99d9-34beb7b7f438/sentinel_nexus_master_flow_infographic_1778257925276.png)

```mermaid
flowchart TD
    Start([fa:fa-play Request Received]) --> Auth{Auth Key Valid?}
    Auth -- No --> Err401[fa:fa-circle-xmark Return 401]
    Auth -- Yes --> Safety{Safety Check Pass?}
    
    Safety -- No --> Err400_S[fa:fa-shield Return 400 Safety Violation]
    Safety -- Yes --> Quota{Within Quota?}
    
    Quota -- No --> Err429[fa:fa-clock Return 429 Too Many Requests]
    Quota -- Yes --> Gate[fa:fa-lock Acquire Semaphore Permit]
    
    Gate --> Hash[fa:fa-fingerprint Generate SHA-256 Hash]
    Hash --> Lock{Redis Lock Available?}
    
    Lock -- No --> Err409_L[fa:fa-lock Return 409 Task Locked]
    Lock -- Yes --> Idem{In Postgres?}
    
    Idem -- Yes --> Exit[fa:fa-check Return Cached Result]
    Idem -- No --> Route[fa:fa-route Intelligent Route Selection]
    
    Route --> SemCache{Semantic Match?}
    SemCache -- Yes --> Exit
    SemCache -- No --> Execute[fa:fa-bolt Call Backend LLM/API]
    
    Execute --> Eval[fa:fa-magnifying-glass Response Evaluation]
    Eval --> Tokens[fa:fa-calculator Token Metering]
    Tokens --> Release[fa:fa-unlock Release Lock & Permit]
    Release --> Finish([fa:fa-stop Response Sent])
```

## 1. Pipeline Engine (Core Framework)

### Purpose

The central middleware framework that runs plugins in stage order. Every request flows through the pipeline. Plugins register during startup; disabled plugins are never added to the chain.

### Internal Architecture

```mermaid
flowchart TB
    subgraph pipeline ["Pipeline Engine (Stage Flow)"]
        direction TB
        PRE["PRE_PROCESS\n(Auth, Safety, Quota, Gate)"]
        DEDUP["DEDUP\n(Hasher, Lock, Idempotency, Nonce)"]
        ROUTE["ROUTE\n(DualRouter, SmartRouter, Cache)"]
        EXEC["EXECUTE\n(CircuitBreaker, SmartRetry)"]
        POST["POST_PROCESS\n(Evaluator, Tokens, Telemetry)"]
    end

    REQ["Incoming JSON + Header"] --> PRE
    PRE --> DEDUP
    DEDUP --> ROUTE
    ROUTE --> EXEC
    EXEC --> POST
    POST --> RES["JSON Response + Metadata"]
```

### Plugin Interface

```go
type Plugin interface {
    Name() string                          // "collision-detection"
    Stage() Stage                          // Which pipeline stage
    Order() int                            // Execution order within stage
    Init(config map[string]string) error   // Setup (DB connections, etc.)
    Middleware() MiddlewareFunc            // The request-processing logic
    Close() error                          // Cleanup on shutdown
}

type MiddlewareFunc func(ctx *RequestContext, next func() error) error
```

### RequestContext (shared across all plugins)

```go
type RequestContext struct {
    RawPayload    []byte
    PayloadHash   string
    RequestID     string
    Nonce         *int64                   // nil if not provided by client
    Route         string                   // "rest" or "mcp"
    Result        interface{}
    Status        string
    HttpStatus    int                      // e.g. 200, 401, 429, 409
    Metrics       map[string]int64         // wait_ms, tokens_input, etc.
    Metadata      map[string]string        // auth_user_id, safety_violation, etc.
    Warnings      []string                 // Non-fatal issues
    Error         error                    // First fatal error
}
```

### Pipeline Build at Startup

```go
func BuildPipeline(config Config) *Pipeline {
    p := NewPipeline()

    // Core (always registered)
    p.Register(&ConcurrencyGatePlugin{})
    p.Register(&PayloadHasherPlugin{})
    p.Register(&DistributedLockPlugin{})

    // Plugins (config-driven)
    if config.Plugins.CollisionDetection.Enabled {
        p.Register(&CollisionDetectionPlugin{})
    }
    if config.Plugins.SmartRetry.Enabled {
        p.Register(&SmartRetryPlugin{})
    }
    // ... other plugins

    p.SortByStageAndOrder()
    return p
}
```

At runtime, `pipeline.Execute(ctx)` iterates through registered plugins in stage order. Each plugin calls `next()` to pass control to the next plugin, or returns an error to short-circuit.

---

## 2. Concurrency Gate (Core Plugin)

### Purpose

Limits the number of requests being processed simultaneously to N permits, protecting downstream resources and maintaining predictable memory usage.

### Plugin Registration

```
Name:  "concurrency-gate"
Stage: PRE_PROCESS
Order: 1
```

### Internal Architecture

```mermaid
flowchart LR
    subgraph incoming ["Incoming Requests"]
        R1["Req 1"]
        R2["Req 2"]
        R3["Req N+1"]
    end

    subgraph gate ["Semaphore (N permits)"]
        P1["Permit 1"]
        P2["Permit 2"]
        PN["Permit N"]
    end

    subgraph processing ["Worker Pool"]
        W1["Process"]
        W2["Process"]
        WN["Process"]
    end

    Queue["Wait Queue"]

    R1 --> P1 --> W1
    R2 --> P2 --> W2
    R3 --> Queue
    Queue -.->|"when permit freed"| PN --> WN
```

### Implementation

```java
class ConcurrencyGatePlugin implements Plugin {
    Semaphore semaphore    // fair=true for FIFO ordering
    AtomicLong activeCount
    AtomicLong totalProcessed
}
```

- `semaphore.acquire()` parks the virtual thread (does not block a carrier thread).
- `try/finally` guarantees `semaphore.release()`.
- Fair semaphore (`new Semaphore(permits, true)`) ensures requests are served in arrival order under contention.
- Each request runs on a virtual thread via `Executors.newVirtualThreadPerTaskExecutor()`.

---

## 3. Distributed Lock (Core Plugin)

### Purpose

Prevent duplicate processing of the same task payload across multiple gateway instances.

### Plugin Registration

```
Name:  "distributed-lock"
Stage: DEDUP
Order: 2
```

### Lock Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Unlocked
    Unlocked --> Locked: SET key val NX EX ttl → OK
    Unlocked --> Rejected: SET key val NX EX ttl → nil
    Locked --> Processing: Worker processes task
    Processing --> Unlocked: EVAL release_lua → 1
    Processing --> Expired: TTL elapsed before release
    Expired --> Unlocked: Key auto-deleted by Redis
    Rejected --> [*]: Return 409
```

### Lock Key Derivation

```
payload bytes → SHA-256 → hex string → "lock:" + hex
```

The SHA-256 hash ensures:
- Same payload always maps to the same lock key (deterministic).
- Different payloads virtually never collide (collision-resistant).

### Redis Commands

**Acquire:**
```
SET lock:{hash} {requestId} NX EX 30
```

**Release (Lua script):**
```lua
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
```

The Lua script runs atomically within Redis, preventing the check-then-delete race condition.

### Request ID Format

```
{first 12 chars of hash}-{nanosecond timestamp}
```

### Implementation

- Uses `JedisPooled` (connection-pooled Redis client).
- **Acquire:** `jedis.set(key, val, SetParams.setParams().nx().ex(ttl))` -- returns `"OK"` on success, `null` if already held.
- **Release:** `jedis.eval(RELEASE_LUA, List.of(key), List.of(val))` -- Lua script for atomic compare-and-delete.

---

## 4. Payload Hasher (Core Plugin)

### Purpose

Generate a deterministic SHA-256 hash of the request payload for use as a deduplication key, lock key, and idempotency key.

### Plugin Registration

```
Name:  "payload-hasher"
Stage: DEDUP
Order: 1
```

### Implementation

```
Input:  raw payload bytes
Output: 64-character lowercase hex string
```

```java
MessageDigest md = MessageDigest.getInstance("SHA-256");
byte[] digest = md.digest(payloadBytes);
String hash = HexFormat.of().formatHex(digest);  // 64-char hex
```

The hasher sets `ctx.PayloadHash` on the `RequestContext`, which downstream plugins (lock, idempotency, collision detection) consume.

---

## 5. HTTP Server & Routing

### Endpoint Map

| Path | Handler | Method |
|---|---|---|
| `/task` | Full plugin pipeline | POST |
| `/healthz` | Health check | GET |
| `/metrics` | JSON gate utilization + aggregated exporter stats | GET |

Planned (Step 4+): `/metrics/prometheus`, `/metrics/locks`, `/clear-collision`, `/task/{hash}/status` when corresponding plugins exist.

### Response Format

All implemented endpoints return `Content-Type: application/json`.

Error responses include an `error` field when applicable. Success responses include a `status` field.

### Port

| Default Port | Env Variable |
|---|---|
| 8080 | `PORT` |

---

## 6. AI Safety & Quality Plugins

### Safety Guardrails
- **Purpose**: Detect jailbreaks and restricted topics in-line.
- **Stage**: `PRE_PROCESS` (Order 3).
- **Logic**: Fast regex matching against `(?i)ignore.*instructions` and keyword denylists (e.g., weapons, hacking). Returns 400 on violation.

### Response Evaluator
- **Purpose**: Detect hallucinations and check grounding.
- **Stage**: `POST_PROCESS` (Order 4).
- **Logic**: Heuristic-based inspection of `ctx.Result`. Checks for refusal patterns ("As an AI...") and grounding against `context` fields in request body.

---

## 7. Production Security Plugins

### API Key Auth
- **Purpose**: Validate caller credentials.
- **Stage**: `PRE_PROCESS` (Order 0).
- **Logic**: Extracts `X-API-Key` header. Validates prefix (`sk-sentinel-`) and extracts `auth_user_id`. In production, this performs a Redis O(1) lookup.

### Client Quota
- **Purpose**: Per-client rate limiting.
- **Stage**: `PRE_PROCESS` (Order 4).
- **Logic**: Uses a Redis sliding window (or `INCR` + `EXPIRE` per minute). Limits are keyed by `auth_user_id`.

---

## 8. Cost & Orchestration Plugins

### Token Meter
- **Purpose**: Cost estimation.
- **Stage**: `POST_PROCESS` (Order 5).
- **Logic**: Naive 4-char-per-token heuristic for input (`RawPayload`) and output (`Result`). Records to `ctx.Metrics`.

### Intelligent Router
- **Purpose**: Model selection and failover.
- **Stage**: `ROUTE` (Order 4).
- **Logic**: Selects route (e.g., `gpt-4o-mini` vs `gpt-4-turbo`) based on prompt length and complexity. Sets `fallback_route` for retry logic.
