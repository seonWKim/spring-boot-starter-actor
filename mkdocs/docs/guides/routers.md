# Routers

This guide explains how to use routers to distribute messages across multiple worker actors for load balancing and parallel processing.

## What are Routers?

A router is a special actor that distributes incoming messages across a pool of worker actors. Instead of sending messages directly to a single actor, you send them to the router, which forwards them to one of its workers based on a routing strategy.

**Routers are ideal for:**

- **Load balancing** - Distributing work evenly across multiple workers
- **Parallel processing** - Processing messages concurrently
- **Scalability** - Easily scale by increasing the pool size
- **Fault tolerance** - Worker failures don't affect the router

## Creating a Router

### Define Your Worker Actor

First, create a worker actor that will process messages:

```java
@Component
public class WorkerActor implements SpringActor<ProcessorRouter.Command> {

    @Autowired
    private WorkerState state;

    @Override
    public SpringActorBehavior<ProcessorRouter.Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(ProcessorRouter.Command.class, ctx)
            .onMessage(ProcessorRouter.ProcessMessage.class, (context, msg) -> {
                // Process the message
                String result = processData(msg.data);
                context.getLog().info("Processed: {}", result);
                return Behaviors.same();
            })
            .build();
    }

    private String processData(String data) {
        // Your processing logic here
        return "Processed: " + data;
    }
}
```

### Create the Router Actor

Create a router actor that uses `SpringRouterBehavior`:

```java
@Component
public class ProcessorRouter implements SpringActor<ProcessorRouter.Command> {

    public interface Command {}

    public static class ProcessMessage implements Command {
        public final String data;

        public ProcessMessage(String data) {
            this.data = data;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringRouterBehavior.builder(Command.class, ctx)
            .withRoutingStrategy(RoutingStrategy.roundRobin())
            .withPoolSize(10)  // 10 worker actors
            .withWorkerActors(WorkerActor.class)
            .build();
    }
}
```

### Use the Router

Spawn and use the router like any other actor:

```java
@Service
public class ProcessingService {
    private final SpringActorSystem actorSystem;

    public ProcessingService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public void processData(String data) {
        actorSystem.getOrSpawn(ProcessorRouter.class, "processor")
            .thenAccept(router ->
                router.tell(new ProcessorRouter.ProcessMessage(data))
            );
    }
}
```

## Routing Strategies

Spring Boot Starter Actor provides four routing strategies, all wrapping Apache Pekko's built-in routers.

### Round Robin

Distributes messages evenly across workers in a circular fashion. This is the **default and most commonly used** strategy.

```java
return SpringRouterBehavior.builder(Command.class, ctx)
    .withRoutingStrategy(RoutingStrategy.roundRobin())
    .withPoolSize(5)
    .withWorkerActors(WorkerActor.class)
    .build();
```

**Message distribution:**

- Message 1 → Worker 1
- Message 2 → Worker 2
- Message 3 → Worker 3
- Message 4 → Worker 1 (cycles back)

**Best for:**

- Equal distribution of work
- Predictable load balancing
- Tasks with similar processing time

### Random

Distributes messages randomly across workers. Very lightweight with no state tracking.

```java
return SpringRouterBehavior.builder(Command.class, ctx)
    .withRoutingStrategy(RoutingStrategy.random())
    .withPoolSize(5)
    .withWorkerActors(WorkerActor.class)
    .build();
```

**Best for:**

- Simple distribution without state tracking
- Non-critical workloads
- Avoiding ordering effects

### Broadcast

Sends **all messages to all workers**. Each worker receives every message.

```java
return SpringRouterBehavior.builder(Command.class, ctx)
    .withRoutingStrategy(RoutingStrategy.broadcast())
    .withPoolSize(3)
    .withWorkerActors(CacheActor.class)
    .build();
```

!!! warning "Message Multiplication"
    Message volume increases by pool size factor (10 messages × 5 workers = 50 total messages processed).

**Best for:**

- Cache invalidation across all workers
- Configuration updates
- Notifications that all workers need to receive
- Coordinated state updates

!!! caution "Performance Impact"
    Use sparingly for high-volume systems due to the message multiplication effect.

### Consistent Hashing

Routes messages with the same hash key to the same worker, enabling **session affinity** and stateful processing.

#### Messages with ConsistentHashable

Implement the `ConsistentHashable` interface for explicit hash keys:

```java
public static class ProcessOrder implements Command, ConsistentHashable {
    private final String customerId;
    private final String orderId;

    public ProcessOrder(String customerId, String orderId) {
        this.customerId = customerId;
        this.orderId = orderId;
    }

    @Override
    public String getConsistentHashKey() {
        return customerId;  // Same customer always goes to same worker
    }
}
```

#### Create Consistent Hashing Router

```java
return SpringRouterBehavior.builder(Command.class, ctx)
    .withRoutingStrategy(RoutingStrategy.consistentHashing())
    .withPoolSize(10)
    .withWorkerActors(OrderWorkerActor.class)
    .build();
```

**With custom virtual nodes factor:**

```java
// Higher virtual nodes = better distribution but more memory
.withRoutingStrategy(RoutingStrategy.consistentHashing(20))
```

**Virtual nodes factor** (default: 10):
- Higher values (10-20) = better distribution, more memory
- Lower values (1-5) = less memory, potential hotspots

**Messages without ConsistentHashable:**

If your message doesn't implement `ConsistentHashable`, the router uses `toString()` as the hash key.

**Best for:**

- User session management (same userId → same worker)
- Entity-based processing (same orderId → same worker)
- Stateful message processing
- Cache locality optimization

## Worker Supervision

Configure how workers are supervised when they fail:

```java
return SpringRouterBehavior.builder(Command.class, ctx)
    .withRoutingStrategy(RoutingStrategy.roundRobin())
    .withPoolSize(10)
    .withWorkerActors(WorkerActor.class)
    .withSupervisionStrategy(SupervisorStrategy.restart())  // Restart failed workers
    .build();
```

**Available strategies:**

- `restart()` - Restart the worker (default)
- `stop()` - Stop the worker permanently
- `resume()` - Resume processing, ignoring the failure

**With limits:**

```java
// Restart up to 3 times within 1 minute, then stop
.withSupervisionStrategy(
    SupervisorStrategy.restart().withLimit(3, Duration.ofMinutes(1))
)
```

## Spring Dependency Injection in Workers

Worker actors are **full Spring components** with dependency injection support:

```java
@Component
public class DatabaseWorkerActor implements SpringActor<Command> {

    @Autowired
    private DatabaseService databaseService;  // Spring DI!

    @Autowired
    private MetricsService metricsService;    // Spring DI!

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(SaveData.class, (context, msg) -> {
                // Use injected Spring beans
                databaseService.save(msg.data);
                metricsService.recordSave();
                return Behaviors.same();
            })
            .build();
    }
}
```

All workers in the pool share the same Spring bean instances (singletons), enabling:
- Shared caching
- Connection pooling
- Centralized metrics

## Ask Pattern with Routers

Routers support the ask pattern for request-response messaging:

```java
@Component
public class ValidationRouter implements SpringActor<ValidationRouter.Command> {

    public interface Command {}

    public static class ValidateData extends AskCommand<Boolean> implements Command {
        public final String data;

        public ValidateData(String data) {
            this.data = data;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringRouterBehavior.builder(Command.class, ctx)
            .withRoutingStrategy(RoutingStrategy.roundRobin())
            .withPoolSize(5)
            .withWorkerActors(ValidationWorkerActor.class)
            .build();
    }
}
```

**Using ask pattern:**

```java
public Mono<Boolean> validateData(String data) {
    return Mono.fromCompletionStage(
        actorSystem.getOrSpawn(ValidationRouter.class, "validator")
            .thenCompose(router ->
                router.ask(new ValidationRouter.ValidateData(data))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
            )
    );
}
```

## Complete Example

Here's a complete example of a processing pipeline with routers:

```java
// Worker actor
@Component
public class OrderProcessorWorker implements SpringActor<OrderRouter.Command> {

    @Autowired
    private OrderService orderService;

    @Override
    public SpringActorBehavior<OrderRouter.Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(OrderRouter.Command.class, ctx)
            .onMessage(OrderRouter.ProcessOrder.class, (context, msg) -> {
                orderService.processOrder(msg.orderId, msg.customerId);
                context.getLog().info("Processed order {} for customer {}",
                    msg.orderId, msg.customerId);
                return Behaviors.same();
            })
            .build();
    }
}

// Router actor with consistent hashing
@Component
public class OrderRouter implements SpringActor<OrderRouter.Command> {

    public interface Command {}

    public static class ProcessOrder implements Command, ConsistentHashable {
        public final String orderId;
        public final String customerId;

        public ProcessOrder(String orderId, String customerId) {
            this.orderId = orderId;
            this.customerId = customerId;
        }

        @Override
        public String getConsistentHashKey() {
            return customerId;  // Same customer always goes to same worker
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringRouterBehavior.builder(Command.class, ctx)
            .withRoutingStrategy(RoutingStrategy.consistentHashing())
            .withPoolSize(10)
            .withWorkerActors(OrderProcessorWorker.class)
            .withSupervisionStrategy(SupervisorStrategy.restart())
            .build();
    }
}

// Service using the router
@Service
public class OrderService {
    private final SpringActorSystem actorSystem;

    public OrderService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public void processOrder(String orderId, String customerId) {
        actorSystem.getOrSpawn(OrderRouter.class, "order-processor")
            .thenAccept(router ->
                router.tell(new OrderRouter.ProcessOrder(orderId, customerId))
            );
    }
}
```

## More Information

For more details about Pekko routers, see the [Pekko Typed Routers Documentation](https://pekko.apache.org/docs/pekko/current/typed/routers.html).

## Next Steps

- [Dispatchers](dispatchers.md) - Configure thread execution for your actors
- [Actor Registration](actor-registration-messaging.md) - Learn how to create and spawn actors
- [Sharded Actors](sharded-actors.md) - Distributed entity management in clusters
