# ADR-004: Jedis vs Redisson, HikariCP vs Raw JDBC, and Manual SQL vs Flyway

## Status

Accepted

## Context

The gateway integrates **Redis** (locks, nonces) and **PostgreSQL** (tasks), and applies **schema changes** over time. We had to pick client libraries and a migration strategy.

## Decisions

### 1. Jedis instead of Redisson

**Decision:** Use **Jedis** (sync client with `JedisPooled`) for Redis access.

**Rationale:**

- **Scope:** We need **strings, SET NX EX, GET, EVAL (Lua), and simple keys** — no distributed data structures, no RMap/RLock lifecycle beyond what we implement ourselves.
- **Footprint:** Redisson pulls a **large dependency tree** and higher baseline memory; Jedis stays small and maps closely to Redis commands, which matches a **learning / gateway** codebase.
- **Control:** Explicit `SET` + Lua for unlock matches Redis documentation and is easy to audit. Redisson’s `RLock` is convenient but adds abstraction we are deliberately reimplementing to mirror production-style patterns.

**Trade-off:** We do not get Redisson’s built-in lock watchdog or advanced collections; TTL + Lua is sufficient for Phase 1–4 scope.

---

### 2. HikariCP instead of “plain JDBC” only

**Decision:** Use **HikariCP** as the `DataSource` implementation; application code still uses **standard JDBC** (`Connection`, `PreparedStatement`) via `TaskRepository`.

**Clarification:** **JDBC is the API**; **HikariCP is a connection pool** sitting in front of the PostgreSQL JDBC driver. The alternative is **`DriverManager.getConnection()` per call**, which is **not** a pool.

**Rationale:**

- **Pooling:** A gateway opens many short DB operations; pooling **reuses connections**, caps concurrency to the DB, and avoids connection churn.
- **Production alignment:** HikariCP is a **de facto standard** for Java services; behavior matches what operators expect when tuning `maximumPoolSize`.
- **Small API surface:** We only configure URL, user, password, and pool size — no ORM.

**Trade-off:** One extra dependency versus raw `DriverManager`; the operational benefit dominates at any realistic RPS.

---

### 3. Manual SQL migrations (`scripts/migrations/`) instead of Flyway (for now)

**Decision:** Ship **versioned SQL files** under `scripts/migrations/` and document how to apply them; **Flyway is optional** for a later hardening pass.

**Rationale:**

- **Docker init:** Fresh volumes already run **`scripts/init-db.sql`** — good for local and CI **from zero**.
- **Incremental changes:** Operators (or CI) apply **`V2__....sql`** when upgrading an **existing** database without embedding a migration runner in the JAR yet.
- **Simplicity:** No Flyway plugin, no classpath `db/migration` packaging, no lock table — faster to iterate while the schema is still moving.

**When to adopt Flyway/Liquibase:** When releases need **automatic, ordered migrations** on app startup across many environments; we can add Flyway later without changing the SQL files themselves.

---

## Consequences

- Redis code stays **thin and explicit** (Jedis + Lua).
- DB access stays **JDBC + pool** (HikariCP + `TaskRepository`).
- Schema evolution is **documented + file-based** until Flyway is introduced.
