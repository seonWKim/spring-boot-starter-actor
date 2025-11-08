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
│  │  │ :2551        │  │ :2551        │  │ :2551        │  │ │
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

3. **Use the built-in commands:**

The setup script provides several useful commands:

```bash
# Show cluster status
./setup-local.sh status

# View application logs
./setup-local.sh logs

# Access individual pods on different ports (8080, 8081, 8082)
./setup-local.sh port-forward

# Rebuild application and restart deployment
./setup-local.sh rebuild

# Clean up all resources
./setup-local.sh cleanup
```

4. **Get help:**

```bash
./setup-local.sh help
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

3. **Pod contacts peers** on management port (8558):
   ```
   HTTP GET http://10.244.0.5:8558/bootstrap/seed-nodes
   HTTP GET http://10.244.0.6:8558/bootstrap/seed-nodes
   HTTP GET http://10.244.0.7:8558/bootstrap/seed-nodes
   ```

4. **Cluster Bootstrap logic:**
   - If cluster exists → Join it
   - If no cluster exists → Node with lowest address forms cluster

No hardcoded seed nodes needed! 🎉

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

Edit `example/chat/src/main/java/io/github/seonwkim/example/HelloController.java`:

```java
@GetMapping("/api/hello")
public String hello() {
    return "Hello from v2!";  // Changed from v1
}
```

### 3. Build New Version

```bash
cd example/chat
../../gradlew clean build -x test

# Build new Docker image
docker build -t spring-actor-chat:v2 .

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
curl http://localhost:8080/api/hello
# Output: Hello from v2!
```

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

## Directory Structure

```
example/kubernetes/
├── README.md                   # This file
├── setup-local.sh              # ⭐ All-in-one script for local development
├── cleanup-local.sh            # Cleanup script (or use: ./setup-local.sh cleanup)
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
├── overlays/                   # Environment-specific configs
│   ├── local/                  # ⭐ Used by setup-local.sh
│   ├── dev/
│   └── prod/
│
└── scripts/                    # Internal helper scripts (used by setup-local.sh)
    ├── kind-config.yaml
    ├── check-prerequisites.sh
    ├── build-local.sh
    ├── port-forward-pods.sh
    ├── deploy.sh
    ├── rollout.sh
    └── debug.sh
```

## setup-local.sh Commands

The `setup-local.sh` script is your main entry point for local Kubernetes development. It provides the following commands:

```bash
./setup-local.sh [command]
```

**Available Commands:**

| Command | Description |
|---------|-------------|
| `setup` | Set up the local Kubernetes cluster (default) |
| `status` | Show cluster and pod status with Pekko cluster info |
| `logs` | Stream logs from all pods |
| `port-forward` | Set up port forwarding to individual pods (8080, 8081, 8082) |
| `rebuild` | Rebuild application and restart deployment |
| `cleanup` | Clean up all resources |
| `help` | Show help message |

**Examples:**

```bash
# Initial setup (default command)
./setup-local.sh
# or explicitly
./setup-local.sh setup

# Check status anytime
./setup-local.sh status

# View real-time logs
./setup-local.sh logs

# Access individual pods for testing
./setup-local.sh port-forward
# Then open: http://localhost:8080, http://localhost:8081, http://localhost:8082

# After making code changes
./setup-local.sh rebuild

# Clean up everything
./setup-local.sh cleanup
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
kubectl exec -n spring-actor <pod-name> -- curl localhost:8558/bootstrap/seed-nodes
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
docker build -t your-registry.com/spring-actor-chat:v1.0.0 -f example/chat/Dockerfile .
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

See [KUBERNETES_PREPARE.md](../../KUBERNETES_PREPARE.md) for comprehensive production deployment guide.

## What You Learned

✅ **How to deploy Pekko Cluster to Kubernetes**
✅ **Automatic cluster formation with Kubernetes service discovery**
✅ **Zero-downtime rolling updates**
✅ **Health checks and monitoring**
✅ **Cluster resilience and self-healing**

## Next Steps

1. **Customize the example** - Modify the chat application
2. **Try rolling updates** - Follow the guide above
3. **Add monitoring** - Set up Prometheus/Grafana
4. **Production deployment** - Use `overlays/prod` configuration
5. **Read the deep dive** - Check [KUBERNETES_PREPARE.md](../../KUBERNETES_PREPARE.md)

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
kubectl exec -n spring-actor <pod-name> -- curl -s localhost:8558/cluster/members | jq

# Check cluster shards
kubectl exec -n spring-actor <pod-name> -- curl -s localhost:8558/cluster/shards | jq

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
- **Documentation**: See [KUBERNETES_PREPARE.md](../../KUBERNETES_PREPARE.md) for detailed guide
- **Community**: Join discussions in GitHub Discussions

---

Happy clustering! 🚀
