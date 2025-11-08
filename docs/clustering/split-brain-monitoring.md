# Split Brain Monitoring and Production Health Checks

## Overview

Production monitoring for split brain scenarios is critical for detecting and responding to network partitions before they cause data inconsistency or system failures.

This guide covers:
- Spring Boot Actuator health indicators
- Cluster state monitoring
- Alerting strategies
- Production runbooks

## Health Indicator

### Setup

The `ClusterHealthIndicator` is automatically enabled when Spring Boot Actuator is on the classpath.

**Add dependency** (Maven):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Add dependency** (Gradle):
```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

**Enable health endpoint** in `application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  
  health:
    cluster:
      enabled: true  # Enable cluster health indicator (default: true)
```

### Health Status Levels

The cluster health indicator reports status based on unreachable members:

| Status | Condition | Split Brain Risk | Action Required |
|--------|-----------|------------------|----------------|
| **UP** | All nodes reachable | LOW | None |
| **DEGRADED** | < 1/3 nodes unreachable | MEDIUM | Monitor |
| **DEGRADED** | 1/3 to 1/2 nodes unreachable | HIGH | Investigate |
| **DOWN** | ≥ 1/2 nodes unreachable | CRITICAL | Immediate action |

### Example Health Response

**Healthy cluster** (GET `/actuator/health`):
```json
{
  "status": "UP",
  "components": {
    "cluster": {
      "status": "UP",
      "details": {
        "clusterSize": 5,
        "reachableNodes": 5,
        "unreachableNodes": 0,
        "unreachableMembers": [],
        "splitBrainRisk": "LOW",
        "selfAddress": "pekko://ActorSystem@10.0.1.5:2551",
        "selfStatus": "Up"
      }
    }
  }
}
```

**Degraded cluster** with unreachable nodes:
```json
{
  "status": "DEGRADED",
  "components": {
    "cluster": {
      "status": "DEGRADED",
      "details": {
        "clusterSize": 5,
        "reachableNodes": 3,
        "unreachableNodes": 2,
        "unreachableMembers": [
          "pekko://ActorSystem@10.0.1.4:2551 (status: Unreachable)",
          "pekko://ActorSystem@10.0.1.5:2551 (status: Unreachable)"
        ],
        "splitBrainRisk": "HIGH",
        "selfAddress": "pekko://ActorSystem@10.0.1.1:2551",
        "selfStatus": "Up"
      }
    }
  }
}
```

**Critical split brain risk**:
```json
{
  "status": "DOWN",
  "components": {
    "cluster": {
      "status": "DOWN",
      "details": {
        "clusterSize": 6,
        "reachableNodes": 3,
        "unreachableNodes": 3,
        "unreachableMembers": [
          "pekko://ActorSystem@10.0.1.4:2551 (status: Unreachable)",
          "pekko://ActorSystem@10.0.1.5:2551 (status: Unreachable)",
          "pekko://ActorSystem@10.0.1.6:2551 (status: Unreachable)"
        ],
        "splitBrainRisk": "CRITICAL",
        "selfAddress": "pekko://ActorSystem@10.0.1.1:2551",
        "selfStatus": "Up"
      }
    }
  }
}
```

## Monitoring in Production

### 1. Health Check Integration

Integrate health checks with your infrastructure:

**Kubernetes Liveness/Readiness Probes**:
```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: actor-app
      livenessProbe:
        httpGet:
          path: /actuator/health
          port: 8080
        initialDelaySeconds: 60
        periodSeconds: 10
        timeoutSeconds: 5
        failureThreshold: 3
      
      readinessProbe:
        httpGet:
          path: /actuator/health
          port: 8080
        initialDelaySeconds: 30
        periodSeconds: 5
        timeoutSeconds: 3
        failureThreshold: 2
```

**Load Balancer Health Checks** (AWS ELB):
```yaml
TargetGroup:
  HealthCheckPath: /actuator/health
  HealthCheckIntervalSeconds: 30
  HealthCheckTimeoutSeconds: 5
  HealthyThresholdCount: 2
  UnhealthyThresholdCount: 3
```

### 2. Monitoring Dashboard

Create a monitoring dashboard to track cluster health:

**Key Metrics to Track**:
- Cluster size over time
- Number of unreachable nodes
- Split brain risk level
- Node join/leave events
- Singleton migrations

**Example Grafana Query** (if using Prometheus):
```promql
# Cluster size
cluster_size

# Unreachable nodes
cluster_unreachable_nodes

# Split brain risk (1 = CRITICAL, 0.75 = HIGH, 0.5 = MEDIUM, 0.25 = LOW)
cluster_split_brain_risk

# Alert when split brain risk is HIGH or CRITICAL
cluster_split_brain_risk > 0.5
```

### 3. Alerting Rules

Set up alerts for split brain scenarios:

**Prometheus AlertManager Rules**:
```yaml
groups:
  - name: cluster-health
    interval: 30s
    rules:
      # CRITICAL: Split brain risk
      - alert: ClusterSplitBrainRiskCritical
        expr: cluster_unreachable_nodes >= (cluster_size / 2)
        for: 2m
        labels:
          severity: critical
          team: platform
        annotations:
          summary: "CRITICAL: Cluster split brain risk"
          description: |
            {{ $value }} nodes are unreachable out of {{ cluster_size }} total.
            This indicates a potential network partition.
            Check network connectivity immediately.
      
      # HIGH: Many unreachable nodes
      - alert: ClusterNodesUnreachable
        expr: cluster_unreachable_nodes > 0
        for: 5m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "Cluster has unreachable nodes"
          description: |
            {{ $value }} nodes are unreachable.
            Investigate network connectivity and node health.
      
      # WARNING: Cluster size below minimum
      - alert: ClusterSizeBelowMinimum
        expr: cluster_size < 3
        for: 2m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "Cluster size below recommended minimum"
          description: |
            Cluster has only {{ $value }} nodes (minimum recommended: 3).
            Consider scaling up to improve resilience.
```

### 4. Alert Notification Channels

Configure notification channels:

```yaml
# alertmanager.yml
route:
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: 'pagerduty'
      continue: true
    
    - match:
        severity: warning
      receiver: 'slack'

receivers:
  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: '<your-service-key>'
  
  - name: 'slack'
    slack_configs:
      - api_url: '<your-webhook-url>'
        channel: '#cluster-alerts'
  
  - name: 'default'
    email_configs:
      - to: 'team@example.com'
```

## Production Runbooks

### Runbook 1: High Split Brain Risk Alert

**Alert**: `ClusterSplitBrainRiskCritical`

**Immediate Actions**:

1. **Check cluster health from all nodes**:
   ```bash
   # On each node
   curl http://localhost:8080/actuator/health | jq '.components.cluster'
   ```

2. **Verify network connectivity**:
   ```bash
   # Check connectivity between nodes
   ping <other-node-ip>
   telnet <other-node-ip> 2551  # Pekko port
   ```

3. **Check Split Brain Resolver logs**:
   ```bash
   # Look for downing decisions
   grep "SplitBrainResolver" /var/log/application.log
   grep "Downing" /var/log/application.log
   ```

4. **Verify which nodes are down**:
   - Check which partition survived
   - Identify downed nodes
   - Determine if split brain resolution worked correctly

5. **Recover downed nodes** (if appropriate):
   ```bash
   # Restart downed nodes if they were incorrectly downed
   kubectl rollout restart deployment/<app-name>
   
   # Or manually restart
   systemctl restart <app-service>
   ```

**Investigation**:
- Check for network issues (firewalls, routing, DNS)
- Review recent deployments or infrastructure changes
- Check for resource exhaustion (CPU, memory, network)
- Verify split brain resolver configuration

### Runbook 2: Nodes Unreachable

**Alert**: `ClusterNodesUnreachable`

**Actions**:

1. **Identify unreachable nodes**:
   ```bash
   curl http://localhost:8080/actuator/health | \
     jq '.components.cluster.details.unreachableMembers'
   ```

2. **Check node health**:
   ```bash
   # SSH to unreachable node
   ssh <node>
   
   # Check if process is running
   ps aux | grep java
   
   # Check logs
   tail -f /var/log/application.log
   ```

3. **Verify network connectivity from surviving nodes**:
   ```bash
   # Ping test
   for node in node1 node2 node3; do
     echo "Testing $node..."
     ping -c 3 $node
   done
   
   # Port test
   nc -zv <unreachable-node> 2551
   ```

4. **Check resource usage on unreachable nodes**:
   ```bash
   # CPU, memory, disk
   top
   free -h
   df -h
   
   # Network
   netstat -tuln | grep 2551
   ```

5. **Decide on action**:
   - If node is healthy: investigate network
   - If node is unhealthy: restart or replace
   - If persistent: check split brain resolver configuration

### Runbook 3: Cluster Size Below Minimum

**Alert**: `ClusterSizeBelowMinimum`

**Actions**:

1. **Check why nodes left**:
   ```bash
   # Check cluster events
   grep "Member.*left\|removed\|exited" /var/log/application.log
   ```

2. **Verify intended cluster size**:
   ```bash
   # Check deployment replica count
   kubectl get deployment <app-name>
   
   # Or check service configuration
   systemctl status <app-service>
   ```

3. **Scale up if needed**:
   ```bash
   # Kubernetes
   kubectl scale deployment <app-name> --replicas=5
   
   # Or use autoscaling
   kubectl autoscale deployment <app-name> --min=3 --max=10
   ```

4. **Investigate crashes or failures**:
   - Review logs for exceptions
   - Check resource limits
   - Verify configuration

## Best Practices

### Monitoring Checklist

- [ ] Health endpoint exposed and accessible
- [ ] Health checks integrated with load balancer/orchestrator
- [ ] Prometheus metrics exported
- [ ] Grafana dashboards created
- [ ] Alerting rules configured
- [ ] Alert notification channels set up
- [ ] Runbooks documented and tested
- [ ] Team trained on responding to alerts

### Alert Tuning

**Start conservative, then tune**:

1. **Initial setup**:
   - Alert on any unreachable nodes after 5 minutes
   - Alert on split brain risk immediately

2. **After observation**:
   - Increase thresholds if too many false positives
   - Decrease delays for critical alerts
   - Add filters for known maintenance windows

3. **Regular review**:
   - Review alert frequency monthly
   - Update thresholds based on actual incidents
   - Document alert response procedures

### Testing Alerts

**Test your monitoring setup**:

```bash
# Simulate network partition in test environment
# Block traffic between nodes
sudo iptables -A INPUT -s <node-ip> -j DROP

# Verify alerts fire
# Wait for alert delay
# Check alert notification received

# Restore connectivity
sudo iptables -D INPUT -s <node-ip> -j DROP

# Verify recovery
# Check that alerts resolve
```

## Integration Examples

### Example 1: Kubernetes Operator Integration

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: actor-cluster-monitor
spec:
  selector:
    matchLabels:
      app: actor-cluster
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
```

### Example 2: AWS CloudWatch

```yaml
# CloudWatch alarm for health check failures
AlarmClusterHealth:
  Type: AWS::CloudWatch::Alarm
  Properties:
    AlarmName: actor-cluster-health-check-failed
    MetricName: UnhealthyHostCount
    Namespace: AWS/ApplicationELB
    Statistic: Average
    Period: 60
    EvaluationPeriods: 2
    Threshold: 1
    ComparisonOperator: GreaterThanThreshold
    AlarmActions:
      - !Ref SNSTopicArn
```

### Example 3: Datadog Integration

```yaml
# datadog.yaml
init_config:

instances:
  - url: http://localhost:8080/actuator/health
    name: actor-cluster-health
    tags:
      - app:actor-cluster
      - env:production
```

## Troubleshooting

### Health Indicator Not Appearing

**Problem**: `/actuator/health` doesn't show cluster health

**Solution**:
```yaml
# 1. Verify actuator is on classpath
# Check build.gradle.kts or pom.xml

# 2. Enable health endpoint
management:
  endpoints:
    web:
      exposure:
        include: health,info

# 3. Enable cluster health indicator
management:
  health:
    cluster:
      enabled: true
```

### Metrics Not Available

**Problem**: Prometheus metrics not exposed

**Solution**:
```yaml
# Add micrometer prometheus dependency
# Then configure:
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

## See Also

- [Split Brain Resolver Configuration](split-brain-resolver-config.md)
- [Split Brain Testing](testing-split-brain-resolver.md)
- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Prometheus Alerting](https://prometheus.io/docs/alerting/latest/overview/)
