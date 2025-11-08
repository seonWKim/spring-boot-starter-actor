# 6. Enhanced Observability

Production-grade observability with health checks, metrics, and tracing - wonderful features for Spring Boot users.

---

## 6.1 Health Checks and Readiness Probes  

**Priority:** HIGH  
**Status:** Wonderful feature - expand implementation

### Overview

Deep Spring Boot Actuator integration for comprehensive actor system health monitoring.

### Implementation

```java
// Actor-specific health indicators
@Component
public class ActorSystemHealthIndicator implements HealthIndicator {
    
    private final SpringActorSystem actorSystem;
    
    @Override
    public Health health() {
        try {
            ActorSystemMetrics metrics = actorSystem.getMetrics();
            
            // Check various health aspects
            boolean healthy = metrics.getActiveActors() > 0 &&
                             metrics.getDeadLetterRate() < 0.01 &&
                             metrics.getAverageMailboxSize() < 1000;
            
            if (!healthy) {
                return Health.down()
                    .withDetail("activeActors", metrics.getActiveActors())
                    .withDetail("deadLetterRate", metrics.getDeadLetterRate())
                    .withDetail("avgMailboxSize", metrics.getAverageMailboxSize())
                    .build();
            }
            
            return Health.up()
                .withDetail("actorSystem", actorSystem.name())
                .withDetail("activeActors", metrics.getActiveActors())
                .withDetail("processedMessages", metrics.getTotalProcessedMessages())
                .withDetail("uptime", metrics.getUptime())
                .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}

// Cluster-specific health
@Component
public class ClusterHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        if (!actorSystem.isClusterEnabled()) {
            return Health.unknown().build();
        }
        
        ClusterState state = actorSystem.getClusterState();
        
        if (state.hasUnreachableMembers()) {
            return Health.down()
                .withDetail("status", "UNREACHABLE_MEMBERS")
                .withDetail("unreachableMembers", state.getUnreachableMembers())
                .build();
        }
        
        return Health.up()
            .withDetail("members", state.getMembers().size())
            .withDetail("leader", state.getLeader())
            .withDetail("roles", state.getRoles())
            .build();
    }
}
```

### Kubernetes Integration

```yaml
# application.yml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: actorSystem, cluster, db
        liveness:
          include: actorSystem

spring:
  actor:
    health:
      enabled: true
      check-interval: 10s
      timeout: 5s
```

```yaml
# Kubernetes deployment.yaml
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: app
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 20
```

---

## 6.2 Enhanced Metrics (Complete TODO.md)

**Priority:** 🚨 **CRITICAL - HIGHEST PRIORITY**
**Status:** ⚠️ **Only 1 of 50+ metrics implemented** (see `metrics/TODO.md`)
**Current State:** `actor.processing-time` ✅ | All other metrics ❌

> **CRITICAL FINDING:** The metrics/TODO.md file shows extensive metrics planned but only 1 is currently implemented. This should be the HIGHEST PRIORITY before adding new features.

### All Required Metrics

```java
// Actor-level metrics
@Metrics
public class ActorMetrics {
    
    @Metric(name = "actor.processing.time")
    private Timer processingTime;
    
    @Metric(name = "actor.mailbox.size")
    private Gauge mailboxSize;
    
    @Metric(name = "actor.messages.processed")
    private Counter messagesProcessed;
    
    @Metric(name = "actor.errors.total")
    private Counter errors;
    
    @Metric(name = "actor.lifecycle.restarts")
    private Counter restarts;
}

// System-level metrics
@Metrics
public class SystemMetrics {
    
    @Metric(name = "system.active.actors")
    private Gauge activeActors;
    
    @Metric(name = "system.dead.letters")
    private Counter deadLetters;
    
    @Metric(name = "system.unhandled.messages")
    private Counter unhandledMessages;
}

// Dispatcher metrics
@Metrics
public class DispatcherMetrics {
    
    @Metric(name = "dispatcher.threads.active")
    private Gauge activeThreads;
    
    @Metric(name = "dispatcher.queue.size")
    private Gauge queueSize;
    
    @Metric(name = "dispatcher.utilization")
    private Gauge utilization;
}
```

### Grafana Dashboard Support

Provide pre-built Grafana dashboards for:
- Actor system overview
- Message flow rates
- Error rates and trends
- Cluster health
- Performance metrics

---

## 6.3 Distributed Tracing Integration

**Priority:** HIGH  
**Complexity:** Medium

### OpenTelemetry Integration

```java
@Component
public class TracedOrderActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withTracing(TracingConfig.builder()
                .enabled(true)
                .sampleRate(0.1)  // 10% sampling
                .build())
            .onMessage(CreateOrder.class, (context, cmd) -> {
                // Automatic span creation
                Span span = context.currentSpan();
                span.setAttribute("order.id", cmd.orderId());
                span.setAttribute("order.amount", cmd.amount());
                
                // Nested span for DB operation
                Span dbSpan = span.createChild("database.save");
                try {
                    repository.save(order);
                    dbSpan.setStatus(StatusCode.OK);
                } finally {
                    dbSpan.end();
                }
                
                return Behaviors.same();
            })
            .build();
    }
}
```

### Configuration

```yaml
spring:
  actor:
    tracing:
      enabled: true
      sampler: probability
      probability: 0.1
      exporter:
        type: otlp
        endpoint: http://localhost:4318/v1/traces
```

---

## 6.4 Structured Logging with MDC

**Priority:** MEDIUM  
**Complexity:** Low

### Implementation

```java
@Component
public class OrderActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withMDC(MDCConfig.builder()
                .put("actorType", "OrderActor")
                .put("environment", environment)
                .extractFromMessage(msg -> Map.of(
                    "orderId", ((OrderCommand) msg).orderId(),
                    "correlationId", ((OrderCommand) msg).correlationId()
                ))
                .build())
            .onMessage(CreateOrder.class, (ctx, msg) -> {
                // MDC automatically includes: actorPath, messageType, correlationId
                log.info("Processing order");  // Will include all MDC context
                return Behaviors.same();
            })
            .build();
    }
}
```

---

## Summary

1. **Health Checks**: Deep Spring Boot Actuator integration with Kubernetes probes
2. **Enhanced Metrics**: Complete TODO.md with all required metrics
3. **Distributed Tracing**: OpenTelemetry integration with automatic span creation
4. **Structured Logging**: MDC support with automatic context propagation

All features designed for **production observability** with **Spring Boot** conventions.
