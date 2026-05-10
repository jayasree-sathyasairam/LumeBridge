import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate } from "k6/metrics";

const errorRate = new Rate("errors");
const lockConflicts = new Counter("lock_conflicts");
const lockSuccesses = new Counter("lock_successes");

const TARGET_URL = __ENV.TARGET_URL || "http://localhost:8080";

export const options = {
  scenarios: {
    contention: {
      executor: "shared-iterations",
      vus: 30,
      iterations: 300,
      maxDuration: "120s",
    },
  },
  thresholds: {
    errors: ["rate<0.05"],
  },
};

export default function () {
  const taskId = Math.floor(Math.random() * 10);
  const payload = JSON.stringify({
    task_id: taskId,
    data: `benchmark-payload-${taskId}`,
    ts: Date.now(),
  });

  const res = http.post(`${TARGET_URL}/task`, payload, {
    headers: { 
      "Content-Type": "application/json",
      "X-API-Key": "sk-sentinel-user123"
    },
  });

  const ok = check(res, {
    "status is 200 or 409": (r) => r.status === 200 || r.status === 409,
  });

  errorRate.add(!ok);

  if (res.status === 200) {
    lockSuccesses.add(1);
  } else if (res.status === 409) {
    lockConflicts.add(1);
  }

  sleep(0.05);
}
