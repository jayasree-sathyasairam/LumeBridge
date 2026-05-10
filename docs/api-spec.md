# API & Protocol Specification

The gateway (Java 21 + Virtual Threads) exposes the following endpoints. Endpoints marked with a plugin name are only available when that plugin is enabled.

> **Architecture:** LumeBridge uses a plugin-based architecture. Some endpoints are always available (core), others only register when their plugin is enabled. See [architecture-decision.md](architecture-decision.md).

---

## Pipeline — POST /task

Runs the plugin pipeline configured in `lumebridge.yaml`. Default stack (when Step 3 plugins are enabled in YAML):

`payload-normalizer` (optional) → `concurrency-gate` → `payload-hasher` → `distributed-lock` → `version-guard` (optional) → `nonce-ordering` (optional) → `collision-detection` (optional) → `task-persistence` (optional) → `circuit-breaker` (optional) → `smart-retry` (optional) → `dlq` (optional, POST_PROCESS).

**Request Headers**

| Header | Required | Description |
|---|---|---|
| `X-API-Key` | **Yes** | Authenticates the request. Must start with `sk-sentinel-` |
| `Content-Type` | **Yes** | Must be `application/json` |

**Request Body** (JSON body; arbitrary keys allowed)

**Response 200** (success)

```json
{
  "status": "completed",
  "payload_hash": "...",
  "request_id": "...",
  "metrics": { "gate_wait_ms": 0, "gate_work_ms": 201, "tokens_input": 45, "tokens_output": 120 },
  "safety": { "violation": false },
  "evaluation": { "hallucination_detected": false },
  "tokens": { "input": 45, "output": 120 }
}
```

**Response 409** — Includes `lock_key` / `lock_holder` when locked; `db_version` / `expected_version` when stale; `cached_nonce` / `incoming_nonce` when nonce rejected; `stored_response_hash` / `incoming_response_hash` when collision (`manual` mode).

**Response 503** — When `circuit-breaker` is enabled and the circuit is open: `status` is `circuit_open` and metadata may include `circuit=open`.

**Response 400** — Empty request body at the HTTP layer, or plugin validation (for example empty JSON for `payload-hasher`).

**DLQ** — With plugin `dlq` enabled and Redpanda reachable, **5xx** responses and **400** + `status: error` may emit a JSON record to the configured Kafka topic (`dlq_topic` / `KAFKA_BOOTSTRAP_SERVERS`).

---

## Core Endpoints (Always Available)

Concurrency and locking are exercised only through **`POST /task`** (see pipeline above). Legacy standalone **`/gate`** and **`/lock`** routes have been removed.

### GET /healthz

Health check endpoint.

**Response 200**
```json
{
  "status": "ok"
}
```

---

### GET /metrics

Current gateway utilization metrics (JSON format, always available).

**Response 200** (core metrics — flat keys from `ConcurrencyGatePlugin`):

```json
{
  "max_permits": 10,
  "active_permits": 3,
  "total_processed": 150
}
```

**Response 200** (with `lock-metrics` plugin enabled — illustrative; shape may evolve):
```json
{
  "concurrency_gate": {
    "max_permits": 10,
    "active_permits": 3,
    "total_processed": 150
  },
  "distributed_lock": {
    "total_acquires": 500,
    "successful_acquires": 480,
    "failed_acquires": 20,
    "contention_rate": 0.04,
    "avg_hold_duration_ms": 245,
    "p99_hold_duration_ms": 1200,
    "expired_locks": 2,
    "active_locks": 5
  },
  "hot_keys": [
    {
      "payload_hash_prefix": "a1b2c3d4...",
      "contention_count": 15,
      "window": "5m"
    }
  ]
}
```

**Response 200** (with `stale-data-cleaner` plugin enabled, adds):
```json
{
  "stale_data_cleaner": {
    "last_run_at": "2026-05-04T10:00:00Z",
    "last_run_deleted": 5432,
    "last_run_duration_ms": 1200,
    "total_deleted": 150000,
    "next_run_at": "2026-05-04T11:00:00Z",
    "current_row_count": 245000,
    "max_rows": 1000000
  }
}
```

---

## Plugin Endpoints (Available When Plugin Is Enabled)

### POST /clear-collision

**Plugin:** `collision-detection`

Resolve a collision detected during idempotency check.

**Request**
```http
POST /clear-collision HTTP/1.1
Content-Type: application/json

{
  "payload_hash": "a1b2c3d4...",
  "resolution": "accept_current"
}
```

| Field | Type | Values | Description |
|---|---|---|---|
| `payload_hash` | string | -- | The hash of the colliding task |
| `resolution` | string | `accept_current`, `reprocess` | How to resolve the collision |

**Response 200**
```json
{
  "status": "resolved",
  "payload_hash": "a1b2c3d4...",
  "resolution": "accept_current",
  "previous_status": "collision",
  "new_status": "completed"
}
```

---

### GET /task/{payload_hash}/status

**Plugin:** `smart-retry`

Look up the current status of a task, including error classification details.

**Response 200**
```json
{
  "payload_hash": "a1b2c3d4...",
  "status": "failed",
  "error_category": "transient_exhausted",
  "retry_count": 3,
  "is_retriable": false,
  "error_detail": {
    "status_code": 503,
    "message": "Service Unavailable",
    "backend_url": "https://api.example.com/users"
  },
  "created_at": "2026-05-04T10:00:00Z",
  "updated_at": "2026-05-04T10:00:12Z"
}
```

**Response 404** -- Task not found.

---

### GET /metrics/locks

**Plugin:** `lock-metrics`

Detailed lock breakdown including active locks and recent contentions.

**Response 200**
```json
{
  "active_locks": [
    {
      "lock_key": "lock:a1b2c3d4...",
      "holder": "a1b2c3d4-1717171717171",
      "held_for_ms": 1500,
      "ttl_remaining_ms": 28500
    }
  ],
  "recent_contentions": [
    {
      "lock_key": "lock:xyz789...",
      "attempted_by": "xyz789-1717171718000",
      "blocked_by": "xyz789-1717171717000",
      "at": "2026-05-04T10:00:05Z"
    }
  ]
}
```

---

### GET /metrics/prometheus

**Plugin:** `metrics-exporter` (format: `prometheus`)

Returns metrics in Prometheus text exposition format for scraping by Prometheus, Grafana Agent, Datadog Agent, etc.

**Response 200** (`Content-Type: text/plain`):
```
# HELP sentinel_gate_active_permits Current active permits
# TYPE sentinel_gate_active_permits gauge
sentinel_gate_active_permits 3

# HELP sentinel_gate_max_permits Maximum permits
# TYPE sentinel_gate_max_permits gauge
sentinel_gate_max_permits 10

# HELP sentinel_lock_total_acquires Total lock acquire attempts
# TYPE sentinel_lock_total_acquires counter
sentinel_lock_total_acquires 500

# HELP sentinel_lock_contention_rate Lock contention ratio
# TYPE sentinel_lock_contention_rate gauge
sentinel_lock_contention_rate 0.04

# HELP sentinel_retry_total Total retries by category
# TYPE sentinel_retry_total counter
sentinel_retry_total{category="transient"} 45
sentinel_retry_total{category="rate_limited"} 12

# HELP sentinel_dlq_size Current DLQ depth
# TYPE sentinel_dlq_size gauge
sentinel_dlq_size 120

# HELP sentinel_circuit_state Circuit breaker state
# TYPE sentinel_circuit_state gauge
sentinel_circuit_state 0

# HELP sentinel_stale_cleaner_total_deleted Total rows cleaned
# TYPE sentinel_stale_cleaner_total_deleted counter
sentinel_stale_cleaner_total_deleted 150000
```

---

## MCP / JSON-RPC Schema

**Plugin:** `dual-mode-router`

When the Dual-Mode Router plugin is enabled, requests with a `jsonrpc` field are routed to the MCP handler.

### Detection Logic

```
if request.content_type == "application/json"
   AND request.body contains "jsonrpc" field:
    → route to MCP handler
else:
    → route to REST handler
```

### JSON-RPC Request Format

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "search_knowledge_base",
    "arguments": {
      "query": "How does the circuit breaker work?"
    }
  },
  "id": 1
}
```

### JSON-RPC Response Format

```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "The circuit breaker monitors failure rates..."
      }
    ]
  },
  "id": 1
}
```

### JSON-RPC Error Response

```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32600,
    "message": "Invalid Request"
  },
  "id": 1
}
```

Standard JSON-RPC error codes:

| Code | Meaning |
|---|---|
| -32700 | Parse error |
| -32600 | Invalid request |
| -32601 | Method not found |
| -32602 | Invalid params |
| -32603 | Internal error |

---

## Error Response Format

All error responses follow a consistent structure:

### Backend Failure (with `smart-retry` plugin)

```json
{
  "error": "backend_failure",
  "payload_hash": "a1b2c3d4...",
  "error_category": "transient_exhausted",
  "retry_count": 3,
  "is_retriable": false,
  "error_detail": {
    "status_code": 503,
    "message": "Service Unavailable",
    "backend_url": "https://api.example.com/users"
  },
  "dlq_message_id": "msg-12345"
}
```

### DLQ Message Structure (published to Redpanda)

```json
{
  "payload_hash": "a1b2c3d4...",
  "payload": {"original": "request"},
  "error_category": "transient_exhausted",
  "retry_count": 3,
  "last_status_code": 503,
  "last_error": "Service Unavailable",
  "failed_at": "2026-05-04T10:00:12Z",
  "gateway_instance": "go-8080"
}
```
