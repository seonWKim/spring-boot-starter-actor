# The live blue Deployment (kubectl get deploy -o json) -> green's: named $name, its pods labelled
# app=$name, running $image, with Kubernetes API discovery looking only for app=$name, and with the
# color green where the generation component sets one.
{apiVersion, kind,
 metadata: {name: $name, namespace: .metadata.namespace, labels: {app: $name}},
 spec: (.spec
    | .selector = {matchLabels: {app: $name}}
    | .template.metadata.labels = {app: $name}
    | .template.spec.containers[0].image = $image
    | .template.spec.containers[0].env |= map(
        if .name == "SPRING_ACTOR_GENERATION_COLOR" then .value = "green" else . end)
    | .template.spec.containers[0].args = (.template.spec.containers[0].args // [])
        + ["--spring.actor.pekko.discovery.kubernetes-api.pod-label-selector=app=" + $name])}
