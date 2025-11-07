#!/bin/bash
set -euo pipefail

# Color output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}Setting up port forwarding to individual pods...${NC}"
echo ""

# Cleanup function to kill background port-forwards
cleanup() {
    echo ""
    echo -e "${YELLOW}Stopping all port forwards...${NC}"
    jobs -p | xargs -r kill 2>/dev/null || true
    exit 0
}

trap cleanup INT TERM

# Get pod names
PODS=($(kubectl get pods -n spring-actor -l app=spring-actor --field-selector=status.phase=Running -o jsonpath='{.items[*].metadata.name}'))

if [ ${#PODS[@]} -eq 0 ]; then
    echo -e "${RED}No running pods found${NC}"
    echo -e "${YELLOW}Please ensure the application is deployed and pods are running${NC}"
    exit 1
fi

echo -e "${GREEN}Found ${#PODS[@]} running pod(s)${NC}"
echo ""

if [ ${#PODS[@]} -ge 1 ]; then
  echo -e "${CYAN}➜${NC} Port forwarding ${PODS[0]} -> localhost:8080"
  kubectl port-forward -n spring-actor ${PODS[0]} 8080:8080 &
fi

if [ ${#PODS[@]} -ge 2 ]; then
  echo -e "${CYAN}➜${NC} Port forwarding ${PODS[1]} -> localhost:8081"
  kubectl port-forward -n spring-actor ${PODS[1]} 8081:8080 &
fi

if [ ${#PODS[@]} -ge 3 ]; then
  echo -e "${CYAN}➜${NC} Port forwarding ${PODS[2]} -> localhost:8082"
  kubectl port-forward -n spring-actor ${PODS[2]} 8082:8080 &
fi

echo ""
echo -e "${GREEN}✓ Port forwarding active${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop all port forwards${NC}"
echo ""

wait
