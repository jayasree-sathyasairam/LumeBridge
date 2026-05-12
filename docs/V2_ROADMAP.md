# LumeBridge V2: From Gateway to LLM Orchestration Platform

This document outlines the evolution of LumeBridge from a generic high-performance gateway into an **LLM-specific orchestration platform** with production-grade caching, cost control, and state management.

---

## 1. Critical Bug Fixes (P0)

### Context-Aware Semantic Caching
**Status**: BROKEN in V1

**Problem**: Current semantic cache uses cosine similarity alone. This causes **cache poisoning**:
```
Query 1: "What's the weather in NYC today?"       [Embedding: [0.12, 0.85, ...]]
Query 2: "What was the weather in NYC yesterday?" [Embedding: [0.11, 0.84, ...]]
         -> Similarity = 0.98 (> 0.95 threshold)
         -> Returns WRONG answer (today's weather instead of yesterday's)
```

**V2 Solution**: Composite cache key with context hash
```java
String cacheKey = semanticSimilarity(query) 
                  + "_" + hashContext(user_id, api_key, timestamp_bucket)
                  + "_ttl:" + getQueryFreshness(intent);
```

**Implementation**:
1. Extract **intent** from query (real-time vs historical vs user-specific)
2. Include request **context** (user_id, parameters, timestamp) in similarity calculation
3. Assign **TTL** per intent (real-time: 1min, historical: 7days, static: 30days)
4. Only cache if `similarity > threshold AND context matches`

**Timeline**: 2 weeks
**Impact**: Eliminates cache-poisoning, increases hit rate while maintaining correctness

---

### Multi-Format Payload Normalization
**Status**: JSON-ONLY in V1

**Problem**: XML, Protobuf, YAML, MessagePack payloads don't hash correctly -> deduplication fails

**V2 Solution**: Pluggable format normalizers
```java
interface PayloadNormalizer {
    CanonicalForm normalize(byte[] payload) throws UnsupportedFormatException;
}

PayloadNormalizer normalizer = detectFormat(payload);  // JSON, XML, Protobuf, etc.
CanonicalForm canonical = normalizer.normalize(payload);
String hash = sha256(canonical);  // stable across formats
```

**Implementation**:
1. Auto-detect format: `Content-Type` header -> body magic bytes -> schema registry
2. Implement normalizers:
   - `JsonNormalizer`: Sort keys (already have this)
   - `XmlNormalizer`: Canonicalize namespace declarations, sort attributes
   - `ProtobufNormalizer`: Extract field values in deterministic order
   - `FormNormalizer`: Parse `application/x-www-form-urlencoded`
3. Return common `CanonicalForm` interface for hash computation

**Timeline**: 1 week
**Impact**: Deduplication now works across all payload types

---

## 2. Domain-Specific LLM Features (P1)

### Intent Classification (Query Freshness)
**Current**: Binary cache-eligible flag
**V2 Approach**: Classify queries into 5 intent categories with different caching rules:

```java
enum QueryIntent {
    REAL_TIME,       // "What time is it?" -> TTL: 1 min
    TEMPORAL,        // "What was the weather yesterday?" -> TTL: 7 days
    STATIC,          // "What's the capital of France?" -> TTL: 30 days
    CONVERSATION,    // "Based on our earlier discussion..." -> No cache
    COMPUTATION      // "Solve this math problem" -> No cache
}
```

**Detector Implementation**:
```java
Map<QueryIntent, List<Pattern>> patterns = Map.of(
    REAL_TIME, List.of(Pattern.compile("(?i)(now|current|today|latest)")),
    TEMPORAL, List.of(Pattern.compile("(?i)(yesterday|last week|2024)")),
    STATIC, List.of(Pattern.compile("(?i)(capital of|largest|most|definition)"))
);

QueryIntent detect(String query) {
    for (Pattern p : patterns) {
        if (p.matcher(query).find()) return intent;
    }
    return CONVERSATION;
}
```

**Timeline**: 1 week
**Impact**: Cache hit rate increases 40-60%, reduces hallucination risk

---

### Intelligent Model Routing (Cost Optimization)
**Current**: Static route to single model
**V2 Approach**: Route by query complexity and token budget

```java
if (queryComplexity == LOW && tokensEstimate < 500) {
    route = HAIKU;          // 80% cost savings
} else if (queryComplexity == MEDIUM && tokensEstimate < 2000) {
    route = SONNET;         // Balanced
} else {
    route = OPUS;           // Only when needed
}
```

**Complexity Detector**:
- Keywords: "design", "analyze", "evaluate" -> HIGH
- Keywords: "summarize", "list", "extract" -> LOW
- Token estimate using BPE tokenizer approximation

**Fallback Chain on failure**:
```
Try OPUS (best quality)
  -> if timeout, try SONNET (faster)
    -> if still timeout, try HAIKU (fastest)
      -> if all fail, return error
```

**Timeline**: 2 weeks
**Impact**: 30-40% cost reduction, improved latency for simple queries

---

### Conversation State Management
**Current**: Each request is independent
**V2 Approach**: Track conversation context and token budget

```java
@Data
class ConversationSession {
    String sessionId;
    List<Message> history;         // Previous messages
    int tokensUsed;                // Running total
    int tokenBudget = 100_000;     // Max tokens allowed
    
    boolean isWithinBudget(int estimatedTokens) {
        return tokensUsed + estimatedTokens < tokenBudget;
    }
}

// On budget exhaust: auto-summarize old messages
ConversationSession session = getOrCreate(sessionId);
if (!session.isWithinBudget(estimate)) {
    summarizeHistory(session);
}
```

**Implementation**:
1. Store sessions in Redis (TTL: 1 hour)
2. On budget exhaust: summarize oldest messages, keep recent context
3. Warn user when approaching budget
4. Return `warning: "60% of token budget consumed"`

**Timeline**: 2 weeks
**Impact**: Enables long-running conversations, prevents surprise costs

---

### Hallucination Grounding
**Current**: Naive keyword patterns
**V2 Approach**: Fact-check against provided context documents

```java
class HallucinationChecker {
    double scoreGrounding(String response, List<String> context) {
        // Check if response is contradicted by context
        // Check if required sources are cited
        return groundingScore (0.0 - 1.0);
    }
}

// Usage:
if (groundingScore < 0.7) {
    ctx.setHttpStatus(206); // Partial Content
    ctx.getWarnings().add("Low grounding score. Response may be inaccurate.");
}
```

**Implementation Options** (priority):
1. **Exact match**: Simple keyword overlap (1 week)
2. **Cross-encoder**: Fine-tuned NLI model (3 weeks)
3. **LLM-based**: Ask Claude for fact-check (2 weeks)

**Timeline**: 1-3 weeks
**Impact**: Reduces hallucination incidents by 60-80%

---

## 3. Operational Excellence (P2)

### Async Write-Behind Persistence
- **Current**: PostgreSQL synchronous writes block every request (~5-10ms)
- **V2 Approach**: Acknowledge to client immediately; write to Redpanda asynchronously
  - Sequence: Request -> Process -> Write to Redpanda (microseconds) -> Return 200 OK
  - Background worker batches and persists to Postgres

**Timeline**: 1 week
**Impact**: 5-10ms latency reduction per request

---

### Adaptive Rate Limiting (Congestion Control)
- **Current**: Fixed permit counts
- **V2 Approach**: TCP-like "slow start" + backoff on backend latency spikes

```java
if (backendP99Latency > threshold) {
    currentPermits = Math.max(minPermits, currentPermits - 1);
} else if (backendP99Latency < threshold / 2) {
    currentPermits = Math.min(maxPermits, currentPermits + 1);
}
```

**Timeline**: 1 week
**Impact**: Prevents cascading failures, maintains optimal throughput

---

### Real-time Observability Dashboard
- **Approach**: WebSocket-based React control tower consuming Redpanda stream
  - Live request heatmaps (users, models, intents)
  - Real-time cache hit rate, latency percentiles
  - Token burn rate, cost-per-request trends
  - PII blocks, safety violations, hallucination detections

**Timeline**: 3 weeks
**Impact**: Full visibility, early warning on anomalies

---

### GraalVM Native Image
- **Approach**: Compile to native binary -> 10-50ms startup (vs 2s JVM)
- **Use case**: Serverless K8s, FaaS (Lambda, Cloud Run)

**Timeline**: 2 weeks
**Impact**: 40x faster cold start, 30% lower memory

---

## 4. Code Quality (P2)

### Reduce Cyclomatic Complexity
**Current Issues**:
- `SafetyGuardrailsPlugin`: Nested loops + early returns (CC: 8)
- `DualModeRouterPlugin`: Duplicated JSONRPC detection (CC: 6)
- `ResponseEvaluatorPlugin`: Multiple boolean chains (CC: 5)

**V2 Solution**: Extract to strategy objects
```java
// Before: Multiple if-else
if (isRefusal) { ... }
if (isHallucination) { ... }
if (lowGrounding) { ... }

// After: Clean strategy pattern
List<ResponseEvaluator> evaluators = List.of(
    new RefusalDetector(),
    new HallucinationDetector(),
    new GroundingScorer()
);
evaluators.forEach(e -> e.evaluate(ctx, response));
```

**Timeline**: 1 week
**Impact**: Easier to test, extend, and reason about

---

## 5. Priority & Timeline

| Feature | Priority | Timeline | Impact |
|---------|----------|----------|--------|
| Context-aware semantic cache | P0 | 2 weeks | Eliminates hallucination risk |
| Multi-format payload normalization | P0 | 1 week | Dedup works everywhere |
| Intent classification | P1 | 1 week | 40-60% cache improvement |
| Intelligent model routing | P1 | 2 weeks | 30-40% cost reduction |
| Conversation state management | P1 | 2 weeks | Enables long conversations |
| Hallucination grounding | P1 | 1-3 weeks | Reduces hallucinations 60-80% |
| Async write-behind | P2 | 1 week | 5-10ms latency improvement |
| Adaptive rate limiting | P2 | 1 week | Prevents cascading failures |
| Observability dashboard | P2 | 3 weeks | Full visibility |
| GraalVM native image | P2 | 2 weeks | 40x cold start improvement |
| Reduce CC in plugins | P2 | 1 week | Maintainability |

---

## 6. Acceptable Trade-offs

- **Memory-for-Speed**: Accept higher RAM (virtual threads) for simplest concurrency
- **Redis Centralization**: Accept Redis dependency for distributed locking
- **Embedding API Cost**: LLM-based grounding requires API calls; use caching to amortize
- **JSON Parsing Overhead**: Multi-format support adds 2-5ms; worth it for 100% dedup

---

## 7. Market Positioning

**V2 Tagline**: "The cost-optimized, hallucination-resistant gateway for production LLM workloads"

**Key Differentiators**:
1. **Context-aware caching** (vs generic gateways that cache blindly)
2. **Intelligent routing** by query complexity (vs fixed routes)
3. **Hallucination grounding** (vs fire-and-forget evaluation)
4. **Token budgets** (vs unlimited burn)

**Target Customers**:
- LLM-powered SaaS (Zapier, Make, Intercom AI)
- Enterprise automation (internal ChatGPT, document processing)
- Agencies building LLM apps (need cost control + quality)

---

© 2026 LumeBridge Engineering Group
