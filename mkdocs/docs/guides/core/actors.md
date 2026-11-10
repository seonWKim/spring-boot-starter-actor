# Actors

An actor is an object that owns some state and handles messages one at a time. Code outside the actor
never touches that state: it sends a message, and the actor changes its state while it handles the
message. Because only one message runs at a time, the actor needs no locks.

Use an actor when something has state that many requests change at once (a counter, a chat room, an
order, a session, an AI agent's conversation), when work should run in the background without blocking
the caller, or when you want a failure to restart one small part of the system instead of the request.

This page covers defining actors, Spring dependency injection, actor ids, spawning and finding actors,
stopping them, and child actors. Sending messages is in [Messaging](messaging.md), failures in
[Supervision](supervision.md), and the tools inside an actor (state, timers, watching) in
[Behaviors](behaviors.md).

## Setup

Add `actor-spring-boot-starter` and put `@EnableActorSupport` on your application class, as shown in
[Getting Started](../../getting-started.md). That starts one actor system for the Spring context and
gives you a `SpringActorSystem` bean to inject. Nothing else is needed for the features on this page.

## Defining an actor

An actor is a Spring bean that implements `SpringActor<C>`, where `C` is the type of the messages it
accepts (its *commands*). It has one method, `create`, which returns the actor's behavior: which
handler runs for which message.

=== "Java"

    ```java
    @Component
    public class GreeterActor implements SpringActor<GreeterActor.Command> {

        // Every message this actor accepts implements Command.
        public interface Command {}

        // A message that expects no answer.
        public record LogVisit(String name) implements Command {}

        // A message that expects a String answer: it extends AskCommand<String>.
        public static class Greet extends AskCommand<String> implements Command {
            public final String name;

            public Greet(String name) {
                this.name = name;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(LogVisit.class, (ctx, msg) -> {
                        ctx.getLog().info("{} visited", msg.name());
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Greet.class, (ctx, msg) -> {
                        msg.reply("Hello, " + msg.name + "!");
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class GreeterActor : SpringActor<GreeterActor.Command> {

        // Every message this actor accepts implements Command.
        sealed interface Command

        // A message that expects no answer.
        data class LogVisit(val name: String) : Command

        // A message that expects a String answer: it extends AskCommand<String>.
        class Greet(val name: String) : AskCommand<String>(), Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .onMessage(LogVisit::class.java) { ctx, msg ->
                    ctx.log.info("{} visited", msg.name)
                    SpringActorBehavior.same()
                }
                .onMessage(Greet::class.java) { ctx, msg ->
                    msg.reply("Hello, ${msg.name}!")
                    SpringActorBehavior.same()
                }
                .build()
    }
    ```

What each part does:

- **`@Component`** makes the class a Spring bean. The starter finds every bean that implements
  `SpringActor` (or `SpringActorWithContext`) and registers it as an actor type. A `@Bean` method works too.
- **`Command`** is the actor's message type. Each message is a class that implements it. Make messages
  immutable: records or classes with `final` fields (`data class` / `val` in Kotlin).
- **`AskCommand<R>`** marks a message that expects an answer of type `R`. The handler answers with
  `msg.reply(value)`. See [Messaging](messaging.md#ask-request-and-reply).
- **`create`** runs once each time an actor of this class is spawned. It gets the actor's context
  (`actorContext.actorId()` is the id it was spawned with) and returns the behavior.
- **`onMessage(Type, handler)`** registers a handler. The handler gets the behavior context (`ctx`: logger,
  self reference, timers, children) and the message, and returns the next behavior. `same()` means "keep
  handling messages this way". See [Behaviors](behaviors.md) for the other return values and for keeping
  state in the actor.

In Kotlin, `actorBehavior<Command>(actorContext)` comes from the `actor-kotlin` module and is the same as
`SpringActorBehavior.builder(Command::class.java, actorContext)`. See the [Kotlin guide](../kotlin.md).

## Spring dependency injection

Actors are ordinary Spring beans, so they get their dependencies through the constructor (or any other
injection style):

=== "Java"

    ```java
    @Component
    public class OrderActor implements SpringActor<OrderActor.Command> {

        private final OrderRepository orderRepository;

        public OrderActor(OrderRepository orderRepository) {
            this.orderRepository = orderRepository;
        }

        public interface Command {}

        public record MarkPaid(long orderId) implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(MarkPaid.class, (ctx, msg) -> {
                        orderRepository.markPaid(msg.orderId());
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class OrderActor(private val orderRepository: OrderRepository) : SpringActor<OrderActor.Command> {

        sealed interface Command

        data class MarkPaid(val orderId: Long) : Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .onMessage(MarkPaid::class.java) { _, msg ->
                    orderRepository.markPaid(msg.orderId)
                    SpringActorBehavior.same()
                }
                .build()
    }
    ```

Things to know:

- **One bean, many actors.** Spring creates one `OrderActor` bean. Every actor spawned from that class
  calls `create` on the same bean. Keep the injected services in the bean's fields, and keep per-actor
  state in the state object of [`withState`](behaviors.md#state-with-withstate), never in a field of the
  bean: a bean field is shared by all actors of the class and by all their threads.
- **Blocking calls.** A JPA repository or a blocking HTTP client blocks the thread. Run such actors on
  the blocking dispatcher (`withBlockingDispatcher()`, see [Dispatchers](dispatchers.md)), or the default
  dispatcher's threads can run out.
- **No proxies on the actor class.** The starter registers the actor under the bean's runtime class. An
  annotation that makes Spring wrap the bean in a class proxy (such as `@Transactional` on the actor
  class) changes that class, and spawning by the declared class then fails with "No factory registered".
  Put such annotations on the services the actor calls instead.

## Actor ids

Every actor has an id: the string you pass to `withId(...)` or `getOrSpawn(..., id)`. The starter names
the actor `<fully.qualified.ClassName>:<id>`, so:

- An id is unique per actor class. `OrderActor` "42" and `InvoiceActor` "42" are two different actors.
- An id is unique within its parent. Top-level actors (spawned from `SpringActorSystem`) share one
  parent on each node; a child's id only has to be unique among its parent's children.
- A plain actor lives on the node that spawned it. In cluster mode, `get` and `exists` on another node
  don't see it. For one actor per id across the whole cluster, use
  [sharded actors](../cluster/sharded-actors.md); for one actor per cluster, use
  [cluster singletons](../cluster/singletons.md).

Use ids that come from your domain, such as an order id or a user id, so any request can find its actor
again.

## Spawning and finding actors

Inject `SpringActorSystem` wherever you need an actor. Every call returns a `CompletionStage` (the Kotlin
module adds `suspend` versions).

| Method | What it does | Default timeout | When it does not work out |
|---|---|---|---|
| `getOrSpawn(Class, id)` | Returns the running actor, or spawns it with default settings | 3 s | Fails if the spawn fails |
| `find(Class, id)` | Returns the running actor | 3 s | Completes with an empty `Optional` when there is none; fails on timeout |
| `exists(Class, id)` | Says whether the actor runs | 3 s | Completes with `false` when there is none; fails on timeout |
| `actor(Class)` | Starts a spawn builder for custom settings (see below) | 3 s | |

Each method has an overload with a `Duration timeout` as the last argument.

### getOrSpawn: the usual way

`getOrSpawn` is the simplest way to use an actor. Call it on every request with the id from the request;
the first call spawns the actor, later calls return it.

=== "Java"

    ```java
    @Service
    public class GreeterService {
        private final SpringActorSystem actorSystem;

        public GreeterService(SpringActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        public CompletionStage<String> greet(String name) {
            return actorSystem.getOrSpawn(GreeterActor.class, "greeter")
                    .thenCompose(greeter -> greeter
                            .ask(new GreeterActor.Greet(name))
                            .withTimeout(Duration.ofSeconds(3))
                            .execute());
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class GreeterService(private val actorSystem: SpringActorSystem) {

        // A suspend function: call it from a coroutine or a suspend controller method.
        suspend fun greet(name: String): String =
            actorSystem.awaitGetOrSpawn<GreeterActor, _>("greeter")
                .awaitAsk(GreeterActor.Greet(name), timeout = 3.seconds)
    }
    ```

In a WebFlux application, wrap the stage with `Mono.fromCompletionStage(...)` (Java) or use a
`suspend` controller method (Kotlin).

`getOrSpawn` checks for the running actor and spawns it in one step, so callers that ask for the same
**new** id at the same moment all get the same actor.

### The spawn builder: custom settings

`actorSystem.actor(Class)` returns a builder. Set the id and any options, then end with `spawn()`,
`getOrSpawn()` or `spawnAndWait()`:

=== "Java"

    ```java
    CompletionStage<SpringActorHandle<OrderActor.Command>> order = actorSystem
            .actor(OrderActor.class)
            .withId("order-42")
            .withBlockingDispatcher()                       // it calls a JPA repository
            .withMailbox(MailboxConfig.bounded(1_000))
            .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(3, Duration.ofMinutes(1)))
            .withTimeout(Duration.ofSeconds(5))
            .getOrSpawn();
    ```

=== "Kotlin"

    ```kotlin
    // In a suspend function
    val order: SpringActorHandle<OrderActor.Command> = actorSystem
        .actor<OrderActor, _>()
        .withId("order-42")
        .withBlockingDispatcher()                       // it calls a JPA repository
        .withMailbox(MailboxConfig.bounded(1_000))
        .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(3, 1.minutes.toJavaDuration()))
        .withTimeout(5.seconds)
        .awaitGetOrSpawn()
    ```

| Builder method | What it sets | Default |
|---|---|---|
| `withId(String)` | The actor id. Required unless you pass a context. | none |
| `withContext(SpringActorContext)` | A custom context (see [Custom actor context](#custom-actor-context)). Its `actorId()` is the id. | a context holding the id |
| `withTimeout(Duration)` | How long to wait for the spawn | 3 s |
| `withSupervisionStrategy(...)` | What happens when a handler throws. See [Supervision](supervision.md). | stop |
| `withDefaultDispatcher()`, `withBlockingDispatcher()`, `withDispatcherFromConfig(name)`, `withDispatcherSameAsParent()` | The thread pool. See [Dispatchers](dispatchers.md). | default dispatcher |
| `withMailbox(MailboxConfig)` | The mailbox: `MailboxConfig.bounded(n)` or `MailboxConfig.fromConfig(name)`. See [Mailboxes](dispatchers.md#mailboxes). | unbounded |
| `withTags(TagsConfig)` | Tags in the `pekkoTags` log field. See [Logging](logging.md). | none |
| `withMdc(MdcConfig)` | Fixed MDC values on every log line of the actor. See [Logging](logging.md). | none |
| `asClusterSingleton()` | One instance in the cluster. Cluster mode only. See [Singletons](../cluster/singletons.md). | off |

| Ending | Returns | Notes |
|---|---|---|
| `spawn()` | `CompletionStage<SpringActorHandle<C>>` | Fails if an actor with this class and id already runs |
| `getOrSpawn()` | `CompletionStage<SpringActorHandle<C>>` | Returns the running actor if there is one; the options apply only when it spawns |
| `spawnAndWait()` | `SpringActorHandle<C>` | Blocks the calling thread until the actor runs |

`actorSystem.getOrSpawn(Class, id)` is `actor(Class).withId(id).getOrSpawn()` with every option at its
default.

!!! warning "Don't block where threads are shared"
    `spawnAndWait()` (and `.toCompletableFuture().join()` on any stage) blocks the calling thread. That is
    fine in a test or at startup, but not inside an actor's handler or a WebFlux pipeline: compose the
    stage instead, or use the `suspend` functions in Kotlin.

### find and exists

`find` returns a handle to a running actor and `exists` says whether one runs. Neither spawns anything.
Use them when an absent actor means something to your code, for example "this order was never
opened":

=== "Java"

    ```java
    actorSystem.find(OrderActor.class, "order-42").thenAccept(order -> order.ifPresentOrElse(
            o -> o.tell(new OrderActor.MarkPaid(42)),
            () -> log.info("order-42 is not running")));
    ```

=== "Kotlin"

    ```kotlin
    // In a suspend function
    val order = actorSystem.awaitGet<OrderActor, _>("order-42")
    if (order == null) {
        log.info("order-42 is not running")
    } else {
        order.tell(OrderActor.MarkPaid(42))
    }
    ```

Both look at the top-level actors of this node and at the [cluster singletons](../cluster/singletons.md)
this node spawned. When the actor system doesn't answer within the timeout (3 s by default), the stage
fails with a `TimeoutException`; it never reports a running actor as missing.

`get(Class, id)`, which completes with `null` when no actor runs, is deprecated: use `find`.

### Keeping a handle

A `SpringActorHandle` stays valid for as long as the actor runs, and sending through it costs nothing
extra. If a service talks to the same actor on every request, look it up once and keep the stage:

=== "Java"

    ```java
    @Service
    public class CounterService {
        private final SpringActorSystem actorSystem;
        private final AtomicReference<CompletionStage<SpringActorHandle<CounterActor.Command>>> counter =
                new AtomicReference<>();

        public CounterService(SpringActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        private CompletionStage<SpringActorHandle<CounterActor.Command>> counter() {
            return counter.updateAndGet(existing ->
                    existing != null ? existing : actorSystem.getOrSpawn(CounterActor.class, "counter"));
        }

        public CompletionStage<Long> increment() {
            return counter().thenCompose(c -> c.ask(new CounterActor.Increment()).execute());
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class CounterService(private val actorSystem: SpringActorSystem) {

        // Spawned on first use; later calls reuse the same handle.
        private val counter = AtomicReference<CompletionStage<SpringActorHandle<CounterActor.Command>>>()

        private fun counter(): CompletionStage<SpringActorHandle<CounterActor.Command>> =
            counter.updateAndGet { existing ->
                existing ?: actorSystem.getOrSpawn(CounterActor::class.java, "counter")
            }

        suspend fun increment(): Long = counter().await().awaitAsk(CounterActor.Increment())
    }
    ```

A kept handle points at one actor. If that actor stops, messages sent through the handle are lost (they
go to dead letters). Keep handles only to actors that live as long as the service, or look the actor up
again after it stops.

## Custom actor context

`SpringActor<C>` receives a plain `SpringActorContext`, which holds the actor id. To give an actor more
than its id when it starts, define your own context class and implement `SpringActorWithContext<C, CTX>`
instead:

=== "Java"

    ```java
    @Component
    public class ReportActor implements SpringActorWithContext<ReportActor.Command, ReportActor.ReportContext> {

        public static class ReportContext extends SpringActorContext {
            private final String reportId;
            final String ownerEmail;

            public ReportContext(String reportId, String ownerEmail) {
                this.reportId = reportId;
                this.ownerEmail = ownerEmail;
            }

            @Override
            public String actorId() {
                return reportId;
            }
        }

        public interface Command {}

        public record Build() implements Command {}

        @Override
        public SpringActorBehavior<Command> create(ReportContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(Build.class, (ctx, msg) -> {
                        ctx.getLog().info("Building {} for {}", actorContext.actorId(), actorContext.ownerEmail);
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    // Spawning it
    actorSystem.actor(ReportActor.class)
            .withContext(new ReportActor.ReportContext("report-7", "kim@example.com"))
            .getOrSpawn();
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class ReportActor : SpringActorWithContext<ReportActor.Command, ReportActor.ReportContext> {

        class ReportContext(private val reportId: String, val ownerEmail: String) : SpringActorContext() {
            override fun actorId(): String = reportId
        }

        sealed interface Command

        class Build : Command

        override fun create(actorContext: ReportContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .onMessage(Build::class.java) { ctx, _ ->
                    ctx.log.info("Building {} for {}", actorContext.actorId(), actorContext.ownerEmail)
                    SpringActorBehavior.same()
                }
                .build()
    }

    // Spawning it, in a suspend function
    actorSystem.actor<ReportActor, _>()
        .withContext(ReportActor.ReportContext("report-7", "kim@example.com"))
        .awaitGetOrSpawn()
    ```

Always spawn such an actor through the builder with `withContext(...)`. The shortcut
`actorSystem.getOrSpawn(ReportActor.class, id)` builds a plain context, which `create` can't accept, so
that spawn fails. `get` and `exists` work as usual: they only need the id.

## Stopping actors

An actor runs until it is stopped. There are three ways to stop one:

| From | How | Notes |
|---|---|---|
| Outside the actor | `handle.stop()` | The stop request goes into the mailbox, so messages sent before it are handled first. |
| Inside the actor | return `SpringActorBehavior.stopped()` from a handler | Stops after the current message. |
| A parent | `ctx.stop(childRef)` in the parent's handler | Only for the parent's own children. |

When an actor stops, its `PostStop` signal handler runs (see [Behaviors](behaviors.md#lifecycle-signals)),
its children stop, and messages still in its mailbox are dropped. Stopping is final: to use the id again,
spawn a new actor. Stopping takes a moment, so a spawn with the same id right after `stop()` can still
find the name taken; `getOrSpawn` after the old actor is gone, or [watch](behaviors.md#watching-other-actors)
it and spawn on `ActorTerminated`.

An idle actor is not stopped for you. If you create one actor per user or per order, stop it when its
work is done (for example with a [timer](behaviors.md#timers) that fires after some idle time), or use
[sharded actors](../cluster/sharded-actors.md), which can passivate idle entities.

## Child actors

An actor can spawn its own actors, called children. Use children to split work under one owner: a
`ParentSupervisor` with a worker per task, a chat room with an actor per connection. Children are tied
to their parent:

- A child stops when its parent stops.
- The parent decides how each child is supervised (see [Supervision](supervision.md#supervising-children)).
- A child's id only has to be unique among its parent's children.

### Enable child operations on the parent

Spawning, finding and checking children works through messages that the parent handles for you. They
are turned on when the parent's command type extends `FrameworkCommand`:

=== "Java"

    ```java
    public interface Command extends FrameworkCommand {}
    ```

=== "Kotlin"

    ```kotlin
    sealed interface Command : FrameworkCommand
    ```

Without it, the parent ignores these requests, and every child operation fails with a timeout.

### Spawning children from inside the parent

Inside a handler, `ctx.getSelf()` is a handle to the parent. Call `child(ChildClass)` on it to get a
child builder. The spawn is a message to the parent itself, and the parent handles it after the current
message. So **never wait for it inside the handler** (`spawnAndWait()` or `join()`): the parent would wait
for a message it can't handle until it finishes waiting, and the spawn times out. Use `pipeToSelf` to
get the result back as a message instead:

=== "Java"

    ```java
    @Component
    public class DispatcherActor implements SpringActor<DispatcherActor.Command> {

        public interface Command extends FrameworkCommand {}

        public record Assign(String workerId, String task) implements Command {}

        // The result of a child spawn, delivered back to this actor as a message.
        record WorkerReady(Assign assign, SpringActorHandle<WorkerActor.Command> worker, Throwable error)
                implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(Assign.class, (ctx, msg) -> {
                        ctx.pipeToSelf(
                                ctx.getSelf()
                                        .child(WorkerActor.class)
                                        .withId(msg.workerId())
                                        .withSupervisionStrategy(SupervisionStrategy.restart())
                                        .getOrSpawn(),
                                (worker, error) -> new WorkerReady(msg, worker, error));
                        return SpringActorBehavior.same();
                    })
                    .onMessage(WorkerReady.class, (ctx, msg) -> {
                        if (msg.error() != null) {
                            ctx.getLog().error("Could not start worker {}", msg.assign().workerId(), msg.error());
                        } else {
                            msg.worker().tell(new WorkerActor.DoTask(msg.assign().task()));
                        }
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class DispatcherActor : SpringActor<DispatcherActor.Command> {

        sealed interface Command : FrameworkCommand

        data class Assign(val workerId: String, val task: String) : Command

        // The result of a child spawn, delivered back to this actor as a message.
        data class WorkerReady(
            val assign: Assign,
            val worker: SpringActorHandle<WorkerActor.Command>?,
            val error: Throwable?,
        ) : Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .onMessage(Assign::class.java) { ctx, msg ->
                    ctx.pipeToSelf(
                        ctx.self
                            .child(WorkerActor::class.java)
                            .withId(msg.workerId)
                            .withSupervisionStrategy(SupervisionStrategy.restart())
                            .getOrSpawn(),
                    ) { worker, error -> WorkerReady(msg, worker, error) }
                    SpringActorBehavior.same()
                }
                .onMessage(WorkerReady::class.java) { ctx, msg ->
                    val worker = msg.worker
                    if (msg.error != null || worker == null) {
                        ctx.log.error("Could not start worker {}", msg.assign.workerId, msg.error)
                    } else {
                        worker.tell(WorkerActor.DoTask(msg.assign.task))
                    }
                    SpringActorBehavior.same()
                }
                .build()
    }
    ```

`WorkerActor` is an ordinary actor (`@Component`, `SpringActor<WorkerActor.Command>`). Children get
Spring dependency injection like any other actor.

You can also start children when the parent starts, in the [`withState`](behaviors.md#state-with-withstate)
factory: call `spawn()` there and don't wait for the result.

### Child operations

The child builder (`handle.child(ChildClass)`) has the same shape as the spawn builder:

| Method | Notes |
|---|---|
| `withId(id)` or `withContext(ctx)` | Required |
| `withSupervisionStrategy(strategy)` | How the parent handles the child's failures. Default: stop. |
| `withTimeout(duration)` | Default 3 s |
| `withDispatcher(DispatcherConfig)` | `DispatcherConfig.blocking()`, `.sameAsParent()`, `.fromConfig(name)`, `.defaultDispatcher()` |
| `withMailbox(MailboxConfig)`, `withTags(TagsConfig)`, `withMdc(MdcConfig)` | As for top-level actors |
| `spawn()` | Spawns the child. If a child with that class and id already runs, returns it. |
| `getOrSpawn()` | Returns the running child, or spawns it |
| `spawnAndWait()` | Blocks until the child runs. Never call it inside the parent. |
| `get()` | Deprecated: use `handle.child(ChildClass, id).get()`, which returns an `Optional` |
| `exists()` | Whether the child runs |

For lookups only, `handle.child(ChildClass, id)` returns a child reference whose `get()` completes with an
`Optional`:

=== "Java"

    ```java
    CompletionStage<Optional<SpringActorHandle<WorkerActor.Command>>> worker =
            dispatcher.child(WorkerActor.class, "worker-1").get();
    CompletionStage<Boolean> running =
            dispatcher.child(WorkerActor.class, "worker-1").withTimeout(Duration.ofSeconds(1)).exists();
    ```

=== "Kotlin"

    ```kotlin
    val worker: CompletionStage<Optional<SpringActorHandle<WorkerActor.Command>>> =
        dispatcher.child(WorkerActor::class.java, "worker-1").get()
    val running: CompletionStage<Boolean> =
        dispatcher.child(WorkerActor::class.java, "worker-1").withTimeout(1.seconds.toJavaDuration()).exists()
    ```

These calls work from outside the parent too: any code with the parent's handle (here `dispatcher`) can
spawn, find or check its children.

Differences from top-level actors:

- `spawn()` on a child returns the running child when the id is taken; a top-level `spawn()` fails.
- A child can't be a cluster singleton.
- Dispatchers are set with `withDispatcher(DispatcherConfig...)` instead of the `with...Dispatcher()`
  shortcuts.

Inside the parent's handlers, `ctx.getChildren()` lists the running children and `ctx.stop(child)` stops
one (see [Behaviors](behaviors.md#the-behavior-context)).

## Pitfalls

- **State in bean fields.** All actors of a class share the bean. Keep per-actor state in `withState`.
- **Blocking inside an actor.** Waiting on a future, `spawnAndWait()`, `Thread.sleep` or blocking I/O on
  the default dispatcher holds a shared thread. Use `pipeToSelf`, or the blocking dispatcher for blocking I/O.
- **Child operations time out.** The parent's command type doesn't extend `FrameworkCommand`, or the
  parent is waiting inside a handler.
- **Second `spawn()` with the same id fails.** Use `getOrSpawn()`, or check `exists` first.
- **Actors that are never stopped.** One actor per request or per user, never stopped, grows memory
  without bound.

## Related

- [Messaging](messaging.md): tell, ask, replies and message adapters
- [Supervision](supervision.md): what happens when a handler throws
- [Behaviors](behaviors.md): state, timers, watching, lifecycle signals
- [Dispatchers](dispatchers.md): thread pools and mailboxes
- [Sharded actors](../cluster/sharded-actors.md): one actor per id across a cluster
- [Kotlin](../kotlin.md): the `actor-kotlin` helpers used in the Kotlin tabs
