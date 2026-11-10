# Reliable Delivery

`tell` is **at most once**: a message that is lost, or that reaches an actor which then crashes, is gone.
Reliable delivery makes one sender-to-actor flow **at least once**: the sender keeps each message until
the receiving actor confirms it, and sends it again after the actor crashed, restarted or (for sharded
entities) moved. It wraps Pekko's
[reliable delivery](https://pekko.apache.org/docs/pekko/current/typed/reliable-delivery.html)
(producer and consumer controllers), with no Pekko types in your code.

Use it for messages that must not be lost while the application runs: an order to an order actor, a
payment step to a sharded entity.

Don't use it when:

- a lost message is fine or the caller retries anyway (a UI refresh, a cache warm-up): plain `tell` is
  cheaper;
- the caller needs an answer: use [`ask`](messaging.md), and retry on timeout;
- the message must survive a restart of the **sending** node: there is no durable queue yet (see
  [Limits](#limits)). Store it first, for example with [event sourcing](../data/event-sourcing.md).

## Receiving: wrap the behavior

The receiving actor wraps the behavior its `create` returns with `ReliableDelivery.consumer(...)`. Its
handlers stay as they are.

=== "Java"

    ```java
    @Component
    public class OrderActor implements SpringActor<OrderActor.Command> {

        public interface Command {}

        public record PlaceOrder(String orderId) implements Command {}

        private final OrderRepository orders;

        public OrderActor(OrderRepository orders) {
            this.orders = orders;
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return ReliableDelivery.consumer(
                    SpringActorBehavior.builder(Command.class, actorContext)
                            .onMessage(PlaceOrder.class, (ctx, msg) -> {
                                orders.save(msg.orderId()); // throws: not confirmed, delivered again
                                return SpringActorBehavior.same();
                            })
                            .build(),
                    SupervisionStrategy.restart());
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class OrderActor(private val orders: OrderRepository) : SpringActor<OrderActor.Command> {

        sealed interface Command

        data class PlaceOrder(val orderId: String) : Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            ReliableDelivery.consumer(
                actorBehavior<Command>(actorContext)
                    .onMessage(PlaceOrder::class.java) { _, msg ->
                        orders.save(msg.orderId) // throws: not confirmed, delivered again
                        SpringActorBehavior.same()
                    }
                    .build(),
                SupervisionStrategy.restart(),
            )
    }
    ```

**How it confirms:** a reliably sent message is confirmed when its handler returns. If the handler
throws, the message is not confirmed, the failure goes to supervision as usual, and the sender delivers
the message again once the actor restarted or resumed. Messages from one sender arrive one at a time,
in the order they were sent.

**Supervision** goes to `ReliableDelivery.consumer(behavior, strategy)`, not to the builder's
`withSupervisionStrategy`: a supervisor inside the behavior would hide the failure and the message would
count as confirmed. The actor fails to start if the builder sets one. Without a strategy, the actor
follows its spawn builder's strategy, and stops on a failure by default; a stopped local actor can't be
delivered to (see below).

Plain `tell` and `ask` to the same actor keep working; they are not confirmed.

Sharded entities wrap their behavior the same way, in their `create(SpringShardedActorContext)`.

## Sending to an actor

Inject the `SpringReliableDelivery` bean and start a sender to an actor handle. `send` returns a
`CompletionStage` that completes when the actor confirmed the message.

=== "Java"

    ```java
    @Service
    public class OrderService {
        private final ReliableSender<OrderActor.Command> orders;

        public OrderService(SpringActorSystem actorSystem, SpringReliableDelivery reliableDelivery) {
            SpringActorHandle<OrderActor.Command> orderActor =
                    actorSystem.actor(OrderActor.class).withId("orders").spawnAndWait();
            this.orders = reliableDelivery.to(orderActor).withProducerId("order-service").start();
        }

        public CompletionStage<Void> placeOrder(String orderId) {
            return orders.send(new OrderActor.PlaceOrder(orderId));
        }

        @PreDestroy
        void close() {
            orders.close();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class OrderService(actorSystem: SpringActorSystem, reliableDelivery: SpringReliableDelivery) {

        private val orders: ReliableSender<OrderActor.Command> = reliableDelivery
            .to(actorSystem.actor(OrderActor::class.java).withId("orders").spawnAndWait())
            .withProducerId("order-service")
            .start()

        // Suspends until the actor confirmed the order (kotlinx.coroutines.future.await).
        suspend fun placeOrder(orderId: String) {
            orders.send(OrderActor.PlaceOrder(orderId)).await()
        }

        @PreDestroy
        fun close() = orders.close()
    }
    ```

- **Producer id:** names the sender in logs and in the receiver's deduplication. It defaults to a random
  id. Two running senders must not share one.
- **No timeout of its own:** the stage waits as long as the message is unconfirmed. Add one with
  `toCompletableFuture().orTimeout(...)`; the message is still delivered after the timeout.
- **The actor stops:** stages of unconfirmed messages fail with `IllegalStateException`, and the sender
  stops. A stopped actor doesn't come back by itself; supervise it with `restart()` so it does.
- **Thread-safe:** any thread can call `send`. `close()` stops the sender; stages of unconfirmed messages
  fail with `IllegalStateException`.

## Sending to sharded entities

In cluster mode, `toSharded(...)` starts one sender for all entities of a sharded actor class. `send` takes
the entity id. The entities wrap their behavior with `ReliableDelivery.consumer(...)` as above.

=== "Java"

    ```java
    ShardedReliableSender<OrderEntity.Command> orders =
            reliableDelivery.toSharded(OrderEntity.class).withProducerId("order-service").start();

    orders.send("order-42", new OrderEntity.Pay(1_000))
            .thenRun(() -> log.info("order-42 confirmed the payment"));
    ```

=== "Kotlin"

    ```kotlin
    val orders: ShardedReliableSender<OrderEntity.Command> =
        reliableDelivery.toSharded(OrderEntity::class.java).withProducerId("order-service").start()

    // In a suspend function
    orders.send("order-42", OrderEntity.Pay(1_000)).await()
    ```

An entity that crashed, was passivated or moved to another node gets its unconfirmed messages again:
the sender resends the first unconfirmed message to an entity that has been quiet for
`resend-first-unconfirmed-idle-timeout` (10 seconds), which starts the entity again. Messages must
implement `JsonSerializable` or `CborSerializable`, as for any message that crosses nodes.

## At least once means duplicates

A message can arrive twice: for example, the handler wrote to the database and the node crashed before
the confirmation reached the sender. Make handlers idempotent: key writes by an id from the message, or
remember the last ids handled.

## What it costs

- **Memory on the sender:** every unconfirmed message stays in the sender's memory, up to the flow
  control window (50 messages in flight per receiver) plus what waits for demand. A slow receiver makes
  the sender's queue grow; watch the stages that don't complete.
- **Extra messages:** each reliably sent message passes through the sender, a producer controller and a
  consumer controller, and its confirmation travels back the same way: about seven local messages
  instead of one. Across nodes, two cross the network: the message and its acknowledgement. Each sender
  is an actor with a producer controller; each receiver has one consumer controller per sender. A
  sharded sender also runs a short-lived actor per unconfirmed message to complete its stage.
- **Throughput:** the receiver handles one message at a time, as with `tell`. Up to 50 messages are in
  flight per receiver; when it falls behind, further messages wait in the sender.
- **Latency after a failure:** a local actor gets the message again as soon as it restarted. A sharded
  entity gets it after the idle timeout above.

## Tuning

The underlying settings are Pekko's and can be set as raw keys under `spring.actor.pekko`:

```yaml
spring:
  actor:
    pekko:
      reliable-delivery:
        consumer-controller:
          flow-control-window: 50          # messages in flight per receiver
          resend-interval-min: 2s          # how soon a receiver asks again for lost messages
        sharding:
          producer-controller:
            resend-first-unconfirmed-idle-timeout: 10s
            buffer-size: 1000              # messages waiting for an entity with no demand
```

## Limits

- **Not durable.** Unconfirmed messages live in the sender's memory and are lost when the sending node
  stops. A durable queue on the persistence journal is planned.
- **One sender, one producer id.** Each sender has its own order and deduplication. Messages from two
  senders to one actor are not ordered relative to each other.
- **No work pulling.** A pool of workers that pull work as they have capacity is not offered yet.
