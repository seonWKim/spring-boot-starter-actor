# Kubernetes Deployment Example

Deploy the Pekko-based distributed chat application to Kubernetes with cluster formation and zero-downtime rolling updates.

## Directory Structure

```
example/kubernetes/
├── base/                       # Base Kubernetes manifests
│   ├── namespace.yaml          # Namespace for the application
│   ├── rbac.yaml              # ServiceAccount, Role, RoleBinding for cluster discovery
│   ├── configmap.yaml         # Pekko configuration
│   ├── service.yaml           # Headless service for cluster discovery
│   └── deployment.yaml        # Application deployment
│
├── overlays/                   # Environment-specific configurations
│   └── prod/                   # Production configuration
│       ├── kustomization.yaml
│       ├── deployment-patch.yaml
│       └── hpa.yaml           # Horizontal Pod Autoscaler
│
└── scripts/                    # Local development helpers
    ├── setup-local.sh         # Setup local kind cluster
    ├── cleanup-local.sh       # Cleanup local resources
    └── ...
```

## Essential Files for Production

### Minimum Required Files

To deploy the Pekko application to your production Kubernetes cluster, you need these **5 core files**:

1. **`namespace.yaml`** - Isolates the application in its own namespace
2. **`rbac.yaml`** - Grants permissions for Kubernetes API access (required for Pekko cluster bootstrap)
3. **`configmap.yaml`** - Contains Pekko configuration for clustering and discovery
4. **`service.yaml`** - Headless service for pod discovery and cluster formation
5. **`deployment.yaml`** - Defines pods, replicas, health checks, and rolling update strategy

### What Each File Does

#### 1. namespace.yaml
Creates an isolated namespace for your application.

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: spring-actor
```

#### 2. rbac.yaml
Provides permissions for the application to query the Kubernetes API to discover other pods in the cluster. This is **required** for Pekko cluster bootstrap.

Key components:
- **ServiceAccount**: Identity for the pods
- **Role**: Permissions to read pods and endpoints
- **RoleBinding**: Links the ServiceAccount to the Role

#### 3. configmap.yaml
Contains the Pekko configuration including:
- **Cluster settings**: Split-brain resolver, coordinated shutdown
- **Discovery method**: `kubernetes-api` for finding other pods
- **Management HTTP**: Health check and cluster membership endpoints
- **Remote Artery**: Configuration for inter-pod communication

Key settings to customize:
```yaml
pekko.cluster.bootstrap.contact-point-discovery:
  service-name: spring-actor          # Must match your service name
  required-contact-point-nr: 2        # Minimum pods for cluster formation
```

#### 4. service.yaml
Headless service (ClusterIP: None) that enables:
- Pod-to-pod discovery via Kubernetes DNS
- Pekko cluster bootstrap to find initial contact points

Key ports:
- **80**: HTTP traffic
- **2551**: Pekko remoting (inter-pod communication)
- **8558**: Management/health checks

#### 5. deployment.yaml
Defines how the application runs:

**Important configurations:**
- **Replicas**: Number of pods (minimum 3 for split-brain resolver)
- **Rolling update strategy**: `maxSurge: 1, maxUnavailable: 0` for zero-downtime
- **Environment variables**: `POD_IP` and `NAMESPACE` for Pekko configuration
- **Health checks**: Liveness and readiness probes using Spring Boot Actuator
- **Resource limits**: CPU and memory constraints

## Deploying to Production

### Prerequisites

1. A running Kubernetes cluster (EKS, GKE, AKS, or on-prem)
2. `kubectl` configured to access your cluster
3. Your application Docker image pushed to a registry accessible by your cluster

### Step 1: Build and Push Your Image

```bash
# Navigate to the chat example
cd example/chat

# Build the JAR
./gradlew bootJar

# Build Docker image
docker build -t your-registry.io/spring-actor-app:v1.0.0 -f Dockerfile.kubernetes .

# Push to your registry
docker push your-registry.io/spring-actor-app:v1.0.0
```

### Step 2: Update Image Reference

Edit `overlays/prod/kustomization.yaml`:

```yaml
images:
- name: your-registry/spring-actor-app
  newName: your-registry.io/spring-actor-app
  newTag: v1.0.0
```

### Step 3: Deploy to Kubernetes

```bash
# Deploy using the production overlay
kubectl apply -k overlays/prod

# Or deploy using base configuration
kubectl apply -k base
```

### Step 4: Verify Deployment

```bash
# Check pod status
kubectl get pods -n spring-actor

# Check if pods are ready
kubectl rollout status deployment/spring-actor-prod -n spring-actor

# Check cluster formation (from any pod)
kubectl exec -n spring-actor <pod-name> -- curl localhost:8558/cluster/members | jq

# View logs
kubectl logs -f <pod-name> -n spring-actor
```

## Key Configuration Details

### Pekko Cluster Bootstrap

The application uses **Kubernetes API** discovery method:

1. Pods query the Kubernetes API to find other pods with label `app=spring-actor`
2. They use the management port (8558) to exchange cluster membership info
3. Once `required-contact-point-nr` pods are found, the cluster forms
4. New pods automatically join the existing cluster

### Health Checks

**Liveness Probe**: `/actuator/health/liveness`
- Determines if the pod should be restarted
- Fails if the JVM is deadlocked or unresponsive

**Readiness Probe**: `/actuator/health/readiness`
- Determines if the pod should receive traffic
- Fails if dependencies are unavailable

### Rolling Updates (Zero Downtime)

The deployment uses this strategy:
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1           # Allow 1 extra pod during update
    maxUnavailable: 0     # Never reduce below desired count
```

**Update flow:**
1. New pod is created (cluster size: 4)
2. New pod becomes ready and joins cluster
3. Old pod receives SIGTERM and gracefully leaves cluster
4. Old pod terminates
5. Repeat until all pods are updated

**Cluster size never drops below the desired count!**

### Graceful Shutdown

When a pod receives SIGTERM:
1. Pekko coordinated shutdown begins
2. Pod leaves the cluster
3. Cluster sharding regions migrate
4. Pod stops accepting new requests
5. Existing requests complete
6. Pod terminates

Configured timeouts in `configmap.yaml`:
```yaml
coordinated-shutdown:
  phases:
    cluster-leave:
      timeout: 10s
    cluster-sharding-shutdown-region:
      timeout: 10s
    actor-system-terminate:
      timeout: 10s
```

## Production Overlay Features

The `overlays/prod/` configuration adds:

- **Higher replica count**: 5 pods instead of 3
- **Horizontal Pod Autoscaler (HPA)**: Auto-scales based on CPU (50-80% utilization)
- **Increased resource limits**: More CPU and memory
- **Production environment variables**

## Customization Guide

### Changing Replica Count

Edit `overlays/prod/kustomization.yaml`:
```yaml
replicas:
- name: spring-actor
  count: 5  # Change this
```

### Updating Pekko Configuration

Edit `base/configmap.yaml` to customize:
- Split-brain resolver strategy
- Cluster bootstrap timeouts
- Discovery method
- Coordinated shutdown timeouts

### Adding Your Own ConfigMap Values

Use `configMapGenerator` in your overlay:
```yaml
configMapGenerator:
- name: spring-actor-config
  behavior: merge
  literals:
  - MY_CUSTOM_VAR=value
```

## Local Development with Kind

For local testing with a multi-node Kubernetes cluster:

```bash
# Quick setup (creates kind cluster, builds image, deploys)
./setup-local.sh

# View status
./setup-local.sh status

# View logs
./setup-local.sh logs

# Rebuild after code changes
./setup-local.sh rebuild

# Clean up
./setup-local.sh cleanup
```

Access the application at: **http://localhost:8080**

## Useful Production Commands

```bash
# View pods across all nodes
kubectl get pods -n spring-actor -o wide

# Check cluster members from a pod
kubectl exec -n spring-actor <pod-name> -- curl localhost:8558/cluster/members | jq

# Scale deployment
kubectl scale deployment/spring-actor-prod -n spring-actor --replicas=7

# Restart deployment (triggers rolling update)
kubectl rollout restart deployment/spring-actor-prod -n spring-actor

# Check rollout status
kubectl rollout status deployment/spring-actor-prod -n spring-actor

# Rollback to previous version
kubectl rollout undo deployment/spring-actor-prod -n spring-actor

# View rollout history
kubectl rollout history deployment/spring-actor-prod -n spring-actor

# Describe pod to see events
kubectl describe pod <pod-name> -n spring-actor
```

## Monitoring Your Application

The application exposes Prometheus metrics at `/actuator/prometheus`.

To integrate with your existing Prometheus setup, configure it to scrape:
```yaml
- job_name: 'spring-actor'
  kubernetes_sd_configs:
  - role: pod
    namespaces:
      names:
      - spring-actor
  relabel_configs:
  - source_labels: [__meta_kubernetes_pod_label_app]
    action: keep
    regex: spring-actor
  - source_labels: [__meta_kubernetes_pod_ip]
    action: replace
    target_label: __address__
    replacement: ${1}:8080
```

## Troubleshooting

### Pods Not Starting

```bash
# Check pod events
kubectl describe pod <pod-name> -n spring-actor

# Check logs
kubectl logs <pod-name> -n spring-actor

# Common issues:
# - Image pull errors: Verify image name and registry access
# - Resource limits: Check cluster has enough CPU/memory
# - RBAC issues: Verify ServiceAccount and Role are created
```

### Cluster Not Forming

```bash
# Check if pods can reach each other
kubectl exec -n spring-actor <pod-name> -- curl http://<another-pod-ip>:8558/cluster/members

# Check RBAC permissions
kubectl auth can-i list pods --as=system:serviceaccount:spring-actor:spring-actor -n spring-actor

# Check service endpoints
kubectl get endpoints spring-actor -n spring-actor

# Common issues:
# - Wait 30-60 seconds for bootstrap to complete
# - Verify required-contact-point-nr is set correctly (must be ≤ replica count)
# - Check network policies aren't blocking pod-to-pod traffic
```

### Rolling Update Issues

```bash
# Check rollout status
kubectl rollout status deployment/spring-actor-prod -n spring-actor

# View rollout history
kubectl rollout history deployment/spring-actor-prod -n spring-actor

# Pause rollout
kubectl rollout pause deployment/spring-actor-prod -n spring-actor

# Resume rollout
kubectl rollout resume deployment/spring-actor-prod -n spring-actor

# Rollback
kubectl rollout undo deployment/spring-actor-prod -n spring-actor
```

## What You Get

✅ **Production-ready Pekko cluster** on Kubernetes
✅ **Automatic cluster formation** using Kubernetes service discovery
✅ **Zero-downtime rolling updates** with graceful shutdown
✅ **Health checks** using Spring Boot Actuator
✅ **Horizontal Pod Autoscaling** in production overlay
✅ **RBAC** configured for least-privilege access
✅ **Split-brain resolver** for network partition handling

## Additional Resources

- **Pekko Cluster**: https://pekko.apache.org/docs/pekko/current/typed/cluster.html
- **Pekko Kubernetes Discovery**: https://pekko.apache.org/docs/pekko-management/current/discovery/kubernetes.html
- **Kubernetes Rolling Updates**: https://kubernetes.io/docs/tutorials/kubernetes-basics/update/update-intro/
- **Project README**: [../../README.md](../../README.md)

---

Need help? [Open an issue](https://github.com/seonwkim/spring-boot-starter-actor/issues)
