# Synchronization Example

This guide demonstrates how to implement efficient synchronization using Spring Boot Starter Actor, comparing it with traditional synchronization approaches.

## Overview

The synchronization example shows how to:

- Implement counters with different synchronization mechanisms
- Compare database locking, Redis locking, and actor-based synchronization
- Handle concurrent access to shared resources
- Achieve high performance with actor-based synchronization

This example demonstrates why using actors for synchronization is cheap and efficient compared to other approaches.

## Source Code

You can find the complete source code for this example on GitHub:

[Synchronization Example Source Code](https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/synchronization)

!!! tip "Performance Comparison"
    This example includes benchmarking code to compare the performance of different synchronization approaches.

## Key Components

The example implements a counter service using three different synchronization approaches:

1. **Database Locking**: Uses database transactions and locks
2. **Redis Locking**: Uses Redis for distributed locking
3. **Actor-Based Synchronization**: Uses actors for message-based synchronization

Let's examine each approach to understand their differences and trade-offs.

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

### Database Synchronization

`DbCounterService` uses database transactions and pessimistic locking to ensure synchronized access to counters:

```java
@Service
public class DbCounterService implements CounterService {
    private final CounterRepository counterRepository;
    private final CustomTransactionTemplate customTransactionTemplate;

    public DbCounterService(
            CounterRepository counterRepository, 
            CustomTransactionTemplate customTransactionTemplate) {
        this.counterRepository = counterRepository;
        this.customTransactionTemplate = customTransactionTemplate;
    }

    @Override
    public void increment(String counterId) {
        incrementInternal(counterId);
    }

    @Override
    public Mono<Long> getValue(String counterId) {
        return Mono.fromCallable(() -> getValueInternal(counterId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Long incrementInternal(String counterId) {
        return customTransactionTemplate.runInTransaction(
                () -> {
                    // Find counter with lock or create new one
                    Counter counter =
                            counterRepository
                                    .findByIdWithLock(counterId)
                                    .orElseGet(
                                            () -> {
                                                return counterRepository.save(new Counter(counterId, 0));
                                            });

                    // Increment and save
                    long newValue = counter.increment();
                    counterRepository.save(counter);

                    return newValue;
                });
    }

    public Long getValueInternal(String counterId) {
        return counterRepository.findById(counterId).map(Counter::getValue).orElse(0L);
    }
}
```

### Redis Synchronization

`RedisCounterService` uses Redis distributed locking to ensure synchronized access to counters:

```java
@Service
public class RedisCounterService implements CounterService {
    private static final String COUNTER_KEY_PREFIX = "counter:";
    private static final String LOCK_KEY_PREFIX = "counter:lock:";
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RETRY_DELAY = Duration.ofMillis(100);
    private static final int MAX_RETRIES = 50; // 5 seconds total retry time

    private final ReactiveRedisTemplate<String, Long> redisTemplate;
    private final ReactiveValueOperations<String, Long> valueOps;

    public RedisCounterService(ReactiveRedisTemplate<String, Long> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
    }

    @Override
    public void increment(String counterId) {
        String counterKey = COUNTER_KEY_PREFIX + counterId;
        String lockKey = LOCK_KEY_PREFIX + counterId;

        valueOps
                .setIfAbsent(lockKey, 1L, LOCK_TIMEOUT)
                .flatMap(locked -> {
                    if (!locked) {
                        // Failed to acquire lock, retry
                        return Mono.error(new RuntimeException("Failed to acquire lock"));
                    }

                    return valueOps
                            .increment(counterKey)
                            .doFinally(signalType ->
                                       // Release lock when done
                                       redisTemplate.delete(lockKey).subscribe());
                })
                .retryWhen(
                        Retry.backoff(MAX_RETRIES, RETRY_DELAY))
                .subscribe();
    }

    @Override
    public Mono<Long> getValue(String counterId) {
        String counterKey = COUNTER_KEY_PREFIX + counterId;
        // Get the counter value or return 0 if it doesn't exist
        return valueOps.get(counterKey).defaultIfEmpty(0L);
    }
}
```

### CounterActor

`CounterActor` is a sharded actor that handles counter operations. Each counter is represented by a separate actor instance, identified by its counterId:

```java
@Component
public class CounterActor implements SpringShardedActor<CounterActor.Command> {
    public static final EntityTypeKey<Command> TYPE_KEY =
            EntityTypeKey.create(Command.class, "CounterActor");

    // Command interface and message types
    public interface Command extends JsonSerializable {}

    public static class Increment implements Command {

        @JsonCreator
        public Increment() {}
    }

    public static class GetValue extends AskCommand<Long> implements Command {
        @JsonCreator
        public GetValue() {}
    }

    // Implementation of SpringShardedActor methods...

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
            msg.reply(value);
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
                springActorSystem.sharded(CounterActor.class).withId(counterId).get();

        // Send an increment message to the actor
        actorRef.tell(new CounterActor.Increment());
    }

    @Override
    public Mono<Long> getValue(String counterId) {
        // Get a reference to the sharded actor for this counter
        SpringShardedActorRef<CounterActor.Command> actorRef =
                springActorSystem.sharded(CounterActor.class).withId(counterId).get();

        // Send a get value message to the actor and get the response
        CompletionStage<Long> response = actorRef
                .ask(new CounterActor.GetValue())
                .withTimeout(Duration.ofSeconds(3))
                .execute();

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

## Actor Efficiency

Actors provide efficient synchronization through:

- **Message-based concurrency** without explicit locks
- **Non-blocking asynchronous processing**
- **Independent actors** that scale across nodes
- **Reduced contention** between different counters
- **Built-in supervision** and error handling

!!! success "Performance Benefits"
    Actor-based synchronization typically outperforms database and Redis locking by 10-100x in high-concurrency scenarios.

## Performance Comparison

When benchmarked under high concurrency:

1. **Database Locking** - Slowest due to transaction overhead and lock contention
2. **Redis Locking** - Better but has network overhead and retry loops
3. **Actor-Based Synchronization** - Fastest with in-memory processing and no locks

!!! info "Benchmark Results"
    Run the example and use a tool like Apache JMeter or wrk to benchmark the different endpoints under load.

## Next Steps

- [Cluster Example](cluster.md) - Distribute actors across multiple nodes
- [Chat Example](chat.md) - Build real-time applications with actors
- [Actor Registration Guide](../guides/actor-registration-messaging.md) - Learn more about actor messaging patterns
