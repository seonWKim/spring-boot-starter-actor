#!/bin/bash
set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENT="${1:-dev}"
IMAGE_TAG="${2:-latest}"
NAMESPACE="spring-actor"
DEPLOYMENT_NAME="spring-actor"

if [ "$ENVIRONMENT" = "prod" ]; then
    NAMESPACE="spring-actor"
    DEPLOYMENT_NAME="spring-actor-prod"
elif [ "$ENVIRONMENT" = "dev" ]; then
    NAMESPACE="spring-actor-dev"
    DEPLOYMENT_NAME="spring-actor-dev"
fi

echo -e "${GREEN}=== Spring Actor Kubernetes Deployment ===${NC}"
echo "Environment: $ENVIRONMENT"
echo "Image Tag: $IMAGE_TAG"
echo "Namespace: $NAMESPACE"
echo

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}Error: kubectl is not installed${NC}"
    exit 1
fi

# Check if kustomize is installed
if ! command -v kustomize &> /dev/null; then
    echo -e "${YELLOW}Warning: kustomize not found, using kubectl apply -k${NC}"
fi

# Check cluster connection
echo -e "${YELLOW}Checking cluster connection...${NC}"
if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}Error: Cannot connect to Kubernetes cluster${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Connected to cluster${NC}"

# Create namespace if it doesn't exist
echo -e "${YELLOW}Ensuring namespace exists...${NC}"
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
echo -e "${GREEN}✓ Namespace ready${NC}"

# Apply the manifests
echo -e "${YELLOW}Applying Kubernetes manifests...${NC}"
if command -v kustomize &> /dev/null; then
    cd "$(dirname "$0")/../overlays/$ENVIRONMENT"
    kustomize edit set image your-registry/spring-actor-app:$IMAGE_TAG
    kustomize build . | kubectl apply -f -
else
    kubectl apply -k "$(dirname "$0")/../overlays/$ENVIRONMENT"
fi

echo -e "${GREEN}✓ Manifests applied${NC}"

# Wait for rollout
echo -e "${YELLOW}Waiting for rollout to complete...${NC}"
kubectl rollout status deployment/$DEPLOYMENT_NAME -n $NAMESPACE --timeout=5m

echo -e "${GREEN}✓ Deployment complete${NC}"

# Show pod status
echo
echo -e "${YELLOW}Pod status:${NC}"
kubectl get pods -n $NAMESPACE -l app=spring-actor

# Show service endpoints
echo
echo -e "${YELLOW}Service endpoints:${NC}"
kubectl get svc -n $NAMESPACE

# Check cluster health
echo
echo -e "${YELLOW}Checking cluster health...${NC}"
POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=spring-actor -o jsonpath='{.items[0].metadata.name}')

if [ -n "$POD_NAME" ]; then
    echo "Querying pod: $POD_NAME"

    # Wait for pod to be ready
    kubectl wait --for=condition=ready pod/$POD_NAME -n $NAMESPACE --timeout=60s

    # Check cluster members
    echo -e "${YELLOW}Cluster members:${NC}"
    kubectl exec -n $NAMESPACE $POD_NAME -- curl -s localhost:8558/cluster/members | jq -r '.members[] | "\(.node) - \(.status)"' 2>/dev/null || echo "Unable to fetch cluster status (may need to wait for startup)"
else
    echo -e "${YELLOW}No pods found yet${NC}"
fi

echo
echo -e "${GREEN}=== Deployment Complete ===${NC}"
echo
echo "Useful commands:"
echo "  kubectl get pods -n $NAMESPACE"
echo "  kubectl logs -f -n $NAMESPACE -l app=spring-actor"
echo "  kubectl exec -n $NAMESPACE <pod-name> -- curl localhost:8558/cluster/members"
echo "  kubectl port-forward -n $NAMESPACE svc/spring-actor-http 8080:80"
