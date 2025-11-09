#!/bin/bash
set -euo pipefail

# Color output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}Setting up port forwarding...${NC}"
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

# Forward application pods
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

# Forward monitoring services if they exist
if kubectl get namespace monitoring &> /dev/null; then
    echo ""
    echo -e "${CYAN}Forwarding monitoring services...${NC}"

    if kubectl get svc grafana -n monitoring &> /dev/null; then
        echo -e "${CYAN}➜${NC} Port forwarding Grafana -> localhost:30300"
        kubectl port-forward -n monitoring svc/grafana 30300:3000 &
    fi

    if kubectl get svc prometheus -n monitoring &> /dev/null; then
        echo -e "${CYAN}➜${NC} Port forwarding Prometheus -> localhost:30090"
        kubectl port-forward -n monitoring svc/prometheus 30090:9090 &
    fi
fi

echo ""
echo -e "${GREEN}✓ Port forwarding active${NC}"
echo ""
echo -e "${CYAN}Application:${NC}"
[ ${#PODS[@]} -ge 1 ] && echo -e "   ${GREEN}Pod 1:${NC} http://localhost:8080"
[ ${#PODS[@]} -ge 2 ] && echo -e "   ${GREEN}Pod 2:${NC} http://localhost:8081"
[ ${#PODS[@]} -ge 3 ] && echo -e "   ${GREEN}Pod 3:${NC} http://localhost:8082"

if kubectl get namespace monitoring &> /dev/null; then
    echo ""
    echo -e "${CYAN}Monitoring:${NC}"
    kubectl get svc grafana -n monitoring &> /dev/null && echo -e "   ${GREEN}Grafana:${NC}    http://localhost:30300 (admin/admin)"
    kubectl get svc prometheus -n monitoring &> /dev/null && echo -e "   ${GREEN}Prometheus:${NC} http://localhost:30090"
fi

echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all port forwards${NC}"
echo ""

wait
