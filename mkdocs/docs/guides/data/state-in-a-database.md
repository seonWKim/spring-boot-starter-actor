# State in a Database

An actor's fields live in memory. They are lost when the actor stops: after a restart of the
application, a deploy, a passivated or moved [sharded entity](../cluster/sharded-actors.md), or a
[singleton](../cluster/singletons.md) that moves to another node. To keep the state, store it in your
database with the tools you already use: Spring Data JPA, `JdbcTemplate`, R2DBC.

The pattern is simple: the actor **loads** its state when it starts and **saves** it in the message handler
that changes it. Actors are Spring beans, so repositories and services are injected into them like into
any other bean. No module of the starter is needed for this.

Use this when you need the latest state only. When the history of changes matters (an audit trail, read
models, replays), use [event sourcing](event-sourcing.md) instead.

## Setup

Add Spring Data and your database driver next to the starter:

=== "Gradle"

    ```kotlin
    implementation("io.github.seonwkim:actor-spring-boot-starter:$version")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    ```

Configure the `DataSource` as usual (`spring.datasource.*`). The samples below use an `Order` JPA entity and
this repository, from the
[persistence example](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/persistence):

=== "Java"

    ```java
    public interface OrderRepository extends JpaRepository<Order, String> {
        Optional<Order> findByOrderId(String orderId);
    }
    ```

=== "Kotlin"

    ```kotlin
    interface OrderRepository : JpaRepository<Order, String> {
        fun findByOrderId(orderId: String): Optional<Order>
    }
    ```

## Load on start, save on change

The actor id is the key of the row. `withState` runs once when the actor starts: load the row there. Each
handler that changes the state saves it before it replies.

=== "Java"

    ```java
    @Component
    public class OrderActor implements SpringActor<OrderActor.Command> {

        private final OrderRepository orderRepository;

        public OrderActor(OrderRepository orderRepository) {
            this.orderRepository = orderRepository;
        }

        public interface Command {}

        public static class CreateOrder extends AskCommand<OrderResponse> implements Command {
            public final String customerId;
            public final double amount;

            public CreateOrder(String customerId, double amount) {
                this.customerId = customerId;
                this.amount = amount;
            }
        }

        public static class GetOrder extends AskCommand<OrderResponse> implements Command {}

        public record OrderResponse(boolean success, Order order, String message) {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            String orderId = actorContext.actorId();
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new OrderBehavior(
                            ctx, orderId, orderRepository, orderRepository.findByOrderId(orderId).orElse(null)))
                    .onMessage(CreateOrder.class, OrderBehavior::onCreate)
                    .onMessage(GetOrder.class, OrderBehavior::onGet)
                    .build();
        }

        private static class OrderBehavior {
            private final SpringBehaviorContext<Command> ctx;
            private final String orderId;
            private final OrderRepository repository;
            private Order current; // null until the order is created

            OrderBehavior(SpringBehaviorContext<Command> ctx, String orderId, OrderRepository repository, Order current) {
                this.ctx = ctx;
                this.orderId = orderId;
                this.repository = repository;
                this.current = current;
            }

            SpringActorBehavior<Command> onCreate(CreateOrder msg) {
                if (current != null) {
                    msg.reply(new OrderResponse(false, current, "Order already exists"));
                    return SpringActorBehavior.same();
                }
                current = repository.save(new Order(orderId, msg.customerId, msg.amount));
                ctx.getLog().info("Order created: {}", orderId);
                msg.reply(new OrderResponse(true, current, "Order created"));
                return SpringActorBehavior.same();
            }

            SpringActorBehavior<Command> onGet(GetOrder msg) {
                msg.reply(current == null
                        ? new OrderResponse(false, null, "Order not found")
                        : new OrderResponse(true, current, "OK"));
                return SpringActorBehavior.same();
            }
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class OrderActor(private val orderRepository: OrderRepository) : SpringActor<OrderActor.Command> {

        sealed interface Command

        class CreateOrder(val customerId: String, val amount: Double) : AskCommand<OrderResponse>(), Command

        class GetOrder : AskCommand<OrderResponse>(), Command

        data class OrderResponse(val success: Boolean, val order: Order?, val message: String)

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> {
            val orderId = actorContext.actorId()
            return actorBehavior<Command>(actorContext)
                .withState { ctx ->
                    OrderBehavior(ctx, orderId, orderRepository, orderRepository.findByOrderId(orderId).orElse(null))
                }
                .onMessage(CreateOrder::class.java) { state, msg -> state.onCreate(msg) }
                .onMessage(GetOrder::class.java) { state, msg -> state.onGet(msg) }
                .build()
        }

        class OrderBehavior(
            private val ctx: SpringBehaviorContext<Command>,
            private val orderId: String,
            private val repository: OrderRepository,
            private var current: Order?, // null until the order is created
        ) {
            fun onCreate(msg: CreateOrder): SpringActorBehavior<Command> {
                if (current != null) {
                    msg.reply(OrderResponse(false, current, "Order already exists"))
                    return SpringActorBehavior.same()
                }
                current = repository.save(Order(orderId, msg.customerId, msg.amount))
                ctx.log.info("Order created: {}", orderId)
                msg.reply(OrderResponse(true, current, "Order created"))
                return SpringActorBehavior.same()
            }

            fun onGet(msg: GetOrder): SpringActorBehavior<Command> {
                msg.reply(
                    current?.let { OrderResponse(true, it, "OK") }
                        ?: OrderResponse(false, null, "Order not found"),
                )
                return SpringActorBehavior.same()
            }
        }
    }
    ```

Because an actor handles one message at a time, the state and the row never see two changes at once.
The database is still the source of truth: the next time the actor starts, it reads what was saved.

!!! warning "Save before you reply"
    Reply only after the save has succeeded. If the save throws, the caller gets no success reply, and the
    actor's field and the row don't drift apart. Catch the exception and reply with an error when the
    caller should learn about it instead of waiting for its timeout.

## Run it on the blocking dispatcher

JPA and JDBC calls block their thread until the database answers. On the default dispatcher, which every
actor shares, a few slow queries can hold all its threads and stall unrelated actors. Spawn actors that
block on the **blocking dispatcher** with `withBlockingDispatcher()`:

=== "Java"

    ```java
    @RestController
    @RequestMapping("/api/orders")
    public class OrderController {

        private final SpringActorSystem actorSystem;

        public OrderController(SpringActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        @PostMapping("/{orderId}")
        public CompletionStage<OrderActor.OrderResponse> createOrder(
                @PathVariable String orderId, @RequestParam String customerId, @RequestParam double amount) {
            return actorSystem
                    .actor(OrderActor.class)
                    .withId(orderId)
                    .withBlockingDispatcher()
                    .getOrSpawn() // the running actor, or a new one on the blocking dispatcher
                    .thenCompose(order -> order.ask(new OrderActor.CreateOrder(customerId, amount))
                            .withTimeout(Duration.ofSeconds(5))
                            .execute());
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @RestController
    @RequestMapping("/api/orders")
    class OrderController(private val actorSystem: SpringActorSystem) {

        // A suspend handler: see the Kotlin guide for what it needs on the classpath
        @PostMapping("/{orderId}")
        suspend fun createOrder(
            @PathVariable orderId: String,
            @RequestParam customerId: String,
            @RequestParam amount: Double,
        ): OrderActor.OrderResponse =
            actorSystem
                .actor<OrderActor, _>()
                .withId(orderId)
                .withBlockingDispatcher()
                .awaitGetOrSpawn() // the running actor, or a new one on the blocking dispatcher
                .awaitAsk(OrderActor.CreateOrder(customerId, amount), timeout = 5.seconds)
    }
    ```

Use `getOrSpawn()` on the spawn builder, as above, rather than `actorSystem.getOrSpawn(OrderActor.class, id)`:
only the builder carries the dispatcher.

The blocking dispatcher is Pekko's default blocking dispatcher, a fixed pool of threads. To run it on
virtual threads instead, set `spring.actor.virtual-threads.enabled: true` (or keep the default `auto` and
turn on Spring Boot's `spring.threads.virtual.enabled`). See [Dispatchers](../core/dispatchers.md).

## Non-blocking calls with R2DBC

Spring Data R2DBC returns a `Mono` instead of blocking, so the actor can stay on the default dispatcher.
Turn the result into a message with `pipeToSelf`: the actor handles it like any other message, one at a
time, so its state stays safe without locks.

=== "Java"

    ```java
    @Component
    public class ReactiveOrderActor implements SpringActor<ReactiveOrderActor.Command> {

        private final ReactiveOrderRepository repository; // a Spring Data R2DBC repository

        public ReactiveOrderActor(ReactiveOrderRepository repository) {
            this.repository = repository;
        }

        public interface Command {}

        public record SaveOrder(Order order) implements Command {}

        // The result of a save, sent by the actor to itself.
        private record SaveDone(Order saved, Throwable failure) implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ReactiveBehavior::new)
                    .onMessage(SaveOrder.class, ReactiveBehavior::onSave)
                    .onMessage(SaveDone.class, ReactiveBehavior::onSaveDone)
                    .build();
        }

        private class ReactiveBehavior {
            private final SpringBehaviorContext<Command> ctx;
            private Order current;

            ReactiveBehavior(SpringBehaviorContext<Command> ctx) {
                this.ctx = ctx;
            }

            SpringActorBehavior<Command> onSave(SaveOrder msg) {
                // Returns at once; the save runs on R2DBC's threads, not the actor's.
                ctx.pipeToSelf(repository.save(msg.order()).toFuture(), SaveDone::new);
                return SpringActorBehavior.same();
            }

            SpringActorBehavior<Command> onSaveDone(SaveDone done) {
                if (done.failure() != null) {
                    ctx.getLog().error("Save failed", done.failure());
                } else {
                    current = done.saved(); // state changes only inside a message handler
                }
                return SpringActorBehavior.same();
            }
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class ReactiveOrderActor(
        private val repository: ReactiveOrderRepository, // a Spring Data R2DBC repository
    ) : SpringActor<ReactiveOrderActor.Command> {

        sealed interface Command

        data class SaveOrder(val order: Order) : Command

        // The result of a save, sent by the actor to itself.
        private data class SaveDone(val saved: Order?, val failure: Throwable?) : Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .withState { ctx -> ReactiveBehavior(ctx) }
                .onMessage(SaveOrder::class.java) { state, msg -> state.onSave(msg) }
                .onMessage(SaveDone::class.java) { state, done -> state.onSaveDone(done) }
                .build()

        private inner class ReactiveBehavior(private val ctx: SpringBehaviorContext<Command>) {
            private var current: Order? = null

            fun onSave(msg: SaveOrder): SpringActorBehavior<Command> {
                // Returns at once; the save runs on R2DBC's threads, not the actor's.
                ctx.pipeToSelf(repository.save(msg.order).toFuture()) { saved, failure -> SaveDone(saved, failure) }
                return SpringActorBehavior.same()
            }

            fun onSaveDone(done: SaveDone): SpringActorBehavior<Command> {
                if (done.failure != null) {
                    ctx.log.error("Save failed", done.failure)
                } else {
                    current = done.saved // state changes only inside a message handler
                }
                return SpringActorBehavior.same()
            }
        }
    }
    ```

To reply to the caller once the save is done, carry the `AskCommand` in the result message and call
`reply` in its handler.

!!! warning "Don't touch state in a callback"
    Never change actor state in `subscribe(...)`, `thenAccept(...)` or any other callback: it runs on
    another thread, alongside the actor. Send the result to the actor with `pipeToSelf` and change state
    in the handler.

## Transactions

When one change writes several tables, put the writes in a `@Transactional` method of a Spring service and
call it from the actor. Spring's transaction works as anywhere else, because the call runs on the actor's
thread from start to end.

=== "Java"

    ```java
    @Service
    public class OrderService {

        private final OrderRepository orders;
        private final OrderItemRepository items;

        public OrderService(OrderRepository orders, OrderItemRepository items) {
            this.orders = orders;
            this.items = items;
        }

        @Transactional
        public Order createWithItems(String orderId, String customerId, double amount, List<OrderItem> newItems) {
            Order order = orders.save(new Order(orderId, customerId, amount));
            for (OrderItem item : newItems) {
                item.setOrder(order);
                items.save(item);
            }
            return order;
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class OrderService(
        private val orders: OrderRepository,
        private val items: OrderItemRepository,
    ) {
        @Transactional
        fun createWithItems(orderId: String, customerId: String, amount: Double, newItems: List<OrderItem>): Order {
            val order = orders.save(Order(orderId, customerId, amount))
            for (item in newItems) {
                item.setOrder(order)
                items.save(item)
            }
            return order
        }
    }
    ```

Inject `OrderService` into the actor instead of the repositories, and call it in the handler. Run the actor
on the blocking dispatcher, as above. Don't put `@Transactional` on the actor class: the actor's handlers
are not called through a Spring proxy.

## Snapshots of large state

When building the state from many rows is slow, store a snapshot of it now and then: serialize the state
to JSON in a snapshot table every N changes or every few seconds, and on start, load the latest snapshot
before falling back to the rows. The persistence example's
[`SnapshotOrderActor`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/persistence/src/main/java/io/github/seonwkim/example/persistence/snapshot)
does this with its own `SnapshotStrategy` and `HybridSnapshotStrategy` classes. These classes belong to the
example, not to the starter.

[Event-sourced actors](event-sourcing.md) have snapshots built in (`snapshotEvery(n)`).

## Choosing an approach

| Approach | Use it when | What to watch |
|---|---|---|
| JPA or JDBC on the blocking dispatcher | The app already uses JPA, as most Spring apps do | Each call holds a thread of the blocking dispatcher. Size the pool, or use virtual threads |
| R2DBC with `pipeToSelf` | Many actors talk to the database at once, or the app is already reactive | Every result comes back as a message, so each call needs a result message type |
| Event sourcing with `actor-persistence` | The history of changes matters, not only the latest state | Events are a stored contract: their JSON shape must stay readable. See [Event-Sourced Actors](event-sourcing.md) |

## One writer per row

The pattern is safe because one actor owns one row. Keep it that way:

- **One node.** A regular actor with a given id runs once per node, so one node never has two writers.
- **Several nodes.** Each node can spawn its own `OrderActor` with the same id, and then two actors write
  the same row. Make the actor a [sharded entity](../cluster/sharded-actors.md) (one per id in the cluster)
  or, for a single global actor, a [singleton](../cluster/singletons.md). For extra safety, add a version
  column (`@Version` in JPA) so a stale write fails instead of overwriting.

## Pitfalls

- **Blocking on the default dispatcher.** Always spawn JPA and JDBC actors with `withBlockingDispatcher()`.
- **Changing state in callbacks.** Use `pipeToSelf`.
- **Replying before saving.** The caller then sees a success that may not be stored.
- **Lazy JPA associations.** The actor keeps the entity after the transaction has ended; load what you need
  in the query (`JOIN FETCH`) or map it to your own class.

## Related

- [Persistence example](../../examples/persistence.md): JPA, a hand-made event log and snapshots
- [Event-Sourced Actors](event-sourcing.md): store events instead of state
- [Dispatchers](../core/dispatchers.md): the blocking dispatcher and virtual threads
- [Sharded Actors](../cluster/sharded-actors.md): one actor per id across a cluster
