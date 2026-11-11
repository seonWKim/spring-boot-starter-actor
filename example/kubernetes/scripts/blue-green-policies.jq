# Blue's NetworkPolicies (kubectl get networkpolicy -o json) -> green's, as a List: a copy of each
# policy that selects app=$blue, with every app=$blue selector (the pods it applies to and its peers)
# turned into app=$green. Green gets blue's restrictions, and its remoting and management ports open
# only to green pods.
{apiVersion: "v1", kind: "List", items: [
  .items[]
  | select(.spec.podSelector.matchLabels.app == $blue)
  | {apiVersion, kind,
     metadata: {name: ($green + (.metadata.name | ltrimstr($blue))), namespace: .metadata.namespace},
     spec: (.spec | walk(if type == "object" and .app == $blue then .app = $green else . end))}
]}
