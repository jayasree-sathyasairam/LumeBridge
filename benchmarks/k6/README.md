# k6 benchmarks

## Stress test (`stress_test.js`)

Loads **`fixtures/scenarios.json`** (committed). **Node is not required** to run stress tests — only to regenerate that file.

1. Start the gateway (separate terminal): `make run PROFILE=ai-pro` or `PROFILE=api-pro`.
2. Run k6:
   - `make stress-test-k6-ai` / `make stress-test-k6-api`
   - Or from repo root: `k6 run -e TARGET_URL=http://localhost:8080 -e TEST_TYPE=AI benchmarks/k6/stress_test.js`

### Regenerate fixtures (optional, needs Node)

After editing scenario definitions in **`scripts/generate-scenarios.mjs`**, refresh JSON:

- From **`benchmarks/k6`**: `node scripts/generate-scenarios.mjs`
- From repo root: `make stress-k6-regenerate-fixtures`  
  If **`make`** cannot spawn **`node`** on Windows, run the **`node …`** command directly in **PowerShell** or **Git Bash** where **`node`** is on **`PATH`**.

### Env overrides

| Variable | Default | Purpose |
|----------|---------|---------|
| `TARGET_URL` | `http://localhost:8080` | Gateway base URL |
| `TEST_TYPE` | `AI` / `API` | Scenario filter (set by Make targets) |
| `STRESS_MODE` | `fixtures` | `legacy` = old inline random mix |
| `VUS` | `50` | Virtual users |
| `ITERATIONS` | `10000` | Total iterations (shared) |
| `K6_P95_MS` | `1500` | `http_req_duration` p(95) threshold (ms); use `1000` for strict SLO |

See [`STRESS_TEST_DATA_GENERATION_PROMPT.md`](STRESS_TEST_DATA_GENERATION_PROMPT.md) for fixture semantics.
