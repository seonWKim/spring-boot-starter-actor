# Routers

A router is an actor that owns a pool of identical worker actors and passes each message it receives to
one of them (or to all of them). You send to the router as if it were one actor; the work runs on several
workers at once.

Use a router when one actor can't keep up and the work doesn't depend on shared state: sending emails,
resizing images, calling a slow API. If the work belongs to an entity (an order, a user), use one actor
per entity instead (see [Actors](actors.md) or [Sharded actors](../cluster/sharded-actors.md)); a router
with [consistent hashing](#consistent-hashing) sits in between.

A router lives on one node, and its workers are its children. Routers come with the core starter; no
extra dependency or configuration is needed.

## Creating a router

A router has two parts: the worker actor that does the work, and the router actor that holds the pool.
They share one command type.

=== "Java"

    ```java
    // The worker: an ordinary actor bean
    @Component
    public class ResizeWorker implements SpringActor<ImageRouter.Command> {

        private final ImageService imageService;

        public ResizeWorker(ImageService imageService) {
            this.imageService = imageService;
        }

        @Override
        public SpringActorBehavior<ImageRouter.Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(ImageRouter.Command.class, actorContext)
                    .onMessage(ImageRouter.Resize.class, (ctx, msg) -> {
                        imageService.resize(msg.imageId(), msg.width());
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    // The router: its behavior is a pool of ResizeWorkers
    @Component
    public class ImageRouter implements SpringActor<ImageRouter.Command> {

        public interface Command {}

        public record Resize(String imageId, int width) implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringRouterBehavior.builder(Command.class, actorContext)
                    .withRoutingStrategy(RoutingStrategy.roundRobin())
                    .withPoolSize(8)
                    .withWorkerActors(ResizeWorker.class)
                    .withSupervisionStrategy(SupervisionStrategy.restart())
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    // The worker: an ordinary actor bean
    @Component
    class ResizeWorker(private val imageService: ImageService) : SpringActor<ImageRouter.Command> {

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<ImageRouter.Command> =
            actorBehavior<ImageRouter.Command>(actorContext)
                .onMessage(ImageRouter.Resize::class.java) { _, msg ->
                    imageService.resize(msg.imageId, msg.width)
                    SpringActorBehavior.same()
                }
                .build()
    }

    // The router: its behavior is a pool of ResizeWorkers
    @Component
    class ImageRouter : SpringActor<ImageRouter.Command> {

        sealed interface Command

        data class Resize(val imageId: String, val width: Int) : Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            SpringRouterBehavior.builder(Command::class.java, actorContext)
                .withRoutingStrategy(RoutingStrategy.roundRobin())
                .withPoolSize(8)
                .withWorkerActors(ResizeWorker::class.java)
                .withSupervisionStrategy(SupervisionStrategy.restart())
                .build()
    }
    ```

Spawn and use the router like any other actor:

=== "Java"

    ```java
    @Service
    public class ImageResizer {
        private final SpringActorSystem actorSystem;

        public ImageResizer(SpringActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        public void resizeLater(String imageId) {
            actorSystem.getOrSpawn(ImageRouter.class, "image-router")
                    .thenAccept(router -> router.tell(new ImageRouter.Resize(imageId, 640)));
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class ImageResizer(private val actorSystem: SpringActorSystem) {

        suspend fun resizeLater(imageId: String) {
            actorSystem.awaitGetOrSpawn<ImageRouter, _>("image-router")
                .tell(ImageRouter.Resize(imageId, 640))
        }
    }
    ```

## Builder options

| Method | Required | Default | Notes |
|---|---|---|---|
| `withRoutingStrategy(strategy)` | yes | none | `build()` throws `IllegalStateException` without it |
| `withWorkerActors(Class)` | yes | none | A `SpringActor` bean whose command type is the router's |
| `withPoolSize(n)` | no | 5 | Must be positive |
| `withSupervisionStrategy(strategy)` | no | stop | Applies to each worker. See [Worker failures](#worker-failures). |

## Routing strategies

| Strategy | Who gets a message | Use it for |
|---|---|---|
| `RoutingStrategy.roundRobin()` | Each worker in turn | Tasks that take about the same time |
| `RoutingStrategy.random()` | A random worker | Simple spreading with no bookkeeping |
| `RoutingStrategy.broadcast()` | Every worker | Telling all workers something, such as "clear your cache" |
| `RoutingStrategy.consistentHashing()` | The worker chosen by the message's key | Keeping all messages for one key on one worker |

### Broadcast

With `broadcast()`, every message is handled once per worker: 10 messages to a pool of 5 means 50
handler calls. Use it for rare control messages, not for regular work. A broadcast ask gets the first
worker's reply; the other replies are dropped.

### Consistent hashing

`consistentHashing()` sends messages with the same key to the same worker, as long as the pool doesn't
change. A worker can then keep a cache per key, or handle one customer's messages in order. Give a
message its key by implementing `ConsistentHashable`:

=== "Java"

    ```java
    public record ProcessOrder(String customerId, String orderId) implements Command, ConsistentHashable {
        @Override
        public String getConsistentHashKey() {
            return customerId;   // one customer's orders always go to the same worker
        }
    }

    // In the router's create():
    return SpringRouterBehavior.builder(Command.class, actorContext)
            .withRoutingStrategy(RoutingStrategy.consistentHashing())
            .withPoolSize(10)
            .withWorkerActors(OrderWorker.class)
            .build();
    ```

=== "Kotlin"

    ```kotlin
    data class ProcessOrder(val customerId: String, val orderId: String) : Command, ConsistentHashable {
        // one customer's orders always go to the same worker
        override fun getConsistentHashKey(): String = customerId
    }

    // In the router's create():
    SpringRouterBehavior.builder(Command::class.java, actorContext)
        .withRoutingStrategy(RoutingStrategy.consistentHashing())
        .withPoolSize(10)
        .withWorkerActors(OrderWorker::class.java)
        .build()
    ```

- The key must not be `null`; a `null` key fails the routing with `IllegalStateException`.
- A message that doesn't implement `ConsistentHashable` is hashed by its `toString()`. Records and data
  classes have a stable `toString()`; most other classes don't, so implement `ConsistentHashable`.
- `consistentHashing(virtualNodesFactor)` sets how many points each worker gets on the hash ring
  (default 10, minimum 1). More points spread keys more evenly.
- This is not a replacement for sharding: the mapping changes when a worker is replaced, and it stays on
  one node. For one actor per key across a cluster, use [sharded actors](../cluster/sharded-actors.md).

## Ask through a router

A router forwards `AskCommand`s like any message, and the worker that handles it replies to the asker:

=== "Java"

    ```java
    public static class Validate extends AskCommand<Boolean> implements Command {
        public final String data;

        public Validate(String data) {
            this.data = data;
        }
    }

    // In the worker:
    .onMessage(Validate.class, (ctx, msg) -> {
        msg.reply(!msg.data.isBlank());
        return SpringActorBehavior.same();
    })

    // The caller:
    CompletionStage<Boolean> valid = router.ask(new ValidationRouter.Validate("payload"))
            .withTimeout(Duration.ofSeconds(2))
            .execute();
    ```

=== "Kotlin"

    ```kotlin
    class Validate(val data: String) : AskCommand<Boolean>(), Command

    // In the worker:
    .onMessage(Validate::class.java) { _, msg ->
        msg.reply(msg.data.isNotBlank())
        SpringActorBehavior.same()
    }

    // The caller, in a suspend function:
    val valid: Boolean = router.awaitAsk(ValidationRouter.Validate("payload"), timeout = 2.seconds)
    ```

## Worker failures

The router's `withSupervisionStrategy` applies to each worker. Without it, a worker whose handler throws
**stops** and leaves the pool, so the pool shrinks with every failure. For a pool that should keep its
size, set `SupervisionStrategy.restart()` (or `restart().withLimit(...)`). With restart, the failed
message is dropped and the other messages are still handled, as the router tests check. See
[Supervision](supervision.md) for what each strategy does.

## Workers and Spring beans

Every worker in a pool is created from the same worker bean, so:

- Injected services are shared by all workers (and by every pool that uses the bean). That is what you
  want for repositories, clients and connection pools.
- Per-worker state belongs in the worker's [`withState`](behaviors.md#state-with-withstate) object, not
  in a field of the bean.
- Workers don't have ids of their own. All workers of a pool get the same generated
  `actorContext.actorId()` (`worker-<uuid>`), so don't use it to tell workers apart.

A worker that blocks (JDBC, blocking HTTP) holds a thread of the router's dispatcher. Spawn the router
with `withBlockingDispatcher()` or a [custom dispatcher](dispatchers.md) in that case.

## Pitfalls

- **No routing strategy.** `build()` fails; there is no default strategy.
- **Pool that shrinks.** Without a restart strategy, failing workers stop for good.
- **Shared state in the worker bean.** All workers of all pools share it.
- **Ordering.** Round robin and random spread consecutive messages over different workers, so they can be
  handled out of order. Use consistent hashing (or one actor per key) when order per key matters.

## Related

- [Actors](actors.md): spawning the router
- [Supervision](supervision.md): worker strategies
- [Dispatchers](dispatchers.md): thread pools for blocking workers
- [Sharded actors](../cluster/sharded-actors.md): one actor per key across a cluster
- [Pekko routers](https://pekko.apache.org/docs/pekko/current/typed/routers.html): the pool router the starter uses
