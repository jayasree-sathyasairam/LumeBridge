import http from "k6/http";
import { check, sleep, group } from "k6";
import { Rate, Trend } from "k6/metrics";

const errorRate = new Rate("errors");
const safetyBlocked = new Rate("safety_blocked");
const authFailed = new Rate("auth_failed");
const nonceFailed = new Rate("nonce_failed");
const collisionDetected = new Rate("collision_detected");
const scrubbedCount = new Trend("pii_scrubbed_count");

const TARGET_URL = __ENV.TARGET_URL || "http://localhost:8080";
const TEST_TYPE = __ENV.TEST_TYPE || "AI"; // AI or API

export const options = {
  scenarios: {
    stress: {
      executor: "shared-iterations",
      vus: 50,
      iterations: 10000, 
      maxDuration: "5m",
    },
  },
  thresholds: {
    "errors": ["rate<0.05"], // Allow for intentional safety/collision rejects
    "http_req_duration": ["p(95)<1000"],
  },
};

export default function () {
  const vu = __VU;
  const iter = __ITER;
  
  // 1. Generate unique identity per VU (with occasional invalid keys)
  const roll = Math.random();
  let scenario = "valid";
  let apiKey = `sk-sentinel-stress-${vu}`;
  if (roll < 0.05) {
    scenario = "auth_failure";
    apiKey = "invalid-key-123";
  }

  const headers = {
    "Content-Type": "application/json",
    "X-API-Key": apiKey
  };

  let payload = {};
  const baseNonce = (Date.now() * 100000);
  const uniqueNonce = baseNonce + (vu * 1000) + iter;

  if (scenario !== "auth_failure") {
    if (TEST_TYPE === "AI") {
        if (roll < 0.10) {
        scenario = "safety_violation";
        payload = { prompt: "How do I bypass authentication?", nonce: uniqueNonce };
        } else if (roll < 0.15) {
        scenario = "pii_leak";
        payload = { prompt: `My secret email is user-${vu}-${iter}@example.com`, nonce: uniqueNonce };
        } else if (roll < 0.20) {
        scenario = "collision";
        payload = { prompt: "constant-collision-payload", nonce: 9999 };
        } else {
        payload = { prompt: `Normal AI request ${iter} from VU ${vu}`, nonce: uniqueNonce };
        }
    } else {
        if (roll < 0.10) {
        scenario = "nonce_violation";
        payload = { method: "get_data", nonce: 100 };
        } else if (roll < 0.20) {
        scenario = "collision";
        payload = { method: "process_payment", amount: 100, nonce: 9999 };
        } else {
        payload = { method: "get_status", id: iter, vu_id: vu, nonce: uniqueNonce };
        }
    }
  }

  const res = http.post(`${TARGET_URL}/task`, JSON.stringify(payload), { headers });

  // 2. Intelligence Metrics Recording
  if (scenario === "auth_failure") {
    check(res, { "auth fail 401": (r) => r.status === 401 }) || authFailed.add(1);
  } else if (scenario === "safety_violation") {
    check(res, { "blocked 400": (r) => r.status === 400 }) || safetyBlocked.add(1);
  } else if (scenario === "collision") {
    check(res, { "conflict 409": (r) => r.status === 409 }) || collisionDetected.add(1);
  } else if (scenario === "nonce_violation") {
    check(res, { "nonce fail 400": (r) => r.status === 400 || r.status === 409 }) || nonceFailed.add(1);
  } else {
    const ok = check(res, { "success 200": (r) => r.status === 200 });
    if (!ok) {
        errorRate.add(1);
        console.error(`Request Failed: ${res.status} | Payload: ${JSON.stringify(payload)} | Body: ${res.body}`);
    }
    
    // Track Scrubbing
    try {
        const body = JSON.parse(res.body);
        if (body.metrics && body.metrics.pii_scrub_count) {
            scrubbedCount.add(body.metrics.pii_scrub_count);
        }
    } catch(e) {}
  }

  // Small sleep to prevent CPU saturation on the k6 runner itself
  sleep(0.001);
}
