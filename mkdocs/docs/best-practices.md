# Best Practices

Guidelines for building production-ready applications with spring-boot-starter-actor.

## Table of Contents

- [Actor Design](#actor-design)
- [Message Design](#message-design)
- [State Management](#state-management)
- [Error Handling](#error-handling)
- [Performance](#performance)
- [Testing](#testing)
- [Deployment](#deployment)
- [Security](#security)

---

## Actor Design

### Keep Actors Small and Focused

**Good** - Single responsibility:
```java
@Component
public class UserSessionActor implements SpringActor<Command> {
    // Only manages user session state
    private String userId;
    private Instant lastActivity;
    private Set<String> activeTopics;
}
```

**Bad** - Too many responsibilities:
```java
@Component
public class GodActor implements SpringActor<Command> {
    // Handles users, sessions, notifications, billing, etc.
    // Too complex, hard to maintain
}
```

### Use Hierarchies for Supervision

Organize actors in supervision hierarchies:

```java
// Supervisor manages worker lifecycle
@Component
public class WorkerSupervisor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(CreateWorker.class, (context, msg) -> {
                // Spawn supervised worker
                context.getSelf()
                    .child(WorkerActor.class)
                    .withId(msg.workerId)
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .spawn();
                return Behaviors.same();
            })
            .build();
    }
}
```

### Avoid Shared Mutable State

**Good** - Encapsulated state:
```java
@Component
public class CounterActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return withCount(0);
    }
    
    private Behavior<Command> withCount(int count) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(Increment.class, (context, msg) -> 
                withCount(count + 1))
            .build();
    }
}
```

**Bad** - Shared mutable state:
```java
// Don't share state between actors!
static final Map<String, Integer> sharedState = new HashMap<>();
```

---

## Message Design

### Make Messages Immutable

Use records for immutability:

```java
public record UserLogin(String userId, String sessionId) 
    implements Command, JsonSerializable {}
```

### Keep Messages Small

**Good** - Reference by ID:
```java
public record ProcessOrder(String orderId) implements Command {}
```

**Bad** - Embedded large objects:
```java
public record ProcessOrder(Order order, List<Item> items, Customer customer) 
    implements Command {} // Too much data
```

### Use Type-Safe Messages

**Good** - Strong typing:
```java
public interface Command {}

public record ProcessData(String data) implements Command {}
public record GetStatus() implements Command {}
```

**Bad** - String-based messages:
```java
// Avoid string-based message types
actor.tell("PROCESS_DATA"); // Error-prone
```

### Implement JsonSerializable for Cluster

For distributed actors, messages must be serializable:

```java
public record UserMessage(String userId, String content) 
    implements Command, JsonSerializable {}
```

---

## State Management

### Use Behaviors for State Transitions

Model state machines with different behaviors:

```java
private Behavior<Command> idle() {
    return SpringActorBehavior.builder(Command.class, ctx)
        .onMessage(StartProcessing.class, (context, msg) -> 
            processing(msg.data))
        .build();
}

private Behavior<Command> processing(String data) {
    return SpringActorBehavior.builder(Command.class, ctx)
        .onMessage(Complete.class, (context, msg) -> idle())
        .onMessage(Error.class, (context, msg) -> failed(msg.error))
        .build();
}
```

### Persist Important State

Use Spring Data for persistence:

```java
@Component
public class OrderActor implements SpringActor<Command> {
    
    private final OrderRepository repository;
    private Order currentOrder;
    
    public OrderActor(OrderRepository repository) {
        this.repository = repository;
    }
    
    private Behavior<Command> handleUpdate(UpdateOrder msg) {
        currentOrder = repository.save(msg.order);
        return Behaviors.same();
    }
}
```

### Use Stash for Message Ordering

When you need to defer messages:

```java
.onMessage(Initialize.class, (ctx, msg) -> {
    ctx.stash(msg); // Defer until ready
    return initializing();
})
```

---

## Error Handling

### Use Supervision Strategies

Configure appropriate supervision:

```java
// Restart on failure (default)
actorSystem.actor(WorkerActor.class)
    .withId("worker-1")
    .withSupervisionStrategy(SupervisorStrategy.restart()
        .withLimit(3, Duration.ofMinutes(1)))
    .spawn();
```

**When to use each strategy:**

- **Restart**: Transient errors (network timeout, temporary failure)
- **Stop**: Permanent errors (invalid configuration)
- **Resume**: Non-critical errors (log and continue)

### Always Handle Timeouts

For ask pattern, always handle timeouts:

```java
// Good - with timeout handler
actor.ask(new GetData())
    .withTimeout(Duration.ofSeconds(5))
    .onTimeout(() -> "default-value")
    .execute();

// Also good - catch timeout exception
actor.ask(new GetData())
    .withTimeout(Duration.ofSeconds(5))
    .execute()
    .exceptionally(ex -> {
        if (ex instanceof AskTimeoutException) {
            return "default-value";
        }
        throw new RuntimeException(ex);
    });
```

### Log Errors Appropriately

Use structured logging:

```java
.onMessage(ProcessData.class, (ctx, msg) -> {
    try {
        processData(msg.data);
    } catch (Exception e) {
        ctx.getLog().error("Failed to process data for {}: {}", 
            msg.id, e.getMessage(), e);
    }
    return Behaviors.same();
})
```

---

## Performance

### Never Block in Actors

**Bad** - Blocking:
```java
.onMessage(FetchData.class, (ctx, msg) -> {
    // DON'T DO THIS!
    String data = blockingHttpClient.get("/api/data");
    return Behaviors.same();
})
```

**Good** - Async:
```java
.onMessage(FetchData.class, (ctx, msg) -> {
    CompletionStage<String> future = asyncHttpClient.get("/api/data");
    future.thenAccept(data -> 
        ctx.getSelf().tell(new DataReceived(data))
    );
    return Behaviors.same();
})
```

### Use Routers for Parallel Processing

Distribute work across multiple workers:

```java
actorSystem.router(WorkerActor.class)
    .withRoutingStrategy(RoutingStrategy.roundRobin())
    .withPoolSize(10)
    .spawn();
```

### Configure Appropriate Dispatchers

For CPU-intensive work:
```yaml
cpu-bound-dispatcher:
  type: Dispatcher
  executor: "fork-join-executor"
  fork-join-executor:
    parallelism-min: 8
    parallelism-max: 16
```

For blocking I/O:
```yaml
blocking-io-dispatcher:
  type: Dispatcher
  executor: "thread-pool-executor"
  thread-pool-executor:
    core-pool-size-min: 10
    core-pool-size-max: 100
```

### Batch Messages When Possible

Instead of processing one item at a time:

```java
// Good - batch processing
public record ProcessBatch(List<Item> items) implements Command {}

.onMessage(ProcessBatch.class, (ctx, msg) -> {
    msg.items.forEach(this::processItem);
    return Behaviors.same();
})
```

---

## Testing

### Use Pekko TestKit

For unit testing actors:

```java
@Test
void testActorBehavior() {
    ActorTestKit testKit = ActorTestKit.create();
    try {
        TestProbe<Response> probe = testKit.createTestProbe();
        ActorRef<Command> actor = testKit.spawn(MyActor.create());
        
        actor.tell(new ProcessData("test", probe.ref()));
        
        probe.expectMessage(Duration.ofSeconds(3), 
            new Response("processed"));
    } finally {
        testKit.shutdownTestKit();
    }
}
```

### Test Happy Path and Failures

Test both success and error scenarios:

```java
@Test
void shouldHandleSuccess() {
    // Test successful processing
}

@Test
void shouldHandleTimeout() {
    // Test timeout scenario
}

@Test
void shouldHandleInvalidData() {
    // Test error handling
}
```

### Use Integration Tests for Clusters

Test cluster behavior with multiple nodes:

```java
@Test
void testClusterFormation() {
    // Start multiple nodes
    // Verify cluster formation
    // Test message routing
}
```

---

## Deployment

### Use Environment-Specific Configuration

```yaml
# Development
spring:
  profiles: dev
  actor:
    pekko:
      actor:
        provider: local

---
# Production
spring:
  profiles: prod
  actor:
    pekko:
      actor:
        provider: cluster
```

### Configure Health Checks

For Kubernetes/container orchestration:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  health:
    defaults:
      enabled: true
```

### Monitor with Metrics

Enable metrics module:

```gradle
implementation 'io.github.seonwkim:spring-boot-starter-actor-metrics:0.8.0'
```

Monitor key metrics:
- `actor.processing-time`
- `system.active-actors`
- `mailbox.size`
- `dispatcher.threads.active`

### Use Graceful Shutdown

Configure shutdown timeout:

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

---

## Security

### Enable TLS for Cluster Communication

```yaml
spring:
  actor:
    pekko:
      remote:
        artery:
          transport: tls-tcp
          ssl:
            enabled: true
            key-store: /path/to/keystore.jks
            key-store-password: ${KEYSTORE_PASSWORD}
            trust-store: /path/to/truststore.jks
            trust-store-password: ${TRUSTSTORE_PASSWORD}
```

### Validate Message Input

Always validate incoming data:

```java
.onMessage(ProcessData.class, (ctx, msg) -> {
    if (msg.data == null || msg.data.isEmpty()) {
        ctx.getLog().warn("Invalid data received");
        return Behaviors.same();
    }
    // Process valid data
    return Behaviors.same();
})
```

### Don't Log Sensitive Data

Avoid logging passwords, tokens, or PII:

```java
// Bad
ctx.getLog().info("Processing user: {}", user); // May contain sensitive data

// Good
ctx.getLog().info("Processing user: {}", user.getId()); // Only ID
```

### Use Secrets Management

Never hardcode secrets:

```yaml
# Bad
spring:
  actor:
    pekko:
      remote:
        artery:
          ssl:
            key-store-password: "mypassword"  # DON'T DO THIS

# Good - use environment variables
spring:
  actor:
    pekko:
      remote:
        artery:
          ssl:
            key-store-password: ${KEYSTORE_PASSWORD}
```

---

## Production Checklist

Before deploying to production:

- [ ] Enable TLS for cluster communication
- [ ] Configure appropriate supervision strategies
- [ ] Set up monitoring and metrics
- [ ] Configure health checks
- [ ] Enable graceful shutdown
- [ ] Set appropriate timeouts
- [ ] Test cluster formation and failure scenarios
- [ ] Configure proper logging levels
- [ ] Secure sensitive configuration
- [ ] Document actor lifecycle and message flows
- [ ] Load test under expected traffic
- [ ] Set up alerting for critical metrics
- [ ] Plan for rolling updates
- [ ] Document disaster recovery procedures

---

## Common Anti-Patterns to Avoid

### ❌ Blocking in Actors

```java
// Don't block the actor thread
String result = httpClient.get("/api/data").block();
```

### ❌ Shared Mutable State

```java
// Don't share state between actors
static final Map<String, Data> sharedCache = new HashMap<>();
```

### ❌ Deeply Nested Actor Hierarchies

```java
// Avoid too many levels (>3-4)
parent -> child -> grandchild -> greatgrandchild -> ...
```

### ❌ Fat Messages

```java
// Don't send large objects
public record HugeMessage(byte[] largeData) {} // Multiple MB
```

### ❌ Ignoring Timeouts

```java
// Always handle timeouts
actor.ask(msg).withTimeout(Duration.ofSeconds(5)).execute();
// What if it times out? Handle it!
```

---

## Summary

Key principles:
1. **Keep actors focused** - Single responsibility
2. **Messages are immutable** - Use records
3. **Never block** - Always async
4. **Handle errors** - Use supervision
5. **Test thoroughly** - Happy path and failures
6. **Monitor in production** - Metrics and health checks
7. **Secure cluster** - TLS and validation
8. **Plan for failure** - Graceful degradation

---

For more information:
- [Troubleshooting Guide](troubleshooting.md)
- [FAQ](faq.md)
- [Examples](examples/index.md)
