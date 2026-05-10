# ADR-005: Model Routing & Multi-Cloud Resiliency Strategy

## Status

Accepted

## Context

The `IntelligentRouterPlugin` must decide which LLM model should handle a specific request. Choosing a single model for all traffic results in either excessive costs (using GPT-4 for simple tasks) or poor performance (using a small model for large context). Furthermore, relying on a single provider (OpenAI) creates a single point of failure for the entire gateway.

## Decision

Implement a **Tiered Routing Strategy** based on payload size and a **Cross-Provider Fallback** mechanism.

### The Selection Logic:
1. **High-Capacity Tier (`gpt-4-turbo`)**: For prompts > 5000 bytes.
2. **Efficiency Tier (`gpt-4o-mini`)**: The default for standard requests.
3. **Resiliency Tier (`claude-3-haiku`)**: The primary fallback if OpenAI services are unreachable.

## Rationale

### 1. gpt-4-turbo (The Powerhouse)
- **Why**: Handles large context windows and complex reasoning more reliably than smaller models.
- **Trigger**: Payload > 5000 bytes indicates a complex task (e.g., summarizing long documents or multi-step logic) where intelligence takes priority over cost.

### 2. gpt-4o-mini (The Efficiency Default)
- **Why**: Extreme cost-efficiency (significantly cheaper than GPT-4) with "GPT-3.5 level" speed and "GPT-4 class" intelligence for routine tasks.
- **Trigger**: Default for any request that doesn't explicitly request a route or exceed the complexity threshold.

### 3. claude-3-haiku (The Resilient Fallback)
- **Why**: True **Multi-Cloud Resilience**. If OpenAI experiences a regional outage or API rate-limiting, falling back to Anthropic ensures the gateway stays online.
- **Selection**: Haiku is chosen because it is the fastest and cheapest in the Claude 3 family, making it an ideal "silent fallback" that provides high availability without a significant cost penalty during failover events.

## Consequences

- **Cost Savings**: Automatically shifts ~80% of routine traffic to the Efficiency Tier.
- **Higher Reliability**: The `SmartRetryPlugin` can now leverage the `fallback_route` metadata to switch providers mid-request.
- **Future-Proofing**: The logic is encapsulated in a single plugin; as new models (like Llama 3 or Gemini) are added, the thresholds can be adjusted without touching the core pipeline.

## Alternative Models Considered

- **GPT-3.5 Turbo**: Rejected in favor of `gpt-4o-mini` due to the latter's superior intelligence at a lower price point.
- **Claude 3 Opus**: Rejected as a fallback because its high latency and cost make it unsuitable for broad-spectrum failover; it is reserved for manual overrides only.
