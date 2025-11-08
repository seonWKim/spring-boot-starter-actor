# Production Deployment Guide

This guide covers deploying Spring Boot Starter Actor applications to production Kubernetes clusters.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Configuration](#configuration)
4. [Deployment Strategies](#deployment-strategies)
5. [Monitoring & Alerting](#monitoring--alerting)
6. [Scaling](#scaling)
7. [High Availability](#high-availability)
8. [Security](#security)
9. [Disaster Recovery](#disaster-recovery)
10. [Troubleshooting](#troubleshooting)

## Prerequisites

### Infrastructure

- **Kubernetes Cluster**: v1.24+ (EKS, GKE, AKS, or on-premises)
- **kubectl**: v1.24+ configured with cluster access
- **kustomize**: v4.5+ (or use `kubectl apply -k`)
- **helm**: v3+ (optional, for dependencies)

### Resource Requirements

**Minimum per pod:**
- Memory: 1 GiB (request), 2 GiB (limit)
- CPU: 500m (request), no limit recommended for Pekko
- Storage: 10 GiB for logs, 20 GiB for heap dumps (optional)

**Recommended cluster:**
- Nodes: 3+ (for HA)
- Total Memory: 12+ GiB
- Total CPU: 6+ cores
- Storage: Network-attached (EBS, GCE PD, Azure Disk)

### Dependencies

Install these in your cluster before deploying:

```bash
# Ingress Controller (choose one)
helm install nginx-ingress ingress-nginx/ingress-nginx --namespace ingress-nginx --create-namespace

# Certificate Manager (for TLS)
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml

# Metrics Server (for HPA)
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Prometheus Operator (optional, for ServiceMonitor/PodMonitor)
helm install prometheus-operator prometheus-community/kube-prometheus-stack --namespace monitoring --create-namespace
```

## Quick Start

### 1. Build and Push Docker Image

```bash
# Build the application
cd example/chat
./gradlew bootJar

# Build Docker image
docker build -t your-registry.io/spring-actor-app:v1.0.0 -f Dockerfile.kubernetes .

# Push to registry
docker push your-registry.io/spring-actor-app:v1.0.0
```

### 2. Configure for Your Environment

Edit `overlays/prod/kustomization.yaml`:

```yaml
images:
- name: your-registry/spring-actor-app
  newName: your-registry.io/spring-actor-app
  newTag: v1.0.0
```

Edit `overlays/prod/deployment-patch.yaml` for your resource needs.

### 3. Deploy to Production

```bash
cd example/kubernetes

# Dry run to verify
kubectl apply -k overlays/prod --dry-run=client

# Deploy
kubectl apply -k overlays/prod

# Wait for rollout
kubectl rollout status deployment/spring-actor-prod -n spring-actor
```

### 4. Verify Deployment

```bash
# Check pods
kubectl get pods -n spring-actor

# Check cluster members
kubectl exec -n spring-actor spring-actor-prod-xxxxx -- \
  curl -s localhost:8558/cluster/members | jq

# Check health
kubectl exec -n spring-actor spring-actor-prod-xxxxx -- \
  curl -s localhost:8080/actuator/health | jq
```

## Configuration

### Environment-Specific Overlays

The project uses Kustomize overlays for environment-specific configuration:

```
overlays/
├── local/      # Local development (kind)
├── dev/        # Development cluster
└── prod/       # Production cluster
```

### Key Configuration Files

1. **base/deployment.yaml**: Base deployment with all production features
2. **base/configmap.yaml**: Application configuration
3. **overlays/prod/deployment-patch.yaml**: Production-specific overrides
4. **overlays/prod/kustomization.yaml**: Production environment config

### Configuring Resources

Edit resource limits in `overlays/prod/deployment-patch.yaml`:

```yaml
resources:
  requests:
    memory: "2Gi"
    cpu: "1000m"
  limits:
    memory: "4Gi"
    # No CPU limit for Pekko workloads
```

### Configuring Replicas

For production, run at least 3 replicas for HA:

```yaml
# In overlays/prod/kustomization.yaml
replicas:
- name: spring-actor
  count: 5  # Adjust based on load
```

### Configuring Pekko Cluster

The minimum number of contact points for cluster formation:

```yaml
# In base/deployment.yaml
env:
- name: JAVA_OPTS
  value: >-
    -Dpekko.management.cluster.bootstrap.contact-point-discovery.required-contact-point-nr=3
```

Set this to `(N/2) + 1` where N is your replica count.

## Deployment Strategies

### Rolling Updates (Default)

Zero-downtime rolling updates are configured by default:

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 0    # Never reduce cluster size
    maxSurge: 1          # Add 1 pod before removing old ones
```

**How it works:**
1. New pod starts and joins cluster (4 total pods)
2. New pod becomes ready
3. Old pod receives SIGTERM and leaves cluster
4. Old pod terminates
5. Repeat for remaining pods

**Trigger a rolling update:**
```bash
# Change image tag
kubectl set image deployment/spring-actor-prod \
  spring-actor=your-registry.io/spring-actor-app:v1.0.1 \
  -n spring-actor

# Or rebuild
cd example/kubernetes
kubectl apply -k overlays/prod
```

### Blue/Green Deployment

For zero-risk deployments, use blue/green:

1. **Deploy green environment:**
```bash
# Create new deployment with -green suffix
kubectl apply -k overlays/prod --namespace spring-actor-green
```

2. **Test green environment:**
```bash
# Port forward to test
kubectl port-forward -n spring-actor-green svc/spring-actor-http 8081:80

# Run smoke tests
curl http://localhost:8081/actuator/health
```

3. **Switch traffic:**
```bash
# Update service selector to green deployment
kubectl patch svc spring-actor-http -n spring-actor \
  -p '{"spec":{"selector":{"version":"green"}}}'
```

4. **Cleanup blue:**
```bash
# After verifying, delete old deployment
kubectl delete deployment spring-actor-prod -n spring-actor
```

### Canary Deployment

Gradual traffic shift to new version:

1. **Deploy canary (10% traffic):**
```yaml
# Create canary deployment with 1 replica
kubectl apply -k overlays/canary
```

2. **Monitor metrics:**
```bash
# Check error rates, latency
kubectl logs -n spring-actor -l version=canary
```

3. **Gradually increase:**
```bash
# Scale canary up, stable down
kubectl scale deployment spring-actor-canary --replicas=2 -n spring-actor
kubectl scale deployment spring-actor-prod --replicas=3 -n spring-actor
```

4. **Complete rollout:**
```bash
# When canary is stable, promote to prod
kubectl delete deployment spring-actor-prod -n spring-actor
kubectl scale deployment spring-actor-canary --replicas=5 -n spring-actor
kubectl label deployment spring-actor-canary version=stable -n spring-actor --overwrite
```

## Monitoring & Alerting

### Deploy Monitoring Stack

```bash
cd example/kubernetes
./setup-local.sh monitoring

# Or for production
kubectl apply -k monitoring/
```

### Access Dashboards

**Via Port Forward:**
```bash
# Grafana
kubectl port-forward -n monitoring svc/grafana 3000:3000

# Prometheus
kubectl port-forward -n monitoring svc/prometheus 9090:9090
```

**Via Ingress:**
Configure ingress in `monitoring/grafana-ingress.yaml`:
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: grafana-ingress
  namespace: monitoring
spec:
  rules:
  - host: grafana.yourdomain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: grafana
            port:
              number: 3000
```

### Pre-configured Dashboards

1. **Pekko Cluster Health**
   - Cluster member status
   - Shard distribution
   - Entity counts
   - HTTP metrics

2. **Rolling Update Monitor**
   - Pod lifecycle
   - Cluster size during updates
   - Resource usage
   - Deployment events

### Alerts

Prometheus alerts are configured in `monitoring/alerts.yaml`:

**Critical Alerts:**
- Pod down
- Cluster unreachable members
- Cluster size dropped
- Pod crash looping

**Warning Alerts:**
- High memory/CPU usage
- High HTTP error rate
- High latency
- Frequent restarts

### Setting up Alertmanager

For production alerting, deploy Alertmanager:

```yaml
# alertmanager-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: alertmanager-config
  namespace: monitoring
data:
  alertmanager.yml: |
    global:
      resolve_timeout: 5m
      slack_api_url: 'YOUR_SLACK_WEBHOOK_URL'
    
    route:
      group_by: ['alertname', 'cluster', 'service']
      group_wait: 10s
      group_interval: 10s
      repeat_interval: 12h
      receiver: 'slack-notifications'
      
      routes:
      - match:
          severity: critical
        receiver: 'pagerduty'
        continue: true
    
    receivers:
    - name: 'slack-notifications'
      slack_configs:
      - channel: '#alerts'
        title: '{{ .GroupLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
    
    - name: 'pagerduty'
      pagerduty_configs:
      - service_key: 'YOUR_PAGERDUTY_KEY'
```

## Scaling

### Horizontal Pod Autoscaling (HPA)

HPA is configured in `overlays/prod/hpa.yaml`:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: spring-actor
spec:
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

**Considerations for Pekko Cluster:**
- Set `minReplicas` to at least 3 for quorum
- Scale gradually to avoid cluster rebalancing
- Monitor shard rebalancing during scale events

### Manual Scaling

```bash
# Scale deployment
kubectl scale deployment spring-actor-prod --replicas=7 -n spring-actor

# Verify cluster membership
kubectl exec -n spring-actor spring-actor-prod-xxxxx -- \
  curl -s localhost:8558/cluster/members | jq
```

### Vertical Scaling

To increase resources per pod:

1. Update `overlays/prod/deployment-patch.yaml`
2. Redeploy with `kubectl apply -k overlays/prod`
3. Rolling update will apply new resource limits

### Cluster Autoscaling

For cloud providers, enable cluster autoscaler:

**AWS (EKS):**
```bash
helm install cluster-autoscaler autoscaler/cluster-autoscaler \
  --namespace kube-system \
  --set autoDiscovery.clusterName=your-cluster-name
```

## High Availability

### Pod Distribution

Anti-affinity ensures pods run on different nodes:

```yaml
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
    - weight: 100
      podAffinityTerm:
        labelSelector:
          matchExpressions:
          - key: app
            operator: In
            values:
            - spring-actor
        topologyKey: kubernetes.io/hostname
```

### Pod Disruption Budget (PDB)

PDB ensures minimum availability during voluntary disruptions:

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: spring-actor-pdb
spec:
  minAvailable: 2  # Always keep 2 pods running
  selector:
    matchLabels:
      app: spring-actor
```

### Multi-Zone Deployment

For cloud deployments, spread across availability zones:

```yaml
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
    - weight: 100
      podAffinityTerm:
        topologyKey: topology.kubernetes.io/zone
```

### Health Checks

Three types of probes ensure pod health:

1. **Startup Probe**: Extra time for initial startup (60s)
2. **Liveness Probe**: Restart pod if unhealthy
3. **Readiness Probe**: Remove from service if not ready

## Security

### Network Policies

Network policies restrict traffic to pods:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: spring-actor-netpol
spec:
  podSelector:
    matchLabels:
      app: spring-actor
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 2551  # Pekko remoting
    - protocol: TCP
      port: 8558  # Pekko management
```

### Pod Security

Pods run with restricted security context:

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1001
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop:
    - ALL
```

### Secrets Management

For sensitive data, use Kubernetes secrets:

```bash
# Create secret
kubectl create secret generic spring-actor-secrets \
  --from-literal=db-password=secret \
  -n spring-actor

# Reference in deployment
env:
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: spring-actor-secrets
      key: db-password
```

**For production, use external secret managers:**
- AWS Secrets Manager + External Secrets Operator
- HashiCorp Vault
- Azure Key Vault
- Google Secret Manager

### TLS/SSL

TLS is configured via Ingress:

```yaml
spec:
  tls:
  - hosts:
    - spring-actor.yourdomain.com
    secretName: spring-actor-tls
```

**Using cert-manager:**
```yaml
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: spring-actor-cert
  namespace: spring-actor
spec:
  secretName: spring-actor-tls
  issuerRef:
    name: letsencrypt-prod
    kind: ClusterIssuer
  dnsNames:
  - spring-actor.yourdomain.com
```

## Disaster Recovery

### Backup Strategy

**What to backup:**
1. Kubernetes manifests (stored in Git)
2. ConfigMaps and Secrets
3. Persistent volumes (if used)
4. Application data (database, etc.)

**Backup ConfigMaps/Secrets:**
```bash
# Export all resources
kubectl get all,configmap,secret -n spring-actor -o yaml > backup.yaml

# Or use Velero for cluster-wide backups
velero backup create spring-actor-backup --include-namespaces spring-actor
```

### Disaster Recovery Procedure

**Scenario: Complete cluster loss**

1. **Provision new cluster**
2. **Install dependencies** (ingress, cert-manager, metrics-server)
3. **Deploy monitoring** (optional but recommended)
4. **Restore application:**
```bash
# Apply from Git
kubectl apply -k overlays/prod

# Or restore from backup
kubectl apply -f backup.yaml
```
5. **Verify cluster formation**
6. **Run smoke tests**
7. **Update DNS/Load balancer** to point to new cluster

**RTO (Recovery Time Objective):** ~15 minutes
**RPO (Recovery Point Objective):** 0 (stateless application)

### Multi-Region Setup

For true HA, run in multiple regions:

```
Region 1 (Primary)          Region 2 (Backup)
┌──────────────────┐       ┌──────────────────┐
│  EKS Cluster     │       │  EKS Cluster     │
│  ┌────────────┐  │       │  ┌────────────┐  │
│  │ Actor App  │  │       │  │ Actor App  │  │
│  │ (5 pods)   │  │       │  │ (3 pods)   │  │
│  └────────────┘  │       │  └────────────┘  │
└──────────────────┘       └──────────────────┘
         │                          │
         └────────┬─────────────────┘
                  │
         Global Load Balancer
          (Route 53 / CloudFlare)
```

## Troubleshooting

### Pod Not Starting

```bash
# Check pod status
kubectl describe pod <pod-name> -n spring-actor

# Common issues:
# 1. ImagePullBackOff: Check image name/tag and registry credentials
# 2. CrashLoopBackOff: Check logs
# 3. Pending: Check resources and node capacity
```

### Cluster Not Forming

```bash
# Check cluster members
kubectl exec -n spring-actor <pod-name> -- \
  curl -s localhost:8558/cluster/members | jq

# Check logs for cluster events
kubectl logs -n spring-actor <pod-name> | grep -i cluster

# Common issues:
# 1. RBAC permissions: Check service account
# 2. Network policy: Ensure pods can reach each other on port 2551
# 3. Discovery: Check pod labels match service selector
```

### High Memory Usage

```bash
# Check memory usage
kubectl top pods -n spring-actor

# Get heap dump
kubectl exec -n spring-actor <pod-name> -- \
  jcmd 1 GC.heap_dump /tmp/heapdump.hprof

# Copy heap dump locally
kubectl cp spring-actor/<pod-name>:/tmp/heapdump.hprof ./heapdump.hprof
```

### Slow Rolling Update

```bash
# Check rollout status
kubectl rollout status deployment/spring-actor-prod -n spring-actor

# Check readiness probes
kubectl describe pod <new-pod> -n spring-actor

# Common issues:
# 1. Startup probe failing: Increase failure threshold or period
# 2. Cluster not ready: Wait for required-contact-point-nr
# 3. Resource constraints: Check if cluster has capacity
```

### Debugging Network Issues

```bash
# Check service endpoints
kubectl get endpoints spring-actor -n spring-actor

# Test connectivity between pods
kubectl exec -n spring-actor <pod-1> -- \
  curl -v telnet://<pod-2-ip>:2551

# Check network policy
kubectl describe networkpolicy -n spring-actor
```

### View Cluster Events

```bash
# Recent events in namespace
kubectl get events -n spring-actor --sort-by='.lastTimestamp'

# Watch events in real-time
kubectl get events -n spring-actor --watch
```

## Support

- **Documentation**: [GitHub Repository](https://github.com/seonwkim/spring-boot-starter-actor)
- **Issues**: [Report bugs](https://github.com/seonwkim/spring-boot-starter-actor/issues)
- **Discussions**: [Community discussions](https://github.com/seonwkim/spring-boot-starter-actor/discussions)

## Next Steps

- Review [Operations Runbook](OPERATIONS_RUNBOOK.md) for day-2 operations
- Set up [CI/CD Pipeline](CICD_GUIDE.md) for automated deployments
- Configure [Log Aggregation](LOG_AGGREGATION.md) for centralized logging
- Implement [Cost Optimization](COST_OPTIMIZATION.md) strategies

---

**Remember**: Production deployments require careful planning, monitoring, and testing. Start with dev/staging environments before deploying to production.
