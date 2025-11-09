#!/bin/bash
set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Checking prerequisites...${NC}"

check_command() {
    if ! command -v $1 &> /dev/null; then
        echo -e "${RED}✗ $1 is not installed${NC}"
        echo -e "${YELLOW}  Please install $1: $2${NC}"
        return 1
    else
        echo -e "${GREEN}✓ $1 is installed${NC}"
        return 0
    fi
}

MISSING_DEPS=0

check_command "docker" "https://docs.docker.com/get-docker/" || MISSING_DEPS=1
check_command "kind" "https://kind.sigs.k8s.io/docs/user/quick-start/#installation" || MISSING_DEPS=1
check_command "kubectl" "https://kubernetes.io/docs/tasks/tools/" || MISSING_DEPS=1

if [ $MISSING_DEPS -eq 1 ]; then
    echo
    echo -e "${RED}Missing required dependencies. Please install them and try again.${NC}"
    exit 1
fi

# Check Docker memory allocation
DOCKER_MEM=$(docker info 2>/dev/null | grep "Total Memory" | awk '{print $3}' || echo "0")
DOCKER_MEM_NUM=$(echo $DOCKER_MEM | sed 's/GiB//')
if (( $(echo "$DOCKER_MEM_NUM < 7" | bc -l 2>/dev/null || echo 0) )); then
    echo
    echo -e "${RED}⚠️  Warning: Docker has only ${DOCKER_MEM} of memory allocated${NC}"
    echo -e "${YELLOW}   A 3-node cluster requires at least 8 GB${NC}"
    echo -e "${YELLOW}   Increase memory: Docker Desktop → Settings → Resources → Memory${NC}"
    echo
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo -e "${GREEN}✓ All prerequisites met${NC}"
