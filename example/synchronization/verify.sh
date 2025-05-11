#!/bin/bash

# Parallel synchronization test using Swagger-compatible endpoints
# Uses /counter/{method}/{counterId}/increment and /counter/{method}/{counterId}

HOSTS=("localhost:8080" "localhost:8081" "localhost:8082")
COUNTER_ID="test-counter-10"
echo "Counter ID = $COUNTER_ID"
NUM_REQUESTS=9000
CONCURRENCY=10
METHODS=("db" "redis" "actor")

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo "=== Parallel Counter Synchronization Verification ==="
echo "Using API shape from Swagger: /counter/{method}/{counterId}/increment"
echo "Assuming all counters start from 0"
echo ""

# Check if ab is installed
if ! command -v ab &> /dev/null; then
    echo -e "${RED}Apache Bench (ab) not found. Please install it first.${NC}"
    exit 1
fi

verify_counter() {
    local method=$1
    local expected=$2
    local host=${HOSTS[0]}  # just pick the first instance

    echo -e "${YELLOW}Verifying result for $method at $host...${NC}"
    local url="http://$host/counter/$method/$COUNTER_ID"
    local value
    value=$(curl -s "$url")

    echo "  Reported value: $value"
    echo "  Expected value: $expected"

    if [ "$value" -eq "$expected" ]; then
        echo -e "${GREEN}✅ TEST PASSED${NC}"
    else
        echo -e "${RED}❌ TEST FAILED${NC}"
    fi
}

# Run load test for each method
test_locking_method() {
    local method=$1
    local per_host_requests=$((NUM_REQUESTS / ${#HOSTS[@]}))

    echo ""
    echo "======================================================"
    echo "🔁 Testing $method with $NUM_REQUESTS total requests"
    echo "======================================================"

    for host in "${HOSTS[@]}"; do
        local endpoint="http://$host/counter/$method/$COUNTER_ID/increment"
        echo -e "${YELLOW}▶ Sending $per_host_requests requests to $host...${NC}"
        ab -n $per_host_requests -c $CONCURRENCY -q "$endpoint" &
    done

    wait

    verify_counter "$method" "$NUM_REQUESTS"
}

# Run tests for all locking methods
for method in "${METHODS[@]}"; do
    test_locking_method "$method"
done

echo "=== ✅ All Tests Complete ==="
