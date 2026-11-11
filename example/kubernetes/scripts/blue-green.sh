#!/bin/bash
# Blue-green: a second, separate Pekko cluster (green) runs the new image; the HTTP Service
# switches to it in one step. The stages of a release, in order:
#   blue-green.sh deploy <image>   1. start green beside blue (blue keeps serving) and wait until
#                                     every green pod is warmed up (/actuator/health/warmup) and ready
#   blue-green.sh activate         2. make green the active generation (POST /actuator/generation: a
#                                     compare-and-set on the epoch), then point the HTTP Service at green
#   blue-green.sh verify           3. wait until the record says green and the Service's endpoints are
#                                     exactly the green pods
#   blue-green.sh retire           4. delete blue once green has served for a while
#   blue-green.sh rollback         activate blue again (a new, higher epoch) and point the Service at it
# activate and rollback take the epoch they expect and an operation ID from EXPECTED_EPOCH and
# OPERATION_ID. Without them they read the active epoch and use <color>-<image>-from-<epoch>. A
# pipeline that retries an activation passes the same two values again: the retry then returns the
# first result instead of failing on the epoch the first attempt already moved. A competing deploy
# fails with 409.
# deploy gates on warm-up, not on `rollout status`: warm-up says that a pod joined its cluster,
# which is what activation needs, and it does not depend on which color gets traffic. It then also
# waits for readiness, because here the Service selector, not readiness, switches the colors.
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

# Runs curl inside pod $1 against its /actuator/generation, with the deployer credentials the pod
# has in its environment (from the spring-actor-deployer Secret): they never leave the pod.
generation() {
    pod=$1; shift
    kubectl -n "$NS" exec "$pod" -- sh -c \
        'curl -s -u "$SPRING_SECURITY_USER_NAME:$SPRING_SECURITY_USER_PASSWORD" "$@" localhost:8081/actuator/generation' \
        sh "$@"
}

# The running pods with app=$1, without the ones shutting down.
running_pods() {
    kubectl -n "$NS" get pods -l app="$1" -o json | jq -r \
        '.items[] | select(.status.phase == "Running" and .metadata.deletionTimestamp == null) | .metadata.name'
}

# A running pod of Deployment $1.
any_pod() {
    running_pods "$1" | head -n 1
}

# Makes color $1 (Deployment $2) the active generation, or fails with the endpoint's answer.
activate_color() {
    color=$1; pod=$(any_pod "$2")
    epoch=${EXPECTED_EPOCH:-$(generation "$pod" | jq '.active.epoch // 0')}
    image=$(kubectl -n "$NS" get deploy "$2" -o jsonpath='{.spec.template.spec.containers[0].image}')
    operation=${OPERATION_ID:-$color-$image-from-$epoch}
    reply=$(generation "$pod" -X POST -H 'Content-Type: application/json' -w '\n%{http_code}' \
        -d "{\"color\":\"$color\",\"expectedEpoch\":$epoch,\"operationId\":\"$operation\"}")
    if [ "$(tail -n 1 <<< "$reply")" != 200 ]; then
        echo "activating $color (expected epoch $epoch, operation $operation) was rejected:" >&2
        sed '$d' <<< "$reply" >&2
        return 1
    fi
    sed '$d' <<< "$reply"
}

# The generation record says color $1 is active.
record_says() {
    [ "$(generation "$(any_pod "$2")" | jq -r '.active.color')" = "$1" ]
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
    pods=$(running_pods "$GREEN")
    [ "$(wc -w <<< "$pods")" -eq "$want" ] || return 1
    for pod in $pods; do
        kubectl -n "$NS" exec "$pod" -- curl -sf localhost:8081/actuator/health/warmup > /dev/null \
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
    # This example switches with the Service selector, and its readiness does not depend on the color:
    # activating pods that are not ready yet would leave the Service without endpoints for a while.
    kubectl -n "$NS" wait --for=condition=ready pod -l app="$GREEN" --timeout="${TIMEOUT}s" > /dev/null
    ;;
activate)
    activate_color green "$GREEN"
    select_pods "{\"app\":\"$GREEN\"}"
    ;;
verify)
    until_ok "the generation record to say green" record_says green "$GREEN"
    until_ok "the Service to serve only green" serves_only "$GREEN"
    ;;
rollback)
    activate_color blue "$BLUE"
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
