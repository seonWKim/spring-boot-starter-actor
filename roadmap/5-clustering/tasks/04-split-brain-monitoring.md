# Task 2.3: Split Brain Monitoring and Health Indicators

**Priority:** HIGH  
**Estimated Effort:** 3-4 days  
**Status:** TODO

## Objective

Implement production-ready monitoring, health indicators, and alerting for split brain scenarios to enable proactive detection and response to cluster partitions.

## Background

Production clusters need:
- Real-time visibility into cluster health
- Early warning for unreachable members
- Metrics for split brain risk assessment
- Integration with monitoring systems (Prometheus, Grafana, etc.)
- Actionable alerts for operators

## Requirements

### 1. Spring Boot Health Indicator

Create a health indicator that reports cluster state and split brain risk:

```java
package io.github.seonwkim.core.health;

@Component
public class ClusterHealthIndicator implements HealthIndicator {
    
    private final SpringActorSystem actorSystem;
    
    public ClusterHealthIndicator(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }
    
    @Override
    public Health health() {
        Cluster cluster = Cluster.get(actorSystem.getRaw());
        ClusterState state = cluster.state();
        
        // Get cluster members
        Set<Member> members = state.getMembers();
        Set<Member> unreachable = state.getUnreachable();
        
        Health.Builder builder;
        
        if (unreachable.isEmpty()) {
            // All nodes reachable - healthy
            builder = Health.up();
        } else if (unreachable.size() < members.size() / 2) {
            // Some unreachable but not split brain - degraded
            builder = Health.status("DEGRADED");
        } else {
            // Potential split brain - down
            builder = Health.down();
        }
        
        return builder
            .withDetail("clusterSize", members.size())
            .withDetail("reachableNodes", members.size() - unreachable.size())
            .withDetail("unreachableNodes", unreachable.size())
            .withDetail("unreachableMembers", formatUnreachable(unreachable))
            .withDetail("splitBrainRisk", calculateSplitBrainRisk(members, unreachable))
            .withDetail("leader", state.getLeader() != null ? 
                state.getLeader().address().toString() : "none")
            .withDetail("selfAddress", cluster.selfMember().address().toString())
            .build();
    }
    
    private String calculateSplitBrainRisk(Set<Member> members, Set<Member> unreachable) {
        if (unreachable.isEmpty()) {
            return "LOW";
        } else if (unreachable.size() >= members.size() / 2) {
            return "CRITICAL";
        } else if (unreachable.size() >= members.size() / 3) {
            return "HIGH";
        } else {
            return "MEDIUM";
        }
    }
    
    private List<String> formatUnreachable(Set<Member> unreachable) {
        return unreachable.stream()
            .map(m -> m.address().toString())
            .collect(Collectors.toList());
    }
}
```

### 2. Detailed Metrics

Implement Micrometer metrics for cluster monitoring:

```java
package io.github.seonwkim.core.metrics;

@Component
public class ClusterMetrics {
    
    private final MeterRegistry meterRegistry;
    private final SpringActorSystem actorSystem;
    
    private final Gauge clusterSizeGauge;
    private final Gauge unreachableNodesGauge;
    private final Counter partitionEventsCounter;
    private final Timer memberReachabilityTimer;
    
    public ClusterMetrics(MeterRegistry meterRegistry, SpringActorSystem actorSystem) {
        this.meterRegistry = meterRegistry;
        this.actorSystem = actorSystem;
        
        // Register metrics
        this.clusterSizeGauge = Gauge.builder("cluster.size", this, ClusterMetrics::getClusterSize)
            .description("Total number of cluster members")
            .register(meterRegistry);
        
        this.unreachableNodesGauge = Gauge.builder("cluster.unreachable.nodes", 
                this, ClusterMetrics::getUnreachableNodeCount)
            .description("Number of unreachable cluster nodes")
            .register(meterRegistry);
        
        this.partitionEventsCounter = Counter.builder("cluster.partition.events")
            .description("Number of network partition events detected")
            .register(meterRegistry);
        
        // Subscribe to cluster events
        subscribeToClusterEvents();
    }
    
    private void subscribeToClusterEvents() {
        Cluster cluster = Cluster.get(actorSystem.getRaw());
        
        cluster.subscribeToMemberEvents(actorSystem.getRaw().systemActorOf(
            Behaviors.setup(ctx -> {
                return Behaviors.receive()
                    .onMessage(UnreachableMember.class, msg -> {
                        handleUnreachableMember(msg);
                        return Behaviors.same();
                    })
                    .onMessage(ReachableMember.class, msg -> {
                        handleReachableMember(msg);
                        return Behaviors.same();
                    })
                    .build();
            }), "cluster-metrics-subscriber"
        ));
    }
    
    private void handleUnreachableMember(UnreachableMember event) {
        // Track when a member becomes unreachable
        meterRegistry.counter("cluster.unreachable.events",
            "member", event.member().address().toString()
        ).increment();
        
        // Check if this could be a partition
        if (isPotentialPartition()) {
            partitionEventsCounter.increment();
        }
    }
    
    private void handleReachableMember(ReachableMember event) {
        // Track recovery
        meterRegistry.counter("cluster.reachable.events",
            "member", event.member().address().toString()
        ).increment();
    }
    
    private int getClusterSize() {
        Cluster cluster = Cluster.get(actorSystem.getRaw());
        return cluster.state().getMembers().size();
    }
    
    private int getUnreachableNodeCount() {
        Cluster cluster = Cluster.get(actorSystem.getRaw());
        return cluster.state().getUnreachable().size();
    }
    
    private boolean isPotentialPartition() {
        Cluster cluster = Cluster.get(actorSystem.getRaw());
        ClusterState state = cluster.state();
        int total = state.getMembers().size();
        int unreachable = state.getUnreachable().size();
        
        // Potential partition if more than 1/3 of nodes are unreachable
        return unreachable > 0 && unreachable >= total / 3;
    }
}
```

### 3. Cluster State Actuator Endpoint

Create a custom actuator endpoint for cluster state:

```java
package io.github.seonwkim.core.actuator;

@Component
@Endpoint(id = "cluster")
public class ClusterStateEndpoint {
    
    private final SpringActorSystem actorSystem;
    
    public ClusterStateEndpoint(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }
    
    @ReadOperation
    public ClusterStateResponse getClusterState() {
        Cluster cluster = Cluster.get(actorSystem.getRaw());
        ClusterState state = cluster.state();
        
        return new ClusterStateResponse(
            cluster.selfMember().address().toString(),
            state.getLeader() != null ? state.getLeader().address().toString() : null,
            state.getMembers().stream()
                .map(this::toMemberInfo)
                .collect(Collectors.toList()),
            state.getUnreachable().stream()
                .map(this::toMemberInfo)
                .collect(Collectors.toList()),
            getSplitBrainResolverStatus()
        );
    }
    
    private MemberInfo toMemberInfo(Member member) {
        return new MemberInfo(
            member.address().toString(),
            member.status().toString(),
            member.getRoles(),
            member.upNumber()
        );
    }
    
    private SplitBrainResolverStatus getSplitBrainResolverStatus() {
        // Get split brain resolver configuration and status
        return new SplitBrainResolverStatus(
            /* strategy */ "keep-majority",
            /* stable-after */ "20s",
            /* active */ true
        );
    }
    
    public static class ClusterStateResponse {
        public final String selfAddress;
        public final String leader;
        public final List<MemberInfo> members;
        public final List<MemberInfo> unreachable;
        public final SplitBrainResolverStatus splitBrainResolver;
        
        // Constructor, getters
    }
    
    public static class MemberInfo {
        public final String address;
        public final String status;
        public final Set<String> roles;
        public final int upNumber;
        
        // Constructor, getters
    }
    
    public static class SplitBrainResolverStatus {
        public final String strategy;
        public final String stableAfter;
        public final boolean active;
        
        // Constructor, getters
    }
}
```

### 4. Alerting Configuration

Document alerting rules for Prometheus:

```yaml
# prometheus-alerts.yml
groups:
  - name: cluster-health
    interval: 30s
    rules:
      # Critical: Split brain risk is high
      - alert: ClusterSplitBrainRiskHigh
        expr: cluster_unreachable_nodes > (cluster_size / 2)
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Cluster split brain risk is HIGH"
          description: "{{ $value }} nodes are unreachable out of {{ cluster_size }} total nodes"
      
      # Warning: Some nodes unreachable
      - alert: ClusterNodesUnreachable
        expr: cluster_unreachable_nodes > 0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Cluster has unreachable nodes"
          description: "{{ $value }} nodes are unreachable"
      
      # Warning: Cluster size below minimum
      - alert: ClusterSizeBelowMinimum
        expr: cluster_size < 3
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Cluster size is below recommended minimum"
          description: "Cluster has only {{ $value }} nodes (minimum recommended: 3)"
      
      # Info: Partition event occurred
      - alert: ClusterPartitionDetected
        expr: increase(cluster_partition_events_total[5m]) > 0
        labels:
          severity: info
        annotations:
          summary: "Network partition detected"
          description: "{{ $value }} partition events in the last 5 minutes"
```

### 5. Grafana Dashboard

Create a JSON dashboard definition:

```json
{
  "dashboard": {
    "title": "Actor Cluster Health",
    "panels": [
      {
        "title": "Cluster Size",
        "targets": [
          {
            "expr": "cluster_size",
            "legendFormat": "Total Nodes"
          },
          {
            "expr": "cluster_size - cluster_unreachable_nodes",
            "legendFormat": "Reachable Nodes"
          }
        ]
      },
      {
        "title": "Unreachable Nodes",
        "targets": [
          {
            "expr": "cluster_unreachable_nodes",
            "legendFormat": "Unreachable"
          }
        ],
        "alert": {
          "conditions": [
            {
              "evaluator": {
                "params": [0],
                "type": "gt"
              }
            }
          ]
        }
      },
      {
        "title": "Partition Events",
        "targets": [
          {
            "expr": "rate(cluster_partition_events_total[5m])",
            "legendFormat": "Partitions/sec"
          }
        ]
      },
      {
        "title": "Member Events",
        "targets": [
          {
            "expr": "rate(cluster_unreachable_events_total[5m])",
            "legendFormat": "Unreachable Events"
          },
          {
            "expr": "rate(cluster_reachable_events_total[5m])",
            "legendFormat": "Reachable Events"
          }
        ]
      }
    ]
  }
}
```

## Deliverables

1. **Health Indicator**: `core/src/main/java/io/github/seonwkim/core/health/ClusterHealthIndicator.java`

2. **Metrics**: `core/src/main/java/io/github/seonwkim/core/metrics/ClusterMetrics.java`

3. **Actuator Endpoint**: `core/src/main/java/io/github/seonwkim/core/actuator/ClusterStateEndpoint.java`

4. **Documentation**: `docs/clustering/monitoring-and-alerting.md`
   - How to enable metrics
   - Available health indicators
   - Prometheus configuration
   - Grafana dashboard setup
   - Alert definitions
   - Troubleshooting guide

5. **Example Configuration**: `example/cluster/src/main/resources/application.yml`
   - Actuator endpoints enabled
   - Metrics exposed
   - Health indicators configured

6. **Grafana Dashboard**: `docs/clustering/grafana-dashboard.json`

7. **Prometheus Rules**: `docs/clustering/prometheus-alerts.yml`

## Testing Requirements

```java
@SpringBootTest
public class ClusterHealthIndicatorTest {
    
    @Test
    public void testHealthUpWhenAllNodesReachable() {
        // Verify health is UP when cluster is healthy
    }
    
    @Test
    public void testHealthDegradedWithUnreachableNodes() {
        // Verify health is DEGRADED when some nodes unreachable
    }
    
    @Test
    public void testHealthDownWithSplitBrainRisk() {
        // Verify health is DOWN when split brain risk is high
    }
}

@SpringBootTest
public class ClusterMetricsTest {
    
    @Test
    public void testClusterSizeMetric() {
        // Verify cluster_size metric is correct
    }
    
    @Test
    public void testUnreachableNodesMetric() {
        // Verify cluster_unreachable_nodes metric is correct
    }
    
    @Test
    public void testPartitionEventsCounter() {
        // Verify partition events are counted
    }
}
```

## Success Criteria

- ✅ Health indicator correctly reports cluster state
- ✅ Metrics are exported to Prometheus
- ✅ Custom actuator endpoint provides detailed cluster info
- ✅ Alerting rules detect split brain scenarios
- ✅ Grafana dashboard visualizes cluster health
- ✅ Documentation enables operators to set up monitoring
- ✅ Tests verify metrics and health indicators

## Production Monitoring Guide

Document best practices:

### Monitoring Checklist
- [ ] Enable `/actuator/health` endpoint
- [ ] Enable `/actuator/cluster` endpoint
- [ ] Configure Prometheus scraping
- [ ] Import Grafana dashboard
- [ ] Set up alerting rules
- [ ] Test alerts with simulated failures
- [ ] Document runbook for split brain scenarios

### Key Metrics to Watch
1. `cluster_size` - Should be stable
2. `cluster_unreachable_nodes` - Should be 0
3. `cluster_partition_events_total` - Should be rare
4. Health endpoint status - Should be UP

### Alert Response Guide
1. **ClusterSplitBrainRiskHigh**:
   - Check network connectivity
   - Review split brain resolver logs
   - Identify which nodes are unreachable
   - Verify split brain resolution is working

2. **ClusterNodesUnreachable**:
   - Check if nodes are actually down
   - Verify network connectivity
   - Check node logs
   - Monitor for automatic recovery

## References

- Spring Boot Actuator: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html
- Micrometer: https://micrometer.io/docs
- Prometheus Alerting: https://prometheus.io/docs/alerting/latest/overview/
