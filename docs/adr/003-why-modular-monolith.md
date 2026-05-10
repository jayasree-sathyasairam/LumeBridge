# ADR-003: Why a Modular Monolith (Plugin Pipeline) Instead of Microservices or a Flat Monolith

## Status

Accepted

## Context

LumeBridge is a **gateway**: it throttles traffic, hashes payloads, acquires distributed locks, and will later add idempotency, dual-mode (REST vs MCP) routing, semantic cache, PII scrubbing, circuit breakers, and DLQ handling. We had three structural options:

1. **Microservices** — separate deployables per concern (e.g. “lock service,” “throttle service,” “cache service”).
2. **Flat monolith** — one application with large, coupled classes and cross-cutting logic spread across call sites.
3. **Modular monolith** — **one deployable process** with **clear module boundaries** and **composable features** (plugins) that can be turned on or off and ordered explicitly.

## Decision

We chose a **modular monolith**: a **single Java process** with a **pipeline of plugins** (`Plugin` + `Pipeline` + `RequestContext`), configuration-driven activation (`lumebridge.yaml` + environment overrides), and stages (`Stage` enum) that keep extension points explicit.

## Rationale

### Operational simplicity

A gateway must add **low latency** and **fewer moving parts** than a mesh of small services. Every extra network hop (throttle → lock → cache as separate HTTP calls) multiplies tail latency, failure modes, and deployment coordination. One process keeps the **hot path in-process** while Redis, Postgres, and Redpanda remain **infrastructure**, not duplicate “gateway fragments.”

### Clear boundaries without network splits

**Modular** means features live in **named plugins** with a **single responsibility** (e.g. `ConcurrencyGatePlugin`, `PayloadHasherPlugin`, `DistributedLockPlugin`) instead of a tangle of static helpers. We still get **separation of concerns**; we avoid **premature distribution**.

### Feature evolution and risk control

Not every deployment needs semantic cache, DLQ, or PII scrubbing. A **plugin + config** model allows **enabling features per environment** (e.g. `PROFILE=lightweight` vs `ai`) without forking the codebase or deploying a different service topology. Disabled features can stay **out of the default path** or **not registered**, keeping the default footprint predictable.

### Alignment with the product problem

The original problem statement emphasized **“modular sidecar design”** and a **lightweight core** with **optional intelligence as plugins interceptors**. A modular monolith matches that story: a **thin core** (`Pipeline`, `RequestContext`, `ConfigLoader`) and **pluggable behavior** around it.

### Path to extraction later

If a plugin later becomes a bottleneck or needs independent scaling, its **interface and stage** are a natural seam to **extract a service** behind the same `Plugin` contract (remote adapter). We are not locked in; we **defer** network boundaries until there is evidence they are needed.

## Architecture Comparison

| Criteria | Flat Monolith | Microservices | Modular Monolith (Selected) |
|---|---|---|---|
| **Latency Overhead** | ~0.1ms (In-process) | ~12-60ms (Network hops) | ~0.1ms (In-process) |
| **Operational Burden** | Low (1 binary) | High (12+ services) | Low (1 binary) |
| **Memory Footprint** | Fixed (all features loaded) | Very High (12 container overheads) | Flexible (only enabled features) |
| **Isolation** | None | Full (Process/Network) | Configuration-based |
| **Evolution** | Hard (tight coupling) | Easy (independent) | Medium (pluggable interface) |

## Pros & Cons (Selected Approach)

### Pros
- **Operational Simplicity**: Single binary to version, release, and monitor.
- **Extreme Performance**: Zero network hops between security, hashing, and cache layers.
- **Granular Control**: Use deployment profiles (`api-minimal`, `ai-pro`, etc.) to pick only the features you need.
- **Clean Boundaries**: Plugins communicate via a structured `RequestContext`, not shared global state.

### Cons & Mitigations
- **Shared Memory**: A memory leak in one plugin affects all. *Mitigation: Strict resource monitoring and isolated try-catch blocks.*
- **Static Scalability**: Cannot scale a single plugin independently. *Mitigation: Horizontal scaling of the entire gateway process.*
- **Interface Rigidity**: The `Plugin` interface must be designed correctly upfront. *Mitigation: Extensive upfront design (see Step 2) and generic Map-based config.*

## Trade-offs we accept

## Design patterns in use

The codebase intentionally applies several well-known patterns (they overlap; that is normal in layered systems).

| Pattern | Where it appears | Role |
|---|---|---|
| **Chain of Responsibility** | `Pipeline` builds an ordered list of `MiddlewareFunc`; each handler may call `next` or stop the chain | Request flows through gate → hasher → lock → … without the caller naming every step |
| **Middleware** | `MiddlewareFunc.apply(RequestContext, Runnable next)` | Same “wrap execution, then delegate” shape as HTTP middleware in many frameworks |
| **Strategy (plug-in strategy)** | `Plugin` interface with implementations (`ConcurrencyGatePlugin`, …) | Swappable behavior per stage; configuration chooses **which** plugins are registered (future phases) |
| **Parameter Object / Context Object** | `RequestContext` carries payload, hash, HTTP status, metrics, metadata | Avoids long parameter lists across the chain; single mutable bag **for one request** |
| **Template workflow** | `Pipeline.build()` validates orders and flattens stages; `execute()` drives `runChain` | Stable skeleton; individual steps are customized via plugins |

Patterns we **do not** rely on as formal singletons: **`ConfigLoader` and plugins are not Gang-of-Four singletons** — `main` constructs **one** config loader and **one** set of plugin instances for the JVM lifecycle; that is normal **application scope**, not global static singleton.

## Consequences

- New behavior should be added as a **`Plugin`** in the appropriate **`Stage`** with an explicit **`order()`**, then registered in composition root (`LumeBridgeApp`) or future plugin discovery — **not** as scattered edits inside one mega-class.
- Documentation (`README`, `concepts.md`) should keep **modular monolith** vocabulary aligned with this ADR so onboarding stays consistent.
- If we later split out a service, we treat it as an **infrastructure change** behind an existing plugin boundary rather than a redesign of the whole gateway.
