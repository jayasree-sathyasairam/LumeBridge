#!/bin/bash

# LumeBridge: All-Profile Stress Test Runner (Robust Version)
# This version handles missing keys in k6 JSON output gracefully.

PROFILES=("api-pro" "ai-pro")
REPORT_FILE="reports/security_audit_report.md"

echo "# LumeBridge: 10K Stress Test & Security Audit" > $REPORT_FILE
echo "Generated on: $(date)" >> $REPORT_FILE
echo "" >> $REPORT_FILE
echo "## 📊 Performance & Security Metrics" >> $REPORT_FILE
echo "" >> $REPORT_FILE
echo "| Profile | Success % | P95 Latency | Auth Rejects | Safety Blocks | Collisions | Nonce Errors |" >> $REPORT_FILE
echo "|---|---|---|---|---|---|---| " >> $REPORT_FILE

for PROFILE in "${PROFILES[@]}"
do
    echo "--- Testing Profile: $PROFILE ---"
    
    # 1. Start Gateway in background
    PROFILE=$PROFILE make run > /tmp/gateway.log 2>&1 &
    GW_PID=$!
    
    # Wait for startup
    echo "Waiting for gateway to warm up..."
    sleep 20
    
    # 2. Run Stress Test
    TEST_MODE="AI"
    if [[ $PROFILE == *"api"* ]]; then TEST_MODE="API"; fi
    
    echo "Starting 10K Stress Test ($TEST_MODE mode)..."
    k6 run -e TARGET_URL=http://localhost:8080 -e TEST_TYPE=$TEST_MODE benchmarks/k6/stress_test.js --summary-export=/tmp/k6_res.json
    
    # 3. Extract Metrics (Using Robust Python parsing)
    SUCCESS=$(python3 -c "import json; d=json.load(open('/tmp/k6_res.json')); r=d.get('metrics', {}).get('checks', {}).get('values', {}).get('rate', 0); print(f'{r*100:.2f}%')")
    P95=$(python3 -c "import json; d=json.load(open('/tmp/k6_res.json')); r=d.get('metrics', {}).get('http_req_duration', {}).get('values', {}).get('p(95)', 0); print(f'{r:.2f}ms')")
    AUTH=$(python3 -c "import json; d=json.load(open('/tmp/k6_res.json')); print(d['metrics'].get('auth_failed', {}).get('values', {}).get('count', 0))")
    SAFETY=$(python3 -c "import json; d=json.load(open('/tmp/k6_res.json')); print(d['metrics'].get('safety_blocked', {}).get('values', {}).get('count', 0))")
    COLLISION=$(python3 -c "import json; d=json.load(open('/tmp/k6_res.json')); print(d['metrics'].get('collision_detected', {}).get('values', {}).get('count', 0))")
    NONCE=$(python3 -c "import json; d=json.load(open('/tmp/k6_res.json')); print(d['metrics'].get('nonce_failed', {}).get('values', {}).get('count', 0))")
    
    echo "| $PROFILE | $SUCCESS | $P95 | $AUTH | $SAFETY | $COLLISION | $NONCE |" >> $REPORT_FILE
    
    # 4. Stop Gateway
    echo "Stopping gateway..."
    kill $GW_PID
    sleep 3
done

echo "" >> $REPORT_FILE
echo "---" >> $REPORT_FILE
echo "" >> $REPORT_FILE
