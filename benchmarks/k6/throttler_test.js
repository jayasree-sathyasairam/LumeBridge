import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const errorRate = new Rate("errors");
const waitTime = new Trend("wait_ms");
const workTime = new Trend("work_ms");
const totalTime = new Trend("total_ms");

const TARGET_URL = __ENV.TARGET_URL || "http://localhost:8080";

export const options = {
  scenarios: {
    burst: {
      executor: "shared-iterations",
      vus: 50,
      iterations: 500,
      maxDuration: "120s",
    },
  },
  thresholds: {
    errors: ["rate<0.01"],
    http_req_duration: ["p(95)<5000"],
  },
};

export default function () {
  const res = http.post(`${TARGET_URL}/gate`, JSON.stringify({ 
    prompt: `bench-prompt-${__VU}-${__ITER}`, 
    nonce: (Date.now() * 1000) + __ITER, 
    ts: Date.now() 
  }), {
    headers: { 
      "Content-Type": "application/json",
      "X-API-Key": "sk-sentinel-user123"
    },
  });

  const ok = check(res, {
    "status is 200": (r) => r.status === 200,
    "has status field": (r) => {
      try { return JSON.parse(r.body).status === "completed"; }
      catch { return false; }
    },
  });

  errorRate.add(!ok);

  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      waitTime.add(body.wait_ms || 0);
      workTime.add(body.work_ms || 0);
      totalTime.add(body.total_ms || 0);
    } catch (_) {}
  }

  sleep(0.01);
}
