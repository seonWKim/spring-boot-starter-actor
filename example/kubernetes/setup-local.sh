#!/bin/bash
set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}"
cat << "EOF"
╔════════════════════════════════════════════════════════════════╗
║                                                                ║
║   Spring Boot Starter Actor - Kubernetes Example Setup         ║
║                                                                ║
╚════════════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}"

echo -e "${YELLOW}⚠️  Prerequisites:${NC}"
echo -e "   Docker Desktop must have ${GREEN}at least 8 GB${NC} of memory allocated"
echo -e "   (Settings → Resources → Memory → 8 GB → Apply & Restart)"
echo

# Check prerequisites
echo -e "${YELLOW}[1/6] Checking prerequisites...${NC}"

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

echo

# Create kind cluster
echo -e "${YELLOW}[2/6] Creating local Kubernetes cluster (this may take a few minutes)...${NC}"

if kind get clusters 2>/dev/null | grep -q "spring-actor-demo"; then
    echo -e "${GREEN}✓ Cluster 'spring-actor-demo' already exists${NC}"
else
    cat <<EOF | kind create cluster --name spring-actor-demo --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  image: kindest/node:v1.27.3
  extraPortMappings:
  # Main load-balanced service
  - containerPort: 30080
    hostPort: 8080
    protocol: TCP
  # Individual pod access
  - containerPort: 30081
    hostPort: 8081
    protocol: TCP
  - containerPort: 30082
    hostPort: 8082
    protocol: TCP
# Add worker nodes for multi-node cluster testing
- role: worker
  image: kindest/node:v1.27.3
- role: worker
  image: kindest/node:v1.27.3
EOF
    echo -e "${GREEN}✓ Cluster created${NC}"

    echo -e "${YELLOW}Waiting for all nodes to be ready...${NC}"
    kubectl wait --for=condition=ready node --all --timeout=180s
    echo -e "${GREEN}✓ All nodes ready${NC}"
fi

echo

# Build application
echo -e "${YELLOW}[3/6] Building Spring Boot application...${NC}"
cd "$(dirname "$0")/../chat"

if [ -f "../../gradlew" ]; then
    ../../gradlew clean build -x test
    echo -e "${GREEN}✓ Application built${NC}"
else
    echo -e "${RED}✗ gradlew not found${NC}"
    exit 1
fi

echo

# Build Docker image
echo -e "${YELLOW}[4/6] Building Docker image...${NC}"

if ls build/libs/*.jar 1> /dev/null 2>&1; then
    docker build -f Dockerfile.kubernetes -t spring-actor-chat:local .

    # Load image into kind
    kind load docker-image spring-actor-chat:local --name spring-actor-demo

    echo -e "${GREEN}✓ Docker image built and loaded into kind${NC}"
else
    echo -e "${RED}✗ JAR file not found${NC}"
    exit 1
fi

echo

# Deploy to Kubernetes
echo -e "${YELLOW}[5/6] Deploying to Kubernetes...${NC}"
cd ../kubernetes

# Apply manifests
kubectl apply -k overlays/local

echo -e "${GREEN}✓ Deployed to Kubernetes${NC}"

echo

# Wait for deployment
echo -e "${YELLOW}[6/6] Waiting for pods to be ready (this may take 1-2 minutes)...${NC}"

kubectl wait --for=condition=ready pod -l app=spring-actor -n spring-actor --timeout=180s || true

echo

# Show status
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✓ Setup Complete!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo

echo -e "${CYAN}📊 Cluster Status:${NC}"
kubectl get pods -n spring-actor -o wide

echo
echo -e "${CYAN}🌐 Service Endpoints:${NC}"
kubectl get svc -n spring-actor

echo
echo -e "${CYAN}🔗 Access the Application:${NC}"
echo -e "   ${GREEN}Chat UI:${NC} http://localhost:8080"
echo

# Get first running pod
POD_NAME=$(kubectl get pods -n spring-actor -l app=spring-actor --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

if [ -n "$POD_NAME" ]; then
    echo -e "${CYAN}🎯 Cluster Status:${NC}"

    # Wait a bit for management endpoint to be ready
    sleep 5

    echo -e "${YELLOW}Checking cluster members...${NC}"
    kubectl exec -n spring-actor $POD_NAME -- curl -s localhost:7626/cluster/members 2>/dev/null | \
        jq -r '.members[] | "\(.node) - \(.status)"' 2>/dev/null || \
        echo "Cluster still forming... (this is normal, check again in 30 seconds)"
fi

echo
echo -e "${CYAN}📝 Useful Commands:${NC}"
echo -e "   ${GREEN}View pods:${NC}             kubectl get pods -n spring-actor"
echo -e "   ${GREEN}View logs:${NC}             kubectl logs -f -n spring-actor -l app=spring-actor"
echo -e "   ${GREEN}Check cluster:${NC}         kubectl exec -n spring-actor <pod-name> -- curl localhost:7626/cluster/members | jq"
echo -e "   ${GREEN}Access all pods:${NC}       ./port-forward-pods.sh (ports 8080, 8081, 8082)"
echo -e "   ${GREEN}Cleanup:${NC}               ./cleanup-local.sh"

echo
echo -e "${YELLOW}💡 Tip: Wait 30-60 seconds for the cluster to fully form before testing!${NC}"
echo -e "${YELLOW}💡 To access individual pods, run: ./port-forward-pods.sh${NC}"
echo
