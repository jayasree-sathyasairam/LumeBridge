# ADR-002: Why Java 21 Virtual Threads as the Single Implementation Language

## Status

Accepted

## Context

LumeBridge was initially prototyped in three languages (Go, Java 21, Python) to compare concurrency models. With the Phase 1 prototype complete, we need to choose a single language for the full plugin-based implementation.

## Decision

We chose **Java 21 with Virtual Threads** as the sole implementation language.

## Rationale

### Familiarity

The primary developer has production experience with Java from iPaaS V1/V2, including debugging concurrency issues, memory problems, and distributed system failures. Building in Java reduces the learning curve and lets us focus on the architecture and patterns rather than language syntax.

### Virtual Threads Solve the Thread Problem

Traditional Java (one platform thread per request) causes OOM under high concurrency -- exactly the issue seen in iPaaS. Virtual threads (Java 21, Project Loom) fix this:

| Aspect | Platform Threads | Virtual Threads |
|---|---|---|
| Memory per thread | ~1MB stack | ~few KB |
| Max concurrent | ~thousands | ~millions |
| Blocking behavior | Blocks OS thread | Unmounts from carrier, frees OS thread |
| Semaphore.acquire() | Blocks carrier thread | Parks virtual thread, carrier is free |
| Thread.sleep() | Wastes OS thread | Yields carrier to other virtual threads |

One line enables this:

```java
server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
```

### Ecosystem

| Need | Java Library |
|---|---|
| Redis client | Jedis (connection-pooled) |
| Postgres client | JDBC / HikariCP |
| HTTP server | JDK HttpServer (no framework needed) |
| JSON | Gson |
| Build | Maven |
| Testing | JUnit 5 |

### Why Not Go or Python

**Go** has excellent concurrency (goroutines) and the lowest memory footprint, but the developer's production experience is in Java. The architectural patterns (semaphore, distributed lock, pipeline) are language-agnostic -- implementing them in Java provides more transferable career value.

**Python** (asyncio) is single-threaded and requires careful `await` placement to avoid blocking the event loop. For a gateway where every plugin may do I/O (Redis, Postgres, HTTP calls), Java's virtual threads handle blocking transparently without requiring `async/await` throughout the codebase.

## Consequences

- Single codebase to maintain, test, and deploy
- Gateway runs on port 8080 (single instance, no port juggling)
- Benchmarks run against one implementation (simpler, more meaningful)
- All plugin implementations are in Java
- Requires JDK 21+ and `--enable-preview` flag
