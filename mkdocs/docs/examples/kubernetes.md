# Kubernetes Example

Kubernetes manifests that run the [chat example](chat.md) as a three-pod Pekko cluster, with scripts for
a local kind cluster, rolling updates, canary and blue-green rollouts, and monitoring.

- **Source:** [`example/kubernetes`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/kubernetes)
  (its [README](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/kubernetes/README.md)
  has every command)
- **Runs:** a local kind cluster, or any Kubernetes cluster
- **Modules:** the chat app's image; Pekko Management, Cluster Bootstrap and Kubernetes API discovery
  (runtime dependencies of `example/chat`); Kustomize

## What it shows

- Cluster formation with no fixed seed nodes: Cluster Bootstrap finds the other pods through the
  Kubernetes API
- The same without RBAC: peers found through PostgreSQL or Redis (`spring.actor.cluster.join: discovery`)
- Zero-downtime rolling updates (`maxUnavailable: 0`, `maxSurge: 1`)
- Canary and blue-green rollouts, and a script that runs all three under load and counts failed requests
- A ServiceMonitor, alert rules and the Grafana dashboard for the Prometheus Operator
- Kustomize overlays for local, dev and prod

## Run it locally

You need Docker (with at least 8 GB of memory), [kind](https://kind.sigs.k8s.io/) and `kubectl`.

```bash
cd example/kubernetes
./setup-local.sh
```

The script creates the kind cluster `spring-actor-demo`, builds the chat app
(`../../gradlew clean build -x test` in `example/chat`), builds the image `spring-actor-chat:local` from
`Dockerfile.kubernetes`, loads it into kind, and applies `overlays/local`. Then open
`http://localhost:8080`.

Check the cluster:

```bash
kubectl get pods -n spring-actor

POD=$(kubectl get pods -n spring-actor -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n spring-actor $POD -- curl -s localhost:7626/cluster/members | jq   # three members, "Up"

kubectl logs -f -n spring-actor -l app=spring-actor
```

`./port-forward-pods.sh` forwards ports 8080, 8081 and 8082 to the three pods one by one, so you can open
the chat on each. Remove everything with `./cleanup-local.sh`.

## Layout

```
example/kubernetes/
├── setup-local.sh, cleanup-local.sh, port-forward-pods.sh
├── base/                  namespace, RBAC (pod reader), ConfigMap, Services, Deployment, monitors, NetworkPolicy
├── overlays/
│   ├── local/             used by setup-local.sh: 3 replicas, NodePort 30080 (kind maps it to localhost:8080)
│   ├── dev/, prod/        prod adds an HPA
│   ├── discovery-jdbc/    no RBAC: peers through PostgreSQL
│   └── discovery-redis/   no RBAC: peers through Redis
├── components/discovery/  shared by the two discovery overlays
├── monitoring/            ServiceMonitor, alert rules, Grafana dashboard
└── scripts/               deploy, rollout, debug, load, canary, blue-green, test-strategies, monitoring
```

## Walkthrough

### Cluster formation through the Kubernetes API

The chat app's `kubernetes` profile clears the seed-node list
(`application-kubernetes.yml`), and the ConfigMap in `base/` sets Cluster Bootstrap under
`spring.actor.pekko.management.cluster.bootstrap` with `discovery-method: kubernetes-api`. Each pod lists
the pods with the label `app=spring-actor` (the RBAC Role in `base/rbac.yaml` allows that), contacts them
on the management port 7626, and joins the existing cluster or forms a new one. All overlays use Pekko's
default ports: remoting 25520, management 7626.

### Graceful shutdown

Pods get `terminationGracePeriodSeconds: 45`: a 10-second `preStop` sleep so the Services stop sending
new requests, Spring Boot's graceful drain of the requests in flight, then up to 25 seconds
(`spring.actor.shutdown.timeout`) for the actor system to leave the cluster.

### Without RBAC: JDBC or Redis discovery

When pods may not read the Kubernetes API, use an overlay that finds peers through a store the app already
uses:

```bash
kubectl apply -k overlays/discovery-jdbc    # PostgreSQL at postgres:5432, Secret spring-actor-db
kubectl apply -k overlays/discovery-redis   # Redis at redis:6379
```

Each pod writes its own entry to the store, renews it every 5 seconds and deletes it on graceful
shutdown. The `components/discovery` component removes the RBAC objects and the service account token,
and sets readiness on Actuator health. The chat image does not contain `actor-jdbc` or `actor-redis`, so these overlays
need your own image (`images:` in the overlay).

`./gradlew :example:kubernetes:test` renders every overlay (it needs `kubectl`, and skips otherwise) and
checks that the ConfigMap, Deployment and Services agree on the ports, that the grace period covers the
shutdown, and that the discovery overlays contain no RBAC and open only the expected ports.

### Rollouts

| Strategy | Command | What happens |
|---|---|---|
| Rolling update | `kubectl set image deployment/spring-actor spring-actor=spring-actor-chat:v2 -n spring-actor` | New pods join the cluster one at a time; old pods leave |
| Canary | `scripts/canary.sh start <image>`, then `promote` or `abort` | A few new pods join the running cluster and get a share of traffic |
| Blue-green | `scripts/blue-green.sh deploy <image>`, `switch`, `rollback`, `finish` | A second, separate cluster; the HTTP Service switches in one step |

`scripts/test-strategies.sh` runs all three under load on the kind cluster and fails if any request
failed. `scripts/load.sh` counts a request as ok only when the actor replied.

Rolling updates and canaries mix versions in one cluster, so messages and sharded state must stay
readable by both versions. The [Deployments guide](../guides/operations/deployments.md) explains how.

### Monitoring

With the Prometheus Operator installed (for example kube-prometheus-stack):

```bash
./scripts/monitoring.sh          # apply; `render` prints the manifests, `delete` removes them
```

It installs the ServiceMonitor for `/actuator/prometheus`, the `actor-alerts` PrometheusRule and the
`Actors` Grafana dashboard. See [Monitoring](../guides/operations/monitoring.md).

## Try next

- Delete a pod (`kubectl delete pod <name> -n spring-actor`) and watch a new one join.
- Scale to five pods: `kubectl scale deployment spring-actor -n spring-actor --replicas=5`.
- Run `scripts/test-strategies.sh` and read the failure counts.

## Related

- [Deployments](../guides/operations/deployments.md): rolling, canary, blue-green, message compatibility
- [Production](../guides/operations/production.md): graceful shutdown and health checks
- [Cluster formation](../guides/cluster/cluster-formation.md): seed nodes, Cluster Bootstrap, discovery
- [Split brain and leases](../guides/cluster/split-brain-and-leases.md)
- [Chat example](chat.md)
