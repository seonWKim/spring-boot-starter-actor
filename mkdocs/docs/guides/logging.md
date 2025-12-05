# Logging with MDC and Tags

This guide explains how to use MDC (Mapped Diagnostic Context) and actor tags to enhance observability and debugging in your actor-based applications.

## What is MDC?

MDC (Mapped Diagnostic Context) is a mechanism for adding contextual information to log entries. It allows you to attach key-value pairs to logs, making it easier to trace requests, correlate events, and filter logs in production environments.

**Common use cases for MDC include:**

- Request and correlation IDs for distributed tracing
- User identifiers for auditing
- Session IDs for tracking user sessions
- Transaction IDs for monitoring business operations

## What are Actor Tags?

Actor tags are labels that you can attach to actors for categorization and filtering. Tags appear in the `pekkoTags` MDC property and are particularly useful for:

- Grouping actors by role (worker, supervisor, coordinator)
- Identifying workload characteristics (cpu-intensive, io-bound)
- Categorizing by service (order-service, payment-service)
- Marking priority levels (critical, high-priority, low-priority)

!!! tip "Tag Usage"
    Use tags liberally to categorize actors. They're lightweight and make filtering logs much easier in production.

## Automatic MDC Values

Pekko automatically adds these MDC properties to all actor log entries:

| Property | Description | Example |
|----------|-------------|---------|
| `pekkoSource` | The actor's path | `pekko://MySystem/user/my-actor` |
| `pekkoAddress` | The ActorSystem address | `pekko://MySystem@localhost:25520` |
| `pekkoTags` | Comma-separated list of tags | `worker,high-priority` |
| `sourceActorSystem` | The ActorSystem name | `MySystem` |

## Using Static MDC

Static MDC values are set when spawning an actor and remain constant throughout the actor's lifetime.

### Spawning with Static MDC

```java
@Service
public class OrderService {

    private final SpringActorSystem actorSystem;

    public OrderService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public void createOrderProcessor(String userId, String sessionId) {
        Map<String, String> mdc = Map.of(
            "userId", userId,
            "sessionId", sessionId,
            "service", "order-service"
        );

        SpringActorRef<OrderActor.Command> processor = actorSystem
            .actor(OrderActor.class)
            .withId("order-processor-" + userId)
            .withMdc(MdcConfig.of(mdc))
            .spawnAndWait();
    }
}
```

All log entries from this actor will include the static MDC values:

```
[INFO] [userId=user-123] [sessionId=session-abc] [service=order-service] Processing order
```

!!! note "Static vs Dynamic"
    Use static MDC for values that remain constant throughout the actor's lifetime. For per-message values, use dynamic MDC instead.

## Using Dynamic MDC

Dynamic MDC values are computed per-message, allowing you to add message-specific context to logs.

### Adding Dynamic MDC to Actor Behavior

```java
@Component
public class OrderActor implements SpringActor<OrderActor.Command> {

    public interface Command {}

    public static class ProcessOrder implements Command {
        public final String orderId;
        public final String userId;

        public ProcessOrder(String orderId, String userId) {
            this.orderId = orderId;
            this.userId = userId;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withMdc(msg -> {
                if (msg instanceof ProcessOrder) {
                    ProcessOrder order = (ProcessOrder) msg;
                    return Map.of(
                        "orderId", order.orderId,
                        "userId", order.userId
                    );
                }
                return Map.of();
            })
            .onMessage(ProcessOrder.class, (ctx, msg) -> {
                // orderId and userId are now in MDC
                ctx.getLog().info("Processing order");
                return Behaviors.same();
            })
            .build();
    }
}
```

Log output includes dynamic values:

```
[INFO] [orderId=order-456] [userId=cust-789] Processing order
```

!!! tip "Dynamic MDC Pattern"
    Dynamic MDC is perfect for per-message context like request IDs, order IDs, or any data that varies with each message.

## Combining Static and Dynamic MDC

You can use both static and dynamic MDC together. Static values provide actor-level context, while dynamic values add message-level context.

```java
// Spawn with static MDC
Map<String, String> staticMdc = Map.of(
    "service", "payment-service",
    "region", "us-east-1"
);

SpringActorRef<PaymentActor.Command> actor = actorSystem
    .actor(PaymentActor.class)
    .withId("payment-processor")
    .withMdc(MdcConfig.of(staticMdc))
    .spawnAndWait();
```

```java
// Actor with dynamic MDC
@Component
public class PaymentActor implements SpringActor<PaymentActor.Command> {

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withMdc(msg -> {
                if (msg instanceof ProcessPayment) {
                    ProcessPayment payment = (ProcessPayment) msg;
                    return Map.of(
                        "paymentId", payment.paymentId,
                        "amount", String.valueOf(payment.amount)
                    );
                }
                return Map.of();
            })
            .onMessage(ProcessPayment.class, (ctx, msg) -> {
                // Both static and dynamic MDC values are available
                ctx.getLog().info("Processing payment");
                return Behaviors.same();
            })
            .build();
    }
}
```

Log output combines both:
```
[INFO] [service=payment-service] [region=us-east-1] [paymentId=pay-123] [amount=99.99] Processing payment
```

## Using Actor Tags

Actor tags provide a way to categorize actors for easier log filtering and analysis.

### Spawning with Tags

```java
// Single tag
SpringActorRef<Worker.Command> worker = actorSystem
    .actor(Worker.class)
    .withId("worker-1")
    .withTags(TagsConfig.of("worker"))
    .spawnAndWait();

// Multiple tags
SpringActorRef<Worker.Command> priorityWorker = actorSystem
    .actor(Worker.class)
    .withId("worker-2")
    .withTags(TagsConfig.of("worker", "high-priority", "cpu-intensive"))
    .spawnAndWait();

// Tags from a set
Set<String> tags = Set.of("worker", "backend");
SpringActorRef<Worker.Command> backendWorker = actorSystem
    .actor(Worker.class)
    .withId("worker-3")
    .withTags(TagsConfig.of(tags))
    .spawnAndWait();
```

Tags appear in the `pekkoTags` MDC property:
```
[INFO] [pekkoSource=pekko://MySystem/user/worker-2] [pekkoTags=worker,high-priority,cpu-intensive] Processing task
```

### Common Tag Categories

Organize your actors using meaningful tag categories:

```java
// By role
TagsConfig.of("worker", "coordinator", "supervisor")

// By priority
TagsConfig.of("critical", "high-priority", "low-priority")

// By service
TagsConfig.of("order-service", "payment-service", "notification-service")

// By workload
TagsConfig.of("cpu-intensive", "io-bound", "memory-intensive")

// By environment
TagsConfig.of("production", "staging", "development")
```

## Child Actors with MDC and Tags

Child actors can have their own MDC values and tags independent of their parents.

```java
@Component
public class ParentActor implements SpringActor<ParentActor.Command> {

    public interface Command extends FrameworkCommand {}

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> {
                SpringActorRef<Command> self = new SpringActorRef<>(
                    ctx.getSystem().scheduler(), ctx.getSelf());

                // Spawn child with its own MDC and tags
                self.child(ChildActor.class)
                    .withId("worker-child")
                    .withTags(TagsConfig.of("child", "worker"))
                    .withMdc(MdcConfig.of(Map.of("childId", "worker-1")))
                    .spawn();

                return new ParentBehavior(ctx, actorContext);
            })
            .build();
    }
}
```

Child actors do not inherit parent MDC values, ensuring isolation:
- Parent logs: `[parentId=parent-1] [sessionId=session-abc]`
- Child logs: `[childId=worker-1] [pekkoTags=child,worker]`

## Configuring Logback for MDC

Configure your `logback.xml` to display MDC values in log output.

### Basic Logback Configuration

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} %-5level [%X{pekkoSource}] [%X{pekkoTags}] [%X{userId}] [%X{requestId}] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

### Async Appender for Production

Use async logging to avoid blocking actors:

```xml
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{ISO8601} %-5level [%X{pekkoSource}] [%X{pekkoTags}] %X{userId} %X{requestId} - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>1024</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <appender-ref ref="FILE"/>
    </appender>

    <root level="INFO">
        <appender-ref ref="ASYNC"/>
    </root>
</configuration>
```

### JSON Logging for Structured Logs

For production environments and log aggregation systems:

```xml
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>pekkoSource</includeMdcKeyName>
            <includeMdcKeyName>pekkoAddress</includeMdcKeyName>
            <includeMdcKeyName>pekkoTags</includeMdcKeyName>
            <includeMdcKeyName>sourceActorSystem</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>requestId</includeMdcKeyName>
            <includeMdcKeyName>orderId</includeMdcKeyName>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

Add the dependency in `build.gradle`:
```gradle
implementation 'net.logstash.logback:logstash-logback-encoder:7.4'
```

## Best Practices

### 1. Use Static MDC for Actor-Level Context

Use static MDC for values that remain constant throughout an actor's lifetime:
```java
// Good: Static values like userId, service name
.withMdc(MdcConfig.of(Map.of("userId", userId, "service", "orders")))
```

### 2. Use Dynamic MDC for Message-Level Context

Use dynamic MDC for values that change per message:
```java
// Good: Message-specific values like requestId, orderId
.withMdc(msg -> Map.of("requestId", msg.getRequestId()))
```

### 3. Use Tags for Categorization

Use tags to group and filter actors by characteristics:
```java
// Good: Categorize by role, priority, and workload
.withTags(TagsConfig.of("worker", "high-priority", "cpu-intensive"))
```

### 4. Keep MDC Keys Consistent

Use consistent naming conventions for MDC keys across your application:
```java
// Good: Consistent naming
"userId", "requestId", "orderId", "sessionId"

// Bad: Inconsistent naming
"user_id", "request-id", "OrderID", "SESSION_ID"
```

### 5. Avoid Sensitive Data in Logs

Never include sensitive information in MDC values:
```java
// Bad: Sensitive data
Map.of("password", password, "creditCard", ccNumber)

// Good: Use identifiers only
Map.of("userId", userId, "transactionId", transactionId)
```

### 6. Use Async Logging in Production

Always use async appenders to prevent logging from blocking actor processing:

```xml
<appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>1024</queueSize>
    <appender-ref ref="FILE"/>
</appender>
```

!!! warning "Blocking Logging"
    Synchronous logging can significantly impact actor throughput. Always use async appenders in production.

### 7. Filter Logs by Tags

Use tags to filter logs in production:
```bash
# Filter by specific tag
grep "pekkoTags=worker" application.log

# Filter by multiple tags
grep "pekkoTags=.*high-priority.*" application.log
```

### 8. Monitor MDC Overhead

Be mindful of the number of MDC keys to avoid performance impact:
```java
// Good: 3-5 MDC keys
Map.of("userId", userId, "requestId", requestId, "service", service)

// Avoid: Too many MDC keys
Map.of("key1", val1, "key2", val2, ..., "key20", val20)
```

## Common Patterns

### Request Tracing

Track requests across multiple actors:

```java
@Component
public class RequestHandler implements SpringActor<RequestHandler.Command> {

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withMdc(msg -> {
                if (msg instanceof HandleRequest) {
                    HandleRequest request = (HandleRequest) msg;
                    return Map.of(
                        "requestId", request.requestId,
                        "traceId", request.traceId,
                        "spanId", request.spanId
                    );
                }
                return Map.of();
            })
            .onMessage(HandleRequest.class, (ctx, msg) -> {
                ctx.getLog().info("Handling request");
                // requestId, traceId, spanId are in MDC
                return Behaviors.same();
            })
            .build();
    }
}
```

### User Session Tracking

Track user sessions throughout the application:

```java
SpringActorRef<SessionActor.Command> sessionActor = actorSystem
    .actor(SessionActor.class)
    .withId("session-" + sessionId)
    .withMdc(MdcConfig.of(Map.of(
        "sessionId", sessionId,
        "userId", userId,
        "ipAddress", ipAddress
    )))
    .spawnAndWait();
```

### Service Categorization

Organize actors by service:

```java
// Order service actors
actorSystem.actor(OrderProcessor.class)
    .withId("order-processor")
    .withTags(TagsConfig.of("order-service", "critical"))
    .withMdc(MdcConfig.of(Map.of("service", "orders")))
    .spawn();

// Payment service actors
actorSystem.actor(PaymentProcessor.class)
    .withId("payment-processor")
    .withTags(TagsConfig.of("payment-service", "critical"))
    .withMdc(MdcConfig.of(Map.of("service", "payments")))
    .spawn();
```

## Integration with Observability Tools

### Elastic Stack (ELK)

Use JSON logging with Logstash encoder for easy ingestion:
```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <customFields>{"application":"my-app","environment":"production"}</customFields>
</encoder>
```

### Distributed Tracing

Add trace and span IDs to MDC for correlation with tracing systems:
```java
.withMdc(msg -> Map.of(
    "traceId", msg.getTraceId(),
    "spanId", msg.getSpanId()
))
```

### Log Aggregation

Use consistent tag naming for easier queries:
```java
// Query in Kibana/Grafana
pekkoTags:worker AND service:orders AND environment:production
```

## Troubleshooting

### MDC Values Not Appearing in Logs

Check your Logback pattern includes `%X{keyName}`:
```xml
<pattern>%d{ISO8601} [%X{userId}] [%X{requestId}] - %msg%n</pattern>
```

### Tags Not Showing Up

Verify you're using `.withTags()` when spawning:
```java
.withTags(TagsConfig.of("worker"))  // Required
```

And include `%X{pekkoTags}` in your pattern:
```xml
<pattern>[%X{pekkoTags}] %msg%n</pattern>
```

### Performance Issues with Logging

Use async appenders and appropriate queue sizes:
```xml
<appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>1024</queueSize>
    <neverBlock>true</neverBlock>
</appender>
```

## More Information

For more information about logging and observability:

- [Actor Registration and Messaging](actor-registration-messaging.md) - Learn how to create and spawn actors
- [Dispatchers](dispatchers.md) - Configure thread execution for your actors
- [Pekko Typed Logging Documentation](https://pekko.apache.org/docs/pekko/current/typed/logging.html)
- [Logback Configuration](http://logback.qos.ch/manual/configuration.html)

## Next Steps

- [Sharded Actors](sharded-actors.md) - Learn about distributed entity management in clusters
- [Persistence with Spring Boot](persistence-spring-boot.md) - Integrate actors with Spring Data
