#!/bin/bash
# Blue-green: a second, separate Pekko cluster (green) runs the new image; the HTTP Service
# switches to it in one step.
#   blue-green.sh deploy <image>   start green beside blue (blue keeps serving)
#   blue-green.sh switch           point the HTTP Service at green
#   blue-green.sh rollback         point the HTTP Service back at blue
#   blue-green.sh finish           delete blue once green has served for a while
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

select_pods() {
    kubectl -n "$NS" patch svc "$SERVICE" --type=json \
        -p "[{\"op\":\"replace\",\"path\":\"/spec/selector\",\"value\":$1}]"
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
    kubectl -n "$NS" rollout status deploy/"$GREEN" --timeout=300s
    ;;
switch)
    select_pods "{\"app\":\"$GREEN\"}"
    ;;
rollback)
    select_pods '{"app":"spring-actor","managed-by":"kustomize"}'
    ;;
finish)
    kubectl -n "$NS" delete deploy "$BLUE"
    ;;
*)
    echo "usage: blue-green.sh deploy <image> | switch | rollback | finish" >&2
    exit 2
    ;;
esac
