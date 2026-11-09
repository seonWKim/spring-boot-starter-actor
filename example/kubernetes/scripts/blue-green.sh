#!/bin/bash
# Blue-green: a second, separate Pekko cluster (green) runs the new image; the HTTP Service
# switches to it in one step.
#   blue-green.sh deploy <image>   start green beside blue (blue keeps serving)
#   blue-green.sh switch           point the HTTP Service at green
#   blue-green.sh rollback         point the HTTP Service back at blue
#   blue-green.sh finish           delete blue once green has served for a while
# Green forms its own cluster (its discovery looks for app=spring-actor-green), so the versions
# never exchange messages. In-memory entity state stays in blue: green starts empty.
set -euo pipefail

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
    kubectl -n "$NS" get deploy "$BLUE" -o json | jq --arg name "$GREEN" --arg image "$IMAGE" '
        {apiVersion, kind,
         metadata: {name: $name, namespace: .metadata.namespace, labels: {app: $name}},
         spec: (.spec
            | .selector = {matchLabels: {app: $name}}
            | .template.metadata.labels = {app: $name}
            | .template.spec.containers[0].image = $image
            | .template.spec.containers[0].args = (.template.spec.containers[0].args // [])
                + ["--spring.actor.pekko.discovery.kubernetes-api.pod-label-selector=app=" + $name])}' \
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
