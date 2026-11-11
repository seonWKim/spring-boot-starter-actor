#!/bin/bash
# Blue-green: a second, separate Pekko cluster (green) runs the new image; the HTTP Service
# switches to it in one step. The stages of a release, in order:
#   blue-green.sh deploy <image>   1. start green beside blue (blue keeps serving) and wait until
#                                     every green pod is warmed up (/actuator/health/warmup)
#   blue-green.sh activate         2. point the HTTP Service at green
#   blue-green.sh verify           3. wait until the Service's endpoints are exactly the green pods
#   blue-green.sh retire           4. delete blue once green has served for a while
#   blue-green.sh rollback         point the HTTP Service back at blue (until retire)
# deploy waits for warm-up, not for `rollout status`: warm-up says that a pod joined its cluster,
# which is what activation needs, and it does not depend on which color gets traffic.
# Green forms its own cluster (its discovery looks for app=spring-actor-green), so the versions
# never exchange messages. Green gets a copy of each blue NetworkPolicy that selects
# app=spring-actor-green instead, so remoting and management open only between pods of one color.
# In-memory entity state stays in blue: green starts empty.
set -euo pipefail

DIR=$(dirname "$0")

NS=${NAMESPACE:-spring-actor}
BLUE=spring-actor
GREEN=spring-actor-green
SERVICE=spring-actor-http
TIMEOUT=${TIMEOUT:-300}

select_pods() {
    kubectl -n "$NS" patch svc "$SERVICE" --type=json \
        -p "[{\"op\":\"replace\",\"path\":\"/spec/selector\",\"value\":$1}]"
}

# Retries "$@" every 2 seconds until it succeeds or $TIMEOUT seconds have passed.
until_ok() {
    what=$1; shift
    end=$(( $(date +%s) + TIMEOUT ))
    until "$@"; do
        if [ "$(date +%s)" -ge "$end" ]; then
            echo "timed out after ${TIMEOUT}s waiting for $what" >&2
            return 1
        fi
        sleep 2
    done
}

# Every pod of the green Deployment's replicas answers UP on /actuator/health/warmup.
green_warm() {
    want=$(kubectl -n "$NS" get deploy "$GREEN" -o jsonpath='{.spec.replicas}')
    pods=$(kubectl -n "$NS" get pods -l app="$GREEN" --field-selector=status.phase=Running \
        -o jsonpath='{.items[*].metadata.name}')
    [ "$(wc -w <<< "$pods")" -eq "$want" ] || return 1
    for pod in $pods; do
        kubectl -n "$NS" exec "$pod" -- curl -sf localhost:8080/actuator/health/warmup > /dev/null \
            2>&1 || return 1
    done
}

# The Service's ready endpoints are exactly the pods of $1 (by IP), and none is left from the other color.
serves_only() {
    want=$(kubectl -n "$NS" get pods -l app="$1" -o jsonpath='{.items[*].status.podIP}' | tr ' ' '\n' | sort)
    have=$(kubectl -n "$NS" get endpointslices -l kubernetes.io/service-name="$SERVICE" -o json \
        | jq -r '.items[].endpoints[]? | select(.conditions.ready) | .addresses[]' | sort)
    [ -n "$want" ] && [ "$want" = "$have" ]
}

case "${1:-}" in
deploy)
    IMAGE=${2:?usage: blue-green.sh deploy <image>}
    # Green's NetworkPolicies first, so no green pod runs unrestricted. None when blue has none.
    policies=$(kubectl -n "$NS" get networkpolicy -o json \
        | jq --arg blue "$BLUE" --arg green "$GREEN" -f "$DIR/blue-green-policies.jq")
    if [ "$(jq '.items | length' <<< "$policies")" -gt 0 ]; then
        kubectl apply -f - <<< "$policies"
    fi
    kubectl -n "$NS" get deploy "$BLUE" -o json \
        | jq --arg name "$GREEN" --arg image "$IMAGE" -f "$DIR/blue-green-deployment.jq" \
        | kubectl apply -f -
    until_ok "green to warm up" green_warm
    ;;
activate)
    select_pods "{\"app\":\"$GREEN\"}"
    ;;
verify)
    until_ok "the Service to serve only green" serves_only "$GREEN"
    ;;
rollback)
    select_pods '{"app":"spring-actor","managed-by":"kustomize"}'
    until_ok "the Service to serve only blue" serves_only "$BLUE"
    ;;
retire)
    kubectl -n "$NS" delete deploy "$BLUE"
    ;;
*)
    echo "usage: blue-green.sh deploy <image> | activate | verify | retire | rollback" >&2
    exit 2
    ;;
esac
