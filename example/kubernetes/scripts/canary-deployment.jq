# The live main Deployment (kubectl get deploy -o json) -> the canary's: named $name, $replicas pods
# of $image, with the main pods' labels plus track=canary. Keeping app=spring-actor puts the canary in
# the main cluster, behind the same Services and NetworkPolicies.
{apiVersion, kind,
 metadata: {name: $name, namespace: .metadata.namespace, labels: (.metadata.labels + {track: "canary"})},
 spec: (.spec
    | .replicas = $replicas
    | .selector.matchLabels += {track: "canary"}
    | .template.metadata.labels += {track: "canary"}
    | .template.spec.containers[0].image = $image)}
