# Dispatchers and Mailboxes

A **dispatcher** is the thread pool that runs actors. When an actor has messages, the dispatcher gives it
a thread, the actor handles a batch of messages, and the thread moves on to another actor. Thousands of
actors share a few threads this way.

A **mailbox** is the queue an actor's messages wait in until it handles them.

Most actors need neither setting: the default dispatcher and the default (unbounded) mailbox fit
non-blocking work. Change them when an actor blocks, when a group of actors must not slow down the
others, or when a queue must not grow without bound.

## Choosing a dispatcher

| Work in the handler | Dispatcher |
|---|---|
| Non-blocking: in-memory state, `tell`, `ask` with `pipeToSelf` | Default (nothing to set) |
| Blocking I/O: JDBC/JPA, file I/O, blocking HTTP clients | `withBlockingDispatcher()` |
| Blocking I/O, many concurrent calls, Java 21 | The blocking dispatcher on [virtual threads](#virtual-threads) |
| A group of actors that must be isolated from the rest | A [custom dispatcher](#custom-dispatchers) |
| Work on a thread pool your application already manages | A dispatcher on a [Spring `Executor` bean](#spring-executor-beans) |

!!! danger "Never block on the default dispatcher"
    The default dispatcher has about as many threads as CPU cores. An actor that blocks there holds one
    of them, and a few blocking actors can stall every other actor in the application.

## Selecting a dispatcher

Choose the dispatcher when you spawn the actor:

=== "Java"

    ```java
    // Blocking work: Pekko's blocking I/O dispatcher
    actorSystem.actor(ReportActor.class)
            .withId("report-1")
            .withBlockingDispatcher()
            .spawn();

    // A dispatcher defined in configuration (see below)
    actorSystem.actor(ReportActor.class)
            .withId("report-2")
            .withDispatcherFromConfig("reports")
            .spawn();
    ```

=== "Kotlin"

    ```kotlin
    // In a suspend function

    // Blocking work: Pekko's blocking I/O dispatcher
    actorSystem.actor<ReportActor, _>()
        .withId("report-1")
        .withBlockingDispatcher()
        .awaitSpawn()

    // A dispatcher defined in configuration (see below)
    actorSystem.actor<ReportActor, _>()
        .withId("report-2")
        .withDispatcherFromConfig("reports")
        .awaitSpawn()
    ```

| Spawn builder method | Child builder equivalent | Runs the actor on |
|---|---|---|
| `withDefaultDispatcher()` (the default) | `withDispatcher(DispatcherConfig.defaultDispatcher())` | The default dispatcher |
| `withBlockingDispatcher()` | `withDispatcher(DispatcherConfig.blocking())` | Pekko's `default-blocking-io-dispatcher` |
| `withDispatcherFromConfig(name)` | `withDispatcher(DispatcherConfig.fromConfig(name))` | The dispatcher defined under `spring.actor.<name>` or `spring.actor.dispatchers.<name>` |
| `withDispatcherSameAsParent()` | `withDispatcher(DispatcherConfig.sameAsParent())` | The parent's dispatcher |

A child actor uses the `withDispatcher(...)` form:

=== "Java"

    ```java
    ctx.getSelf().child(ExportWorker.class)
            .withId("export-1")
            .withDispatcher(DispatcherConfig.blocking())
            .spawn();
    ```

=== "Kotlin"

    ```kotlin
    ctx.self.child<ExportWorker, _>()
        .withId("export-1")
        .withDispatcher(DispatcherConfig.blocking())
        .spawn()
    ```

An unknown dispatcher name makes the spawn fail; the actor system keeps working.

## The blocking dispatcher

`withBlockingDispatcher()` uses Pekko's `default-blocking-io-dispatcher`, a thread pool meant for
blocking calls. Pekko gives it 16 threads by default. Its threads are separate from the default
dispatcher's, so blocking actors can only slow each other down. Change its size with raw Pekko keys:

```yaml
spring:
  actor:
    pekko:
      actor:
        default-blocking-io-dispatcher:
          thread-pool-executor:
            fixed-pool-size: 32
```

## Custom dispatchers

Define your own dispatcher under `spring.actor.<name>` with Pekko's dispatcher keys. Every key under
`spring.actor` that the starter doesn't know is passed to Pekko without the prefix, so
`spring.actor.my-dispatcher` becomes Pekko's `my-dispatcher` (see
[Configuration](../operations/configuration.md)). Then spawn with `withDispatcherFromConfig("my-dispatcher")`.

A fixed thread pool, for blocking work you want to keep apart from the rest:

```yaml
spring:
  actor:
    db-dispatcher:
      type: Dispatcher
      executor: thread-pool-executor
      thread-pool-executor:
        fixed-pool-size: 16
      throughput: 1
```

A fork-join pool, for CPU-heavy work you want to keep apart:

```yaml
spring:
  actor:
    cpu-dispatcher:
      type: Dispatcher
      executor: fork-join-executor
      fork-join-executor:
        parallelism-min: 2
        parallelism-factor: 1.0   # threads = CPU cores x factor, within min and max
        parallelism-max: 8
      throughput: 100
```

A thread pool that grows and shrinks:

```yaml
spring:
  actor:
    elastic-dispatcher:
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

Pekko also has `type: PinnedDispatcher`, which gives each actor its own thread. It uses one thread per
actor, so keep it for a handful of actors. All dispatcher keys are in the
[Pekko dispatcher documentation](https://pekko.apache.org/docs/pekko/current/typed/dispatchers.html).

### Throughput

`throughput` is the number of messages an actor may handle before its thread moves on to the next
actor. A high value (100) means fewer switches and more speed; a low value (1) is fairer, so one busy
actor can't keep others waiting. Use 1 for dispatchers whose actors block.

## Spring Executor beans

A dispatcher can run its actors on an `Executor` bean of your application, for example a
`ThreadPoolTaskExecutor` you already tune and monitor:

=== "Java"

    ```java
    @Bean
    ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("reports-");
        executor.setCorePoolSize(8);
        return executor;
    }
    ```

=== "Kotlin"

    ```kotlin
    @Bean
    fun reportExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        setThreadNamePrefix("reports-")
        corePoolSize = 8
    }
    ```

```yaml
spring:
  actor:
    dispatchers:
      reports:
        executor: reportExecutor   # the bean name
        throughput: 10             # optional
```

Spawn with `withDispatcherFromConfig("reports")`. The Spring context owns the executor: the actor system
never shuts it down, and it keeps running while the actor system stops.

| Key | Meaning |
|---|---|
| `spring.actor.dispatchers.<name>.executor` | The name of an `Executor` bean |
| `spring.actor.dispatchers.<name>.virtual` | `true` for a dispatcher on virtual threads (see below) |
| `spring.actor.dispatchers.<name>.throughput` | Optional throughput |

A dispatcher sets either `executor` or `virtual: true`, not both. The name may contain letters, digits,
`-` and `_`.

## Virtual threads

On Java 21, blocking actors can run on virtual threads: a blocked virtual thread doesn't hold a platform
thread, so many blocking calls can wait at once.

```yaml
spring:
  actor:
    virtual-threads:
      enabled: true            # auto (default) | true | false
    dispatchers:
      virtual-thread-dispatcher:
        virtual: true
```

- `spring.actor.virtual-threads.enabled` runs the blocking dispatcher (`withBlockingDispatcher()`) on
  virtual threads. The default, `auto`, follows Spring Boot's `spring.threads.virtual.enabled`, so an
  application that turns on virtual threads for Spring gets them for blocking actors too. `false` keeps
  platform threads even when Spring's flag is on.
- `spring.actor.dispatchers.<name>.virtual: true` defines a named dispatcher on virtual threads, whatever
  the switch says. Spawn with `withDispatcherFromConfig("<name>")`.
- The default dispatcher always stays on its platform fork-join pool: actors that never block gain
  nothing from virtual threads.
- A raw `spring.actor.pekko.actor.default-blocking-io-dispatcher.executor` still wins over the switch,
  with a warning in the log.

!!! warning "Pinning"
    A virtual thread that blocks inside a `synchronized` block, or in some native calls, pins its
    carrier (platform) thread until it unblocks, and too many pinned carriers stall every virtual thread.
    Use `java.util.concurrent` locks (`ReentrantLock`) around blocking calls, and find pinning with
    `-Djdk.tracePinnedThreads=short`.

Virtual threads help blocking I/O, not CPU-heavy work. The JVM's own settings
(`jdk.virtualThreadScheduler.parallelism`, `jdk.virtualThreadScheduler.maxPoolSize`) rarely need
changes. The [virtual-threads example](../../examples/virtual-threads.md) compares platform and virtual
threads on the same workload.

## Mailboxes

By default each actor has an unbounded mailbox: it accepts every message, and a slow actor's queue grows
until memory runs out. Set another mailbox when you spawn the actor:

=== "Java"

    ```java
    // At most 1,000 waiting messages; further messages go to dead letters
    actorSystem.actor(IngestActor.class)
            .withId("ingest")
            .withMailbox(MailboxConfig.bounded(1_000))
            .spawn();

    // A mailbox defined in configuration
    actorSystem.actor(IngestActor.class)
            .withId("ingest-priority")
            .withMailbox(MailboxConfig.fromConfig("priority-mailbox"))
            .spawn();
    ```

=== "Kotlin"

    ```kotlin
    // In a suspend function

    // At most 1,000 waiting messages; further messages go to dead letters
    actorSystem.actor<IngestActor, _>()
        .withId("ingest")
        .withMailbox(MailboxConfig.bounded(1_000))
        .awaitSpawn()

    // A mailbox defined in configuration
    actorSystem.actor<IngestActor, _>()
        .withId("ingest-priority")
        .withMailbox(MailboxConfig.fromConfig("priority-mailbox"))
        .awaitSpawn()
    ```

| `MailboxConfig` | Mailbox |
|---|---|
| `defaultMailbox()` | Unbounded (the default) |
| `bounded(capacity)` | Holds at most `capacity` messages; messages beyond that are dropped to dead letters. `capacity` must be positive. |
| `fromConfig(name)` | The mailbox defined under `spring.actor.<name>` |

A mailbox from configuration names a Pekko mailbox class, for example a priority mailbox that handles
some messages before others:

```yaml
spring:
  actor:
    priority-mailbox:
      mailbox-type: com.example.PriorityMailbox
```

Pekko's priority mailboxes (`UnboundedPriorityMailbox`, `UnboundedStablePriorityMailbox`,
`BoundedPriorityMailbox`, `BoundedStablePriorityMailbox`) need a subclass that supplies the ordering;
see the [Pekko mailbox documentation](https://pekko.apache.org/docs/pekko/current/typed/mailboxes.html).

A bounded mailbox drops messages silently from the sender's point of view: `tell` still returns, and an
`ask` times out. Use it to protect memory, and make senders slow down (with ask, or with
[streams](../streaming/reactor-streams.md)) when you need back-pressure.

## Pitfalls

- **Blocking on the default dispatcher.** The most common cause of an application that stops responding
  under load.
- **One huge blocking pool for everything.** Give separate dispatchers to workloads that must not wait
  for each other (for example, the database and a slow partner API).
- **Unbounded mailboxes in front of slow actors.** Watch mailbox sizes (see
  [Monitoring](../operations/monitoring.md)) and bound the ones that can grow.
- **`virtual` and `executor` on one dispatcher.** The application fails to start; choose one.

## Related

- [Actors](actors.md): the spawn builder
- [Configuration](../operations/configuration.md): all `spring.actor` keys
- [Monitoring](../operations/monitoring.md): dispatcher and mailbox metrics
- [Virtual threads example](../../examples/virtual-threads.md)
