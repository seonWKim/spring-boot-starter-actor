#!/bin/bash
set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

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
echo "  • Kubernetes namespace 'spring-actor' and all resources"
echo "  • Kind cluster 'spring-actor-demo'"
echo "  • Docker image 'spring-actor-chat:local'"
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
if kubectl get namespace spring-actor &> /dev/null; then
    kubectl delete namespace spring-actor --timeout=60s || true
    echo -e "${GREEN}✓ Namespace deleted${NC}"
else
    echo -e "${YELLOW}✓ Namespace doesn't exist${NC}"
fi

echo

# Delete kind cluster
echo -e "${YELLOW}[2/3] Deleting kind cluster...${NC}"
if kind get clusters 2>/dev/null | grep -q "spring-actor-demo"; then
    kind delete cluster --name spring-actor-demo
    echo -e "${GREEN}✓ Cluster deleted${NC}"
else
    echo -e "${YELLOW}✓ Cluster doesn't exist${NC}"
fi

echo

# Delete Docker image
echo -e "${YELLOW}[3/3] Deleting Docker image...${NC}"
if docker images | grep -q "spring-actor-chat.*local"; then
    docker rmi spring-actor-chat:local 2>/dev/null || true
    echo -e "${GREEN}✓ Image deleted${NC}"
else
    echo -e "${YELLOW}✓ Image doesn't exist${NC}"
fi

echo
echo -e "${GREEN}✓ Cleanup complete!${NC}"
echo
