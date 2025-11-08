#!/bin/bash
set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

CLUSTER_NAME="spring-actor-demo"
IMAGE_NAME="spring-actor-chat:local"
NAMESPACE="spring-actor"

echo -e "${CYAN}"
cat << "EOF"
╔════════════════════════════════════════════════════════════════╗
║                                                                ║
║   Spring Boot Starter Actor - Cleanup                          ║
║                                                                ║
╚════════════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}"

echo -e "${YELLOW}This will delete:${NC}"
echo "  • Kubernetes namespace '$NAMESPACE' and all resources"
echo "  • Kind cluster '$CLUSTER_NAME'"
echo "  • Docker image '$IMAGE_NAME'"
echo

read -p "Continue? (y/N) " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted"
    exit 0
fi

echo

# Delete namespace
echo -e "${YELLOW}[1/3] Deleting Kubernetes resources...${NC}"
if kubectl get namespace $NAMESPACE &> /dev/null; then
    kubectl delete namespace $NAMESPACE --timeout=60s || true
    echo -e "${GREEN}✓ Namespace deleted${NC}"
else
    echo -e "${YELLOW}✓ Namespace doesn't exist${NC}"
fi

echo

# Delete kind cluster
echo -e "${YELLOW}[2/3] Deleting kind cluster...${NC}"
if kind get clusters 2>/dev/null | grep -q "$CLUSTER_NAME"; then
    kind delete cluster --name $CLUSTER_NAME
    echo -e "${GREEN}✓ Cluster deleted${NC}"
else
    echo -e "${YELLOW}✓ Cluster doesn't exist${NC}"
fi

echo

# Delete Docker image
echo -e "${YELLOW}[3/3] Deleting Docker image...${NC}"
if docker images | grep -q "spring-actor-chat.*local"; then
    docker rmi $IMAGE_NAME 2>/dev/null || true
    echo -e "${GREEN}✓ Image deleted${NC}"
else
    echo -e "${YELLOW}✓ Image doesn't exist${NC}"
fi

echo
echo -e "${GREEN}✓ Cleanup complete!${NC}"
echo
