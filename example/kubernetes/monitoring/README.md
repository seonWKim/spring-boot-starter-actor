# Monitoring Stack for Spring Actor System

Complete monitoring solution with Prometheus and Grafana for observing your Pekko-based distributed actor system, including real-time visualization of cluster health, rolling updates, and JVM metrics.

## Overview

This monitoring stack provides:
- **Prometheus** for metrics collection and storage
- **Grafana** for visualization with pre-configured dashboards
- **Blue/Green deployment tracking** for rolling update visualization
- **Pekko cluster metrics** including sharding, actors, and cluster membership
- **JVM and system metrics** for performance monitoring

## Quick Start

### 1. Deploy the Monitoring Stack

```bash
./setup-monitoring.sh
```

This will:
- Deploy Prometheus and Grafana to the `spring-actor-monitoring` namespace
- Configure Prometheus to scrape metrics from your actor system
- Provision 3 Grafana dashboards automatically
- Make Grafana accessible at http://localhost:30000

### 2. Deploy the Application

```bash
cd ../app
./setup-local.sh
```

### 3. Set Up Port Forwarding for Blue/Green Visualization

To visualize rolling updates with blue/green deployments, set up port forwarding for 6 pods:

```bash
# Get pod names
kubectl get pods -n spring-actor -l app=spring-actor

# Forward blue deployment (ports 8080-8082)
kubectl port-forward -n spring-actor <pod-1> 8080:8080 &
kubectl port-forward -n spring-actor <pod-2> 8081:8080 &
kubectl port-forward -n spring-actor <pod-3> 8082:8080 &

# Forward green deployment (ports 8083-8085)
kubectl port-forward -n spring-actor <pod-4> 8083:8080 &
kubectl port-forward -n spring-actor <pod-5> 8084:8080 &
kubectl port-forward -n spring-actor <pod-6> 8085:8080 &
```

### 4. Access Grafana

Open your browser to: **http://localhost:30000**

- **Username:** admin
- **Password:** admin
- **Or:** Use anonymous access (enabled by default)

## Dashboards

### 1. Pekko Cluster Overview

**Path:** Home → Pekko Cluster Overview

Visualizes your actor system's cluster health:
- **Cluster members** (Up/Joining/Leaving/Exiting)
- **Sharding regions** per pod
- **Shard distribution** across the cluster
- **Entity counts** in each shard region
- **Actor message processing rates**

**Key Metrics:**
```
pekko_cluster_members{status="Up"}           # Active cluster members
pekko_cluster_sharding_regions               # Number of shard regions
pekko_cluster_sharding_shards                # Total shards
pekko_cluster_sharding_entity_count          # Entities per region
pekko_actor_processed_messages_total         # Message throughput
```

### 2. Rolling Updates Visualization

**Path:** Home → Rolling Updates Visualization

Tracks pod lifecycle during rolling updates:
- **Blue vs Green deployment** visualization
- **Pod lifecycle timeline** (creation/termination)
- **Cluster size monitoring** (should never drop below target)
- **HTTP request distribution** across deployments
- **Pod uptime tracking**

**Use Case:**
During a rolling update, watch:
1. Blue deployment (8080-8082) serves initial pods
2. Green deployment (8083-8085) comes online with new version
3. Blue pods gracefully leave the cluster
4. Cluster size stays >= 3 (zero-downtime guarantee)

**Key Metrics:**
```
up{job="blue-deployment"}                    # Blue deployment health
up{job="green-deployment"}                   # Green deployment health
process_uptime_seconds                       # Pod age
pekko_cluster_members{status="Leaving"}      # Graceful shutdown
```

### 3. JVM & System Metrics

**Path:** Home → JVM & System Metrics

Monitor application performance:
- **Heap memory usage** (used/committed/max)
- **GC activity** (pause time, event frequency)
- **Thread counts** (live/daemon)
- **CPU usage** (system/process)
- **Class loading**
- **File descriptors**

**Key Metrics:**
```
jvm_memory_used_bytes{area="heap"}           # Heap usage
jvm_gc_pause_seconds_sum                     # GC pause time
jvm_threads_live_threads                     # Active threads
system_cpu_usage                             # CPU utilization
```

## Architecture

### Prometheus Configuration

**Scrape Jobs:**

1. **spring-actor-pods** - Kubernetes service discovery
   - Auto-discovers pods with label `app=spring-actor`
   - Scrapes `/actuator/prometheus` on port 8080
   - Adds pod/namespace/node labels

2. **blue-deployment** - Static config for port-forwarded pods
   - Targets: localhost:8080, 8081, 8082
   - Label: `deployment=blue`

3. **green-deployment** - Static config for port-forwarded pods
   - Targets: localhost:8083, 8084, 8085
   - Label: `deployment=green`

**Retention:** 7 days

### Grafana Configuration

**Auto-provisioned:**
- Prometheus datasource (http://prometheus:9090)
- 3 dashboards loaded from ConfigMaps
- Default home dashboard: Pekko Cluster Overview

**Access:** NodePort 30000 (http://localhost:30000)

## Directory Structure

```
monitoring/
├── base/                              # Kubernetes manifests
│   ├── namespace.yaml                 # spring-actor-monitoring namespace
│   ├── prometheus-rbac.yaml           # ServiceAccount and permissions
│   ├── prometheus-config.yaml         # Prometheus scrape configuration
│   ├── prometheus-deployment.yaml     # Prometheus deployment & service
│   ├── grafana-config.yaml            # Grafana datasource config
│   ├── grafana-dashboards.yaml        # Dashboard definitions
│   ├── grafana-deployment.yaml        # Grafana deployment & service
│   └── kustomization.yaml             # Kustomize configuration
│
├── dashboards/                        # Dashboard JSON files (source)
│   ├── pekko-cluster-overview.json
│   ├── rolling-updates.json
│   └── jvm-system-metrics.json
│
├── setup-monitoring.sh                # Deployment script
└── README.md                          # This file
```

## Common Operations

### Check Monitoring Stack Status

```bash
kubectl get pods -n spring-actor-monitoring
kubectl get svc -n spring-actor-monitoring
```

### View Prometheus Targets

```bash
# Open Prometheus UI
open http://localhost:30090/targets

# Or check via kubectl
kubectl port-forward -n spring-actor-monitoring svc/prometheus 9090:9090
```

### Restart Monitoring Stack

```bash
kubectl rollout restart deployment/prometheus -n spring-actor-monitoring
kubectl rollout restart deployment/grafana -n spring-actor-monitoring
```

### View Logs

```bash
# Prometheus logs
kubectl logs -f -n spring-actor-monitoring -l app=prometheus

# Grafana logs
kubectl logs -f -n spring-actor-monitoring -l app=grafana
```

### Delete Monitoring Stack

```bash
kubectl delete namespace spring-actor-monitoring
```

## Testing Rolling Updates

To visualize a rolling update:

1. **Set up monitoring and app:**
   ```bash
   ./setup-monitoring.sh
   cd ../app && ./setup-local.sh
   ```

2. **Set up port forwarding** (see Quick Start #3)

3. **Open Grafana** and navigate to "Rolling Updates Visualization"

4. **Trigger a rolling update:**
   ```bash
   cd ../app
   kubectl rollout restart deployment/spring-actor -n spring-actor
   ```

5. **Watch the dashboard** to see:
   - New pods (green) starting up
   - Old pods (blue) gracefully terminating
   - Cluster size staying >= 3
   - Zero downtime maintained

## Prometheus Query Examples

Useful queries for custom dashboards or alerts:

```promql
# Cluster size over time
count(pekko_cluster_members{status="Up"})

# Message processing rate per second
rate(pekko_actor_processed_messages_total[1m])

# Heap usage percentage
(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100

# Pods in each deployment
count by (deployment) (up{job=~"blue-deployment|green-deployment"})

# GC pause time per minute
rate(jvm_gc_pause_seconds_sum[1m])

# CPU usage per pod
process_cpu_usage{job="spring-actor-pods"} * 100
```

## Customization

### Adding Custom Dashboards

1. Create your dashboard in Grafana UI
2. Export as JSON: Dashboard Settings → JSON Model
3. Add to `dashboards/` directory
4. Update `base/grafana-dashboards.yaml`:
   ```yaml
   data:
     my-dashboard.json: |
       <paste your JSON here, indented by 4 spaces>
   ```
5. Redeploy: `kubectl apply -k base`

### Modifying Prometheus Scrape Config

Edit `base/prometheus-config.yaml` and update the scrape jobs:

```yaml
scrape_configs:
  - job_name: 'my-custom-job'
    static_configs:
      - targets: ['my-service:8080']
```

Then apply changes:
```bash
kubectl apply -k base
kubectl rollout restart deployment/prometheus -n spring-actor-monitoring
```

### Changing Grafana Port

Edit `base/grafana-deployment.yaml`:
```yaml
spec:
  type: NodePort
  ports:
    - name: web
      port: 3000
      targetPort: 3000
      nodePort: 30001  # Change this (30000-32767)
```

## Troubleshooting

### Grafana shows "No data"

**Check:**
1. Application is running: `kubectl get pods -n spring-actor`
2. Prometheus is scraping: http://localhost:30090/targets
3. Port forwarding is active: `lsof -i :8080-8085`

### Prometheus not scraping pods

**Verify:**
```bash
# Check if Prometheus can list pods
kubectl exec -n spring-actor-monitoring deploy/prometheus -- \
  wget -qO- http://kubernetes.default.svc/api/v1/namespaces/spring-actor/pods

# Check RBAC permissions
kubectl auth can-i list pods --as=system:serviceaccount:spring-actor-monitoring:prometheus -n spring-actor
```

### Dashboards not loading

**Solution:**
```bash
# Check if ConfigMap is created
kubectl get configmap grafana-dashboards -n spring-actor-monitoring -o yaml

# Restart Grafana to reload dashboards
kubectl rollout restart deployment/grafana -n spring-actor-monitoring
```

### Blue/Green metrics not showing

**Ensure:**
1. Port forwarding is set up for all 6 ports (8080-8085)
2. Pods are actually running on those forwarded ports
3. Use `host.docker.internal` in Prometheus config for Kind clusters

## Production Considerations

For production deployments:

1. **Persistence:** Add PersistentVolumes for Prometheus and Grafana
   ```yaml
   volumes:
     - name: storage
       persistentVolumeClaim:
         claimName: prometheus-pvc
   ```

2. **Resource Limits:** Adjust based on your cluster size
   ```yaml
   resources:
     requests:
       memory: "2Gi"
       cpu: "1000m"
     limits:
       memory: "4Gi"
       cpu: "2000m"
   ```

3. **Retention:** Configure Prometheus retention in deployment args:
   ```yaml
   args:
     - '--storage.tsdb.retention.time=30d'
     - '--storage.tsdb.retention.size=50GB'
   ```

4. **Security:**
   - Remove anonymous access from Grafana
   - Use secrets for credentials
   - Enable TLS for Grafana and Prometheus
   - Set up OAuth/LDAP authentication

5. **Alerting:** Configure Prometheus AlertManager for critical metrics

6. **Ingress:** Replace NodePort with proper Ingress for external access

## Additional Resources

- **Prometheus:** https://prometheus.io/docs/
- **Grafana:** https://grafana.com/docs/
- **Pekko Metrics:** https://pekko.apache.org/docs/pekko/current/typed/cluster-metrics.html
- **Spring Boot Actuator:** https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html

---

**Need help?** [Open an issue](https://github.com/seonwkim/spring-boot-starter-actor/issues)
