#!/bin/bash
# Canary: pods of a new image join the running cluster and get a share of the traffic.
#   canary.sh start <image> [replicas]   one canary pod by default; traffic share = canary pods / all pods
#   canary.sh promote                    roll the main Deployment to the canary image, then remove the canary
#   canary.sh abort                      remove the canary; the main Deployment is untouched
# The canary runs in the same Pekko cluster as the main pods, so both versions must read each
# other's messages and sharded entity state (see "Changing versions" in ../README.md).
set -euo pipefail

NS=${NAMESPACE:-spring-actor}
MAIN=spring-actor
CANARY=spring-actor-canary

case "${1:-}" in
start)
    IMAGE=${2:?usage: canary.sh start <image> [replicas]}
    REPLICAS=${3:-1}
    # Copy the live main Deployment, so the canary gets the same config, probes and labels.
    # The Services select app=spring-actor, so they pick up the canary pods too.
    kubectl -n "$NS" get deploy "$MAIN" -o json | jq \
        --arg name "$CANARY" --arg image "$IMAGE" --argjson replicas "$REPLICAS" '
        {apiVersion, kind,
         metadata: {name: $name, namespace: .metadata.namespace, labels: (.metadata.labels + {track: "canary"})},
         spec: (.spec
            | .replicas = $replicas
            | .selector.matchLabels += {track: "canary"}
            | .template.metadata.labels += {track: "canary"}
            | .template.spec.containers[0].image = $image)}' \
        | kubectl apply -f -
    kubectl -n "$NS" rollout status deploy/"$CANARY" --timeout=300s
    ;;
promote)
    IMAGE=$(kubectl -n "$NS" get deploy "$CANARY" -o jsonpath='{.spec.template.spec.containers[0].image}')
    kubectl -n "$NS" set image deploy/"$MAIN" spring-actor="$IMAGE"
    kubectl -n "$NS" rollout status deploy/"$MAIN" --timeout=300s
    kubectl -n "$NS" delete deploy "$CANARY"
    ;;
abort)
    kubectl -n "$NS" delete deploy "$CANARY"
    ;;
*)
    echo "usage: canary.sh start <image> [replicas] | promote | abort" >&2
    exit 2
    ;;
esac
