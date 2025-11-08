# Operations Runbook

Day-to-day operations guide for running Spring Boot Starter Actor in production.

## Table of Contents

1. [Daily Operations](#daily-operations)
2. [Common Tasks](#common-tasks)
3. [Incident Response](#incident-response)
4. [Maintenance](#maintenance)
5. [Performance Tuning](#performance-tuning)
6. [Capacity Planning](#capacity-planning)

## Daily Operations

### Health Check Routine

**Morning checklist (5 minutes):**

```bash
# 1. Check all pods are running
kubectl get pods -n spring-actor
# Expected: All pods in Running state, READY count matches

# 2. Check cluster health
kubectl exec -n spring-actor $(kubectl get pods -n spring-actor -l app=spring-actor -o jsonpath='{.items[0].metadata.name}') -- \
  curl -s localhost:8558/cluster/members | jq '.members[] | {address: .node, status: .status}'
# Expected: All members with status "Up"

# 3. Check recent restarts
kubectl get pods -n spring-actor -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.containerStatuses[0].restartCount}{"\n"}{end}'
# Expected: Low or zero restart counts

# 4. Check Prometheus alerts
kubectl port-forward -n monitoring svc/prometheus 9090:9090 &
open http://localhost:9090/alerts
# Expected: No firing critical alerts

# 5. Check resource usage
kubectl top pods -n spring-actor
# Expected: Memory and CPU within limits
```

### Metrics to Monitor

| Metric | Good | Warning | Critical | Action |
|--------|------|---------|----------|--------|
| Pod count | = Desired | ±1 for 5m | < Min for 5m | Investigate pod failures |
| Cluster members (Up) | ≥ 3 | 2 | < 2 | Check network, restart pods |
| Memory usage | < 75% | 75-85% | > 85% | Consider scaling up |
| CPU usage | < 70% | 70-85% | > 85% | Consider scaling out |
| HTTP error rate | < 1% | 1-5% | > 5% | Check application logs |
| HTTP P95 latency | < 500ms | 500ms-1s | > 1s | Check application performance |
| Restart count | 0 | 1-2/day | > 2/day | Investigate OOM or crashes |

## Common Tasks

### Scaling Operations

**Scale up (add pods):**
```bash
# Manual scaling
kubectl scale deployment spring-actor-prod --replicas=7 -n spring-actor

# Wait for new pods to join cluster
kubectl rollout status deployment/spring-actor-prod -n spring-actor

# Verify cluster membership
kubectl exec -n spring-actor $(kubectl get pods -n spring-actor -l app=spring-actor -o jsonpath='{.items[0].metadata.name}') -- \
  curl -s localhost:8558/cluster/members | jq '.members | length'
```

**Scale down (remove pods):**
```bash
# IMPORTANT: Always scale down gradually for Pekko clusters
# Never scale down more than 1 pod at a time

# Scale down by 1
kubectl scale deployment spring-actor-prod --replicas=4 -n spring-actor

# Wait for pod to terminate gracefully (30s by default)
sleep 35

# Verify cluster rebalanced
kubectl logs -n spring-actor -l app=spring-actor --tail=100 | grep -i "shard"

# Then scale down again if needed
kubectl scale deployment spring-actor-prod --replicas=3 -n spring-actor
```

### Deploying New Version

**Standard rolling update:**
```bash
# 1. Update image tag in kustomization
cd example/kubernetes/overlays/prod
vim kustomization.yaml  # Update newTag

# 2. Apply changes
kubectl apply -k .

# 3. Monitor rollout
kubectl rollout status deployment/spring-actor-prod -n spring-actor

# 4. Watch cluster membership during update
watch -n 2 'kubectl exec -n spring-actor $(kubectl get pods -n spring-actor -l app=spring-actor,version=v1 -o jsonpath="{.items[0].metadata.name}") -- curl -s localhost:8558/cluster/members 2>/dev/null | jq ".members | length"'

# 5. Verify new version
kubectl get pods -n spring-actor -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.containers[0].image}{"\n"}{end}'
```

**Rollback if needed:**
```bash
# Rollback to previous version
kubectl rollout undo deployment/spring-actor-prod -n spring-actor

# Or rollback to specific revision
kubectl rollout history deployment/spring-actor-prod -n spring-actor
kubectl rollout undo deployment/spring-actor-prod -n spring-actor --to-revision=3
```

### Restarting Pods

**Restart single pod:**
```bash
# Delete pod (will be recreated)
kubectl delete pod <pod-name> -n spring-actor

# Verify new pod joined cluster
kubectl wait --for=condition=ready pod -l app=spring-actor -n spring-actor --timeout=120s
```

**Restart all pods (rolling):**
```bash
# Trigger rolling restart
kubectl rollout restart deployment/spring-actor-prod -n spring-actor

# Monitor progress
kubectl rollout status deployment/spring-actor-prod -n spring-actor
```

**Emergency restart (all at once) - ONLY IF CLUSTER IS BROKEN:**
```bash
# WARNING: This will cause downtime!
kubectl scale deployment spring-actor-prod --replicas=0 -n spring-actor
sleep 10
kubectl scale deployment spring-actor-prod --replicas=3 -n spring-actor
```

### Log Management

**View live logs:**
```bash
# All pods
kubectl logs -f -n spring-actor -l app=spring-actor

# Specific pod
kubectl logs -f -n spring-actor <pod-name>

# Previous pod instance (after crash)
kubectl logs -n spring-actor <pod-name> --previous

# Last 100 lines from all pods
kubectl logs -n spring-actor -l app=spring-actor --tail=100
```

**Search logs:**
```bash
# Search for errors
kubectl logs -n spring-actor -l app=spring-actor --tail=1000 | grep -i error

# Search for cluster events
kubectl logs -n spring-actor -l app=spring-actor --tail=1000 | grep -i "cluster"

# Count occurrences
kubectl logs -n spring-actor -l app=spring-actor --tail=1000 | grep -i "exception" | wc -l
```

**Export logs:**
```bash
# Export logs from all pods
for pod in $(kubectl get pods -n spring-actor -l app=spring-actor -o jsonpath='{.items[*].metadata.name}'); do
  kubectl logs -n spring-actor $pod > logs-$pod-$(date +%Y%m%d-%H%M%S).log
done

# Export to centralized logging
kubectl logs -n spring-actor -l app=spring-actor --since=1h | \
  kubectl run -i --rm --restart=Never log-forwarder --image=busybox -- sh -c "cat - > /dev/stdout" | \
  your-log-aggregator-tool
```

### Configuration Updates

**Update ConfigMap:**
```bash
# 1. Edit ConfigMap
kubectl edit configmap spring-actor-config -n spring-actor

# 2. Restart pods to pick up changes
kubectl rollout restart deployment/spring-actor-prod -n spring-actor

# Or use replace:
kubectl create configmap spring-actor-config --from-file=application.yaml --dry-run=client -o yaml | \
  kubectl replace -f -
```

**Update Secrets:**
```bash
# Create new secret
kubectl create secret generic spring-actor-secrets \
  --from-literal=new-key=new-value \
  --dry-run=client -o yaml | \
  kubectl apply -f -

# Restart to pick up changes
kubectl rollout restart deployment/spring-actor-prod -n spring-actor
```

## Incident Response

### Incident Severity Levels

| Severity | Description | Response Time | Examples |
|----------|-------------|---------------|----------|
| **P0 - Critical** | Service down | Immediate (< 5 min) | All pods down, cluster split |
| **P1 - High** | Degraded service | < 30 min | Partial outage, high error rate |
| **P2 - Medium** | Minor impact | < 2 hours | Single pod issue, slow response |
| **P3 - Low** | No user impact | < 24 hours | Warnings, resource alerts |

### P0: Service Completely Down

**Symptoms:**
- All pods in CrashLoopBackOff
- No cluster members
- Health checks failing

**Response:**

```bash
# 1. ASSESS: Check pod status
kubectl get pods -n spring-actor

# 2. GATHER INFO: Get logs from failed pods
kubectl logs -n spring-actor -l app=spring-actor --tail=100 > incident-logs.txt

# 3. CHECK RECENT CHANGES: View recent deployments
kubectl rollout history deployment/spring-actor-prod -n spring-actor

# 4. ROLLBACK IF RECENT CHANGE:
kubectl rollout undo deployment/spring-actor-prod -n spring-actor

# 5. IF NOT CHANGE-RELATED: Check infrastructure
kubectl describe nodes
kubectl get events -n spring-actor --sort-by='.lastTimestamp' | tail -20

# 6. EMERGENCY RECOVERY: Restart deployment
kubectl delete deployment spring-actor-prod -n spring-actor
kubectl apply -k overlays/prod

# 7. NOTIFY: Update status page and stakeholders
```

**Escalation:** If not resolved in 15 minutes, escalate to platform team.

### P1: Partial Outage

**Symptoms:**
- Some pods down
- High error rate (> 5%)
- Cluster members unreachable

**Response:**

```bash
# 1. IDENTIFY AFFECTED PODS:
kubectl get pods -n spring-actor | grep -v "Running"

# 2. CHECK CLUSTER STATUS:
kubectl exec -n spring-actor $(kubectl get pods -n spring-actor -l app=spring-actor -o jsonpath='{.items[0].metadata.name}') -- \
  curl -s localhost:8558/cluster/members | jq

# 3. RESTART AFFECTED PODS:
for pod in $(kubectl get pods -n spring-actor --field-selector=status.phase!=Running -o name); do
  kubectl delete $pod -n spring-actor
  sleep 10  # Wait between restarts
done

# 4. MONITOR RECOVERY:
watch kubectl get pods -n spring-actor

# 5. CHECK METRICS:
# Open Grafana and check for anomalies in error rates, latency
```

### P2: Single Pod Issue

**Symptoms:**
- One pod not ready
- One pod high memory/CPU
- One pod restarting

**Response:**

```bash
# 1. INVESTIGATE POD:
kubectl describe pod <pod-name> -n spring-actor

# 2. CHECK LOGS:
kubectl logs -n spring-actor <pod-name> --tail=200

# 3. CHECK RESOURCE USAGE:
kubectl top pod <pod-name> -n spring-actor

# 4. IF OOM (Out of Memory):
# - Get heap dump if possible
kubectl exec -n spring-actor <pod-name> -- jcmd 1 GC.heap_dump /tmp/heap.hprof
kubectl cp spring-actor/<pod-name>:/tmp/heap.hprof ./heap-$(date +%Y%m%d).hprof

# 5. RESTART POD:
kubectl delete pod <pod-name> -n spring-actor

# 6. MONITOR:
kubectl wait --for=condition=ready pod -l app=spring-actor -n spring-actor --timeout=120s
```

### Cluster Split Brain

**Symptoms:**
- Multiple unreachable members
- Inconsistent cluster state
- Duplicate entities

**Response:**

```bash
# 1. IDENTIFY SPLIT:
for pod in $(kubectl get pods -n spring-actor -l app=spring-actor -o name); do
  echo "=== $pod ==="
  kubectl exec -n spring-actor ${pod#pod/} -- curl -s localhost:8558/cluster/members 2>/dev/null | jq '.members[] | {node: .node, status: .status}'
done

# 2. CHECK NETWORK:
# Verify pods can reach each other
kubectl exec -n spring-actor <pod-1> -- nc -zv <pod-2-ip> 2551

# 3. RESOLVE SPLIT:
# Option A: Restart minority partition pods
kubectl delete pod <unreachable-pods> -n spring-actor

# Option B: Full cluster restart (if split is severe)
kubectl scale deployment spring-actor-prod --replicas=0 -n spring-actor
sleep 30
kubectl scale deployment spring-actor-prod --replicas=3 -n spring-actor

# 4. VERIFY RESOLUTION:
kubectl exec -n spring-actor $(kubectl get pods -n spring-actor -l app=spring-actor -o jsonpath='{.items[0].metadata.name}') -- \
  curl -s localhost:8558/cluster/members | jq '.members[] | select(.status != "Up")'
```

## Maintenance

### Planned Maintenance Window

**Preparation (1 day before):**
```bash
# 1. Announce maintenance window
# 2. Backup current configuration
kubectl get all,configmap,secret -n spring-actor -o yaml > backup-$(date +%Y%m%d).yaml

# 3. Prepare runbook for maintenance tasks
# 4. Ensure rollback plan is ready
```

**During maintenance:**
```bash
# 1. Set deployment to maintenance mode (if applicable)
kubectl scale deployment spring-actor-prod --replicas=1 -n spring-actor

# 2. Perform maintenance tasks
# - Update Kubernetes version
# - Update node pools
# - Update storage classes
# etc.

# 3. Restore service
kubectl scale deployment spring-actor-prod --replicas=3 -n spring-actor

# 4. Verify cluster health
./health-check.sh
```

### Node Draining

When replacing or upgrading nodes:

```bash
# 1. Cordon node (prevent new pods)
kubectl cordon <node-name>

# 2. Drain node gracefully
kubectl drain <node-name> \
  --ignore-daemonsets \
  --delete-emptydir-data \
  --grace-period=60

# 3. Wait for pods to reschedule
kubectl get pods -n spring-actor -o wide | grep <node-name>

# 4. Verify cluster still healthy
kubectl exec -n spring-actor $(kubectl get pods -n spring-actor -l app=spring-actor -o jsonpath='{.items[0].metadata.name}') -- \
  curl -s localhost:8558/cluster/members | jq

# 5. Perform node maintenance

# 6. Uncordon node
kubectl uncordon <node-name>
```

### Certificate Rotation

```bash
# 1. Check current certificate expiry
kubectl get secret spring-actor-tls -n spring-actor -o jsonpath='{.data.tls\.crt}' | \
  base64 -d | openssl x509 -noout -dates

# 2. Request new certificate (if using cert-manager)
kubectl delete certificate spring-actor-cert -n spring-actor
kubectl apply -f cert.yaml

# 3. Wait for new certificate
kubectl wait --for=condition=ready certificate/spring-actor-cert -n spring-actor --timeout=300s

# 4. Verify new certificate
kubectl get secret spring-actor-tls -n spring-actor -o jsonpath='{.data.tls\.crt}' | \
  base64 -d | openssl x509 -noout -text
```

## Performance Tuning

### JVM Tuning

**Current defaults (base/deployment.yaml):**
```yaml
JAVA_OPTS: >-
  -XX:+UseContainerSupport
  -XX:MaxRAMPercentage=75
  -XX:InitialRAMPercentage=50
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
```

**For high-throughput workloads:**
```yaml
JAVA_OPTS: >-
  -XX:+UseContainerSupport
  -XX:MaxRAMPercentage=75
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=100
  -XX:+ParallelRefProcEnabled
  -XX:G1HeapRegionSize=32m
```

**For low-latency workloads:**
```yaml
JAVA_OPTS: >-
  -XX:+UseContainerSupport
  -XX:MaxRAMPercentage=75
  -XX:+UseShenandoahGC
  -XX:ShenandoahGCHeuristics=compact
```

### Pekko Tuning

**Dispatcher tuning (base/configmap.yaml):**
```yaml
pekko:
  actor:
    default-dispatcher:
      fork-join-executor:
        parallelism-min: 8
        parallelism-factor: 2.0
        parallelism-max: 64
```

**Cluster sharding tuning:**
```yaml
pekko:
  cluster:
    sharding:
      least-shard-allocation-strategy:
        rebalance-threshold: 3
        max-simultaneous-rebalance: 5
```

### Resource Right-Sizing

**Monitor actual usage over 7 days:**
```bash
# Get P95 memory usage
kubectl top pods -n spring-actor --containers | awk '{print $3}' | sort -h | tail -1

# Get P95 CPU usage
kubectl top pods -n spring-actor --containers | awk '{print $2}' | sort -h | tail -1
```

**Adjust resources accordingly:**
- Memory request: P95 + 20% buffer
- Memory limit: P95 + 50% buffer (allow for spikes)
- CPU request: P95 + 30% buffer
- CPU limit: No limit for Pekko (avoid throttling)

## Capacity Planning

### Growth Projections

**Metrics to track:**
- Request rate (req/s)
- Active entities
- Cluster members
- Resource usage per pod

**Example capacity model:**
```
Current state:
- 3 pods
- 500 req/s
- 100 req/s per pod

Target:
- Support 2000 req/s
- Keep per-pod load at 100 req/s

Required pods:
- 2000 / 100 = 20 pods
- Add 20% buffer = 24 pods
- Minimum 3 pods for HA

Recommended: Set HPA maxReplicas to 25
```

### When to Scale Infrastructure

**Scale out (add pods):**
- CPU usage > 70% sustained
- Memory usage > 75% sustained
- Request rate growing
- Response time increasing

**Scale up (bigger nodes):**
- Memory limits being hit
- JVM GC pressure
- Individual pod performance poor
- Storage IOPS saturated

### Cost Monitoring

```bash
# Estimate monthly cost
kubectl cost --namespace spring-actor

# Or manually:
# - Node cost per hour: $X
# - Pods per node: Y
# - Cost per pod per hour: $X / Y
# - Monthly cost: ($X / Y) * 730 * replica_count
```

## Checklists

### Weekly Maintenance Checklist

- [ ] Review Prometheus alerts history
- [ ] Check for pods with high restart counts
- [ ] Review resource usage trends
- [ ] Check certificate expiry dates (< 30 days)
- [ ] Review and rotate logs if needed
- [ ] Check for pending Kubernetes updates
- [ ] Review application error logs
- [ ] Test backup and restore procedure

### Monthly Review Checklist

- [ ] Review capacity planning metrics
- [ ] Update runbooks with new learnings
- [ ] Review and update alert thresholds
- [ ] Conduct disaster recovery drill
- [ ] Review security patches and updates
- [ ] Audit access logs and permissions
- [ ] Review cost optimization opportunities
- [ ] Update documentation

### Quarterly Checklist

- [ ] Major version updates (Kubernetes, dependencies)
- [ ] Security audit
- [ ] Performance benchmarking
- [ ] Review SLO/SLA compliance
- [ ] Capacity planning for next quarter
- [ ] Review and update disaster recovery plan
- [ ] Team training on new features

---

**Remember**: When in doubt, check the logs, verify cluster health, and don't hesitate to rollback if something looks wrong.

For questions or issues not covered here, refer to:
- [Production Guide](PRODUCTION_GUIDE.md)
- [Troubleshooting Guide](PRODUCTION_GUIDE.md#troubleshooting)
- [GitHub Issues](https://github.com/seonwkim/spring-boot-starter-actor/issues)
