#!/bin/bash
set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE="${1:-spring-actor}"

echo -e "${GREEN}=== Debugging Spring Actor Cluster ===${NC}"
echo "Namespace: $NAMESPACE"
echo

# Get all pods
echo -e "${BLUE}=== Pods ===${NC}"
kubectl get pods -n $NAMESPACE -l app=spring-actor -o wide

# Get pod details
echo
echo -e "${BLUE}=== Pod Details ===${NC}"
PODS=$(kubectl get pods -n $NAMESPACE -l app=spring-actor -o jsonpath='{.items[*].metadata.name}')

for POD in $PODS; do
    echo
    echo -e "${YELLOW}Pod: $POD${NC}"

    # Check if pod is ready
    READY=$(kubectl get pod $POD -n $NAMESPACE -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}')
    echo "Ready: $READY"

    # Get pod IP
    POD_IP=$(kubectl get pod $POD -n $NAMESPACE -o jsonpath='{.status.podIP}')
    echo "IP: $POD_IP"

    # Get node
    NODE=$(kubectl get pod $POD -n $NAMESPACE -o jsonpath='{.spec.nodeName}')
    echo "Node: $NODE"

    # Check readiness probe
    echo -e "${YELLOW}Readiness probe:${NC}"
    kubectl exec -n $NAMESPACE $POD -- curl -s http://localhost:8080/actuator/health/readiness | jq 2>/dev/null || echo "Not ready"

    # Check cluster membership
    echo -e "${YELLOW}Cluster membership:${NC}"
    kubectl exec -n $NAMESPACE $POD -- curl -s http://localhost:8558/cluster/members 2>/dev/null | jq -r '.members[] | "\(.node) - \(.status)"' 2>/dev/null || echo "Unable to connect"
done

# Check services
echo
echo -e "${BLUE}=== Services ===${NC}"
kubectl get svc -n $NAMESPACE

# Check endpoints
echo
echo -e "${BLUE}=== Service Endpoints ===${NC}"
kubectl get endpoints -n $NAMESPACE

# Check RBAC
echo
echo -e "${BLUE}=== RBAC ===${NC}"
SA_NAME="spring-actor-sa"
echo "ServiceAccount: $SA_NAME"
kubectl get serviceaccount $SA_NAME -n $NAMESPACE 2>/dev/null || echo "Not found"

echo
echo "Role: spring-actor-pod-reader"
kubectl get role spring-actor-pod-reader -n $NAMESPACE 2>/dev/null || echo "Not found"

echo
echo "RoleBinding: spring-actor-pod-reader-binding"
kubectl get rolebinding spring-actor-pod-reader-binding -n $NAMESPACE 2>/dev/null || echo "Not found"

# Test RBAC permissions
echo
echo -e "${YELLOW}Testing pod list permission:${NC}"
kubectl auth can-i list pods --as=system:serviceaccount:$NAMESPACE:$SA_NAME -n $NAMESPACE

# Check recent events
echo
echo -e "${BLUE}=== Recent Events ===${NC}"
kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp' | tail -20

# Check logs
echo
echo -e "${BLUE}=== Recent Logs (Last 50 lines) ===${NC}"
POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=spring-actor -o jsonpath='{.items[0].metadata.name}')
if [ -n "$POD_NAME" ]; then
    kubectl logs -n $NAMESPACE $POD_NAME --tail=50
else
    echo "No pods found"
fi

# Network connectivity check
echo
echo -e "${BLUE}=== Network Connectivity ===${NC}"
PODS=$(kubectl get pods -n $NAMESPACE -l app=spring-actor -o jsonpath='{.items[*].metadata.name}')

for POD in $PODS; do
    POD_IP=$(kubectl get pod $POD -n $NAMESPACE -o jsonpath='{.status.podIP}')
    echo -e "${YELLOW}Testing from $POD ($POD_IP):${NC}"

    # Test management port
    echo "Management port 8558:"
    kubectl exec -n $NAMESPACE $POD -- curl -s -m 2 http://localhost:8558/ready 2>/dev/null && echo "OK" || echo "FAILED"

    # Test remoting port
    echo "Remoting port 2551:"
    kubectl exec -n $NAMESPACE $POD -- nc -zv localhost 2551 2>&1 | grep -q "succeeded" && echo "OK" || echo "FAILED"

    echo
done

# Cluster formation check
echo -e "${BLUE}=== Cluster Formation ===${NC}"
POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=spring-actor --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)

if [ -n "$POD_NAME" ]; then
    echo "Checking cluster from pod: $POD_NAME"
    echo

    echo -e "${YELLOW}Cluster Members:${NC}"
    kubectl exec -n $NAMESPACE $POD_NAME -- curl -s http://localhost:8558/cluster/members 2>/dev/null | jq || echo "Unable to fetch"

    echo
    echo -e "${YELLOW}Cluster Sharding:${NC}"
    kubectl exec -n $NAMESPACE $POD_NAME -- curl -s http://localhost:8558/cluster/shards 2>/dev/null | jq || echo "No sharding info available"
fi

echo
echo -e "${GREEN}=== Debug Complete ===${NC}"
echo
echo "Common troubleshooting commands:"
echo "  kubectl describe pod <pod-name> -n $NAMESPACE"
echo "  kubectl logs -f <pod-name> -n $NAMESPACE"
echo "  kubectl exec -n $NAMESPACE <pod-name> -- curl localhost:8558/cluster/members"
echo "  kubectl port-forward -n $NAMESPACE <pod-name> 8080:8080"
