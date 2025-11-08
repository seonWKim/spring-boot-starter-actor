#!/bin/bash
set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLUSTER_NAME="spring-actor-demo"
NAMESPACE="spring-actor"

# Function to print banner
print_banner() {
    echo -e "${CYAN}"
    cat << "EOF"
╔════════════════════════════════════════════════════════════════╗
║                                                                ║
║   Spring Boot Starter Actor - Kubernetes Example              ║
║                                                                ║
╚════════════════════════════════════════════════════════════════╝
EOF
    echo -e "${NC}"
}

# Function to show usage
show_usage() {
    print_banner
    echo -e "${CYAN}Usage:${NC}"
    echo -e "  ./setup-local.sh [command]"
    echo
    echo -e "${CYAN}Commands:${NC}"
    echo -e "  ${GREEN}setup${NC}         Set up the local Kubernetes cluster (default)"
    echo -e "  ${GREEN}status${NC}        Show cluster and pod status"
    echo -e "  ${GREEN}logs${NC}          View application logs"
    echo -e "  ${GREEN}port-forward${NC}  Set up port forwarding to individual pods"
    echo -e "  ${GREEN}rebuild${NC}       Rebuild application and restart deployment"
    echo -e "  ${GREEN}cleanup${NC}       Clean up all resources"
    echo -e "  ${GREEN}help${NC}          Show this help message"
    echo
}

# Function to check if cluster exists
cluster_exists() {
    kind get clusters 2>/dev/null | grep -q "$CLUSTER_NAME"
}

# Function to check if namespace exists
namespace_exists() {
    kubectl get namespace $NAMESPACE &> /dev/null
}

# Setup function
setup_cluster() {
    print_banner

    echo -e "${YELLOW}⚠️  Prerequisites:${NC}"
    echo -e "   Docker Desktop must have ${GREEN}at least 8 GB${NC} of memory allocated"
    echo -e "   (Settings → Resources → Memory → 8 GB → Apply & Restart)"
    echo

    # Check prerequisites
    echo -e "${YELLOW}[1/5] Checking prerequisites...${NC}"
    "$SCRIPT_DIR/scripts/check-prerequisites.sh"
    echo

    # Create kind cluster
    echo -e "${YELLOW}[2/5] Creating local Kubernetes cluster...${NC}"

    if cluster_exists; then
        echo -e "${GREEN}✓ Cluster '$CLUSTER_NAME' already exists${NC}"
    else
        kind create cluster --name $CLUSTER_NAME --config="$SCRIPT_DIR/scripts/kind-config.yaml"
        echo -e "${GREEN}✓ Cluster created${NC}"

        echo -e "${YELLOW}Waiting for all nodes to be ready...${NC}"
        kubectl wait --for=condition=ready node --all --timeout=180s
        echo -e "${GREEN}✓ All nodes ready${NC}"
    fi

    echo

    # Build and load image
    echo -e "${YELLOW}[3/5] Building application and Docker image...${NC}"
    CLUSTER_NAME=$CLUSTER_NAME "$SCRIPT_DIR/scripts/build-local.sh"
    echo

    # Deploy to Kubernetes
    echo -e "${YELLOW}[4/5] Deploying to Kubernetes...${NC}"
    kubectl apply -k "$SCRIPT_DIR/overlays/local"
    echo -e "${GREEN}✓ Deployed to Kubernetes${NC}"
    echo

    # Wait for deployment
    echo -e "${YELLOW}[5/5] Waiting for pods to be ready (this may take 1-2 minutes)...${NC}"
    kubectl wait --for=condition=ready pod -l app=spring-actor -n $NAMESPACE --timeout=180s || true
    echo

    # Show status
    show_status

    echo
    echo -e "${CYAN}🔗 Access the Application:${NC}"
    echo -e "   ${GREEN}Main Service:${NC}      http://localhost:8080"
    echo

    echo -e "${CYAN}📝 Available Commands:${NC}"
    echo -e "   ${GREEN}./setup-local.sh status${NC}        Show cluster status"
    echo -e "   ${GREEN}./setup-local.sh logs${NC}          View application logs"
    echo -e "   ${GREEN}./setup-local.sh port-forward${NC}  Access individual pods (8080, 8081, 8082)"
    echo -e "   ${GREEN}./setup-local.sh rebuild${NC}       Rebuild and restart"
    echo -e "   ${GREEN}./setup-local.sh cleanup${NC}       Remove all resources"

    echo
    echo -e "${YELLOW}💡 Tip: Wait 30-60 seconds for the cluster to fully form before testing!${NC}"
    echo
}

# Status function
show_status() {
    if ! cluster_exists; then
        echo -e "${RED}✗ Cluster '$CLUSTER_NAME' does not exist${NC}"
        echo -e "${YELLOW}  Run './setup-local.sh setup' to create it${NC}"
        return 1
    fi

    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}✓ Cluster Status${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo

    echo -e "${CYAN}📊 Pods:${NC}"
    kubectl get pods -n $NAMESPACE -o wide 2>/dev/null || echo -e "${YELLOW}  No pods found in namespace '$NAMESPACE'${NC}"

    echo
    echo -e "${CYAN}🌐 Services:${NC}"
    kubectl get svc -n $NAMESPACE 2>/dev/null || echo -e "${YELLOW}  No services found in namespace '$NAMESPACE'${NC}"

    # Get first running pod for cluster info
    POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=spring-actor --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

    if [ -n "$POD_NAME" ]; then
        echo
        echo -e "${CYAN}🎯 Pekko Cluster Members:${NC}"
        kubectl exec -n $NAMESPACE $POD_NAME -- curl -s localhost:8558/cluster/members 2>/dev/null | \
            jq -r '.members[] | "   \(.node) - \(.status)"' 2>/dev/null || \
            echo -e "${YELLOW}   Cluster still forming... (check again in 30 seconds)${NC}"
    fi
}

# Logs function
show_logs() {
    if ! namespace_exists; then
        echo -e "${RED}✗ Namespace '$NAMESPACE' does not exist${NC}"
        echo -e "${YELLOW}  Run './setup-local.sh setup' first${NC}"
        return 1
    fi

    echo -e "${CYAN}Streaming logs from all pods...${NC}"
    echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
    echo
    kubectl logs -f -n $NAMESPACE -l app=spring-actor --all-containers=true --max-log-requests=10
}

# Port forward function
port_forward() {
    if ! namespace_exists; then
        echo -e "${RED}✗ Namespace '$NAMESPACE' does not exist${NC}"
        echo -e "${YELLOW}  Run './setup-local.sh setup' first${NC}"
        return 1
    fi

    echo -e "${CYAN}Setting up port forwarding to individual pods...${NC}"
    echo

    # Get pod names
    PODS=($(kubectl get pods -n $NAMESPACE -l app=spring-actor --field-selector=status.phase=Running -o jsonpath='{.items[*].metadata.name}'))

    if [ ${#PODS[@]} -eq 0 ]; then
        echo -e "${RED}✗ No running pods found${NC}"
        echo -e "${YELLOW}  Please ensure the application is deployed and pods are running${NC}"
        return 1
    fi

    echo -e "${GREEN}Found ${#PODS[@]} running pod(s)${NC}"
    echo

    # Cleanup function to kill background port-forwards
    cleanup_port_forwards() {
        echo
        echo -e "${YELLOW}Stopping all port forwards...${NC}"
        jobs -p | xargs kill 2>/dev/null || true
        exit 0
    }

    trap cleanup_port_forwards INT TERM

    if [ ${#PODS[@]} -ge 1 ]; then
        echo -e "${CYAN}➜${NC} Port forwarding ${PODS[0]} -> localhost:8080"
        kubectl port-forward -n $NAMESPACE ${PODS[0]} 8080:8080 &
    fi

    if [ ${#PODS[@]} -ge 2 ]; then
        echo -e "${CYAN}➜${NC} Port forwarding ${PODS[1]} -> localhost:8081"
        kubectl port-forward -n $NAMESPACE ${PODS[1]} 8081:8080 &
    fi

    if [ ${#PODS[@]} -ge 3 ]; then
        echo -e "${CYAN}➜${NC} Port forwarding ${PODS[2]} -> localhost:8082"
        kubectl port-forward -n $NAMESPACE ${PODS[2]} 8082:8080 &
    fi

    echo
    echo -e "${GREEN}✓ Port forwarding active${NC}"
    echo -e "${CYAN}Access individual pods at:${NC}"
    [ ${#PODS[@]} -ge 1 ] && echo -e "   ${GREEN}Pod 1:${NC} http://localhost:8080"
    [ ${#PODS[@]} -ge 2 ] && echo -e "   ${GREEN}Pod 2:${NC} http://localhost:8081"
    [ ${#PODS[@]} -ge 3 ] && echo -e "   ${GREEN}Pod 3:${NC} http://localhost:8082"
    echo
    echo -e "${YELLOW}Press Ctrl+C to stop all port forwards${NC}"
    echo

    wait
}

# Rebuild function
rebuild() {
    if ! cluster_exists; then
        echo -e "${RED}✗ Cluster '$CLUSTER_NAME' does not exist${NC}"
        echo -e "${YELLOW}  Run './setup-local.sh setup' first${NC}"
        return 1
    fi

    print_banner
    echo -e "${YELLOW}Rebuilding application and restarting deployment...${NC}"
    echo

    echo -e "${CYAN}[1/2] Building application and Docker image...${NC}"
    CLUSTER_NAME=$CLUSTER_NAME "$SCRIPT_DIR/scripts/build-local.sh"
    echo

    echo -e "${CYAN}[2/2] Restarting deployment...${NC}"
    kubectl rollout restart deployment/spring-actor -n $NAMESPACE
    echo -e "${GREEN}✓ Deployment restarted${NC}"

    echo
    echo -e "${YELLOW}Waiting for pods to be ready...${NC}"
    kubectl wait --for=condition=ready pod -l app=spring-actor -n $NAMESPACE --timeout=180s || true

    echo
    echo -e "${GREEN}✓ Rebuild complete!${NC}"
    echo
}

# Cleanup function
cleanup() {
    "$SCRIPT_DIR/cleanup-local.sh"
}

# Main execution
COMMAND="${1:-setup}"

case "$COMMAND" in
    setup)
        setup_cluster
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs
        ;;
    port-forward)
        port_forward
        ;;
    rebuild)
        rebuild
        ;;
    cleanup)
        cleanup
        ;;
    help|--help|-h)
        show_usage
        ;;
    *)
        echo -e "${RED}Unknown command: $COMMAND${NC}"
        echo
        show_usage
        exit 1
        ;;
esac
