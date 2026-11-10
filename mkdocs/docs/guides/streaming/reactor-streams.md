# Reactor Streams

`actor-streams` connects actors to Project Reactor. It gives you three things, all as `Mono` and `Flux`:

| You want | Method | Backpressure |
|---|---|---|
| The reply of one ask | `ask(askBuilder)` | n/a |
| To send a stream of elements to an actor | `askEach(elements, parallelism, ask)` | at most `parallelism` asks in flight |
| The messages an actor sends | `messages(type)...flux(onSubscribe)` | a bounded buffer, or an acknowledgement per message |

No Pekko type appears in this API. Pekko Streams runs underneath and stays an implementation detail.

## Setup

Add the module next to the starter:

=== "Gradle"

    ```kotlin
    implementation("io.github.seonwkim:actor-spring-boot-starter:$version")
    implementation("io.github.seonwkim:actor-streams:$version")
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>io.github.seonwkim</groupId>
        <artifactId>actor-streams</artifactId>
        <version>${actor.version}</version>
    </dependency>
    ```

With `actor-streams` on the classpath, the application has an `ActorStreams` bean. Inject it where you need it:

=== "Java"

    ```java
    @Service
    public class OrderService {
        private final ActorStreams streams;
        private final SpringActorSystem actorSystem;

        public OrderService(ActorStreams streams, SpringActorSystem actorSystem) {
            this.streams = streams;
            this.actorSystem = actorSystem;
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class OrderService(
        private val streams: ActorStreams,
        private val actorSystem: SpringActorSystem,
    )
    ```

## `Mono` of an ask

Pass the ask builder you already use. The command is sent when the `Mono` is subscribed, not when it is
created, and again on each new subscription. The builder's timeout and `onTimeout` fallback apply.

=== "Java"

    ```java
    Mono<Order> order = streams.ask(orders.ask(new GetOrder(id)).withTimeout(Duration.ofSeconds(2)));
    ```

=== "Kotlin"

    ```kotlin
    val order: Mono<Order> = streams.ask(orders.ask(GetOrder(id)).withTimeout(2.seconds))
    ```

It works the same for a sharded entity's handle.

## Feeding an actor from a `Flux`

`askEach` asks an actor about each element and emits the replies in the order of the elements:

=== "Java"

    ```java
    Flux<Receipt> receipts = streams.askEach(payments, 8, payment -> ledger.ask(new Charge(payment)));
    ```

=== "Kotlin"

    ```kotlin
    val receipts: Flux<Receipt> = streams.askEach(payments, 8) { payment -> ledger.ask(Charge(payment)) }
    ```

At most `parallelism` asks (here 8) wait for a reply at once. Until one of them is answered, `askEach`
requests nothing more from `payments`. So a slow actor slows the upstream `Flux` (a Kafka consumer, a
database cursor, an HTTP body) instead of filling its mailbox.

A failed ask, for example a timeout, fails the `Flux`. Give the builder `onTimeout(...)` for a fallback
reply, or use Reactor's `onErrorResume` and `retry` operators.

The actor replies as for any ask:

=== "Java"

    ```java
    .onMessage(Charge.class, (ctx, msg) -> {
        msg.reply(ledger.charge(msg.payment()));
        return SpringActorBehavior.same();
    })
    ```

=== "Kotlin"

    ```kotlin
    .onMessage(Charge::class.java) { _, msg ->
        msg.reply(ledger.charge(msg.payment))
        SpringActorBehavior.same()
    }
    ```

## A `Flux` of the messages an actor sends

`messages(type)` creates, for each subscriber, a reference that actors can send messages to. Whatever
they send to it comes out of the `Flux`. Pass the reference to the actor in `flux(onSubscribe)`:

=== "Java"

    ```java
    Flux<Price> prices = streams.messages(Price.class)
            .onClose(ref -> ticker.tell(new Unsubscribe(ref)))
            .flux(ref -> ticker.tell(new Subscribe(ref)));
    ```

=== "Kotlin"

    ```kotlin
    val prices: Flux<Price> = streams.messages(Price::class.java)
        .onClose { ref -> ticker.tell(Unsubscribe(ref)) }
        .flux { ref -> ticker.tell(Subscribe(ref)) }
    ```

The reference is a `SpringActorRef<Price>`, so the actor stores it and calls `tell` like on any other
reference. In this ticker, the state holds the behavior context (`ctx`) and a set of subscribers:

=== "Java"

    ```java
    .onMessage(Subscribe.class, (state, msg) -> {
        state.subscribers().add(msg.subscriber());
        state.ctx().watch(msg.subscriber());
        return SpringActorBehavior.same();
    })
    .onMessage(PriceChanged.class, (state, msg) -> {
        state.subscribers().forEach(subscriber -> subscriber.tell(msg.price()));
        return SpringActorBehavior.same();
    })
    .onSignal(ActorTerminated.class, (state, signal) -> {
        state.subscribers().remove(signal.ref());
        return SpringActorBehavior.same();
    })
    ```

=== "Kotlin"

    ```kotlin
    .onMessage(Subscribe::class.java) { state, msg ->
        state.subscribers.add(msg.subscriber)
        state.ctx.watch(msg.subscriber)
        SpringActorBehavior.same()
    }
    .onMessage(PriceChanged::class.java) { state, msg ->
        state.subscribers.forEach { subscriber -> subscriber.tell(msg.price) }
        SpringActorBehavior.same()
    }
    .onSignal(ActorTerminated::class.java) { state, signal ->
        state.subscribers.removeIf { it == signal.ref() }
        SpringActorBehavior.same()
    }
    ```

### When the subscriber goes away

When the subscriber cancels (an HTTP client disconnects, a `take(10)` is satisfied), the reference stops.
The actor can learn it in two ways; use either:

- It `watch`es the reference and receives `ActorTerminated`, as above.
- `onClose` runs with the reference when the `Flux` ends for that subscriber: completed, failed or
  cancelled. Tell the actor to forget it there.

Messages sent to a stopped reference go to dead letters.

### Ending the stream from the actor

`completeWhen` names the message that ends the `Flux`. Messages sent before it are still emitted; it is not:

=== "Java"

    ```java
    Flux<Event> events = streams.messages(Event.class)
            .completeWhen(event -> event instanceof Done)
            .flux(ref -> job.tell(new Watch(ref)));
    ```

=== "Kotlin"

    ```kotlin
    val events: Flux<Event> = streams.messages(Event::class.java)
        .completeWhen { event -> event is Done }
        .flux { ref -> job.tell(Watch(ref)) }
    ```

### Buffer and overflow

A subscriber may request fewer messages than the actor sends. The reference keeps up to `bufferSize`
messages (default 256) that the subscriber has not requested yet. When the buffer is full, `overflow`
decides:

| `Overflow` | Effect |
|---|---|
| `FAIL` (default) | The `Flux` fails with an error |
| `DROP_OLDEST` | The oldest buffered message is dropped |
| `DROP_NEWEST` | The new message is dropped |

=== "Java"

    ```java
    Flux<Price> prices = streams.messages(Price.class)
            .bufferSize(16)
            .overflow(ActorStreams.Overflow.DROP_OLDEST)
            .flux(ref -> ticker.tell(new Subscribe(ref)));
    ```

=== "Kotlin"

    ```kotlin
    val prices: Flux<Price> = streams.messages(Price::class.java)
        .bufferSize(16)
        .overflow(ActorStreams.Overflow.DROP_OLDEST)
        .flux { ref -> ticker.tell(Subscribe(ref)) }
    ```

`DROP_OLDEST` suits values where only the latest matters, such as prices or positions.

### Backpressure to the sending actor

When nothing may be dropped, let the sending actor wait for the subscriber. With `ackTo(sender, ack)`, the
reference sends `ack` to `sender` each time a message has gone on to the subscriber. The sender sends its
next message only after an `ack`:

=== "Java"

    ```java
    Flux<Row> rows = streams.messages(Row.class)
            .ackTo(exporter, new Exporter.Ack())
            .completeWhen(row -> row.isLast())
            .flux(ref -> exporter.tell(new Exporter.Start(ref)));
    ```

=== "Kotlin"

    ```kotlin
    val rows: Flux<Row> = streams.messages(Row::class.java)
        .ackTo(exporter, Exporter.Ack())
        .completeWhen { row -> row.isLast }
        .flux { ref -> exporter.tell(Exporter.Start(ref)) }
    ```

=== "Java"

    ```java
    .onMessage(Start.class, (state, msg) -> {
        state.subscriber = msg.subscriber();
        state.sendNext();
        return SpringActorBehavior.same();
    })
    .onMessage(Ack.class, (state, msg) -> {
        state.sendNext();
        return SpringActorBehavior.same();
    })
    ```

=== "Kotlin"

    ```kotlin
    .onMessage(Start::class.java) { state, msg ->
        state.subscriber = msg.subscriber
        state.sendNext()
        SpringActorBehavior.same()
    }
    .onMessage(Ack::class.java) { state, _ ->
        state.sendNext()
        SpringActorBehavior.same()
    }
    ```

With `ackTo`, there is no buffer, so `bufferSize` and `overflow` do not apply.

## Limits

- The bridges are tested on one node. The reference that `messages` creates lives on the subscriber's node.
- An actor cannot fail a `messages` `Flux`; it can end it with `completeWhen`. To report an error, send a
  message that the subscriber maps to an error with Reactor's `handle` operator.

## Related

- [Streaming to Clients](streaming-to-clients.md): Server-Sent Events, WebSocket and gRPC on top of `messages`
- [Messaging](../core/messaging.md): `tell`, `ask` and replies
- [Behaviors](../core/behaviors.md): `watch` and `ActorTerminated`
