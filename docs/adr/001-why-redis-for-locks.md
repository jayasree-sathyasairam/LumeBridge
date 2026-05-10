# ADR-001: Why Redis for Distributed Locks Instead of Database-Level Locking

## Status

Accepted

## Context

LumeBridge requires a distributed locking mechanism to prevent duplicate processing of tasks across multiple gateway instances. We evaluated three approaches:

1. **PostgreSQL Advisory Locks** (`pg_advisory_lock`)
2. **PostgreSQL Row-Level Locks** (`SELECT ... FOR UPDATE`)
3. **Redis SETNX with TTL**

## Decision

We chose **Redis `SET ... NX EX`** for distributed locking.

## Rationale

### Performance

| Metric | Redis SETNX | Postgres Advisory Lock | Postgres Row Lock |
|---|---|---|---|
| Lock acquire latency | ~0.1ms | ~1-5ms | ~1-5ms (+ query parse) |
| Lock release latency | ~0.1ms (Lua) | ~1ms | Tied to transaction |
| Throughput at 1000 RPS | Negligible impact | Connection pool pressure | Connection pool pressure |

Redis operates entirely in-memory with single-threaded command processing, making lock operations predictably fast with sub-millisecond latency.

### SET Command Parameters

```
SET lock:{hash} {request_id} NX EX 30
```

| Parameter | Type | Function |
|---|---|---|
| `key` | String | The unique name of the lock (e.g., `lock:{payload_hash}`) |
| `value` | String | The owner's unique identifier (e.g., `{hash_prefix}-{nanosecond_timestamp}`) |
| `NX` | Condition | "Set if Not eXists" -- ensures exclusivity. Only one worker can hold the lock. |
| `PX` | Unit | Sets expiration in **milliseconds** (for sub-second TTLs) |
| `EX` | Unit | Sets expiration in **seconds** (used in LumeBridge: `EX 30` = 30 second TTL) |

`NX` is what makes this a lock -- if the key already exists, the command returns `nil` and nothing changes. `EX`/`PX` is what makes it safe -- even if the holder crashes, the lock auto-releases after the TTL.

### Automatic Expiry (TTL)

Redis locks expire automatically via the `EX` parameter. If a worker crashes mid-processing, the lock is released after the TTL window. PostgreSQL advisory locks require explicit release or connection termination -- a crashed process with a persistent connection pool leaves orphaned locks.

### Separation of Concerns

Using Redis for locks keeps the Postgres connection pool exclusively for data operations. Under high concurrency, mixing lock traffic with data queries can exhaust the pool and create cascading failures.

### Safe Release via Lua

The atomic compare-and-delete Lua script prevents a common race condition where a slow worker's lock expires, another worker acquires it, and the original worker then accidentally releases the new lock:

```lua
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
```

### Trade-offs Accepted

- **Redis is an additional dependency**: We accept this because Redis is already required for rate limiting and (later) semantic caching, so it adds no new operational burden.
- **No built-in lock queuing**: Redis SETNX is a simple try-or-fail lock. For Phase 1, this is sufficient; the concurrency gate already manages queuing. If fair queuing is needed later, we can layer Redlock or a Redpanda-based queue.
- **Single-node Redis risk**: In development, a single Redis node is acceptable. For production, Redis Sentinel or Cluster should be used for HA.

## Consequences

- Lock operations do not consume Postgres connections.
- Crashed workers do not leave permanent orphaned locks.
- Lock latency remains sub-millisecond even under high concurrency.
- The lock protocol is Redis-native, making it implementation-agnostic and easy to reason about.
