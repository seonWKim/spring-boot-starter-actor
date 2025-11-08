# Kubernetes Deployment Example

Deploy a Spring Boot Starter Actor application to Kubernetes with Pekko clustering, zero-downtime rolling updates, and Grafana monitoring.

## Quick Start (5 minutes)

### Prerequisites

Install these tools (one-time setup):

- **Docker** - [Install Docker](https://docs.docker.com/get-docker/)
- **kind** - [Install kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
- **kubectl** - [Install kubectl](https://kubernetes.io/docs/tasks/tools/)

**Important**: Docker Desktop needs **at least 8 GB of memory** allocated (Settings → Resources → Memory)

### Setup

```bash
cd example/kubernetes
./setup-local.sh
```

That's it! The script will:
- Create a local Kubernetes cluster (3 nodes)
- Build the Spring Boot application
- Build and load Docker image
- Deploy to Kubernetes (3 pods)
- Wait for pods to be ready

Access the application: **http://localhost:8080**

## Commands

The `./setup-local.sh` script provides everything you need:

| Command | Description |
|---------|-------------|
| `./setup-local.sh` | Set up cluster (default command) |
| `./setup-local.sh monitoring` | Deploy Grafana monitoring |
| `./setup-local.sh status` | Show cluster status |
| `./setup-local.sh logs` | View application logs |
| `./setup-local.sh port-forward` | Forward pods & monitoring (8080-8082, 30300, 30090) |
| `./setup-local.sh rebuild` | Rebuild app and restart deployment |
| `./setup-local.sh cleanup` | Remove everything |
| `./setup-local.sh help` | Show help |

### Examples

```bash
# Initial setup
./setup-local.sh

# Deploy monitoring to visualize rolling updates
./setup-local.sh monitoring

# Check cluster status
./setup-local.sh status

# View logs from all pods
./setup-local.sh logs

# Access individual pods and monitoring
./setup-local.sh port-forward
# Application: http://localhost:8080, 8081, 8082
# Monitoring: http://localhost:30300 (Grafana), http://localhost:30090 (Prometheus)

# After making code changes
./setup-local.sh rebuild

# Clean up everything
./setup-local.sh cleanup
```

## Monitoring with Grafana

Monitor your Pekko cluster and rolling updates in real-time:

```bash
# Deploy monitoring stack
./setup-local.sh monitoring

# Access Grafana
open http://localhost:30300
# Login: admin/admin
```

### Pre-configured Dashboards

**1. Pekko Cluster Health**
- Cluster members (Up/Unreachable)
- Shard distribution
- Active entities
- HTTP request metrics

**2. Rolling Update Monitor**
- Pod lifecycle tracking
- Cluster size during updates
- Resource usage
- Deployment events

### Watch a Rolling Update

```bash
# 1. Deploy monitoring
./setup-local.sh monitoring

# 2. Open Grafana "Rolling Update Monitor" dashboard
open http://localhost:30300

# 3. Trigger a rolling update
./setup-local.sh rebuild

# 4. Watch the dashboard show:
#    - Pod count increases (maxSurge: 1)
#    - Old pods gracefully shutdown
#    - New pods join cluster
#    - Cluster size stays ≥ 3 (zero downtime!)
```

**Access Points:**
- **Grafana**: http://localhost:30300 (admin/admin)
- **Prometheus**: http://localhost:30090
- **Application**: http://localhost:8080

## Testing Rolling Updates

### Modify the Application

Edit `example/chat/src/main/java/io/github/seonwkim/example/HelloController.java`:

```java
@GetMapping("/api/hello")
public String hello() {
    return "Hello from v2!";  // Change this
}
```

### Deploy the Update

```bash
./setup-local.sh rebuild
```

Watch in Grafana or terminal as:
1. New pod starts and joins cluster (4 pods total)
2. First old pod leaves and terminates (back to 3 pods)
3. Process repeats until all pods are updated
4. **Cluster size never drops below 3** = zero downtime!

### Verify the Update

```bash
curl http://localhost:8080/api/hello
# Output: Hello from v2!
```

## Troubleshooting

### Pods Not Starting

```bash
# Check pod status
kubectl describe pod <pod-name> -n spring-actor

# Check logs
kubectl logs <pod-name> -n spring-actor
```

**Common issues:**
- Image not loaded: `kind load docker-image spring-actor-chat:local --name spring-actor-demo`
- Not enough memory: Increase Docker memory to 8 GB

### Cluster Not Forming

```bash
# Check cluster members
kubectl exec -n spring-actor <pod-name> -- curl localhost:8558/cluster/members | jq
```

**Common issues:**
- Wait 30-60 seconds for cluster to form
- Check RBAC: `kubectl get serviceaccount -n spring-actor`

### Can't Access Application

```bash
# Check services
kubectl get svc -n spring-actor

# Manual port forward
kubectl port-forward -n spring-actor svc/spring-actor-http 8080:80
```

## Useful Commands

```bash
# View pods
kubectl get pods -n spring-actor

# View pod details
kubectl describe pod <pod-name> -n spring-actor

# View logs
kubectl logs -f <pod-name> -n spring-actor

# Check cluster members
kubectl exec -n spring-actor <pod-name> -- curl localhost:8558/cluster/members | jq

# Scale deployment
kubectl scale deployment spring-actor -n spring-actor --replicas=5

# Restart deployment
kubectl rollout restart deployment/spring-actor -n spring-actor

# Rollback
kubectl rollout undo deployment/spring-actor -n spring-actor
```

## Directory Structure

```
example/kubernetes/
├── setup-local.sh              # ⭐ Main script - run this!
├── cleanup-local.sh            # Cleanup script
│
├── base/                       # Base Kubernetes manifests
│   ├── namespace.yaml
│   ├── rbac.yaml
│   ├── configmap.yaml
│   ├── service.yaml
│   ├── deployment.yaml
│   └── ...
│
├── overlays/                   # Environment-specific configs
│   ├── local/                  # ⭐ Local development config
│   ├── dev/
│   └── prod/
│
├── monitoring/                 # Grafana monitoring stack
│   ├── prometheus.yaml
│   ├── grafana.yaml
│   ├── dashboards.yaml
│   └── ...
│
└── scripts/                    # Internal helper scripts
    ├── kind-config.yaml
    ├── check-prerequisites.sh
    ├── build-local.sh
    └── ...
```

## What You Get

✅ **3-node Kubernetes cluster** running locally with kind
✅ **3 Spring Boot pods** forming a Pekko cluster
✅ **Zero-downtime rolling updates** with maxSurge strategy
✅ **Automatic cluster formation** using Kubernetes service discovery
✅ **Health checks** and readiness probes
✅ **Grafana dashboards** for monitoring
✅ **Prometheus metrics** from Spring Boot Actuator and Pekko Management

## Production Deployment

For deploying to a real Kubernetes cluster, we provide comprehensive production guides:

### 📚 Documentation

- **[Production Deployment Guide](PRODUCTION_GUIDE.md)** - Complete guide for production deployment
  - Prerequisites and dependencies
  - Build and deployment process
  - Configuration management
  - Deployment strategies (rolling, blue/green, canary)
  - Monitoring and alerting setup
  - High availability configuration
  - Security best practices
  - Disaster recovery procedures

- **[Operations Runbook](OPERATIONS_RUNBOOK.md)** - Day-to-day operations guide
  - Daily health checks
  - Common operational tasks
  - Incident response procedures
  - Maintenance windows
  - Performance tuning
  - Capacity planning

- **[CI/CD Pipeline Guide](CICD_GUIDE.md)** - Automated deployment pipelines
  - GitHub Actions workflows
  - GitLab CI/CD pipelines
  - Jenkins pipelines
  - ArgoCD GitOps setup
  - Best practices and examples

- **[Secrets Management](SECRETS_MANAGEMENT.md)** - Secure secrets handling
  - Kubernetes secrets
  - External Secrets Operator
  - HashiCorp Vault integration
  - Cloud provider secrets (AWS, GCP, Azure)
  - Best practices and security

- **[Cost Optimization](COST_OPTIMIZATION.md)** - Reduce infrastructure costs
  - Resource right-sizing
  - Autoscaling strategies
  - Spot/preemptible instances
  - Storage optimization
  - Cost monitoring and budgets

- **[Log Aggregation](LOG_AGGREGATION.md)** - Centralized logging
  - ELK Stack (Elasticsearch, Logstash, Kibana)
  - Loki with Grafana
  - Fluent Bit configuration
  - Cloud provider logging
  - Best practices and troubleshooting

### 🚀 Production Features

The production configuration includes:

✅ **High Availability**
- Pod anti-affinity for node distribution
- PodDisruptionBudget (minimum 2 pods always available)
- Multi-zone deployment support
- Graceful shutdown with 30s grace period

✅ **Resource Management**
- ResourceQuota for namespace limits
- LimitRange for default resource constraints
- Optimized JVM settings for containers
- No CPU limits (prevents Pekko throttling)

✅ **Security**
- Network policies for traffic control
- Non-root container execution
- Read-only root filesystem
- Security contexts and capabilities
- TLS/SSL termination via Ingress

✅ **Monitoring & Alerting**
- Prometheus metrics collection
- 15+ production-ready alert rules
- Pre-configured Grafana dashboards
- Health checks (startup, liveness, readiness)

✅ **Scaling**
- Horizontal Pod Autoscaling (HPA)
- Cluster autoscaling ready
- Schedule-based scaling examples
- Custom metrics support

✅ **Deployment Strategies**
- Zero-downtime rolling updates (maxSurge: 1, maxUnavailable: 0)
- Blue/green deployment guide
- Canary deployment examples
- Automated rollback capabilities

### Quick Production Deploy

```bash
# 1. Build and push image
docker build -t your-registry.io/spring-actor-app:v1.0.0 -f example/chat/Dockerfile.kubernetes example/chat
docker push your-registry.io/spring-actor-app:v1.0.0

# 2. Update image in prod overlay
cd example/kubernetes/overlays/prod
vim kustomization.yaml  # Update newTag

# 3. Deploy
kubectl apply -k example/kubernetes/overlays/prod

# 4. Verify
kubectl rollout status deployment/spring-actor-prod -n spring-actor
```

For detailed instructions, see [Production Deployment Guide](PRODUCTION_GUIDE.md).

## Support

- **Issues**: Report bugs at [GitHub Issues](https://github.com/seonwkim/spring-boot-starter-actor/issues)
- **Documentation**: See [KUBERNETES_PREPARE.md](../../KUBERNETES_PREPARE.md) for detailed guide

---

Happy clustering! 🚀
