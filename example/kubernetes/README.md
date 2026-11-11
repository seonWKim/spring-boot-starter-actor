# Kubernetes Deployment Example

This example demonstrates how to deploy a Spring Boot Starter Actor application (the chat example) to Kubernetes with clustering support and rolling updates.

## What This Example Shows

✨ **Zero-downtime rolling updates** with Pekko Cluster
🔄 **Automatic cluster formation** using Kubernetes service discovery
📊 **Production-ready configuration** with health checks and monitoring
🎯 **Easy local testing** with kind (Kubernetes in Docker)

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                        │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Namespace: spring-actor                                │ │
│  │                                                          │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │ │
│  │  │   Pod 1      │  │   Pod 2      │  │   Pod 3      │  │ │
│  │  │              │  │              │  │              │  │ │
│  │  │ Chat Actor   │◄─┼─►Chat Actor  │◄─┼─►Chat Actor  │  │ │
│  │  │ :8080        │  │ :8080        │  │ :8080        │  │ │
│  │  │ Remoting     │  │ Remoting     │  │ Remoting     │  │ │
│  │  │ :25520       │  │ :25520       │  │ :25520       │  │ │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │ │
│  │         │                  │                  │          │ │
│  │         └──────────────────┴──────────────────┘          │ │
│  │                            │                             │ │
│  │                   ┌────────▼─────────┐                   │ │
│  │                   │  Pekko Cluster   │                   │ │
│  │                   │  (Single Logical │                   │ │
│  │                   │   Cluster)       │                   │ │
│  │                   └──────────────────┘                   │ │
│  │                                                          │ │
│  │  ┌────────────────────────────────────────────────────┐ │ │
│  │  │  Service: spring-actor (Headless)                  │ │ │
│  │  │  • Used for pod discovery                          │ │ │
│  │  │  • Cluster Bootstrap via Kubernetes API            │ │ │
│  │  └────────────────────────────────────────────────────┘ │ │
│  │                                                          │ │
│  │  ┌────────────────────────────────────────────────────┐ │ │
│  │  │  Service: spring-actor-http (LoadBalancer)         │ │ │
│  │  │  • Exposes chat UI on port 80                      │ │ │
│  │  └────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Quick Start (5 minutes!)

### Prerequisites

Install these tools (one-time setup):

- **Docker** - [Install Docker](https://docs.docker.com/get-docker/)
- **kind** - [Install kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
- **kubectl** - [Install kubectl](https://kubernetes.io/docs/tasks/tools/)

### Run the Example

1. **Run the setup script:**

```bash
cd example/kubernetes
./setup-local.sh
```

That's it! The script will:
- ✅ Create a local Kubernetes cluster
- ✅ Build the chat application
- ✅ Build and load Docker image
- ✅ Deploy to Kubernetes
- ✅ Wait for pods to be ready

2. **Access the application:**

Open your browser: **http://localhost:8080**

You'll see the chat UI connected to a 3-node Pekko cluster!

3. **Verify cluster is working:**

```bash
# Get pod names
kubectl get pods -n spring-actor

# Check cluster members
POD=$(kubectl get pods -n spring-actor -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n spring-actor $POD -- curl -s localhost:7626/cluster/members | jq
```

You should see 3 cluster members with status "Up".

4. **Watch logs:**

```bash
kubectl logs -f -n spring-actor -l app=spring-actor
```

5. **Cleanup when done:**

```bash
./cleanup-local.sh
```

## What's Happening Under the Hood?

### Cluster Formation

```
Time    Action                          Cluster State
────────────────────────────────────────────────────────────
T+0s    Kind cluster created            Empty
T+10s   Pod 1 starts                    Discovering peers...
T+15s   Pod 1 discovers itself          Forms new cluster (size: 1)
T+20s   Pod 2 starts                    Discovering peers...
T+25s   Pod 2 finds Pod 1               Joins cluster (size: 2)
T+30s   Pod 3 starts                    Discovering peers...
T+35s   Pod 3 finds Pod 1 & 2           Joins cluster (size: 3)
T+40s   All pods ready                  ✓ Cluster fully formed!
```

### How Pods Discover Each Other

1. **Pod starts** and queries Kubernetes API:
   ```
   GET /api/v1/namespaces/spring-actor/pods?labelSelector=app=spring-actor
   ```

2. **Kubernetes returns** all pods with that label:
   ```json
   {
     "items": [
       {"name": "spring-actor-abc123", "ip": "10.244.0.5"},
       {"name": "spring-actor-def456", "ip": "10.244.0.6"},
       {"name": "spring-actor-ghi789", "ip": "10.244.0.7"}
     ]
   }
   ```

3. **Pod contacts peers** on management port (7626):
   ```
   HTTP GET http://10.244.0.5:7626/bootstrap/seed-nodes
   HTTP GET http://10.244.0.6:7626/bootstrap/seed-nodes
   HTTP GET http://10.244.0.7:7626/bootstrap/seed-nodes
   ```

4. **Cluster Bootstrap logic:**
   - If cluster exists → Join it
   - If no cluster exists → Node with lowest address forms cluster

No hardcoded seed nodes needed! 🎉

### Graceful shutdown

When Kubernetes stops a pod (a rollout, a scale-down, a node drain), the pod has
`terminationGracePeriodSeconds: 45` before it is killed:

1. The `preStop` hook sleeps 10s, so the Services stop sending it new requests.
2. Spring Boot finishes the HTTP requests in flight (`server.shutdown: graceful`).
3. The actor system leaves the cluster and hands its shards to the other pods, within
   `spring.actor.shutdown.timeout` (25s in the ConfigMap).

## Without RBAC: discovery through JDBC or Redis

The default setup finds pods through the Kubernetes API, so the pods' service account needs a Role
that can list pods. When the platform team won't grant that, the overlays `discovery-jdbc` and
`discovery-redis` form the cluster through a store the application already uses
(`spring.actor.cluster.join: discovery`, see the
[Cluster Formation guide](https://seonwkim.github.io/actor-spring-boot-starter/guides/cluster/cluster-formation/)).
Each pod writes its own entry (its IP and management port) to the `actor_peer` table or a Redis key,
renews it every 5 seconds, and deletes it when it shuts down gracefully. Cluster Bootstrap reads the
live entries and joins or forms the cluster.

```bash
kubectl apply -k overlays/discovery-jdbc    # PostgreSQL at postgres:5432, Secret spring-actor-db
kubectl apply -k overlays/discovery-redis   # Redis at redis:6379
```

Both overlays use the component `components/discovery`, which:

- removes the pod-reader `Role` and `RoleBinding`, and mounts no service account token;
- replaces `application.yaml` with `join: discovery` (the store comes from each overlay's environment);
- probes Spring Boot's Actuator health (readiness includes `actorCluster`, so a pod gets traffic only
  while it is an `Up` member);
- deletes the pod's entry on shutdown, within the base Deployment's 45s grace period (see
  [Graceful shutdown](#graceful-shutdown)).

The application image needs the store module and its client next to the Pekko Management libraries
the chat example already has. The chat image does not include them; use your own application's image
(`images:` in the overlay) with:

```kotlin
implementation("io.github.seonwkim:actor-jdbc:$version")          // discovery-jdbc
implementation("org.springframework.boot:spring-boot-starter-jdbc")
runtimeOnly("org.postgresql:postgresql")

implementation("io.github.seonwkim:actor-redis:$version")         // discovery-redis
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

### Network rules to ask the platform team for

| From | To | Port | Why |
|---|---|---|---|
| app pods | app pods | TCP 25520 | Pekko remoting: messages between nodes |
| app pods | app pods | TCP 7626 | Pekko Management: Cluster Bootstrap contacts peers here |
| app pods | the database or Redis | TCP 5432 (PostgreSQL) or 6379 (Redis) | Peer entries |
| app pods | cluster DNS | UDP and TCP 53 | Resolving the store's host name |
| clients, ingress, Prometheus | app pods | TCP 8080 | HTTP, Actuator health and metrics |

No access to the Kubernetes API is needed. The overlays ship these rules as two NetworkPolicies:
`spring-actor` (pod to pod, DNS, HTTP in) and `spring-actor-store` (app to store). Change the store
policy's selector, or use an `ipBlock`, to match where your database or Redis runs.

`kubectl kustomize overlays/discovery-jdbc` renders an overlay without a cluster;
`./gradlew :example:kubernetes:test` checks that every overlay renders with matching ports and a grace
period that covers the shutdown, and that the discovery overlays contain no RBAC and open only these ports.

## Rolling Updates Example

Let's see zero-downtime rolling updates in action:

### 1. Check Current State

```bash
kubectl get pods -n spring-actor
# NAME                            READY   STATUS    AGE
# spring-actor-abc123             1/1     Running   2m
# spring-actor-def456             1/1     Running   2m
# spring-actor-ghi789             1/1     Running   2m
```

### 2. Modify the Application

Edit the reply in `example/chat/src/main/java/io/github/seonwkim/example/HelloActor.java`, which
`GET /hello` returns:

```java
final String message = "v2: received from entity [" + entityId + "] on node [" + nodeAddress + "]";
```

### 3. Build New Version

```bash
cd example/chat
../../gradlew clean build -x test

# Build new Docker image
docker build -f Dockerfile.kubernetes -t spring-actor-chat:v2 .

# Load into kind
kind load docker-image spring-actor-chat:v2 --name spring-actor-demo
```

### 4. Perform Rolling Update

```bash
cd ../kubernetes

# Update image
kubectl set image deployment/spring-actor spring-actor=spring-actor-chat:v2 -n spring-actor

# Watch the rollout
kubectl rollout status deployment/spring-actor -n spring-actor

# In another terminal, watch pods
watch -n 1 'kubectl get pods -n spring-actor'
```

### What You'll See

```
Minute 0:00 - Starting rollout
  spring-actor-abc123    Running  (v1)
  spring-actor-def456    Running  (v1)
  spring-actor-ghi789    Running  (v1)

Minute 0:15 - New pod created
  spring-actor-abc123    Running  (v1)
  spring-actor-def456    Running  (v1)
  spring-actor-ghi789    Running  (v1)
  spring-actor-new123    Running  (v2)  ← New pod joins cluster!

Minute 0:30 - First old pod terminated
  spring-actor-def456    Running  (v1)
  spring-actor-ghi789    Running  (v1)
  spring-actor-new123    Running  (v2)

... continues until all pods are v2 ...

Minute 2:00 - Rollout complete
  spring-actor-new123    Running  (v2)
  spring-actor-new456    Running  (v2)
  spring-actor-new789    Running  (v2)
```

**Key Point:** At no time does the cluster size drop below 3! This is controlled by:
```yaml
strategy:
  rollingUpdate:
    maxUnavailable: 0    # Never reduce below 3
    maxSurge: 1          # Add 1 new pod before removing old
```

### 5. Verify v2 is Running

```bash
kubectl port-forward -n spring-actor svc/spring-actor-http 8080:80

# In another terminal
curl "http://localhost:8080/hello?message=hi&entityId=1"
# Output: v2: received from entity [1] on node [...]
```

## Canary and Blue-Green

A rolling update replaces pods one at a time inside one cluster. Two more ways to roll out a version:

| Strategy | What runs | Traffic | Script |
|---|---|---|---|
| Rolling update | One cluster; old and new pods side by side during the rollout | Moves pod by pod | `kubectl set image` |
| Canary | One cluster; a few new pods join the running cluster | Share = canary pods / all pods | `scripts/canary.sh` |
| Blue-green | Two separate clusters (blue and green) | Switches in one step | `scripts/blue-green.sh` |

```bash
# Canary: one pod of v3 joins the cluster and serves about 1 in 4 requests
scripts/canary.sh start spring-actor-chat:v3
scripts/canary.sh promote        # roll the main pods to v3 and remove the canary
# scripts/canary.sh abort        # or remove the canary and keep v2

# Blue-green: green is its own cluster on v4; blue keeps serving until activation
scripts/blue-green.sh deploy spring-actor-chat:v4   # 1. start green, wait for /actuator/health/warmup, then ready
scripts/blue-green.sh activate   # 2. green becomes the active generation, then the Service selects it
scripts/blue-green.sh verify     # 3. wait until the Service's endpoints are only green pods
# scripts/blue-green.sh rollback # switch back to blue
scripts/blue-green.sh retire     # 4. delete blue
```

`deploy` gates on the warm-up health group, not on `kubectl rollout status`: warm-up says the pod joined
its cluster, and it does not depend on which color gets traffic. Then it waits for the green pods to be
ready, because here the Service selector switches the colors. `activate` and `rollback` first change the
active generation through the starter's `generation` endpoint, a compare-and-set on its epoch in Redis
(`components/generation`, part of the `local` overlay: a Redis Deployment, the deployer Secret and the
`spring.actor.generation.*` settings). A competing or stale activation fails with 409 and leaves the
Service alone. See
[Deployments](../../mkdocs/docs/guides/operations/deployments.md#blue-green) for the stages.

`scripts/test-strategies.sh` runs all three under load on the kind cluster from `setup-local.sh` and fails if any request failed:

```bash
scripts/test-strategies.sh
# rolling        total=... ok=... failed=0 ok
# canary         total=... ok=... failed=0 ok
# blue-green     total=... ok=... failed=0 ok
```

The new versions there are retags of the same image, so it checks the rollout mechanics, not a change in the code. `load.sh` counts a request as ok only when the actor replied: the chat app answers `200` with `Request timed out ...` when an ask fails, so the HTTP status alone would hide lost messages.

### Changing Versions

- **Rolling update and canary mix versions in one cluster.** Old and new pods exchange messages, and a sharded entity can move from an old pod to a new one. Keep every message and every piece of entity state that crosses nodes readable by both versions: add fields as optional, don't rename or remove fields or types in the same release, and keep each type on the same serializer (`JsonSerializable` or `CborSerializable`). Make an incompatible change in two releases: first one that reads both formats, then one that writes the new format. Checked on kind with a canary of the chat app: adding a field to `SayHello` lost no requests (28,842 of 28,842), while renaming the class lost 104 of 7,105, which timed out because the old nodes could not read the new type.
- **Blue-green never mixes versions.** Green finds its peers with its own label (`app=spring-actor-green`), so the two clusters never exchange messages and the message format can change freely. In-memory state does not move: entities, singletons and topics start empty on green. Keep state that must survive the switch in a database or event journal both clusters read.
- **Rollback.** A canary is removed with `canary.sh abort`; until `blue-green.sh retire`, `blue-green.sh rollback` sends traffic back to blue, which still has its state.

## Testing Cluster Resilience

### Simulate Pod Failure

```bash
# Delete a pod
kubectl delete pod <pod-name> -n spring-actor

# Watch it restart and rejoin
kubectl get pods -n spring-actor -w
```

The cluster automatically:
1. Detects the failure
2. Kubernetes restarts the pod
3. New pod rejoins the cluster
4. Cluster rebalances sharded entities

### Simulate Network Partition

```bash
# Block network to a pod
kubectl exec -n spring-actor <pod-name> -- iptables -A INPUT -j DROP

# Wait 20 seconds for Split Brain Resolver to activate
# The pod will be downed and removed from the cluster

# Restore network
kubectl delete pod <pod-name> -n spring-actor

# New pod will rejoin cleanly
```

## Monitoring

With the Prometheus Operator in the cluster (for example
[kube-prometheus-stack](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack)),
one command installs the actor monitoring:

```bash
./scripts/monitoring.sh          # apply; `render` prints the manifests, `delete` removes them
```

It applies the `monitoring/` kustomization: the ServiceMonitor that scrapes `/actuator/prometheus`, the
`actor-alerts` PrometheusRule and the `Actors` Grafana dashboard as a ConfigMap labelled
`grafana_dashboard: "1"`, which Grafana's dashboard sidecar loads. The rules and the dashboard come from the
repository's [`monitoring/`](../../monitoring) directory. Your Prometheus must select them: kube-prometheus-stack
selects rules and monitors by its release label unless you set
`prometheus.prometheusSpec.ruleSelectorNilUsesHelmValues=false` and
`serviceMonitorSelectorNilUsesHelmValues=false`, and its sidecar searches other namespaces only with
`grafana.sidecar.dashboards.searchNamespace=ALL`.

## Directory Structure

```
example/kubernetes/
├── README.md                   # This file
├── setup-local.sh              # ⭐ Run this to start!
├── cleanup-local.sh            # Cleanup everything
│
├── base/                       # Base Kubernetes manifests
│   ├── namespace.yaml
│   ├── rbac.yaml
│   ├── configmap.yaml
│   ├── service.yaml
│   ├── deployment.yaml
│   ├── servicemonitor.yaml
│   ├── podmonitor.yaml
│   ├── networkpolicy.yaml
│   └── kustomization.yaml
│
├── monitoring/                 # ServiceMonitor, actor alert rules, Grafana dashboard
│   └── kustomization.yaml      #   (Prometheus Operator; see "Monitoring" below)
│
├── overlays/                   # Environment-specific configs
│   ├── local/                  # ⭐ Used by setup-local.sh
│   ├── dev/
│   ├── prod/
│   ├── discovery-jdbc/         # No RBAC: peers through PostgreSQL
│   └── discovery-redis/        # No RBAC: peers through Redis
│
├── components/discovery/       # Shared by the discovery overlays: no RBAC, NetworkPolicy, ports
├── components/generation/      # Blue-green activation: Redis, the deployer Secret, spring.actor.generation.*
│
└── scripts/                    # Advanced deployment scripts
    ├── deploy.sh
    ├── rollout.sh
    ├── debug.sh
    ├── load.sh                 # Requests under load; counts failures
    ├── monitoring.sh           # Installs the monitoring/ kustomization
    ├── canary.sh               # Canary: start, promote, abort
    ├── blue-green.sh           # Blue-green: deploy, activate, verify, retire, rollback
    └── test-strategies.sh      # Runs all three strategies under load on kind
```

## Troubleshooting

### Pods Not Starting

```bash
# Check pod events
kubectl describe pod <pod-name> -n spring-actor

# Check logs
kubectl logs <pod-name> -n spring-actor
```

**Common issues:**
- Image not loaded into kind: `kind load docker-image spring-actor-chat:local --name spring-actor-demo`
- Insufficient resources: Increase Docker memory/CPU limits

### Cluster Not Forming

```bash
# Check RBAC permissions
kubectl auth can-i list pods --as=system:serviceaccount:spring-actor:spring-actor-sa -n spring-actor

# Check service discovery
kubectl exec -n spring-actor <pod-name> -- curl localhost:7626/bootstrap/seed-nodes
```

**Common issues:**
- RBAC not configured: Run `kubectl apply -k overlays/local` again
- Pods not ready: Wait 60 seconds for startup

### Can't Access Application

```bash
# Check services
kubectl get svc -n spring-actor

# Check endpoints
kubectl get endpoints -n spring-actor

# Manual port forward
kubectl port-forward -n spring-actor svc/spring-actor-http 8080:80
```

## Advanced: Deploy to Real Cluster

To deploy to a real Kubernetes cluster:

### 1. Build and Push to Registry

```bash
# Build application
./gradlew clean build

# Build and push Docker image
docker build -t your-registry.com/spring-actor-chat:v1.0.0 -f example/chat/Dockerfile.kubernetes example/chat
docker push your-registry.com/spring-actor-chat:v1.0.0
```

### 2. Update Image Reference

Edit `base/kustomization.yaml`:

```yaml
images:
- name: your-registry/spring-actor-app
  newName: your-registry.com/spring-actor-chat
  newTag: v1.0.0
```

### 3. Deploy

```bash
# For development
./scripts/deploy.sh dev v1.0.0

# For production
./scripts/deploy.sh prod v1.0.0
```

See the [Production guide](https://seonwkim.github.io/actor-spring-boot-starter/guides/operations/production/)
and the [Deployments guide](https://seonwkim.github.io/actor-spring-boot-starter/guides/operations/deployments/)
before deploying to production.

## What You Learned

✅ **How to deploy Pekko Cluster to Kubernetes**
✅ **Automatic cluster formation with Kubernetes service discovery**
✅ **Zero-downtime rolling updates**
✅ **Health checks and monitoring**
✅ **Cluster resilience and self-healing**

## Next Steps

1. **Customize the example** - Modify the chat application
2. **Try rolling updates** - Follow the guide above
3. **Add monitoring** - Run `./scripts/monitoring.sh` (see [Monitoring](#monitoring))
4. **Production deployment** - Use `overlays/prod` configuration
5. **Read the deep dive** - Check the [Production guide](https://seonwkim.github.io/actor-spring-boot-starter/guides/operations/production/)

## Useful Commands Reference

```bash
# View pods
kubectl get pods -n spring-actor

# View detailed pod info
kubectl describe pod <pod-name> -n spring-actor

# View logs
kubectl logs -f <pod-name> -n spring-actor

# View all pod logs
kubectl logs -f -l app=spring-actor -n spring-actor

# Check cluster members
kubectl exec -n spring-actor <pod-name> -- curl -s localhost:7626/cluster/members | jq

# Check cluster shards
kubectl exec -n spring-actor <pod-name> -- curl -s localhost:7626/cluster/shards | jq

# Port forward to pod
kubectl port-forward -n spring-actor <pod-name> 8080:8080

# Port forward to service
kubectl port-forward -n spring-actor svc/spring-actor-http 8080:80

# Execute command in pod
kubectl exec -it -n spring-actor <pod-name> -- /bin/sh

# Scale deployment
kubectl scale deployment spring-actor -n spring-actor --replicas=5

# Rolling update
kubectl set image deployment/spring-actor spring-actor=spring-actor-chat:v2 -n spring-actor

# Rollback
kubectl rollout undo deployment/spring-actor -n spring-actor

# Restart deployment
kubectl rollout restart deployment/spring-actor -n spring-actor

# Debug deployment
./scripts/debug.sh spring-actor
```

## Support

- **Issues**: Report bugs or ask questions in GitHub Issues
- **Documentation**: See the [Kubernetes example page](https://seonwkim.github.io/actor-spring-boot-starter/examples/kubernetes/)
- **Community**: Join discussions in GitHub Discussions

---

Happy clustering! 🚀
