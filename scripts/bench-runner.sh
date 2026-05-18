#!/usr/bin/env bash
#
# LumeBridge stress test: api-pro then ai-pro (10k k6 iterations each).
# Portable: macOS, Linux, Git Bash, WSL — bash, k6, make, Python (python3 / python / py -3).
# Summary parsing supports k6 v2.x flat --summary-export JSON and older nested-.values format.
#
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

if [[ ! -f "$REPO_ROOT/lumebridge.yaml" ]]; then
  echo "Missing $REPO_ROOT/lumebridge.yaml - copy config/lumebridge.example.yaml" >&2
  exit 1
fi

export CONFIG_FILE="$REPO_ROOT/lumebridge.yaml"

if command -v python3 >/dev/null 2>&1; then
  PYTHON_CMD=(python3)
elif command -v python >/dev/null 2>&1; then
  PYTHON_CMD=(python)
elif command -v py >/dev/null 2>&1; then
  # Windows: python.org installer often adds only the "py" launcher, not "python" on PATH
  PYTHON_CMD=(py -3)
else
  echo "Need python3, python, or py (Windows launcher) on PATH for k6 summary parsing." >&2
  exit 1
fi

command -v k6 >/dev/null 2>&1 || { echo "Need k6 on PATH (https://grafana.com/docs/k6/latest/set-up/install-k6/)." >&2; exit 1; }
command -v make >/dev/null 2>&1 || { echo "Need make on PATH." >&2; exit 1; }

# Git Bash/MSYS: Windows Python (py.exe) needs a Win32 path; Unix paths like /c/Users/... break JSON loading → empty metrics / zeros in the report.
python_k6_json_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1" 2>/dev/null || printf '%s\n' "$1"
  else
    printf '%s\n' "$1"
  fi
}

# Temp dir (Git Bash sets TEMP; Unix often TMPDIR; else /tmp)
TMP_BASE="${TMPDIR:-${TEMP:-/tmp}}"
TMP_BASE="${TMP_BASE%/}"
if ! LB_TMP="$(mktemp -d "${TMP_BASE}/lumebridge-stress.XXXXXX" 2>/dev/null)"; then
  if ! LB_TMP="$(mktemp -d 2>/dev/null)"; then
    LB_TMP="${TMP_BASE}/lumebridge-stress-$$"
    mkdir -p "$LB_TMP"
  fi
fi
GW_LOG="$LB_TMP/gateway.log"
K6_JSON="$LB_TMP/k6_res.json"

PROFILES=(api-pro ai-pro)
REPORT_FILE="$REPO_ROOT/reports/security_audit_report.md"
mkdir -p "$REPO_ROOT/reports"

{
  echo "# LumeBridge: 10K Stress Test and Security Audit"
  echo "Generated on: $(date -Iseconds 2>/dev/null || date)"
  echo ""
  echo "## Performance and Security Metrics"
  echo ""
  echo "| Profile | Success % | P95 Latency | Auth Rejects | Safety Blocks | Collisions | Nonce Errors |"
  echo "|---|---|---|---|---|---|---|"
} >"$REPORT_FILE"

append_metrics_row() {
  local profile="$1"
  local k6_win
  k6_win="$(python_k6_json_path "$K6_JSON")"
  if [[ ! -s "$K6_JSON" ]]; then
    echo "bench-runner: missing or empty k6 summary (expected data at $K6_JSON)" >&2
    exit 1
  fi
  PROFILE="$profile" K6_JSON="$k6_win" "${PYTHON_CMD[@]}" <<'PY' >>"$REPORT_FILE"
import json
import os

# k6 v0.4x summary-export nested metrics under .values; k6 v2.x uses a flat shape per metric.
path = os.environ["K6_JSON"]
with open(path, encoding="utf-8") as f:
    d = json.load(f)


def metric_vals(m):
    if not isinstance(m, dict):
        return {}
    inner = m.get("values")
    return inner if isinstance(inner, dict) else m


def check_pass_rate(metrics: dict) -> float:
    c = metrics.get("checks")
    if not isinstance(c, dict):
        return 0.0
    v = c.get("values")
    if isinstance(v, dict) and v.get("rate") is not None:
        return float(v["rate"])
    if isinstance(v, dict):
        p, fl = v.get("passes"), v.get("fails")
    else:
        p, fl = c.get("passes"), c.get("fails")
    if p is not None and fl is not None:
        tot = int(p) + int(fl)
        return (float(p) / tot) if tot else 0.0
    return float(c.get("rate") or c.get("value") or 0.0)


def bag(metric_name: str, metrics: dict) -> int:
    """Counts from Counter / Rate / Trend aggregates (v1 + v2 JSON)."""
    v = metric_vals(metrics.get(metric_name))
    if not v:
        return 0
    if v.get("count") is not None:
        try:
            return int(v["count"])
        except (TypeError, ValueError):
            pass
    p, fl = v.get("passes"), v.get("fails")
    if p is not None or fl is not None:
        try:
            return int(p or 0) + int(fl or 0)
        except (TypeError, ValueError):
            pass
    return 0


def http_p95_ms(metrics: dict) -> float:
    h = metrics.get("http_req_duration")
    if not isinstance(h, dict):
        return 0.0
    v = h.get("values")
    raw = v.get("p(95)") if isinstance(v, dict) else h.get("p(95)")
    try:
        return float(raw or 0)
    except (TypeError, ValueError):
        return 0.0


metrics = d.get("metrics") or {}
rate = check_pass_rate(metrics)
success = f"{rate * 100:.2f}%"
p95 = f"{http_p95_ms(metrics):.2f}ms"

prof = os.environ["PROFILE"]
print(
    f"| {prof} | {success} | {p95} | {bag('auth_failed', metrics)} | {bag('safety_blocked', metrics)} | "
    f"{bag('collision_detected', metrics)} | {bag('nonce_failed', metrics)} |"
)
PY
  local _st=$?
  if [[ $_st -ne 0 ]]; then
    echo "bench-runner: Python failed (exit $_st) appending metrics for $profile (K6_JSON=$k6_win)" >&2
    exit 1
  fi
}

for PROFILE in "${PROFILES[@]}"; do
  echo "--- Testing Profile: $PROFILE ---"

  PROFILE="$PROFILE" make run >"$GW_LOG" 2>&1 &
  GW_PID=$!

  echo "Waiting for gateway to warm up..."
  sleep 20

  TEST_MODE="AI"
  [[ "$PROFILE" == *api* ]] && TEST_MODE="API"

  echo "Starting 10K Stress Test ($TEST_MODE mode)..."
  k6 run -e TARGET_URL=http://localhost:8080 -e "TEST_TYPE=$TEST_MODE" \
    "$REPO_ROOT/benchmarks/k6/stress_test.js" --summary-export="$K6_JSON"

  # Always persist raw k6 output (parser/debug); survives temp dir cleanup.
  RAW_OUT="$REPO_ROOT/reports/k6_summary_${PROFILE}_latest.json"
  cp "$K6_JSON" "$RAW_OUT"
  echo "bench-runner: raw k6 summary saved → $RAW_OUT"

  append_metrics_row "$PROFILE"

  echo "Stopping gateway..."
  kill "$GW_PID" 2>/dev/null || true
  wait "$GW_PID" 2>/dev/null || true
  sleep 3
done

{
  echo ""
  echo "Raw k6 \`--summary-export\` JSON (verbatim; use if table numbers look wrong):"
  echo "- reports/k6_summary_api-pro_latest.json"
  echo "- reports/k6_summary_ai-pro_latest.json"
  echo ""
  echo "---"
} >>"$REPORT_FILE"

echo "Report written to $REPORT_FILE"
echo "Gateway log (last run): $GW_LOG"
