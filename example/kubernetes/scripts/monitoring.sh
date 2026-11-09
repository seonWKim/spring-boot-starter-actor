#!/bin/bash
# Installs the actor ServiceMonitor, alert rules and Grafana dashboard (needs the Prometheus Operator,
# for example kube-prometheus-stack). Usage: scripts/monitoring.sh [apply|delete|render]
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
ACTION="${1:-apply}"

render() {
    # The dashboard and rules live in the repository's monitoring/ directory, outside the kustomization.
    kubectl kustomize --load-restrictor LoadRestrictionsNone "$DIR/monitoring"
}

case "$ACTION" in
    render) render ;;
    apply) render | kubectl apply -f - ;;
    delete) render | kubectl delete --ignore-not-found -f - ;;
    *) echo "Usage: $0 [apply|delete|render]" >&2; exit 1 ;;
esac
