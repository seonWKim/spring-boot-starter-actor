#!/bin/bash
# Runs each rollout strategy under load on the local kind cluster and fails if any request failed.
# Needs the example deployed by ../setup-local.sh (cluster spring-actor-demo, image spring-actor-chat:local).
#   1. rolling update        main Deployment -> v2
#   2. canary, then promote  one v3 pod joins the cluster, then main -> v3
#   3. blue-green            a separate green cluster on v4, the Service switches to it
# The new versions are retags of the local image: this checks the rollout mechanics, not a code change.
set -euo pipefail

cd "$(dirname "$0")"
NS=${NAMESPACE:-spring-actor}
CLUSTER=${KIND_CLUSTER:-spring-actor-demo}
BASE_IMAGE=${BASE_IMAGE:-spring-actor-chat:local}
LOGS=$(mktemp -d)
failed=0

image() {
    docker tag "$BASE_IMAGE" "spring-actor-chat:$1"
    kind load docker-image "spring-actor-chat:$1" --name "$CLUSTER" > /dev/null 2>&1
    echo "spring-actor-chat:$1"
}

members_up() {
    pod=$(kubectl -n "$NS" get pods -l "$1" -o jsonpath='{.items[0].metadata.name}')
    kubectl -n "$NS" exec "$pod" -- curl -s localhost:7626/cluster/members \
        | jq -r '[.members[] | select(.status == "Up")] | length'
}

# Runs "$@" while load.sh sends requests; prints the result line and records a failure.
under_load() {
    name=$1; seconds=$2; shift 2
    ./load.sh "$seconds" "$LOGS/$name.log" > "$LOGS/$name.sum" &
    load=$!
    sleep 5
    "$@"
    if wait "$load"; then result=ok; else result=FAILED; failed=1; fi
    printf '%-14s %s %s\n' "$name" "$(cat "$LOGS/$name.sum")" "$result"
}

rolling() {
    kubectl -n "$NS" set image deploy/spring-actor spring-actor="$(image v2)" > /dev/null
    kubectl -n "$NS" rollout status deploy/spring-actor --timeout=300s > /dev/null
}

canary() {
    ./canary.sh start "$(image v3)" > /dev/null
    sleep 20
    echo "  members Up with the canary: $(members_up track=canary)"
    ./canary.sh promote > /dev/null
}

blue_green() {
    ./blue-green.sh deploy "$(image v4)" > /dev/null
    sleep 20
    echo "  green members Up: $(members_up app=spring-actor-green)"
    ./blue-green.sh switch > /dev/null
    sleep 20
}

under_load rolling 120 rolling
under_load canary 180 canary
under_load blue-green 90 blue_green
./blue-green.sh finish > /dev/null   # green now serves; run ../setup-local.sh again to start over

echo "logs: $LOGS"
exit $failed
