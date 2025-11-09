#!/bin/bash
set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

NAMESPACE="spring-actor-monitoring"

echo -e "${CYAN}"
cat << "EOF"
╔════════════════════════════════════════════════════════════════╗
║                                                                ║
║   Monitoring Stack Cleanup                                     ║
║                                                                ║
╚════════════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}"

echo -e "${YELLOW}This will delete:${NC}"
echo "  • Namespace '$NAMESPACE' and all resources"
echo "  • Prometheus deployment and data"
echo "  • Grafana deployment and dashboards"
echo "  • ClusterRole and ClusterRoleBinding for Prometheus"
echo
echo -e "${RED}⚠️  Warning: This action cannot be undone!${NC}"
echo

read -p "Continue? (y/N) " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted"
    exit 0
fi

echo

# Check if namespace exists
if ! kubectl get namespace $NAMESPACE &> /dev/null; then
    echo -e "${YELLOW}✓ Namespace '$NAMESPACE' doesn't exist - nothing to clean up${NC}"
    exit 0
fi

# Delete namespace (this will delete all resources in it)
echo -e "${YELLOW}[1/2] Deleting namespace and all resources...${NC}"
kubectl delete namespace $NAMESPACE --timeout=60s || true
echo -e "${GREEN}✓ Namespace deleted${NC}"

echo

# Delete cluster-scoped resources
echo -e "${YELLOW}[2/2] Deleting cluster-scoped resources...${NC}"

if kubectl get clusterrole prometheus &> /dev/null; then
    kubectl delete clusterrole prometheus
    echo -e "${GREEN}✓ ClusterRole deleted${NC}"
else
    echo -e "${YELLOW}✓ ClusterRole doesn't exist${NC}"
fi

if kubectl get clusterrolebinding prometheus &> /dev/null; then
    kubectl delete clusterrolebinding prometheus
    echo -e "${GREEN}✓ ClusterRoleBinding deleted${NC}"
else
    echo -e "${YELLOW}✓ ClusterRoleBinding doesn't exist${NC}"
fi

echo
echo -e "${GREEN}✓ Cleanup complete!${NC}"
echo
echo -e "${CYAN}To redeploy monitoring:${NC}"
echo -e "   ./setup-monitoring.sh"
echo
