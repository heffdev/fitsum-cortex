#!/bin/bash

# Fitsum Cortex Evaluation Script
# Runs the evaluation framework and reports quality metrics

set -e

echo "🧪 Starting Fitsum Cortex Evaluation..."

# Check if API is running
if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "❌ API server is not running. Start it first with: cd api && mvn spring-boot:run"
    exit 1
fi

echo "✅ API server is running"

# Load test cases if needed
echo "📋 Loading evaluation test cases..."
# TODO: Implement test case loading from fixtures

# Run evaluation via API endpoint
echo "🔬 Running evaluation..."
RESULT=$(curl -s -X POST http://localhost:8080/v1/eval/run \
    -H "Content-Type: application/json" \
    -u demo@fitsum.ai:demo \
    -d '{"description": "Automated evaluation run"}')

# Parse results (requires jq)
if command -v jq > /dev/null 2>&1; then
    P_AT_5=$(echo "$RESULT" | jq -r '.precisionAt5')
    MRR=$(echo "$RESULT" | jq -r '.mrr')
    
    echo ""
    echo "📊 Evaluation Results:"
    echo "  Precision@5: $P_AT_5"
    echo "  MRR:         $MRR"
    echo ""
    
    # Check thresholds
    THRESHOLD_P5=0.70
    THRESHOLD_MRR=0.75
    
    if (( $(echo "$P_AT_5 < $THRESHOLD_P5" | bc -l) )); then
        echo "❌ FAIL: Precision@5 ($P_AT_5) below threshold ($THRESHOLD_P5)"
        exit 1
    fi
    
    if (( $(echo "$MRR < $THRESHOLD_MRR" | bc -l) )); then
        echo "❌ FAIL: MRR ($MRR) below threshold ($THRESHOLD_MRR)"
        exit 1
    fi
    
    echo "✅ PASS: All metrics above thresholds"
else
    echo "⚠️  jq not found, displaying raw result:"
    echo "$RESULT"
fi

echo ""
echo "📈 View detailed results in Jaeger: http://localhost:16686"

