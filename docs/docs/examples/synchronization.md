# Synchronization Example

This guide demonstrates how to implement efficient synchronization using Spring Boot Starter Actor, comparing it with traditional synchronization approaches.

## Overview

The synchronization example shows how to:

- Implement counters with different synchronization mechanisms
- Compare database locking, Redis locking, and actor-based synchronization
- Handle concurrent access to shared resources
- Achieve high performance with actor-based synchronization

This example demonstrates why using actors for synchronization is cheap and efficient compared to other approaches.

## Key Components

The example implements a counter service using three different synchronization approaches:

1. **Database Locking**: Uses database transactions and locks
2. **Redis Locking**: Uses Redis for distributed locking
3. **Actor-Based Synchronization**: Uses actors for message-based synchronization

### Counter Entity

The `Counter` class is a simple JPA entity representing a counter in the database:

```java
@Entity
@Table(name = "counter")
public class Counter {
    @Id
    @Column(name = "counter_id", nullable = false)
    private String counterId;

    @Column(name = "value", nullable = false)
    private long value;

    // Constructors, getters, setters...

    public long increment() {
        return ++value;
    }
}
```

### CounterActor

`CounterActor` is a sharded actor that handles counter operations. Each counter is represented by a separate actor instance, identified by its counterId:

```java
@Component
public class CounterActor implements ShardedActor<CounterActor.Command> {
    public static final EntityTypeKey<Command> TYPE_KEY =
            EntityTypeKey.create(Command.class, "CounterActor");

    // Command interface and message types
    public interface Command extends JsonSerializable {}

    public static class Increment implements Command {

        @JsonCreator
        public Increment() {}
    }

    public static class GetValue implements Command {
        public final ActorRef<Long> replyTo;

        @JsonCreator
        public GetValue(@JsonProperty("replyTo") ActorRef<Long> replyTo) {
            this.replyTo = replyTo;
        }
    }

    // Implementation of ShardedActor methods...

    private static class CounterActorBehavior {
        private final ActorContext<Command> ctx;
        private final String counterId;
        private long value = 0;

        // Constructor and behavior creation...

        private Behavior<Command> onIncrement(Increment msg) {
            value++;
            return Behaviors.same();
        }

        private Behavior<Command> onGetValue(GetValue msg) {
            msg.replyTo.tell(value);
            return Behaviors.same();
        }
    }
}
```

### ActorCounterService

`ActorCounterService` uses actor-based synchronization to handle counter operations:

```java
@Service
public class ActorCounterService implements CounterService {
    private final SpringActorSystem springActorSystem;

    public ActorCounterService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    @Override
    public void increment(String counterId) {
        // Get a reference to the sharded actor for this counter
        SpringShardedActorRef<CounterActor.Command> actorRef =
                springActorSystem.entityRef(CounterActor.TYPE_KEY, counterId);

        // Send an increment message to the actor
        actorRef.tell(new CounterActor.Increment());
    }

    @Override
    public Mono<Long> getValue(String counterId) {
        // Get a reference to the sharded actor for this counter
        SpringShardedActorRef<CounterActor.Command> actorRef =
                springActorSystem.entityRef(CounterActor.TYPE_KEY, counterId);

        // Send a get value message to the actor and get the response
        CompletionStage<Long> response =
                actorRef.ask(replyTo -> new CounterActor.GetValue(replyTo), TIMEOUT);

        return Mono.fromCompletionStage(response);
    }
}
```

### CounterController

`CounterController` exposes the counter functionality via HTTP endpoints for all three synchronization approaches:

```java
@RestController
@RequestMapping("/counter")
public class CounterController {
    private final DbCounterService dbCounterService;
    private final RedisCounterService redisCounterService;
    private final ActorCounterService actorCounterService;

    // Constructor and endpoint methods for each service...

    @GetMapping("/actor/{counterId}/increment")
    public void incrementActorCounter(@PathVariable String counterId) {
        actorCounterService.increment(counterId);
    }

    @GetMapping("/actor/{counterId}")
    public Mono<Long> getActorCounter(@PathVariable String counterId) {
        return actorCounterService.getValue(counterId);
    }
}
```

## Why Actors Are Efficient for Synchronization

### 1. Message-Based Concurrency

Actors use a message-based concurrency model where:

- Each actor processes one message at a time
- No explicit locks are needed
- The actor's state is never shared directly with other components

This eliminates the need for complex locking mechanisms and the associated overhead.

### 2. No Blocking

Unlike traditional synchronization approaches:

- Database locking requires database transactions, which can block other operations
- Redis locking requires network round-trips and can lead to contention
- Actors process messages asynchronously without blocking threads

### 3. Scalability

Actor-based synchronization scales well because:

- Each counter is an independent actor that can run on any node in the cluster
- The system can handle many counters simultaneously
- Adding more nodes increases the overall capacity

### 4. Reduced Contention

With actors:

- Each counter has its own dedicated actor
- Contention only occurs when multiple requests target the same counter
- Different counters can be updated concurrently without interference

### 5. Simplified Error Handling

Actors provide built-in supervision and error handling:

- If an actor fails, it can be restarted automatically
- The actor's state can be preserved or reset as needed
- Errors are isolated to individual actors

## Performance Comparison

When benchmarked under high concurrency:

1. **Database Locking**: Slowest due to transaction overhead and database contention
2. **Redis Locking**: Better than database locking but still has network overhead
3. **Actor-Based Synchronization**: Fastest due to in-memory processing and message-based concurrency

## Key Takeaways

- Actor-based synchronization is more efficient than traditional approaches for managing concurrent access to shared resources
- Actors eliminate the need for explicit locks, reducing complexity and overhead
- The message-based concurrency model naturally handles synchronization without blocking
- Spring Boot Starter Actor makes it easy to implement actor-based synchronization in Spring applications
- For high-concurrency scenarios, actors provide better performance and scalability than database or Redis locking
