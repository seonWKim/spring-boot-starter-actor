# Reliable Delivery

`tell` is **at most once**: a message that is lost, or that reaches an actor whose handler fails, is
gone. Reliable delivery makes a send **at least once, handled once per key**: the sender retries the
message until the receiving actor acknowledges it, and the receiver remembers the idempotency keys it
handled, so a retry doesn't run the handler again. It works for local actors and sharded entities.

Use it for commands that must not be dropped while the application runs: an order to an order actor,
a payment step to a sharded entity.

Don't use it when:

- a lost message is fine (a UI refresh, a cache warm-up): plain `tell` is cheaper;
- the caller needs an answer: use [`ask`](messaging.md);
- the message must survive a restart of the **sending** node, or the receiver's deduplication must
  survive a restart of the **receiving** actor: see [Limits](#limits). Store the work first, for
  example with [event sourcing](../data/event-sourcing.md).

## Receiving: wrap the behavior

The receiving actor wraps the behavior its `create` returns with `ReliableDelivery.consumer(...)`. Its
handlers and its supervision stay as they are.

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
            return ReliableDelivery.consumer(SpringActorBehavior.builder(Command.class, actorContext)
                    .withSupervisionStrategy(SupervisionStrategy.restart())
                    .onMessage(PlaceOrder.class, (ctx, msg) -> {
                        orders.save(msg.orderId()); // throws: no ack, the sender retries
                        return SpringActorBehavior.same();
                    })
                    .build());
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
                    .withSupervisionStrategy(SupervisionStrategy.restart())
                    .onMessage(PlaceOrder::class.java) { _, msg ->
                        orders.save(msg.orderId) // throws: no ack, the sender retries
                        SpringActorBehavior.same()
                    }
                    .build(),
            )
    }
    ```

- **Ack:** a reliable message is acknowledged when one of the actor's handlers returned. If the handler
  threw, or no handler matched the message, there is no ack and the sender retries.
- **Deduplication:** the actor remembers the key of every message it handled, with a fingerprint of its
  payload (its `hashCode`). A message whose key it remembers is acknowledged again without calling the
  handler.
- **Payload conflict:** the same key with a different payload is not a duplicate. The handler doesn't run,
  and the send fails at once with `PAYLOAD_CONFLICT` (see [Outcomes](#outcomes)). Give reliable messages
  value equality (records, data classes): a class that keeps `Object`'s `hashCode` has no fingerprint, so
  its payloads aren't compared.
- **How long:** keys are remembered for `spring.actor.delivery.idempotency-ttl` (default 10 minutes).
  One actor can override it: `ReliableDelivery.consumer(behavior, Duration.ofHours(1))`.
- **How many:** at most `spring.actor.delivery.max-remembered-keys` keys per actor (default 10,000). Past
  that, the oldest keys are forgotten first, so a burst can't grow the memory without limit.
- **Plain messages:** `tell` and `ask` to the same actor keep working, without deduplication.

The behavior must come from `SpringActorBehavior.builder(...)`. Sharded entities wrap their behavior the
same way, in their `create(SpringShardedActorContext)`.

### Stale generation: not the owner yet, or anymore

During a deployment a message can reach an actor that doesn't own its state: a new instance still loading
it, or an old one that a newer generation replaced. The handler says so by throwing
`StaleGenerationException`. The message is not acknowledged and its key is not remembered, the actor is not
restarted, and the sender retries the same key and payload, which reaches the active owner once it is
ready.

=== "Java"

    ```java
    .onMessage(PlaceOrder.class, (ctx, msg) -> {
        if (!ownership.isActive(msg.orderId())) {
            throw new StaleGenerationException("order " + msg.orderId() + " is not active here");
        }
        orders.save(msg.orderId());
        return SpringActorBehavior.same();
    })
    ```

=== "Kotlin"

    ```kotlin
    .onMessage(PlaceOrder::class.java) { _, msg ->
        if (!ownership.isActive(msg.orderId)) {
            throw StaleGenerationException("order ${msg.orderId} is not active here")
        }
        orders.save(msg.orderId)
        SpringActorBehavior.same()
    }
    ```

`ownership` stands for your own check, such as a lease or an epoch in your database. For blue-green
deployments, `DeploymentGeneration.check(epoch)` is that check: it throws `StaleGenerationException` for work
stamped by a generation that is no longer active
([The active generation](../operations/deployments.md#the-active-generation)). For a plain `tell`,
the exception is an ordinary failure for the actor's supervision.

## Sending to an actor

Inject the `SpringReliableDelivery` bean and build a sender for an actor handle. `send` returns a
`CompletionStage` that completes when the actor acknowledged the message.

=== "Java"

    ```java
    @Service
    public class OrderService {
        private final ReliableSender<OrderActor.Command> orders;

        public OrderService(SpringActorSystem actorSystem, SpringReliableDelivery reliableDelivery) {
            SpringActorHandle<OrderActor.Command> orderActor =
                    actorSystem.actor(OrderActor.class).withId("orders").spawnAndWait();
            this.orders = reliableDelivery.to(orderActor).build();
        }

        public CompletionStage<Void> placeOrder(String orderId) {
            // The order id is the key: placing the same order twice runs the handler once.
            return orders.send(new OrderActor.PlaceOrder(orderId), orderId);
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class OrderService(actorSystem: SpringActorSystem, reliableDelivery: SpringReliableDelivery) {

        private val orders: ReliableSender<OrderActor.Command> = reliableDelivery
            .to(actorSystem.actor(OrderActor::class.java).withId("orders").spawnAndWait())
            .build()

        // Suspends until the actor acknowledged the order (kotlinx.coroutines.future.await).
        // The order id is the key: placing the same order twice runs the handler once.
        suspend fun placeOrder(orderId: String) {
            orders.send(OrderActor.PlaceOrder(orderId), orderId).await()
        }
    }
    ```

**Idempotency keys:**

- `send(message)`: the starter makes a new key for this send. Its retries run the handler once, but
  sending the same message again is a new message.
- `send(message, key)`: you pass a business id, such as an order id or a payment id. Any send with that
  key runs the handler once while the receiver remembers it: re-sends from another sender, from another
  node, or after the sender's node restarted.

A sender holds no resources: build one per receiver and keep it, or build one per call.

## Retries

A send is retried with exponential backoff and full jitter until it is acknowledged. Every attempt carries
the same idempotency key and the same payload. The backoff doubles from `initialBackoff` up to
`maxBackoff`, and the pause before the next attempt is random, from zero to the backoff, so senders that
failed together don't retry together.

| `RetryPolicy` | Property | Default | |
|---|---|---|---|
| `maxAttempts` | `spring.actor.delivery.retry.max-attempts` | 5 | attempts in total, including the first |
| `timeout` | `spring.actor.delivery.retry.timeout` | 30s | from the first attempt; no attempt starts after it |
| `attemptTimeout` | `spring.actor.delivery.retry.attempt-timeout` | 3s | how long one attempt waits for the ack |
| `initialBackoff` / `maxBackoff` | `spring.actor.delivery.retry.initial-backoff` / `.max-backoff` | 200ms / 5s | the backoff doubles up to the maximum |

The properties set the policy of every sender. One sender can set its own:

=== "Java"

    ```java
    ReliableSender<OrderActor.Command> orders = reliableDelivery.to(orderActor)
            .withRetry(RetryPolicy.defaults()
                    .withMaxAttempts(10)
                    .withTimeout(Duration.ofMinutes(1))
                    .withBackoff(Duration.ofMillis(100), Duration.ofSeconds(10)))
            .build();
    ```

=== "Kotlin"

    ```kotlin
    val orders: ReliableSender<OrderActor.Command> = reliableDelivery.to(orderActor)
        .withRetry(
            RetryPolicy.defaults()
                .withMaxAttempts(10)
                .withTimeout(Duration.ofMinutes(1))
                .withBackoff(Duration.ofMillis(100), Duration.ofSeconds(10)),
        )
        .build()
    ```

## Outcomes

A send that fails completes its stage with `ReliableDeliveryException`. Its `reason()` says why, and
`reason().isRetryable()` whether sending again can succeed:

| Reason | Retryable | When | Suggested HTTP response |
|---|---|---|---|
| `STALE_GENERATION` | yes | the handler threw `StaleGenerationException` on every attempt | 409 Conflict with `Retry-After` |
| `TIMEOUT` | yes | the last attempt waited its `attemptTimeout` without an ack | 504 Gateway Timeout |
| `UNAVAILABLE` | yes | the last attempt failed at once, for example because the actor is stopped | 503 Service Unavailable |
| `PAYLOAD_CONFLICT` | no | the receiver handled the key with a different payload; not retried | 409 Conflict or 422 Unprocessable Entity |

A retryable failure means the retry policy ran out. The message may still have been handled (only the acks
may have been lost), so a client can send it again with the same key and payload: within the receiver's TTL
it runs once. A payload conflict fails the same way every time: the client must not retry it, and should
use a new key for a new payload.

An application that sends from an HTTP handler can map the reasons once:

=== "Java"

    ```java
    @RestControllerAdvice
    public class ReliableDeliveryErrors {

        @ExceptionHandler(ReliableDeliveryException.class)
        public ResponseEntity<String> deliveryFailed(ReliableDeliveryException e) {
            return switch (e.reason()) {
                case STALE_GENERATION -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .header(HttpHeaders.RETRY_AFTER, "1")
                        .body("Not ready yet, retry with the same key");
                case TIMEOUT -> ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body("Timed out, retry");
                case UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Unavailable, retry");
                case PAYLOAD_CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Key " + e.idempotencyKey() + " was used for a different request");
            };
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @RestControllerAdvice
    class ReliableDeliveryErrors {

        @ExceptionHandler(ReliableDeliveryException::class)
        fun deliveryFailed(e: ReliableDeliveryException): ResponseEntity<String> =
            when (e.reason()) {
                ReliableDeliveryException.Reason.STALE_GENERATION -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .header(HttpHeaders.RETRY_AFTER, "1")
                    .body("Not ready yet, retry with the same key")
                ReliableDeliveryException.Reason.TIMEOUT ->
                    ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body("Timed out, retry")
                ReliableDeliveryException.Reason.UNAVAILABLE ->
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Unavailable, retry")
                ReliableDeliveryException.Reason.PAYLOAD_CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Key ${e.idempotencyKey()} was used for a different request")
            }
    }
    ```

The exception reaches the handler when the controller returns the send's stage or waits for it, as
`await()` does in Kotlin.

## Sending to sharded entities

In cluster mode, `toSharded(...)` builds one sender for all entities of a sharded actor class. `send` takes
the entity id.

=== "Java"

    ```java
    ShardedReliableSender<OrderEntity.Command> payments =
            reliableDelivery.toSharded(OrderEntity.class).build();

    payments.send("order-42", new OrderEntity.Pay(1_000), "payment-77")
            .thenRun(() -> log.info("order-42 acknowledged payment-77"));
    ```

=== "Kotlin"

    ```kotlin
    val payments: ShardedReliableSender<OrderEntity.Command> =
        reliableDelivery.toSharded(OrderEntity::class.java).build()

    // In a suspend function
    payments.send("order-42", OrderEntity.Pay(1_000), "payment-77").await()
    ```

A retry reaches the entity wherever it runs; if the entity stopped or moved, the retry starts it again.
Messages must implement `JsonSerializable` or `CborSerializable`, as for any message that crosses nodes.

## What it costs

- **Per message:** one ask and one ack, and one more of each per retry. No extra actors stay alive.
- **On the receiver:** a key and an expiry for each handled message, up to the TTL and the key limit
  (10,000 keys of about 100 bytes each is about 1 MB per actor).
- **While a receiver is down:** each pending send retries until it gives up; with the defaults, at most
  5 asks over about 30 seconds.

## Limits

- **Dedupe memory is in the actor.** So is the payload fingerprint: a conflict is found only while the key
  is remembered. The memory is lost when the receiving actor restarts by its spawn supervision,
  stops, or (sharded) is passivated or moves to another node. After that, a retry or a re-send with the
  same key runs the handler again. Make handlers safe to run twice when that matters. A durable key store
  is a planned follow-up.
- **Stopped local actors don't come back.** Retries to a local actor that stopped fail when the retry
  policy runs out. A sharded entity starts again on the next retry.
- **No ordering.** Retries can overtake later messages: send one at a time (wait for the stage) when the
  order matters.
- **Not durable on the sender.** A send in progress is lost when the sending node stops; the caller sees
  no result. Re-send with the same key when it restarts.
