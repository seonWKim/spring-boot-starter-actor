# Dispatchers

This guide explains how to use dispatchers to control thread execution for your actors in Spring Boot Starter Actor.

## What are Dispatchers?

A dispatcher is the engine that manages message processing and thread execution for actors. It's "what makes Pekko Actors 'tick'".

Dispatchers are responsible for:
- Scheduling actors to run on threads
- Managing the thread pool
- Controlling message throughput
- Handling mailbox processing

By default, all actors use Pekko's default dispatcher, but you can configure actors to use different dispatchers based on their workload characteristics.

## Why Use Different Dispatchers?

Different types of actors have different execution requirements:

- **CPU-Intensive Actors**: Benefit from dedicated thread pools optimized for computational work
- **Blocking I/O Actors**: Should use specialized dispatchers to prevent thread pool starvation
- **High-Priority Actors**: May need isolated thread pools to ensure responsiveness
- **Batch Processing Actors**: Can use larger thread pools with higher throughput settings

## Dispatcher Selection API

Spring Boot Starter Actor provides a fluent API for selecting dispatchers when spawning actors:

### Default Dispatcher

Use the default Pekko dispatcher (this is the default behavior):

```java
SpringActorRef<MyActor.Command> actor = actorSystem
    .actor(MyActor.class)
    .withId("my-actor")
    .withDefaultDispatcher()  // Optional - this is the default
    .spawnAndWait();
```

### Blocking Dispatcher

Use Pekko's default blocking I/O dispatcher for actors that perform blocking operations:

```java
SpringActorRef<DatabaseActor.Command> dbActor = actorSystem
    .actor(DatabaseActor.class)
    .withId("db-actor")
    .withBlockingDispatcher()  // Use blocking I/O dispatcher
    .spawnAndWait();
```

**When to use:**
- Database operations
- File I/O
- Network calls (blocking APIs)
- Any operation that blocks the thread

### Custom Dispatcher from Configuration

Use a custom dispatcher defined in your application configuration:

```java
SpringActorRef<WorkerActor.Command> worker = actorSystem
    .actor(WorkerActor.class)
    .withId("worker")
    .withDispatcherFromConfig("my-custom-dispatcher")
    .spawnAndWait();
```

### Same-as-Parent Dispatcher

Inherit the dispatcher from the parent actor (useful for child actors):

```java
SpringActorRef<ChildActor.Command> child = parentRef
    .child(ChildActor.class)
    .withId("child")
    .withDispatcherSameAsParent()
    .spawnAndWait();
```

## Configuring Custom Dispatchers

Define custom dispatchers in your `application.yml`:

### Fork-Join Executor (Default)

Best for CPU-bound tasks with moderate parallelism:

```yaml
spring:
  actor:
    my-fork-join-dispatcher:
      type: Dispatcher
      executor: fork-join-executor
      fork-join-executor:
        parallelism-min: 2
        parallelism-factor: 2.0
        parallelism-max: 10
      throughput: 100
```

**Configuration options:**
- `parallelism-min`: Minimum number of threads
- `parallelism-factor`: Multiplier for available processors
- `parallelism-max`: Maximum number of threads
- `throughput`: Messages processed per actor before yielding thread

### Thread Pool Executor

Best for blocking operations with fixed pool size:

```yaml
spring:
  actor:
    my-blocking-dispatcher:
      type: Dispatcher
      executor: thread-pool-executor
      thread-pool-executor:
        fixed-pool-size: 16
      throughput: 1
```

**Configuration options:**
- `fixed-pool-size`: Fixed number of threads in the pool
- `throughput`: Set to 1 for blocking operations to avoid context switching

### Dynamic Thread Pool Executor

For workloads with variable demand:

```yaml
spring:
  actor:
    my-dynamic-dispatcher:
      type: Dispatcher
      executor: thread-pool-executor
      thread-pool-executor:
        core-pool-size-min: 4
        core-pool-size-factor: 2.0
        core-pool-size-max: 16
        max-pool-size-min: 8
        max-pool-size-factor: 2.0
        max-pool-size-max: 32
      throughput: 10
```

## Common Dispatcher Patterns

### Pattern 1: Database Access Actor

For actors that perform database operations:

```java
@Component
public class DatabaseActor implements SpringActor<DatabaseActor.Command> {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DatabaseActor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public interface Command {}

    public static class Query implements Command {
        public final String sql;
        public final ActorRef<List<Map<String, Object>>> replyTo;

        public Query(String sql, ActorRef<List<Map<String, Object>>> replyTo) {
            this.sql = sql;
            this.replyTo = replyTo;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .onMessage(Query.class, (ctx, msg) -> {
                // Blocking database operation
                List<Map<String, Object>> results = jdbcTemplate.queryForList(msg.sql);
                msg.replyTo.tell(results);
                return Behaviors.same();
            })
            .build();
    }
}

// Spawn with blocking dispatcher
@Service
public class DatabaseService {
    private final SpringActorSystem actorSystem;

    public DatabaseService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public SpringActorRef<DatabaseActor.Command> createDatabaseActor(String id) {
        return actorSystem
            .actor(DatabaseActor.class)
            .withId(id)
            .withBlockingDispatcher()  // Important for blocking operations
            .spawnAndWait();
    }
}
```

Configuration for blocking operations:

```yaml
spring:
  actor:
    # Pekko's default blocking dispatcher is already configured
    # But you can customize it if needed
    pekko:
      actor:
        default-blocking-io-dispatcher:
          type: Dispatcher
          executor: thread-pool-executor
          thread-pool-executor:
            fixed-pool-size: 16
          throughput: 1
```

### Pattern 2: CPU-Intensive Worker Pool

For computationally heavy tasks:

```java
@Component
public class ComputeWorkerActor implements SpringActor<ComputeWorkerActor.Command> {

    public interface Command {}

    public static class HeavyComputation implements Command {
        public final byte[] data;
        public final ActorRef<byte[]> replyTo;

        public HeavyComputation(byte[] data, ActorRef<byte[]> replyTo) {
            this.data = data;
            this.replyTo = replyTo;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .onMessage(HeavyComputation.class, (ctx, msg) -> {
                // CPU-intensive operation
                byte[] result = performHeavyComputation(msg.data);
                msg.replyTo.tell(result);
                return Behaviors.same();
            })
            .build();
    }

    private byte[] performHeavyComputation(byte[] data) {
        // Simulate CPU-intensive work
        // ...
        return data;
    }
}
```

Configuration for CPU-intensive work:

```yaml
spring:
  actor:
    cpu-intensive-dispatcher:
      type: Dispatcher
      executor: fork-join-executor
      fork-join-executor:
        parallelism-min: 4
        parallelism-factor: 1.0
        parallelism-max: 8
      throughput: 5
```

Spawn workers with custom dispatcher:

```java
@Service
public class ComputeService {
    private final SpringActorSystem actorSystem;

    public ComputeService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public List<SpringActorRef<ComputeWorkerActor.Command>> createWorkerPool(int size) {
        List<SpringActorRef<ComputeWorkerActor.Command>> workers = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            SpringActorRef<ComputeWorkerActor.Command> worker = actorSystem
                .actor(ComputeWorkerActor.class)
                .withId("worker-" + i)
                .withDispatcherFromConfig("cpu-intensive-dispatcher")
                .spawnAndWait();
            workers.add(worker);
        }

        return workers;
    }
}
```

### Pattern 3: High-Priority Message Processing

For actors that need guaranteed responsiveness:

```yaml
spring:
  actor:
    priority-dispatcher:
      type: Dispatcher
      executor: fork-join-executor
      fork-join-executor:
        parallelism-min: 2
        parallelism-factor: 1.0
        parallelism-max: 4
      throughput: 1  # Low throughput for high responsiveness
```

```java
SpringActorRef<AlertActor.Command> alertActor = actorSystem
    .actor(AlertActor.class)
    .withId("alert-processor")
    .withDispatcherFromConfig("priority-dispatcher")
    .spawnAndWait();
```

## Best Practices

### 1. Isolate Blocking Operations

**❌ Bad - Blocking on default dispatcher:**
```java
// This will starve the default dispatcher!
SpringActorRef<DatabaseActor.Command> dbActor = actorSystem
    .actor(DatabaseActor.class)
    .withId("db-actor")
    .spawnAndWait();  // Uses default dispatcher by default
```

**✅ Good - Using blocking dispatcher:**
```java
SpringActorRef<DatabaseActor.Command> dbActor = actorSystem
    .actor(DatabaseActor.class)
    .withId("db-actor")
    .withBlockingDispatcher()  // Dedicated dispatcher for blocking operations
    .spawnAndWait();
```

### 2. Size Thread Pools Appropriately

**For blocking operations:**
- Start with `fixed-pool-size = number of CPU cores * 2`
- Adjust based on monitoring and load testing
- Set `throughput = 1` to minimize context switching

**For CPU-intensive work:**
- Use `parallelism-max = number of CPU cores` or slightly higher
- Set `throughput = 5-10` for balanced throughput

### 3. Monitor Thread Pool Usage

Watch for these warning signs:
- Increasing message processing latency
- Growing mailbox backlogs
- Thread pool saturation

### 4. Use Descriptive Dispatcher Names

```yaml
spring:
  actor:
    database-operations-dispatcher:  # Clear purpose
      type: Dispatcher
      executor: thread-pool-executor
      thread-pool-executor:
        fixed-pool-size: 16
      throughput: 1

    image-processing-dispatcher:  # Clear purpose
      type: Dispatcher
      executor: fork-join-executor
      fork-join-executor:
        parallelism-max: 4
      throughput: 5
```

### 5. Test Under Load

Always test dispatcher configurations under realistic load:
- Monitor thread usage
- Measure message processing latency
- Check for thread starvation
- Verify throughput meets requirements

## Common Pitfalls

### Pitfall 1: Using Default Dispatcher for Blocking Operations

```java
// ❌ This will cause problems!
@Component
public class FileReaderActor implements SpringActor<FileReaderActor.Command> {
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .onMessage(ReadFile.class, (ctx, msg) -> {
                // Blocking file I/O on default dispatcher - BAD!
                String content = Files.readString(Path.of(msg.path));
                msg.replyTo.tell(content);
                return Behaviors.same();
            })
            .build();
    }
}
```

**Solution:** Use `.withBlockingDispatcher()` or a custom dispatcher.

### Pitfall 2: Too Many Threads

```yaml
# ❌ Too many threads can cause context switching overhead
spring:
  actor:
    my-dispatcher:
      type: Dispatcher
      executor: thread-pool-executor
      thread-pool-executor:
        fixed-pool-size: 1000  # Way too many!
```

**Solution:** Start with `cores * 2` for blocking, `cores` for CPU-intensive.

### Pitfall 3: Sharing Dispatchers Inappropriately

Don't share dispatchers between actors with very different characteristics:
- Blocking and non-blocking operations
- High-priority and low-priority work
- CPU-intensive and I/O-intensive tasks

## Throughput Configuration

The `throughput` setting controls how many messages an actor processes before yielding the thread:

**Low throughput (1-5):**
- Better fairness and responsiveness
- More context switching overhead
- Good for interactive/priority actors

**High throughput (50-100):**
- Better overall throughput
- Higher latency for individual messages
- Good for batch processing

**Example:**
```yaml
spring:
  actor:
    interactive-dispatcher:
      type: Dispatcher
      throughput: 1  # High responsiveness

    batch-processor-dispatcher:
      type: Dispatcher
      throughput: 100  # High throughput
```

## Complete Example: Multi-Tier Application

```yaml
spring:
  actor:
    # API layer - fast response times
    api-dispatcher:
      type: Dispatcher
      executor: fork-join-executor
      fork-join-executor:
        parallelism-min: 4
        parallelism-max: 8
      throughput: 5

    # Database layer - blocking operations
    database-dispatcher:
      type: Dispatcher
      executor: thread-pool-executor
      thread-pool-executor:
        fixed-pool-size: 16
      throughput: 1

    # Background jobs - high throughput
    background-dispatcher:
      type: Dispatcher
      executor: fork-join-executor
      fork-join-executor:
        parallelism-max: 4
      throughput: 100
```

```java
@Service
public class ApplicationService {
    private final SpringActorSystem actorSystem;

    public ApplicationService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public void initializeActors() {
        // API layer actors - default or fast dispatcher
        actorSystem.actor(ApiHandlerActor.class)
            .withId("api-handler")
            .withDispatcherFromConfig("api-dispatcher")
            .spawnAndWait();

        // Database actors - blocking dispatcher
        actorSystem.actor(DatabaseActor.class)
            .withId("db-actor")
            .withDispatcherFromConfig("database-dispatcher")
            .spawnAndWait();

        // Background job actors - batch dispatcher
        actorSystem.actor(BackgroundJobActor.class)
            .withId("background-job")
            .withDispatcherFromConfig("background-dispatcher")
            .spawnAndWait();
    }
}
```

## Next Steps

- [Learn about actor hierarchies and supervision](actor-registration-messaging.md#spawning-child-actors)
- [Explore sharded actors for distributed systems](sharded-actors.md)
- Check the [Pekko Dispatcher Documentation](https://pekko.apache.org/docs/pekko/1.0/typed/dispatchers.html) for advanced configuration
