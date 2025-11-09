#!/bin/bash
set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLUSTER_NAME="spring-actor-demo"
NAMESPACE="spring-actor-monitoring"

# Function to print banner
print_banner() {
    echo -e "${CYAN}"
    cat << "EOF"
╔════════════════════════════════════════════════════════════════╗
║                                                                ║
║   Monitoring Stack (Prometheus + Grafana)                      ║
║                                                                ║
╚════════════════════════════════════════════════════════════════╝
EOF
    echo -e "${NC}"
}

# Function to show usage
show_usage() {
    print_banner
    echo -e "${CYAN}Usage:${NC}"
    echo -e "  ./setup-monitoring.sh [command]"
    echo
    echo -e "${CYAN}Commands:${NC}"
    echo -e "  ${GREEN}setup${NC}      Set up the monitoring stack (default)"
    echo -e "  ${GREEN}status${NC}     Show monitoring stack status"
    echo -e "  ${GREEN}logs${NC}       View monitoring logs"
    echo -e "  ${GREEN}restart${NC}    Restart monitoring components"
    echo -e "  ${GREEN}cleanup${NC}    Clean up monitoring stack"
    echo -e "  ${GREEN}help${NC}       Show this help message"
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

# Function to pre-pull and load images into Kind
load_images() {
    echo -e "${YELLOW}Pre-pulling and loading images into Kind cluster...${NC}"

    # Image list
    PROMETHEUS_IMAGE="prom/prometheus:v2.48.0"
    GRAFANA_IMAGE="grafana/grafana:10.2.2"

    # Pull images
    echo -e "${CYAN}Pulling Prometheus image...${NC}"
    docker pull $PROMETHEUS_IMAGE || {
        echo -e "${RED}✗ Failed to pull Prometheus image${NC}"
        echo -e "${YELLOW}  This might be due to Docker Hub rate limiting or network issues${NC}"
        echo -e "${YELLOW}  Continuing anyway - will try to use cached image if available${NC}"
    }

    echo -e "${CYAN}Pulling Grafana image...${NC}"
    docker pull $GRAFANA_IMAGE || {
        echo -e "${RED}✗ Failed to pull Grafana image${NC}"
        echo -e "${YELLOW}  This might be due to Docker Hub rate limiting or network issues${NC}"
        echo -e "${YELLOW}  Continuing anyway - will try to use cached image if available${NC}"
    }

    # Load into Kind
    if cluster_exists; then
        echo -e "${CYAN}Loading images into Kind cluster...${NC}"
        kind load docker-image $PROMETHEUS_IMAGE --name $CLUSTER_NAME || echo -e "${YELLOW}⚠️  Failed to load Prometheus image${NC}"
        kind load docker-image $GRAFANA_IMAGE --name $CLUSTER_NAME || echo -e "${YELLOW}⚠️  Failed to load Grafana image${NC}"
        echo -e "${GREEN}✓ Images loaded into Kind cluster${NC}"
    else
        echo -e "${RED}✗ Kind cluster '$CLUSTER_NAME' not found${NC}"
        echo -e "${YELLOW}  Please run '../app/setup-local.sh' first to create the cluster${NC}"
        exit 1
    fi
}

# Setup function
setup_monitoring() {
    print_banner

    # Check if kubectl is available
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}✗ kubectl not found${NC}"
        echo -e "${YELLOW}  Please install kubectl first${NC}"
        exit 1
    fi

    # Check if kind is available
    if ! command -v kind &> /dev/null; then
        echo -e "${RED}✗ kind not found${NC}"
        echo -e "${YELLOW}  Please install kind first${NC}"
        exit 1
    fi

    # Check if cluster exists
    if ! cluster_exists; then
        echo -e "${RED}✗ Kind cluster '$CLUSTER_NAME' not found${NC}"
        echo -e "${YELLOW}  Please run '../app/setup-local.sh' first to create the cluster${NC}"
        exit 1
    fi

    echo -e "${YELLOW}[1/4] Pre-loading images into Kind cluster...${NC}"
    load_images
    echo

    echo -e "${YELLOW}[2/4] Deploying monitoring stack...${NC}"
    kubectl apply -k "$SCRIPT_DIR/base"
    echo -e "${GREEN}✓ Monitoring resources created${NC}"
    echo

    echo -e "${YELLOW}[3/4] Waiting for pods to be ready (this may take 1-2 minutes)...${NC}"
    echo -e "${CYAN}Waiting for Prometheus...${NC}"
    kubectl wait --for=condition=ready pod -l app=prometheus -n $NAMESPACE --timeout=180s || {
        echo -e "${RED}✗ Prometheus pod failed to become ready${NC}"
        echo -e "${YELLOW}  Run './setup-monitoring.sh status' to check the issue${NC}"
    }

    echo -e "${CYAN}Waiting for Grafana...${NC}"
    kubectl wait --for=condition=ready pod -l app=grafana -n $NAMESPACE --timeout=180s || {
        echo -e "${RED}✗ Grafana pod failed to become ready${NC}"
        echo -e "${YELLOW}  Run './setup-monitoring.sh status' to check the issue${NC}"
    }
    echo

    echo -e "${YELLOW}[4/4] Checking status...${NC}"
    show_status
    echo

    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}✓ Monitoring Stack Deployed Successfully!${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo

    echo -e "${CYAN}🔗 Access URLs:${NC}"
    echo -e "   ${GREEN}Grafana:${NC}     http://localhost:30000"
    echo -e "   ${GREEN}Prometheus:${NC}  http://localhost:30090"
    echo

    echo -e "${CYAN}📊 Grafana Dashboards:${NC}"
    echo -e "   • Pekko Cluster Overview"
    echo -e "   • Rolling Updates Visualization"
    echo -e "   • JVM & System Metrics"
    echo

    echo -e "${CYAN}🔐 Grafana Credentials:${NC}"
    echo -e "   ${GREEN}Username:${NC} admin"
    echo -e "   ${GREEN}Password:${NC} admin"
    echo -e "   ${YELLOW}Or use anonymous access (no login required)${NC}"
    echo

    echo -e "${CYAN}📝 Available Commands:${NC}"
    echo -e "   ${GREEN}./setup-monitoring.sh status${NC}   Show monitoring status"
    echo -e "   ${GREEN}./setup-monitoring.sh logs${NC}     View monitoring logs"
    echo -e "   ${GREEN}./setup-monitoring.sh restart${NC}  Restart monitoring"
    echo -e "   ${GREEN}./setup-monitoring.sh cleanup${NC}  Remove all resources"
    echo

    echo -e "${YELLOW}💡 Next Steps:${NC}"
    echo -e "   1. Deploy the application: cd ../app && ./setup-local.sh"
    echo -e "   2. Open Grafana at http://localhost:30000"
    echo -e "   3. View real-time metrics and dashboards"
    echo
}

# Status function
show_status() {
    if ! cluster_exists; then
        echo -e "${RED}✗ Cluster '$CLUSTER_NAME' does not exist${NC}"
        echo -e "${YELLOW}  Run '../app/setup-local.sh' to create the cluster first${NC}"
        return 1
    fi

    if ! namespace_exists; then
        echo -e "${RED}✗ Namespace '$NAMESPACE' does not exist${NC}"
        echo -e "${YELLOW}  Run './setup-monitoring.sh setup' to deploy monitoring${NC}"
        return 1
    fi

    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}✓ Monitoring Stack Status${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo

    echo -e "${CYAN}📊 Pods:${NC}"
    kubectl get pods -n $NAMESPACE -o wide 2>/dev/null || echo -e "${YELLOW}  No pods found${NC}"

    echo
    echo -e "${CYAN}🌐 Services:${NC}"
    kubectl get svc -n $NAMESPACE 2>/dev/null || echo -e "${YELLOW}  No services found${NC}"

    echo
    echo -e "${CYAN}📦 ConfigMaps:${NC}"
    kubectl get configmap -n $NAMESPACE 2>/dev/null | grep -E "NAME|prometheus|grafana" || echo -e "${YELLOW}  No configmaps found${NC}"

    # Check pod status details
    echo
    echo -e "${CYAN}🔍 Pod Details:${NC}"

    PROM_POD=$(kubectl get pods -n $NAMESPACE -l app=prometheus -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    if [ -n "$PROM_POD" ]; then
        PROM_STATUS=$(kubectl get pod -n $NAMESPACE $PROM_POD -o jsonpath='{.status.phase}')
        echo -e "   ${GREEN}Prometheus:${NC} $PROM_POD ($PROM_STATUS)"
    else
        echo -e "   ${RED}Prometheus:${NC} No pod found"
    fi

    GRAFANA_POD=$(kubectl get pods -n $NAMESPACE -l app=grafana -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    if [ -n "$GRAFANA_POD" ]; then
        GRAFANA_STATUS=$(kubectl get pod -n $NAMESPACE $GRAFANA_POD -o jsonpath='{.status.phase}')
        echo -e "   ${GREEN}Grafana:${NC}    $GRAFANA_POD ($GRAFANA_STATUS)"
    else
        echo -e "   ${RED}Grafana:${NC}    No pod found"
    fi

    echo
    echo -e "${CYAN}🔗 Access URLs:${NC}"
    echo -e "   ${GREEN}Grafana:${NC}     http://localhost:30000"
    echo -e "   ${GREEN}Prometheus:${NC}  http://localhost:30090"
}

# Logs function
show_logs() {
    if ! namespace_exists; then
        echo -e "${RED}✗ Namespace '$NAMESPACE' does not exist${NC}"
        echo -e "${YELLOW}  Run './setup-monitoring.sh setup' first${NC}"
        return 1
    fi

    echo -e "${CYAN}Select component to view logs:${NC}"
    echo -e "  ${GREEN}1)${NC} Prometheus"
    echo -e "  ${GREEN}2)${NC} Grafana"
    echo -e "  ${GREEN}3)${NC} All (both components)"
    echo
    read -p "Enter choice (1-3): " -n 1 -r
    echo
    echo

    case "$REPLY" in
        1)
            echo -e "${CYAN}Streaming Prometheus logs...${NC}"
            echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
            echo
            kubectl logs -f -n $NAMESPACE -l app=prometheus
            ;;
        2)
            echo -e "${CYAN}Streaming Grafana logs...${NC}"
            echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
            echo
            kubectl logs -f -n $NAMESPACE -l app=grafana
            ;;
        3)
            echo -e "${CYAN}Streaming all monitoring logs...${NC}"
            echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
            echo
            kubectl logs -f -n $NAMESPACE -l 'app in (prometheus,grafana)' --all-containers=true --max-log-requests=10
            ;;
        *)
            echo -e "${RED}Invalid choice${NC}"
            return 1
            ;;
    esac
}

# Restart function
restart_monitoring() {
    if ! namespace_exists; then
        echo -e "${RED}✗ Namespace '$NAMESPACE' does not exist${NC}"
        echo -e "${YELLOW}  Run './setup-monitoring.sh setup' first${NC}"
        return 1
    fi

    print_banner
    echo -e "${YELLOW}Restarting monitoring stack...${NC}"
    echo

    echo -e "${CYAN}[1/2] Restarting Prometheus...${NC}"
    kubectl rollout restart deployment/prometheus -n $NAMESPACE
    echo -e "${GREEN}✓ Prometheus restarted${NC}"

    echo -e "${CYAN}[2/2] Restarting Grafana...${NC}"
    kubectl rollout restart deployment/grafana -n $NAMESPACE
    echo -e "${GREEN}✓ Grafana restarted${NC}"

    echo
    echo -e "${YELLOW}Waiting for pods to be ready...${NC}"
    kubectl wait --for=condition=ready pod -l app=prometheus -n $NAMESPACE --timeout=120s || true
    kubectl wait --for=condition=ready pod -l app=grafana -n $NAMESPACE --timeout=120s || true

    echo
    echo -e "${GREEN}✓ Restart complete!${NC}"
    echo
    show_status
}

# Cleanup function
cleanup_monitoring() {
    "$SCRIPT_DIR/cleanup-monitoring.sh"
}

# Main execution
COMMAND="${1:-setup}"

case "$COMMAND" in
    setup)
        setup_monitoring
        ;;
    status)
        print_banner
        show_status
        ;;
    logs)
        print_banner
        show_logs
        ;;
    restart)
        restart_monitoring
        ;;
    cleanup)
        cleanup_monitoring
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
