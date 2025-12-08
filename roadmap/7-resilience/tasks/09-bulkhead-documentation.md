# Task 4.1: Bulkhead Pattern Documentation

**Priority:** MEDIUM
**Estimated Effort:** 1 week
**Dependencies:** None
**Assignee:** AI Agent

---

## Objective

Document existing dispatcher configuration for implementing bulkhead patterns. NO CODE IMPLEMENTATION REQUIRED - this is a documentation-only task.

---

## Background

The library already has comprehensive dispatcher configuration support! The bulkhead pattern is achieved through dispatcher isolation. This task focuses on:
1. Documenting existing dispatcher features
2. Providing best practices and examples
3. Creating guides for different bulkhead patterns

---

## Documentation Requirements

### 1. Dispatcher Configuration Guide

Create comprehensive documentation for existing dispatcher configuration:

**Document:** `docs/resilience/bulkhead-pattern.md`

```markdown
# Bulkhead Pattern with Dispatcher Configuration

## Overview

The bulkhead pattern isolates different parts of your system to prevent cascading failures. In the spring-boot-starter-actor library, bulkhead patterns are implemented using **dispatcher configuration** to isolate thread pools and resources.

## Key Concepts

### What is a Bulkhead?

A bulkhead is a resilience pattern that isolates critical resources to prevent a failure in one part of the system from affecting others. Named after the compartments in a ship's hull, bulkheads contain failures to a specific area.

### Dispatchers as Bulkheads

In Pekko/Akka, dispatchers control the thread pool used to execute actors. By assigning different actors to different dispatchers, you create isolated bulkheads:

- **Compute-intensive actors** → Dedicated compute dispatcher
- **Blocking I/O actors** → Blocking I/O dispatcher
- **External service calls** → External service dispatcher
- **Critical path actors** → Priority dispatcher

## Configuration

### YAML Configuration

```yaml
spring:
  actor:
    dispatchers:
      # Bulkhead for external API calls
      external-api-dispatcher:
        type: fixed-pool
        core-pool-size: 10
        max-pool-size: 10
        queue-size: 100
        rejection-policy: caller-runs
      
      # Bulkhead for database operations
      database-dispatcher:
        type: fixed-pool
        core-pool-size: 20
        max-pool-size: 50
        queue-size: 200
        keep-alive-time: 60s
      
      # Bulkhead for compute-intensive tasks
      compute-dispatcher:
        type: fork-join
        parallelism: 8
        throughput: 100
      
      # Bulkhead for blocking I/O
      blocking-io-dispatcher:
        type: fixed-pool
        core-pool-size: 30
        max-pool-size: 100
        queue-size: 1000
```

### Assigning Dispatchers to Actors

```java
@Component
public class ExternalApiActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withDispatcher("external-api-dispatcher")  // Isolated bulkhead
            .onMessage(CallApi.class, this::handleApiCall)
            .build();
    }
}
```

## Bulkhead Patterns

### Pattern 1: Per-Service Bulkhead

Isolate calls to different external services:

```yaml
spring:
  actor:
    dispatchers:
      payment-service-dispatcher:
        type: fixed-pool
        core-pool-size: 10
        max-pool-size: 10
      
      inventory-service-dispatcher:
        type: fixed-pool
        core-pool-size: 15
        max-pool-size: 15
      
      notification-service-dispatcher:
        type: fixed-pool
        core-pool-size: 5
        max-pool-size: 5
```

**Benefits:**
- If payment service is slow, it doesn't affect inventory or notifications
- Each service has guaranteed resources
- Failures are contained

### Pattern 2: Per-Operation-Type Bulkhead

Separate read and write operations:

```yaml
spring:
  actor:
    dispatchers:
      read-dispatcher:
        type: fork-join
        parallelism: 16  # More threads for reads
        throughput: 100
      
      write-dispatcher:
        type: fixed-pool
        core-pool-size: 5  # Fewer threads for writes
        max-pool-size: 10
```

### Pattern 3: Per-Priority Bulkhead

Separate critical and non-critical workloads:

```yaml
spring:
  actor:
    dispatchers:
      critical-dispatcher:
        type: fixed-pool
        core-pool-size: 20
        max-pool-size: 20
        priority: high
      
      background-dispatcher:
        type: fixed-pool
        core-pool-size: 5
        max-pool-size: 10
        priority: low
```

### Pattern 4: Blocking I/O Bulkhead

Isolate blocking operations:

```yaml
spring:
  actor:
    dispatchers:
      blocking-dispatcher:
        type: fixed-pool
        core-pool-size: 50
        max-pool-size: 200
        keep-alive-time: 60s
        queue-size: 1000
```

```java
@Component
public class DatabaseActor implements SpringActor<Command> {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withDispatcher("blocking-dispatcher")
            .onMessage(QueryDatabase.class, this::handleQuery)
            .build();
    }
    
    private Behavior<Command> handleQuery(ActorContext<Command> ctx, QueryDatabase msg) {
        // This blocks, but it's isolated in its own dispatcher
        List<Row> results = jdbcTemplate.query(msg.sql(), msg.rowMapper());
        msg.reply(results);
        return Behaviors.same();
    }
}
```

## Best Practices

### 1. Size Bulkheads Appropriately

**For I/O-bound operations:**
- Larger thread pools (50-200 threads)
- Higher queue capacity
- Longer keep-alive time

**For CPU-bound operations:**
- Thread pool size = number of cores
- Moderate queue capacity
- Use fork-join dispatcher

**For external service calls:**
- Fixed thread pool
- Size based on service capacity
- Lower queue capacity to fail fast

### 2. Monitor Bulkhead Health

```yaml
management:
  endpoints:
    web:
      exposure:
        include: metrics,health
  metrics:
    export:
      prometheus:
        enabled: true
```

**Key metrics to monitor:**
- Thread pool utilization
- Queue depth
- Rejection count
- Task execution time

### 3. Configure Rejection Policies

```yaml
spring:
  actor:
    dispatchers:
      critical-dispatcher:
        rejection-policy: abort  # Throw exception
      
      background-dispatcher:
        rejection-policy: caller-runs  # Execute in caller thread
      
      buffered-dispatcher:
        rejection-policy: discard-oldest  # Drop oldest task
```

### 4. Use Circuit Breakers with Bulkheads

Combine bulkheads with circuit breakers for comprehensive protection:

```java
return SpringActorBehavior.builder(Command.class, ctx)
    .withDispatcher("payment-service-dispatcher")  // Bulkhead
    .withCircuitBreaker("payment-service")          // Circuit breaker
    .onMessage(ProcessPayment.class, this::processPayment)
    .build();
```

## Examples

### Example 1: E-commerce System

```yaml
spring:
  actor:
    dispatchers:
      # Customer-facing operations (high priority)
      order-processing-dispatcher:
        type: fixed-pool
        core-pool-size: 20
        max-pool-size: 20
      
      # External payment gateway
      payment-dispatcher:
        type: fixed-pool
        core-pool-size: 10
        max-pool-size: 10
      
      # Inventory updates (can be slower)
      inventory-dispatcher:
        type: fixed-pool
        core-pool-size: 5
        max-pool-size: 15
      
      # Background tasks (low priority)
      analytics-dispatcher:
        type: fixed-pool
        core-pool-size: 2
        max-pool-size: 5
```

### Example 2: Microservices Gateway

```yaml
spring:
  actor:
    dispatchers:
      # Separate dispatcher per downstream service
      user-service-dispatcher:
        type: fixed-pool
        core-pool-size: 15
        max-pool-size: 15
      
      product-service-dispatcher:
        type: fixed-pool
        core-pool-size: 20
        max-pool-size: 20
      
      recommendation-service-dispatcher:
        type: fixed-pool
        core-pool-size: 10
        max-pool-size: 10
```

## Troubleshooting

### Problem: Thread Pool Exhaustion

**Symptoms:**
- Increased latency
- Rejected tasks
- Queue buildup

**Solutions:**
- Increase pool size
- Increase queue capacity
- Add circuit breaker
- Review task duration

### Problem: Thread Starvation

**Symptoms:**
- Tasks waiting in queue
- Uneven load distribution

**Solutions:**
- Separate dispatcher for blocking operations
- Use appropriate pool type (fixed vs fork-join)
- Monitor thread utilization

## References

- [Pekko Dispatcher Documentation](https://pekko.apache.org/docs/pekko/current/dispatchers.html)
- [Bulkhead Pattern - Microsoft](https://docs.microsoft.com/en-us/azure/architecture/patterns/bulkhead)
- [Release It! - Michael Nygard](https://pragprog.com/titles/mnee2/release-it-second-edition/)
```

---

## Implementation Tasks

### Files to Create

1. **`docs/resilience/bulkhead-pattern.md`**
   - Comprehensive bulkhead pattern guide
   - Dispatcher configuration reference
   - Best practices and examples

2. **`docs/resilience/dispatcher-tuning.md`**
   - Performance tuning guide
   - Thread pool sizing guidelines
   - Monitoring and metrics

3. **`examples/bulkhead/README.md`**
   - Example applications demonstrating bulkhead patterns
   - Different scenarios and configurations

4. **`docs/resilience/common-patterns.md`**
   - Collection of common bulkhead patterns
   - When to use each pattern
   - Trade-offs and considerations

---

## Example Applications

Create example applications demonstrating different bulkhead patterns:

### Example 1: E-commerce with Multiple Services

```
examples/bulkhead/ecommerce/
├── README.md
├── src/main/java/
│   ├── OrderActor.java
│   ├── PaymentActor.java
│   ├── InventoryActor.java
│   └── NotificationActor.java
└── src/main/resources/
    └── application.yml
```

### Example 2: Blocking I/O Isolation

```
examples/bulkhead/blocking-io/
├── README.md
├── src/main/java/
│   ├── DatabaseActor.java
│   ├── ComputeActor.java
│   └── NonBlockingActor.java
└── src/main/resources/
    └── application.yml
```

### Example 3: Priority-Based Bulkheads

```
examples/bulkhead/priority/
├── README.md
├── src/main/java/
│   ├── CriticalActor.java
│   ├── StandardActor.java
│   └── BackgroundActor.java
└── src/main/resources/
    └── application.yml
```

---

## Acceptance Criteria

- [ ] Comprehensive bulkhead pattern documentation created
- [ ] Dispatcher configuration guide complete
- [ ] Best practices documented
- [ ] Multiple example applications created
- [ ] Performance tuning guide written
- [ ] Troubleshooting section included
- [ ] Integration with other resilience patterns documented (circuit breaker, retry)
- [ ] Documentation reviewed and proofread
- [ ] Examples tested and verified

---

## Documentation Structure

```
docs/
└── resilience/
    ├── README.md                    # Overview of all resilience patterns
    ├── bulkhead-pattern.md          # Bulkhead pattern guide
    ├── dispatcher-tuning.md         # Performance tuning
    ├── common-patterns.md           # Pattern catalog
    └── monitoring.md                # Monitoring and metrics
```

---

## Cross-References

Link bulkhead documentation with:
- Circuit breaker documentation (Task 1.3)
- Retry mechanisms documentation (Task 3.1)
- Backpressure handling documentation (Task 5.1)
- Performance tuning guides

---

## Review Checklist

- [ ] Technical accuracy verified
- [ ] Code examples tested
- [ ] YAML configurations validated
- [ ] Links and cross-references work
- [ ] Images and diagrams included (if applicable)
- [ ] Spelling and grammar checked
- [ ] Consistent terminology throughout
- [ ] Examples follow project conventions
