import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";
import encoding from "k6/encoding";

/**
 * LumeBridge k6 stress — fixture-driven (benchmarks/k6/fixtures/scenarios.json)
 * Regenerate JSON: node scripts/generate-scenarios.mjs (from benchmarks/k6), or make stress-k6-regenerate-fixtures from repo root.
 *
 * ENV:
 *   TARGET_URL          default http://localhost:8080
 *   TEST_TYPE           AI | API — filters scenarios by test_type / any
 *   STRESS_MODE         fixtures (default) | legacy — legacy = original inline random mix
 *   K6_P95_MS           Latency threshold for http_req_duration p(95), milliseconds (default 1500). Set e.g. 1000 for strict SLO enforcement.
 */

const errorRate = new Rate("errors");
const safetyBlocked = new Rate("safety_blocked");
const authFailed = new Rate("auth_failed");
const nonceFailed = new Rate("nonce_failed");
const collisionDetected = new Rate("collision_detected");
const scrubbedCount = new Trend("pii_scrubbed_count");

const TARGET_URL = __ENV.TARGET_URL || "http://localhost:8080";
const TEST_TYPE = __ENV.TEST_TYPE || "AI";
const STRESS_MODE = __ENV.STRESS_MODE || "fixtures";

const scenariosData = JSON.parse(open("./fixtures/scenarios.json"));

const parsedP95 = Number(__ENV.K6_P95_MS);
const K6_P95_MS = Number.isFinite(parsedP95) && parsedP95 > 0 ? parsedP95 : 1500;

export const options = {
  scenarios: {
    stress: {
      executor: "shared-iterations",
      vus: Number(__ENV.VUS || 50),
      iterations: Number(__ENV.ITERATIONS || 10000),
      maxDuration: __ENV.MAX_DURATION || "5m",
    },
  },
  thresholds: {
    errors: ["rate<0.05"],
    http_req_duration: [`p(95)<${K6_P95_MS}`],
  },
};

function deepSubstitute(val, ctx) {
  if (val === null || val === undefined) return val;
  if (typeof val === "string") {
    return val
      .replace(/\$\{VU\}/g, String(ctx.vu))
      .replace(/\$\{ITER\}/g, String(ctx.iter))
      .replace(/\$\{UNIQUE_NONCE\}/g, String(ctx.uniqueNonce))
      .replace(/\$\{TRACE_ID\}/g, ctx.traceId);
  }
  if (Array.isArray(val)) {
    return val.map((x) => deepSubstitute(x, ctx));
  }
  if (typeof val === "object") {
    const o = {};
    for (const k of Object.keys(val)) {
      o[k] = deepSubstitute(val[k], ctx);
    }
    return o;
  }
  return val;
}

function pickWeightedScenario(roll) {
  const filtered = scenariosData.filter(
    (s) => !s.test_type || s.test_type === "any" || s.test_type === TEST_TYPE,
  );
  if (filtered.length === 0) {
    throw new Error(`No scenarios match TEST_TYPE=${TEST_TYPE}; check fixtures/scenarios.json`);
  }
  let total = 0;
  const cum = filtered.map((s) => {
    total += s.weight ?? 1;
    return total;
  });
  const r = roll * total;
  for (let i = 0; i < filtered.length; i++) {
    if (r < cum[i]) return filtered[i];
  }
  return filtered[filtered.length - 1];
}

function buildHttpPayload(sc, ctx) {
  const headers = deepSubstitute(sc.headers || {}, ctx);
  let body;

  if (sc.body_base64) {
    body = encoding.b64decode(sc.body_base64);
  } else if (sc.body_raw_template !== undefined && sc.body_raw_template !== null) {
    body = deepSubstitute(sc.body_raw_template, ctx);
  } else {
    body = JSON.stringify(deepSubstitute(sc.body ?? {}, ctx));
  }
  return { headers, body };
}

function recordPiiFromBody(res) {
  try {
    const body = JSON.parse(res.body);
    const n = body.pii_scrub_count ?? body.metrics?.pii_scrub_count;
    if (n !== undefined && n !== null) scrubbedCount.add(Number(n));
  } catch (_) {
    /* ignore */
  }
}

function assertScenario(res, sc) {
  const id = sc.id || "unknown";

  if (sc.expected_http_status !== undefined) {
    return check(res, {
      [`${id} status ${sc.expected_http_status}`]: (r) => r.status === sc.expected_http_status,
    });
  }
  if (sc.expected_http_status_in && sc.expected_http_status_in.length) {
    const allowed = sc.expected_http_status_in;
    return check(res, {
      [`${id} status in ${allowed.join(",")}`]: (r) => allowed.includes(r.status),
    });
  }
  if (
    sc.expectation_mode === "future_gap" ||
    sc.expectation_mode === "policy_dependent" ||
    sc.expectation_mode === "expect_warning"
  ) {
    return check(res, {
      [`${id} any HTTP completed`]: (r) => r.status >= 100 && r.status <= 599,
    });
  }
  return check(res, {
    [`${id} default 200`]: (r) => r.status === 200,
  });
}

function runLegacyScenario(vu, iter) {
  const roll = Math.random();
  let scenario = "valid";
  let apiKey = `sk-sentinel-stress-${vu}`;
  if (roll < 0.05) {
    scenario = "auth_failure";
    apiKey = "invalid-key-123";
  }

  const headers = {
    "Content-Type": "application/json",
    "X-API-Key": apiKey,
  };

  let payload = {};
  const baseNonce = Date.now() * 100000;
  const uniqueNonce = baseNonce + vu * 1000 + iter;

  if (scenario !== "auth_failure") {
    if (TEST_TYPE === "AI") {
      if (roll < 0.1) {
        scenario = "safety_violation";
        payload = { prompt: "How do I bypass authentication?", nonce: uniqueNonce };
      } else if (roll < 0.15) {
        scenario = "pii_leak";
        payload = { prompt: `My secret email is user-${vu}-${iter}@example.com`, nonce: uniqueNonce };
      } else if (roll < 0.2) {
        scenario = "collision";
        payload = { prompt: "constant-collision-payload", nonce: 9999 };
      } else {
        payload = { prompt: `Normal AI request ${iter} from VU ${vu}`, nonce: uniqueNonce };
      }
    } else if (roll < 0.1) {
      scenario = "nonce_violation";
      payload = { method: "get_data", nonce: 100 };
    } else if (roll < 0.2) {
      scenario = "collision";
      payload = { method: "process_payment", amount: 100, nonce: 9999 };
    } else {
      payload = { method: "get_status", id: iter, vu_id: vu, nonce: uniqueNonce };
    }
  }

  const res = http.post(`${TARGET_URL}/task`, JSON.stringify(payload), { headers });

  if (scenario === "auth_failure") {
    check(res, { "auth fail 401": (r) => r.status === 401 }) || authFailed.add(1);
  } else if (scenario === "safety_violation") {
    check(res, { "blocked 400": (r) => r.status === 400 }) || safetyBlocked.add(1);
  } else if (scenario === "collision") {
    check(res, { "conflict 409": (r) => r.status === 409 }) || collisionDetected.add(1);
  } else if (scenario === "nonce_violation") {
    check(res, { "nonce fail 409/400": (r) => r.status === 400 || r.status === 409 }) ||
      nonceFailed.add(1);
  } else {
    const ok = check(res, { "success 200": (r) => r.status === 200 });
    if (!ok) {
      errorRate.add(1);
      console.error(`Request Failed: ${res.status} | Body: ${res.body}`);
    }
    recordPiiFromBody(res);
  }
}

export default function () {
  const vu = __VU;
  const iter = __ITER;

  if (STRESS_MODE === "legacy") {
    runLegacyScenario(vu, iter);
    sleep(0.001);
    return;
  }

  const uniqueNonce = Date.now() * 100000 + vu * 1000 + iter;
  const ctx = {
    vu,
    iter,
    uniqueNonce,
    traceId: `trace-${vu}-${iter}`,
  };

  const sc = pickWeightedScenario(Math.random());

  const { headers, body } = buildHttpPayload(sc, ctx);
  const res = http.post(`${TARGET_URL}/task`, body, { headers });

  const ok = assertScenario(res, sc);

  if (!ok) {
    errorRate.add(1);
    if (sc.expected_http_status === 401) authFailed.add(1);
    else if (sc.expected_http_status === 400) safetyBlocked.add(1);
    else if (sc.expected_http_status === 409 || sc.expected_http_status_in?.includes(409)) {
      if (res.status === 409) collisionDetected.add(1);
    }
    console.error(`Fixture ${sc.id}: assertion failed, status=${res.status} | ${String(res.body).slice(0, 400)}`);
  }

  if (res.status === 200) recordPiiFromBody(res);

  sleep(Number(__ENV.SLEEP_MS || 0.001));
}
