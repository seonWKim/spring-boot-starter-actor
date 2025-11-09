#!/bin/bash
set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

CLUSTER_NAME="${CLUSTER_NAME:-spring-actor-demo}"
IMAGE_NAME="${IMAGE_NAME:-spring-actor-chat:local}"

# Build application
echo -e "${YELLOW}Building Spring Boot application...${NC}"
cd "$(dirname "$0")/../../../chat"

if [ -f "../../gradlew" ]; then
    ../../gradlew clean build -x test
    echo -e "${GREEN}✓ Application built${NC}"
else
    echo -e "${RED}✗ gradlew not found${NC}"
    exit 1
fi

# Build Docker image
echo -e "${YELLOW}Building Docker image...${NC}"

if ls build/libs/*.jar 1> /dev/null 2>&1; then
    docker build -f Dockerfile.kubernetes -t $IMAGE_NAME .
    echo -e "${GREEN}✓ Docker image built${NC}"
else
    echo -e "${RED}✗ JAR file not found${NC}"
    exit 1
fi

# Load image into kind cluster if it exists
if kind get clusters 2>/dev/null | grep -q "$CLUSTER_NAME"; then
    echo -e "${YELLOW}Loading image into kind cluster...${NC}"
    kind load docker-image $IMAGE_NAME --name $CLUSTER_NAME
    echo -e "${GREEN}✓ Image loaded into kind cluster${NC}"
else
    echo -e "${YELLOW}⚠️  Kind cluster '$CLUSTER_NAME' not found, skipping image load${NC}"
    echo -e "${YELLOW}   Run setup-local.sh to create the cluster first${NC}"
fi
