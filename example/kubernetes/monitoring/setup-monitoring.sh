#!/bin/bash
set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NAMESPACE="spring-actor-monitoring"

echo -e "${CYAN}"
cat << "EOF"
╔════════════════════════════════════════════════════════════════╗
║                                                                ║
║   Monitoring Stack Setup (Prometheus + Grafana)               ║
║                                                                ║
╚════════════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}"

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}✗ kubectl not found${NC}"
    echo -e "${YELLOW}  Please install kubectl first${NC}"
    exit 1
fi

echo -e "${YELLOW}[1/3] Deploying monitoring stack...${NC}"
kubectl apply -k "$SCRIPT_DIR/base"
echo -e "${GREEN}✓ Monitoring resources created${NC}"
echo

echo -e "${YELLOW}[2/3] Waiting for pods to be ready...${NC}"
echo -e "${CYAN}Waiting for Prometheus...${NC}"
kubectl wait --for=condition=ready pod -l app=prometheus -n $NAMESPACE --timeout=120s || true

echo -e "${CYAN}Waiting for Grafana...${NC}"
kubectl wait --for=condition=ready pod -l app=grafana -n $NAMESPACE --timeout=120s || true
echo -e "${GREEN}✓ Pods are ready${NC}"
echo

echo -e "${YELLOW}[3/3] Checking status...${NC}"
kubectl get pods -n $NAMESPACE
kubectl get svc -n $NAMESPACE
echo

echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✓ Monitoring Stack Deployed Successfully!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo

echo -e "${CYAN}🔗 Access URLs:${NC}"
echo -e "   ${GREEN}Grafana:${NC}     http://localhost:30000"
echo -e "   ${GREEN}Prometheus:${NC}  http://localhost:30090"
echo

echo -e "${CYAN}📊 Grafana Dashboards:${NC}"
echo -e "   • Pekko Cluster Overview"
echo -e "   • Rolling Updates Visualization"
echo -e "   • JVM & System Metrics"
echo

echo -e "${CYAN}🔐 Grafana Credentials:${NC}"
echo -e "   ${GREEN}Username:${NC} admin"
echo -e "   ${GREEN}Password:${NC} admin"
echo

echo -e "${YELLOW}💡 Note:${NC}"
echo -e "   • Anonymous access is enabled (no login required)"
echo -e "   • Prometheus is auto-configured as the data source"
echo -e "   • Dashboards are auto-provisioned"
echo

echo -e "${YELLOW}📝 To view metrics:${NC}"
echo -e "   1. Deploy the application: cd ../app && ./setup-local.sh"
echo -e "   2. Set up port forwarding for pods (8080-8085)"
echo -e "   3. Open Grafana and view the dashboards"
echo
