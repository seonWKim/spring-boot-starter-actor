# Synchronization Example

This guide demonstrates how to implement efficient synchronization using Actor Spring Boot Starter, comparing it with traditional synchronization approaches.

## Overview

The synchronization example shows how to:

- Implement counters with different synchronization mechanisms
- Compare database locking, Redis locking, and actor-based synchronization
- Handle concurrent access to shared resources
- Achieve high performance with actor-based synchronization

This example demonstrates why using actors for synchronization is cheap and efficient compared to other approaches.

## Source Code

You can find the complete source code for this example on GitHub:

[Synchronization Example Source Code](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/synchronization)

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

    /** Default constructor required by JPA. */
    public Counter() {}

    /**
     * Creates a new counter with the given ID and initial value.
     *
     * @param counterId The ID of the counter
     * @param value The initial value of the counter
     */
    public Counter(String counterId, long value) {
        this.counterId = counterId;
        this.value = value;
    }

    /**
     * Gets the counter ID.
     *
     * @return The counter ID
     */
    public String getCounterId() {
        return counterId;
    }

    /**
     * Sets the counter ID.
     *
     * @param counterId The counter ID
     */
    public void setCounterId(String counterId) {
        this.counterId = counterId;
    }

    /**
     * Gets the counter value.
     *
     * @return The counter value
     */
    public long getValue() {
        return value;
    }

    /**
     * Sets the counter value.
     *
     * @param value The counter value
     */
    public void setValue(long value) {
        this.value = value;
    }

    /**
     * Increments the counter value by 1.
     *
     * @return The new counter value
     */
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

    private static final Logger logger = LoggerFactory.getLogger(DbCounterService.class);

    private final CounterRepository counterRepository;
    private final CustomTransactionTemplate customTransactionTemplate;

    /**
     * Creates a new DbCounterServiceImpl with the given repository.
     *
     * @param counterRepository The repository for accessing Counter entities
     */
    public DbCounterService(CounterRepository counterRepository, CustomTransactionTemplate customTransactionTemplate) {
        this.counterRepository = counterRepository;
        this.customTransactionTemplate = customTransactionTemplate;
    }

    /**
     * Increments the counter value by 1 and returns the new value. Uses database pessimistic locking
     * to ensure synchronization. The blocking logic is delegated to a boundedElastic scheduler.
     *
     * @param counterId The ID of the counter to increment
     */
    @Override
    public void increment(String counterId) {
        incrementInternal(counterId);
    }

    /**
     * Gets the current value of the counter. The blocking logic is delegated to a boundedElastic
     * scheduler.
     *
     * @param counterId The ID of the counter to get
     * @return A Mono containing the current counter value
     */
    @Override
    public Mono<Long> getValue(String counterId) {
        return Mono.fromCallable(() -> getValueInternal(counterId)).subscribeOn(Schedulers.boundedElastic());
    }

    public Long incrementInternal(String counterId) {
        logger.debug("Incrementing counter with ID: {}", counterId);

        return customTransactionTemplate.runInTransaction(() -> {
            Counter counter = counterRepository.findByIdWithLock(counterId).orElseGet(() -> {
                logger.debug("Counter not found, creating new counter with ID: {}", counterId);
                return counterRepository.save(new Counter(counterId, 0));
            });

            long newValue = counter.increment();
            counterRepository.save(counter);

            logger.debug("Counter with ID: {} incremented to: {}", counterId, newValue);
            return newValue;
        });
    }

    public Long getValueInternal(String counterId) {
        logger.debug("Getting value for counter with ID: {}", counterId);
        return counterRepository.findById(counterId).map(Counter::getValue).orElse(0L);
    }
}
```

### Redis Synchronization

`RedisCounterService` uses Redis distributed locking to ensure synchronized access to counters:

```java
@Service
public class RedisCounterService implements CounterService {

    private static final Logger logger = LoggerFactory.getLogger(RedisCounterService.class);
    private static final String COUNTER_KEY_PREFIX = "counter:";
    private static final String LOCK_KEY_PREFIX = "counter:lock:";
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RETRY_DELAY = Duration.ofMillis(100);
    private static final int MAX_RETRIES = 50; // 5 seconds total retry time

    private final ReactiveRedisTemplate<String, Long> redisTemplate;
    private final ReactiveValueOperations<String, Long> valueOps;

    /**
     * Creates a new RedisCounterServiceImpl with the given Redis template.
     *
     * @param redisTemplate The Redis template for accessing Redis
     */
    public RedisCounterService(ReactiveRedisTemplate<String, Long> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
    }

    /**
     * Increments the counter value by 1 and returns the new value. Uses Redis distributed locking to
     * ensure synchronization.
     *
     * @param counterId The ID of the counter to increment
     */
    @Override
    public void increment(String counterId) {
        logger.debug("Incrementing counter with ID: {}", counterId);

        String counterKey = COUNTER_KEY_PREFIX + counterId;
        String lockKey = LOCK_KEY_PREFIX + counterId;

        valueOps.setIfAbsent(lockKey, 1L, LOCK_TIMEOUT)
                .flatMap(locked -> {
                    if (!locked) {
                        logger.warn("Failed to acquire lock for counter: {}", counterId);
                        return Mono.error(new RuntimeException("Failed to acquire lock for counter: " + counterId));
                    }

                    return valueOps.increment(counterKey)
                            .doOnSuccess(newValue ->
                                    logger.debug("Counter with ID: {} incremented to: {}", counterId, newValue))
                            .doFinally(
                                    signalType -> redisTemplate.delete(lockKey).subscribe(deleted -> {
                                        if (deleted > 0) {
                                            logger.debug("Released lock for counter: {}", counterId);
                                        } else {
                                            logger.warn("Failed to release lock for counter: {}", counterId);
                                        }
                                    }));
                })
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                        .doBeforeRetry(retrySignal -> logger.debug(
                                "Retrying lock acquisition for counter: {}, attempt: {}",
                                counterId,
                                retrySignal.totalRetries() + 1)))
                .subscribe(
                        null, // onNext is ignored since we don't need the result
                        error -> logger.error("Error while incrementing counter: {}", counterId, error));
    }

    /**
     * Gets the current value of the counter.
     *
     * @param counterId The ID of the counter to get
     * @return A Mono containing the current counter value
     */
    @Override
    public Mono<Long> getValue(String counterId) {
        logger.debug("Getting value for counter with ID: {}", counterId);

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

    private static final Logger logger = LoggerFactory.getLogger(CounterActor.class);

    // Define a type key for this actor type
    public static final ShardedEntityType<Command> TYPE_KEY = ShardedEntityType.of(Command.class, "CounterActor");

    /** Base interface for all commands that can be sent to the counter actor. */
    public interface Command extends JsonSerializable {}

    /** Command to increment the counter and get the new value. */
    public static class Increment implements Command {
        public Increment() {}
    }

    /** Command to get the current value of the counter. */
    public static class GetValue extends AskCommand<Long> implements Command {
        public GetValue() {}
    }

    @Override
    public ShardedEntityType<Command> entityType() {
        return TYPE_KEY;
    }

    @Override
    public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
                .withState(context -> new CounterActorBehavior(ctx.getEntityId()))
                .onMessage(Increment.class, CounterActorBehavior::onIncrement)
                .onMessage(GetValue.class, CounterActorBehavior::onGetValue)
                .build();
    }

    /**
     * Inner class to isolate stateful behavior logic. This separates the actor's state and behavior
     * from its interface.
     */
    private static class CounterActorBehavior {
        private final String counterId;
        private long value = 0;

        /**
         * Creates a new behavior with the given context and counter ID.
         */
        CounterActorBehavior(String counterId) {
            this.counterId = counterId;
            logger.debug("Created counter actor for ID: {}", counterId);
        }

        /**
         * Handles Increment commands by incrementing the counter and responding with the new value.
         *
         * @param msg The Increment message
         * @return The next behavior (same in this case)
         */
        private SpringActorBehavior<Command> onIncrement(Increment msg) {
            logger.debug("Incrementing counter with ID: {}", counterId);
            value++;
            logger.debug("Counter with ID: {} incremented to: {}", counterId, value);
            return SpringActorBehavior.same();
        }

        /**
         * Handles GetValue commands by responding with the current counter value.
         *
         * @param msg The GetValue message
         * @return The next behavior (same in this case)
         */
        private SpringActorBehavior<Command> onGetValue(GetValue msg) {
            logger.debug("Getting value for counter with ID: {}", counterId);
            msg.reply(value);
            return SpringActorBehavior.same();
        }
    }
}
```

### ActorCounterService

`ActorCounterService` uses actor-based synchronization to handle counter operations:

```java
@Service
public class ActorCounterService implements CounterService {

    private static final Logger logger = LoggerFactory.getLogger(ActorCounterService.class);

    private final SpringActorSystem springActorSystem;

    /**
     * Creates a new ActorCounterServiceImpl with the given actor system.
     *
     * @param springActorSystem The Spring actor system
     */
    public ActorCounterService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    /**
     * Increments the counter value by 1 and returns the new value. Uses actor-based locking to ensure
     * synchronization.
     *
     * @param counterId The ID of the counter to increment
     */
    @Override
    public void increment(String counterId) {
        logger.debug("Incrementing counter with ID: {}", counterId);

        // Get a reference to the sharded actor for this counter using the new simplified API
        var actorRef =
                springActorSystem.sharded(CounterActor.class).withId(counterId).get();

        // Send an increment message to the actor and get the response
        actorRef.tell(new CounterActor.Increment());
    }

    /**
     * Gets the current value of the counter.
     *
     * @param counterId The ID of the counter to get
     * @return A Mono containing the current counter value
     */
    @Override
    public Mono<Long> getValue(String counterId) {
        logger.debug("Getting value for counter with ID: {}", counterId);

        // Get a reference to the sharded actor for this counter using the new simplified API
        var actorRef =
                springActorSystem.sharded(CounterActor.class).withId(counterId).get();

        // Send a get value message to the actor using the ask() method with error handling
        CompletionStage<Long> response = actorRef.ask(new CounterActor.GetValue())
                .withTimeout(Duration.ofSeconds(3))
                .onTimeout(() -> {
                    logger.warn("Timeout getting value for counter: {}", counterId);
                    return 0L; // Return default value on timeout
                })
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

    /**
     * Creates a new CounterController with the given services.
     *
     * @param dbCounterService The DB-based counter service
     * @param redisCounterService The Redis-based counter service
     * @param actorCounterService The actor-based counter service
     */
    public CounterController(
            DbCounterService dbCounterService,
            RedisCounterService redisCounterService,
            ActorCounterService actorCounterService) {
        this.dbCounterService = dbCounterService;
        this.redisCounterService = redisCounterService;
        this.actorCounterService = actorCounterService;
    }

    /**
     * Endpoint to increment a counter using DB locking.
     *
     * @param counterId The ID of the counter to increment
     * @return A Mono containing the new counter value
     */
    @GetMapping("/db/{counterId}/increment")
    public void incrementDbCounter(@PathVariable String counterId) {
        dbCounterService.increment(counterId);
    }

    /**
     * Endpoint to get a counter value using DB locking.
     *
     * @param counterId The ID of the counter to get
     * @return A Mono containing the current counter value
     */
    @GetMapping("/db/{counterId}")
    public Mono<Long> getDbCounter(@PathVariable String counterId) {
        return dbCounterService.getValue(counterId);
    }

    /**
     * Endpoint to increment a counter using Redis locking.
     *
     * @param counterId The ID of the counter to increment
     */
    @GetMapping("/redis/{counterId}/increment")
    public void incrementRedisCounter(@PathVariable String counterId) {
        redisCounterService.increment(counterId);
    }

    /**
     * Endpoint to get a counter value using Redis locking.
     *
     * @param counterId The ID of the counter to get
     * @return A Mono containing the current counter value
     */
    @GetMapping("/redis/{counterId}")
    public Mono<Long> getRedisCounter(@PathVariable String counterId) {
        return redisCounterService.getValue(counterId);
    }

    /**
     * Endpoint to increment a counter using actor-based locking.
     *
     * @param counterId The ID of the counter to increment
     * @return A Mono containing the new counter value
     */
    @GetMapping("/actor/{counterId}/increment")
    public void incrementActorCounter(@PathVariable String counterId) {
        actorCounterService.increment(counterId);
    }

    /**
     * Endpoint to get a counter value using actor-based locking.
     *
     * @param counterId The ID of the counter to get
     * @return A Mono containing the current counter value
     */
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
