import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

// Step 5: enable pii-scrubber, dual-mode-router, intent-classifier in lumebridge.yaml (semantic-cache optional).
const errorRate = new Rate("errors");
const TARGET_URL = __ENV.TARGET_URL || "http://localhost:8080";

export const options = {
  scenarios: {
    step5: {
      executor: "shared-iterations",
      vus: 10,
      iterations: 100,
      maxDuration: "60s",
    },
  },
  thresholds: {
    errors: ["rate<0.1"],
  },
};

export default function () {
  const commonHeaders = {
    "Content-Type": "application/json",
    "X-API-Key": `sk-sentinel-client-${__VU}`
  };

  const piiPayload = JSON.stringify({
    prompt: `contact user-${__VU}-${__ITER}@example.com or 555-123-4567`,
    nonce: (Date.now() * 1000) + __ITER,
  });

  let res = http.post(`${TARGET_URL}/task`, piiPayload, {
    headers: commonHeaders,
  });
  check(res, { "pii request 200": (r) => r.status === 200 }) || errorRate.add(1);

  const mcpPayload = JSON.stringify({
    prompt: `list tools for user ${__VU}-${__ITER}`,
    jsonrpc: "2.0",
    method: "tools/list",
    id: 1,
    nonce: (Date.now() * 1000) + __ITER + 1000,
  });
  res = http.post(`${TARGET_URL}/task`, mcpPayload, {
    headers: Object.assign({}, commonHeaders, { "MCP-Protocol-Version": "2024-11-05" }),
  });
  check(res, {
    "mcp request 200": (r) => r.status === 200,
  }) || errorRate.add(1);

  const intentPayload = JSON.stringify({ 
    prompt: `do some work for session ${__VU}-${__ITER}`,
    work: true, 
    cache_bypass: true,
    nonce: (Date.now() * 1000) + __ITER + 2000,
  });
  res = http.post(`${TARGET_URL}/task`, intentPayload, {
    headers: commonHeaders,
  });
  check(res, {
    "intent request 200": (r) => r.status === 200
  }) || errorRate.add(1);

  sleep(0.05);
}
