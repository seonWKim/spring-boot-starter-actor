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

echo -e "${GREEN}=== Rolling Update ===${NC}"
echo "Environment: $ENVIRONMENT"
echo "Image Tag: $IMAGE_TAG"
echo "Namespace: $NAMESPACE"
echo "Deployment: $DEPLOYMENT_NAME"
echo

# Check current deployment
echo -e "${YELLOW}Current deployment:${NC}"
kubectl get deployment $DEPLOYMENT_NAME -n $NAMESPACE

# Show current image
CURRENT_IMAGE=$(kubectl get deployment $DEPLOYMENT_NAME -n $NAMESPACE -o jsonpath='{.spec.template.spec.containers[0].image}')
echo "Current image: $CURRENT_IMAGE"
echo "New image: your-registry/spring-actor-app:$IMAGE_TAG"
echo

# Confirm
read -p "Continue with rolling update? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted"
    exit 0
fi

# Update image
echo -e "${YELLOW}Updating image...${NC}"
kubectl set image deployment/$DEPLOYMENT_NAME spring-actor=your-registry/spring-actor-app:$IMAGE_TAG -n $NAMESPACE

# Watch rollout in real-time
echo -e "${YELLOW}Monitoring rollout...${NC}"
echo

# Start monitoring in background
kubectl rollout status deployment/$DEPLOYMENT_NAME -n $NAMESPACE &
ROLLOUT_PID=$!

# Monitor cluster health during rollout
echo -e "${YELLOW}Cluster health during rollout:${NC}"
for i in {1..30}; do
    sleep 5
    POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=spring-actor --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

    if [ -n "$POD_NAME" ]; then
        CLUSTER_STATUS=$(kubectl exec -n $NAMESPACE $POD_NAME -- curl -s localhost:8558/cluster/members 2>/dev/null | jq -r '.members | length' 2>/dev/null || echo "N/A")
        UNREACHABLE=$(kubectl exec -n $NAMESPACE $POD_NAME -- curl -s localhost:8558/cluster/members 2>/dev/null | jq -r '.unreachable | length' 2>/dev/null || echo "N/A")
        echo "[$(date +%H:%M:%S)] Cluster members: $CLUSTER_STATUS | Unreachable: $UNREACHABLE"
    else
        echo "[$(date +%H:%M:%S)] Waiting for pods..."
    fi

    # Check if rollout is done
    if ! kill -0 $ROLLOUT_PID 2>/dev/null; then
        break
    fi
done

wait $ROLLOUT_PID

echo
echo -e "${GREEN}✓ Rollout complete${NC}"

# Show final status
echo
echo -e "${YELLOW}Final pod status:${NC}"
kubectl get pods -n $NAMESPACE -l app=spring-actor

echo
echo -e "${YELLOW}Final cluster status:${NC}"
POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=spring-actor --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n $NAMESPACE $POD_NAME -- curl -s localhost:8558/cluster/members | jq

echo
echo -e "${GREEN}=== Rolling Update Complete ===${NC}"
