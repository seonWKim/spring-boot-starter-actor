# Dispatchers

This guide explains how to use dispatchers to control thread execution for your actors.

## What are Dispatchers?

A dispatcher is the "engine" that makes Pekko Actors work. It is responsible for selecting which actors receive messages and allocating threads from the thread pool.

By default, all actors use Pekko's default dispatcher, but you can configure actors to use different dispatchers based on their workload characteristics.

## Why Use Different Dispatchers?

The main reason to use different dispatchers is to **isolate blocking operations** from the default dispatcher. If you perform blocking operations (like blocking I/O) on the default dispatcher, it can starve the thread pool and prevent other actors from processing messages.

## Dispatcher Selection API

Spring Boot Starter Actor provides a fluent API for selecting dispatchers when spawning actors:

### Default Dispatcher

Use the default Pekko dispatcher (this is the default behavior):

```java
SpringActorHandle<MyActor.Command> actor = actorSystem
    .actor(MyActor.class)
    .withId("my-actor")
    .withDefaultDispatcher()  // Optional - this is the default
    .spawnAndWait();
```

### Blocking Dispatcher

Use Pekko's default blocking I/O dispatcher for actors that perform blocking operations:

```java
SpringActorHandle<DatabaseActor.Command> dbActor = actorSystem
    .actor(DatabaseActor.class)
    .withId("db-actor")
    .withBlockingDispatcher()  // Use blocking I/O dispatcher
    .spawnAndWait();
```

### When to use:

- Database operations
- File I/O
- Network calls (blocking APIs)
- Any operation that blocks the thread

### Custom Dispatcher from Configuration

Use a custom dispatcher defined in your application configuration:

```java
SpringActorHandle<WorkerActor.Command> worker = actorSystem
    .actor(WorkerActor.class)
    .withId("worker")
    .withDispatcherFromConfig("my-custom-dispatcher")
    .spawnAndWait();
```

### Same-as-Parent Dispatcher

Inherit the dispatcher from the parent actor (useful for child actors):

```java
SpringActorHandle<ChildActor.Command> child = parentRef
    .child(ChildActor.class)
    .withId("child")
    .withDispatcherSameAsParent()
    .spawnAndWait();
```

## Types of Dispatchers

### Dispatcher (Default)

The default dispatcher is used when no specific dispatcher is configured. It uses a fork-join-executor by default and provides excellent performance for non-blocking operations.

!!! info "Fork-Join Executor"
    The fork-join executor is a work-stealing thread pool that provides efficient CPU utilization for non-blocking workloads.

### PinnedDispatcher

A PinnedDispatcher dedicates a unique thread for each actor using it. This can be useful for actors that need thread-local state or for bulkheading critical actors.

!!! warning "Resource Usage"
    PinnedDispatcher creates one thread per actor, which can lead to high resource consumption. Use sparingly and only when necessary.

## Configuring Custom Dispatchers

Define custom dispatchers in your `application.yml`:

### Fork-Join Executor

The fork-join-executor is a work-stealing thread pool:

```yaml
spring:
  actor:
    my-dispatcher:
      type: Dispatcher
      executor: fork-join-executor
      fork-join-executor:
        # Min number of threads
        parallelism-min: 2
        # Thread count = ceil(available processors * factor)
        parallelism-factor: 2.0
        # Max number of threads
        parallelism-max: 10
      # Throughput defines the maximum number of messages
      # to be processed per actor before the thread jumps
      # to the next actor. Set to 1 for as fair as possible.
      throughput: 100
```

### Thread Pool Executor

The thread-pool-executor is based on a `java.util.concurrent.ThreadPoolExecutor`:

```yaml
spring:
  actor:
    my-blocking-dispatcher:
      type: Dispatcher
      executor: thread-pool-executor
      thread-pool-executor:
        # Fixed pool size
        fixed-pool-size: 16
      throughput: 1
```

### Dynamic Thread Pool Executor

For variable pool sizes:

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

### Virtual Thread Executor (Java 21+)

If you're running on Java 21 or later, you can use Pekko's built-in virtual thread executor for actors performing blocking I/O operations. Virtual threads are lightweight threads that provide excellent scalability for blocking operations.

**Benefits of Virtual Threads:**

- Very low memory overhead (~1KB per thread vs ~1MB for platform threads)
- Can handle millions of concurrent operations
- Ideal for blocking I/O (database calls, HTTP requests, file operations)
- No need for reactive programming patterns

!!! note "Java 21+ Required"
    Virtual thread support requires Java 21 or later. If you're on Java 11-17, use thread-pool-executor instead.

**Configuration:**

```yaml
spring:
  actor:
    virtual-thread-dispatcher:
      executor: virtual-thread-executor
```

**Usage:**

```java
SpringActorHandle<DatabaseActor.Command> dbActor = actorSystem
    .actor(DatabaseActor.class)
    .withId("db-actor")
    .withDispatcherFromConfig("virtual-thread-dispatcher")
    .spawnAndWait();
```

**JVM Tuning (Optional):**

You can further configure virtual threads using JVM system properties:

- `jdk.virtualThreadScheduler.parallelism` - Number of platform threads for virtual thread scheduling
- `jdk.virtualThreadScheduler.maxPoolSize` - Maximum pool size for virtual thread scheduler
- `jdk.unparker.maxPoolSize` - Maximum pool size for unparking virtual threads

!!! tip "When to Tune"
    Most applications don't need to tune these settings. Only adjust if you've identified specific bottlenecks through profiling.

**Requirements:**
- Java 21 or later
- Suitable for blocking I/O operations only (not CPU-intensive tasks)

**When to use Virtual Threads vs Thread Pool:**

| Use Case | Recommended Dispatcher |
|----------|----------------------|
| Blocking I/O (Java 21+) | Virtual Thread Executor |
| Blocking I/O (Java 11-17) | Thread Pool Executor |
| CPU-intensive tasks | Fork-Join Executor (default) |
| Non-blocking operations | Default Dispatcher |

!!! example "Virtual Threads Example"
    See the [virtual-threads example](https://github.com/seonwkim/spring-boot-starter-actor/tree/main/example/virtual-threads) for a complete working demonstration of virtual threads with actors.

## Blocking Operations

The most important reason to use a separate dispatcher is to isolate blocking operations from the default dispatcher.

**Problem:**

If you have blocking operations (such as blocking I/O, database calls, or expensive computations) and run them on the default dispatcher, it will block threads that are needed for other actors to process their messages. This can cause your application to become unresponsive.

**Solution:**

Always use a separate dispatcher with a thread pool executor for actors that perform blocking operations. Use `.withBlockingDispatcher()` or a custom dispatcher with a thread-pool-executor.

!!! danger "Thread Starvation"
    Running blocking operations on the default dispatcher can lead to thread starvation, where all threads are blocked waiting for I/O, preventing other actors from processing messages.

Example configuration for blocking operations:

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

## Throughput Configuration

The `throughput` setting defines the maximum number of messages to be processed per actor before the thread jumps to the next actor. Set to 1 for as fair as possible.

Higher throughput values can improve performance by reducing the number of context switches, but may increase latency for individual messages.

## More Information

For more detailed information about dispatchers, refer to the [Pekko Dispatcher Documentation](https://pekko.apache.org/docs/pekko/1.0/typed/dispatchers.html).

## Next Steps

- [Actor Registration](actor-registration-messaging.md) - Learn how to create and spawn actors
- [Routers](routers.md) - Use routers for load balancing and parallel processing
- [Logging with MDC](logging.md) - Enhance observability with MDC and tags
