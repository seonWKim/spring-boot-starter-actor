# Cost Optimization Guide

Strategies for optimizing costs when running Spring Boot Starter Actor on Kubernetes.

## Table of Contents

1. [Resource Right-Sizing](#resource-right-sizing)
2. [Autoscaling Strategies](#autoscaling-strategies)
3. [Node Management](#node-management)
4. [Storage Optimization](#storage-optimization)
5. [Monitoring & Observability](#monitoring--observability)
6. [Cost Monitoring](#cost-monitoring)

## Resource Right-Sizing

### Analyze Current Usage

**Collect metrics over 7-14 days:**

```bash
# Get resource usage stats
kubectl top pods -n spring-actor --containers > usage-$(date +%Y%m%d).txt

# Or use Prometheus queries
# P95 memory usage
histogram_quantile(0.95, rate(container_memory_working_set_bytes{namespace="spring-actor"}[7d]))

# P95 CPU usage  
histogram_quantile(0.95, rate(container_cpu_usage_seconds_total{namespace="spring-actor"}[7d]))
```

### Right-Size Resources

**Current defaults:**
```yaml
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
```

**Optimization strategy:**
1. Set requests to P95 actual usage + 20% buffer
2. Set memory limit to P95 + 50% buffer (for spikes)
3. Remove CPU limits (avoid throttling with Pekko)

**Example optimized:**
```yaml
# If P95 memory = 800Mi
resources:
  requests:
    memory: "960Mi"  # 800Mi * 1.2
    cpu: "400m"      # Based on actual usage
  limits:
    memory: "1200Mi" # 800Mi * 1.5
    # No CPU limit
```

### Cost Savings Calculation

```
Before:
- Memory request: 1Gi per pod
- 5 pods = 5Gi total
- Cost: $X per GB-hour

After:
- Memory request: 960Mi per pod  
- 5 pods = 4.7Gi total
- Savings: 6% memory = ~6% cost reduction
```

## Autoscaling Strategies

### Horizontal Pod Autoscaler (HPA)

**Current prod HPA:**
```yaml
spec:
  minReplicas: 3
  maxReplicas: 10
```

**Optimization:**
1. Set minReplicas to actual baseline (not over-provision)
2. Adjust based on traffic patterns
3. Use custom metrics for better scaling decisions

**Traffic-based scaling:**
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: spring-actor
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: spring-actor-prod
  minReplicas: 3
  maxReplicas: 10
  metrics:
  # Scale based on actual request rate
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "100"  # 100 req/s per pod
  
  # Scale based on active entities (Pekko-specific)
  - type: Pods
    pods:
      metric:
        name: pekko_cluster_sharding_entities
      target:
        type: AverageValue
        averageValue: "1000"  # 1000 entities per pod
  
  # Fallback to CPU/memory
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

**Cost impact:**
```
Scenario: Off-peak hours (50% traffic)
Without HPA: 5 pods @ $X/hour = $5X
With HPA: 3 pods @ $X/hour = $3X
Savings: 40% during off-peak
```

### Vertical Pod Autoscaler (VPA)

Automatically adjusts resource requests:

```yaml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: spring-actor-vpa
  namespace: spring-actor
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: spring-actor-prod
  updatePolicy:
    updateMode: "Auto"  # or "Recreate" for production
  resourcePolicy:
    containerPolicies:
    - containerName: spring-actor
      minAllowed:
        cpu: "200m"
        memory: "512Mi"
      maxAllowed:
        cpu: "2"
        memory: "4Gi"
      controlledResources: ["cpu", "memory"]
```

**Benefits:**
- Automatic right-sizing
- Adapts to changing workloads
- Can save 20-40% on over-provisioned resources

### Schedule-Based Scaling

For predictable traffic patterns:

```yaml
# scale-schedule.yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: scale-up-business-hours
  namespace: spring-actor
spec:
  schedule: "0 8 * * 1-5"  # 8 AM weekdays
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: scaler-sa
          containers:
          - name: kubectl
            image: bitnami/kubectl:latest
            command:
            - /bin/sh
            - -c
            - kubectl scale deployment spring-actor-prod --replicas=5 -n spring-actor
          restartPolicy: OnFailure
---
apiVersion: batch/v1
kind: CronJob
metadata:
  name: scale-down-off-hours
  namespace: spring-actor
spec:
  schedule: "0 18 * * 1-5"  # 6 PM weekdays
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: scaler-sa
          containers:
          - name: kubectl
            image: bitnami/kubectl:latest
            command:
            - /bin/sh
            - -c
            - kubectl scale deployment spring-actor-prod --replicas=3 -n spring-actor
          restartPolicy: OnFailure
```

**Cost savings:**
```
Business hours (8AM-6PM): 5 pods × 10 hours = 50 pod-hours
Off hours (6PM-8AM): 3 pods × 14 hours = 42 pod-hours
Weekend: 3 pods × 48 hours = 144 pod-hours
Total: 236 pod-hours/week

Without scheduling: 5 pods × 168 hours = 840 pod-hours/week
Savings: (840 - 236) / 840 = 72% potential savings
```

## Node Management

### Node Types and Pricing

**Choose appropriate instance types:**

| Workload Type | Recommended | Reason | Cost |
|--------------|-------------|--------|------|
| Production stable | General purpose (m5, e2) | Balanced CPU/memory | $$ |
| Bursty workload | Burstable (t3, e2-small) | Cost-effective for variable load | $ |
| High throughput | Compute optimized (c5, c2) | More CPU per dollar | $$$ |
| Memory intensive | Memory optimized (r5, m1) | More memory per dollar | $$$$ |

**For Pekko workloads:**
- General purpose instances (m5.large, e2-standard-4)
- CPU:Memory ratio of 1:2 or 1:4
- Network performance matters for clustering

### Spot/Preemptible Instances

Use spot instances for non-critical workloads (dev, staging):

**AWS (Spot):**
```yaml
# Node group with spot instances
apiVersion: eksctl.io/v1alpha5
kind: ClusterConfig
metadata:
  name: spring-actor-cluster
nodeGroups:
- name: spring-actor-spot
  instancesDistribution:
    instanceTypes: ["m5.large", "m5a.large", "m5n.large"]
    onDemandBaseCapacity: 2  # Always 2 on-demand for stability
    onDemandPercentageAboveBaseCapacity: 0  # Rest are spot
    spotAllocationStrategy: "capacity-optimized"
  desiredCapacity: 5
  tags:
    workload: spring-actor
```

**GCP (Preemptible):**
```yaml
# Node pool with preemptible VMs
apiVersion: container.cnrm.cloud.google.com/v1beta1
kind: ContainerNodePool
metadata:
  name: spring-actor-preemptible
spec:
  nodeCount: 3
  nodeConfig:
    preemptible: true
    machineType: e2-standard-4
```

**Cost savings:**
- Spot/Preemptible: 60-90% cheaper than on-demand
- Combine with on-demand for critical workloads

**Best practices:**
1. Use for development/staging
2. Mix spot + on-demand for production
3. Handle interruptions gracefully (PDB helps)

### Cluster Autoscaler

Automatically adjust node count:

**AWS (EKS):**
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: cluster-autoscaler
  namespace: kube-system
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT:role/ClusterAutoscaler
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cluster-autoscaler
  namespace: kube-system
spec:
  template:
    spec:
      serviceAccountName: cluster-autoscaler
      containers:
      - image: k8s.gcr.io/autoscaling/cluster-autoscaler:v1.28.0
        name: cluster-autoscaler
        command:
        - ./cluster-autoscaler
        - --cloud-provider=aws
        - --namespace=kube-system
        - --skip-nodes-with-system-pods=false
        - --balance-similar-node-groups
        - --scale-down-enabled=true
        - --scale-down-delay-after-add=10m
        - --scale-down-unneeded-time=10m
```

**Cost savings:**
- Remove idle nodes automatically
- Scale down after low traffic periods
- Can save 30-50% during off-peak

## Storage Optimization

### Ephemeral Storage

Use ephemeral storage where possible:

```yaml
# Instead of PVC for temporary data
volumes:
- name: tmp
  emptyDir: {}  # Free, no PVC cost
- name: cache
  emptyDir:
    sizeLimit: 1Gi
```

### Storage Classes

Choose appropriate storage class:

| Type | Use Case | Cost | Performance |
|------|----------|------|-------------|
| Standard HDD | Logs, backups | $ | Low IOPS |
| Balanced SSD | General purpose | $$ | Medium IOPS |
| High-performance SSD | Databases, high I/O | $$$ | High IOPS |

**Example:**
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: spring-actor-logs
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
  storageClassName: standard  # Use cheaper HDD for logs
```

### Lifecycle Management

Delete old data automatically:

```yaml
# CronJob to clean old logs
apiVersion: batch/v1
kind: CronJob
metadata:
  name: cleanup-old-logs
  namespace: spring-actor
spec:
  schedule: "0 2 * * *"  # 2 AM daily
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: cleanup
            image: busybox
            command:
            - /bin/sh
            - -c
            - |
              # Find and delete logs older than 7 days
              find /logs -name "*.log" -mtime +7 -delete
            volumeMounts:
            - name: logs
              mountPath: /logs
          volumes:
          - name: logs
            persistentVolumeClaim:
              claimName: spring-actor-logs
          restartPolicy: OnFailure
```

### Compression

Enable compression for logs:

```yaml
env:
- name: LOGGING_FILE_MAX_SIZE
  value: "10MB"
- name: LOGGING_FILE_MAX_HISTORY
  value: "7"
- name: LOGGING_PATTERN_FILE
  value: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"  # Minimal pattern
```

## Monitoring & Observability

### Metrics Retention

Reduce Prometheus storage costs:

```yaml
# prometheus.yaml
args:
- '--storage.tsdb.retention.time=15d'  # Instead of 30d
- '--storage.tsdb.retention.size=50GB' # Limit size
```

**Cost calculation:**
```
30 days retention: 100GB storage @ $0.10/GB = $10/month
15 days retention: 50GB storage @ $0.10/GB = $5/month
Savings: 50%
```

### Sampling

For high-traffic applications, use sampling:

```yaml
# In Prometheus config
scrape_configs:
- job_name: 'spring-actor'
  scrape_interval: 30s  # Instead of 15s
  metric_relabel_configs:
  # Drop high-cardinality metrics
  - source_labels: [__name__]
    regex: 'http_request_duration_bucket'
    action: drop
```

### Log Levels

Reduce log volume in production:

```yaml
env:
- name: LOG_LEVEL
  value: "INFO"  # Not DEBUG
- name: SPRING_JPA_SHOW_SQL
  value: "false"
```

**Impact:**
```
DEBUG logs: 1GB/day × $0.50/GB = $15/month
INFO logs: 100MB/day × $0.50/GB = $1.50/month
Savings: 90%
```

## Cost Monitoring

### Kubernetes Cost Tools

**Kubecost:**
```bash
# Install
helm install kubecost kubecost/cost-analyzer \
  --namespace kubecost \
  --create-namespace

# Access UI
kubectl port-forward -n kubecost svc/kubecost-cost-analyzer 9090:9090
```

**Features:**
- Per-namespace cost breakdown
- Recommendations for optimization
- Cost allocation by team/project

**OpenCost (open source alternative):**
```bash
helm install opencost opencost/opencost \
  --namespace opencost \
  --create-namespace
```

### Cloud Provider Cost Tools

**AWS Cost Explorer:**
- Tag resources: `app=spring-actor`, `environment=prod`
- Filter by tags in Cost Explorer
- Set budgets and alerts

**GCP Cost Management:**
- Use labels consistently
- Enable billing export to BigQuery
- Create custom cost dashboards

**Azure Cost Management:**
- Tag resources systematically
- Use cost analysis tools
- Set spending limits

### Budget Alerts

**Example budget with alerts:**

```yaml
# AWS Budget (via Terraform)
resource "aws_budgets_budget" "spring_actor" {
  name              = "spring-actor-monthly"
  budget_type       = "COST"
  limit_amount      = "1000"
  limit_unit        = "USD"
  time_unit         = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type            = "PERCENTAGE"
    notification_type         = "ACTUAL"
    subscriber_email_addresses = ["team@example.com"]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type            = "PERCENTAGE"
    notification_type         = "FORECASTED"
    subscriber_email_addresses = ["team@example.com"]
  }

  cost_filter {
    name   = "TagKeyValue"
    values = ["user:app$spring-actor"]
  }
}
```

## Cost Optimization Checklist

### Quick Wins (0-1 week)

- [ ] Right-size resource requests based on actual usage
- [ ] Remove CPU limits for Pekko workloads
- [ ] Enable HPA with appropriate min/max
- [ ] Use ephemeral storage for temporary data
- [ ] Reduce log level to INFO in production
- [ ] Set Prometheus retention to 15 days

**Estimated savings: 20-30%**

### Medium Term (1-4 weeks)

- [ ] Implement schedule-based scaling
- [ ] Use spot/preemptible instances for dev/staging
- [ ] Enable cluster autoscaler
- [ ] Implement log rotation and cleanup
- [ ] Use appropriate storage classes
- [ ] Set up cost monitoring and alerts

**Estimated savings: 40-50%**

### Long Term (1-3 months)

- [ ] Implement custom metrics-based autoscaling
- [ ] Optimize node types and sizes
- [ ] Consolidate small workloads
- [ ] Implement VPA for automatic right-sizing
- [ ] Reserved instances for predictable workloads
- [ ] Multi-region optimization

**Estimated savings: 50-70%**

## Cost Optimization Examples

### Before Optimization

```yaml
# Configuration
replicas: 5
resources:
  requests:
    memory: "2Gi"
    cpu: "1000m"
  limits:
    memory: "4Gi"
    cpu: "2000m"

# Node: m5.xlarge (4 CPU, 16GB RAM) × 3 nodes
# Monthly cost: $450 (nodes) + $50 (storage) = $500
```

### After Optimization

```yaml
# Configuration
replicas: 3 (HPA: min 3, max 8)
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "1.5Gi"
    # No CPU limit

# Node: m5.large (2 CPU, 8GB RAM) × 2 nodes + 1 spot
# Monthly cost: $180 (nodes) + $30 (storage) = $210

# Savings: $290/month (58%)
```

## Regional Pricing Differences

Different regions have different costs:

**Example (AWS m5.large):**
- us-east-1: $0.096/hour
- eu-central-1: $0.107/hour (+11%)
- ap-southeast-1: $0.110/hour (+15%)

**Recommendation:**
- Use closest region to users (latency)
- Consider price for dev/staging environments
- Use reserved instances in expensive regions

## Summary

**Key cost optimization strategies:**

1. **Right-size resources** (20-30% savings)
2. **Use autoscaling** (30-40% savings)
3. **Spot instances** (60-90% savings on dev/staging)
4. **Appropriate storage** (10-20% savings)
5. **Monitor and optimize** (ongoing)

**Total potential savings: 50-70%** while maintaining production quality.

---

For more information, see:
- [Production Guide](PRODUCTION_GUIDE.md)
- [Operations Runbook](OPERATIONS_RUNBOOK.md)
