# Streaming to Clients

Send what actors produce to browsers and services over Server-Sent Events, WebSocket or gRPC. Each bridge
builds on the `messages` `Flux` of [Reactor Streams](reactor-streams.md): a client gets a reference that
actors send to, and when the client disconnects, the reference stops. An actor that `watch`es it receives
`ActorTerminated` and can forget the client or stop itself.

All three need `actor-streams`:

=== "Gradle"

    ```kotlin
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

| Transport | Stack | What you write |
|---|---|---|
| Server-Sent Events | Spring MVC or WebFlux | A controller method that returns the `messages` `Flux` |
| WebSocket | WebFlux | An `ActorWebSocketHandler` with a connector |
| gRPC | grpc-java or Spring gRPC | `ActorGrpc.respond(flux, responseObserver)` in the service method |

## Server-Sent Events

Return the `Flux` from a controller with `produces = text/event-stream`. Spring MVC and WebFlux both write
each element as an event, and cancel the `Flux` when the client goes away:

=== "Java"

    ```java
    @RestController
    public class PricesController {
        private final ActorStreams streams;
        private final SpringActorSystem actorSystem;

        public PricesController(ActorStreams streams, SpringActorSystem actorSystem) {
            this.streams = streams;
            this.actorSystem = actorSystem;
        }

        @GetMapping(path = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public Flux<Price> prices() {
            return Mono.fromCompletionStage(actorSystem.actor(TickerActor.class).withId("ticker").getOrSpawn())
                    .flatMapMany(ticker -> streams.messages(Price.class)
                            .bufferSize(16)
                            .overflow(ActorStreams.Overflow.DROP_OLDEST)
                            .flux(ref -> ticker.tell(new Subscribe(ref))));
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @RestController
    class PricesController(
        private val streams: ActorStreams,
        private val actorSystem: SpringActorSystem,
    ) {
        @GetMapping(path = ["/prices"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
        fun prices(): Flux<Price> =
            Mono.fromCompletionStage(actorSystem.actor<TickerActor, _>().withId("ticker").getOrSpawn())
                .flatMapMany { ticker ->
                    streams.messages(Price::class.java)
                        .bufferSize(16)
                        .overflow(ActorStreams.Overflow.DROP_OLDEST)
                        .flux { ref -> ticker.tell(Subscribe(ref)) }
                }
    }
    ```

The ticker keeps its subscribers and forgets one when it stops, as shown in
[Reactor Streams](reactor-streams.md#a-flux-of-the-messages-an-actor-sends).

When the client disconnects:

- **WebFlux** notices at once, and the reference stops.
- **Spring MVC** (a servlet container) notices only on its next write to that client. The reference stops
  after the next message the actor sends, which fails to write. If an actor may stay quiet for a long time,
  send a heartbeat now and then.

Use a small buffer with `DROP_OLDEST` for values where only the latest matters, so a slow client never
holds many messages.

## WebSocket (WebFlux)

`ActorWebSocketHandler` is a WebFlux `WebSocketHandler`. For each connection it creates a `client`
reference and calls your connector, which connects it to actors and returns where the client's text frames
go:

=== "Java"

    ```java
    @Configuration
    public class WebSocketConfig {

        @Bean
        public HandlerMapping chatSocket(ActorStreams streams, SpringActorSystem actorSystem) {
            ActorWebSocketHandler handler = new ActorWebSocketHandler(streams, (session, client) -> actorSystem
                    .actor(UserActor.class)
                    .withContext(new UserContext(UUID.randomUUID().toString(), client))
                    .spawn()
                    .thenApply(user -> text -> user.tell(new Incoming(text))));
            return new SimpleUrlHandlerMapping(Map.of("/ws/chat", handler), 1);
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Configuration
    class WebSocketConfig {

        @Bean
        fun chatSocket(streams: ActorStreams, actorSystem: SpringActorSystem): HandlerMapping {
            val handler = ActorWebSocketHandler(streams) { _, client ->
                actorSystem.actor<UserActor, _>()
                    .withContext(UserContext(UUID.randomUUID().toString(), client))
                    .spawn()
                    .thenApply { user -> Consumer<String> { text -> user.tell(Incoming(text)) } }
            }
            return SimpleUrlHandlerMapping(mapOf("/ws/chat" to handler), 1)
        }
    }
    ```

The user actor sends text to the browser with `client.tell(json)`. To stop with the connection, it watches
the reference (`context` is the actor's own `SpringActorContext` subclass, which carries the client reference):

=== "Java"

    ```java
    .withState(ctx -> {
        ctx.watch(context.client());
        return new UserState(ctx, context.client());
    })
    .onSignal(ActorTerminated.class, (state, signal) -> SpringActorBehavior.stopped())
    ```

=== "Kotlin"

    ```kotlin
    .withState { ctx ->
        ctx.watch(context.client)
        UserState(ctx, context.client)
    }
    .onSignal(ActorTerminated::class.java) { _, _ -> SpringActorBehavior.stopped() }
    ```

When the client disconnects, the reference stops and the user actor stops with it. The other way round,
when the outgoing `Flux` completes (see `completeWhen` below), the session closes.

The third constructor argument configures the outgoing messages, with the same settings as
[`messages`](reactor-streams.md#buffer-and-overflow):

=== "Java"

    ```java
    new ActorWebSocketHandler(streams, connector, messages -> messages.bufferSize(64).completeWhen("bye"::equals));
    ```

=== "Kotlin"

    ```kotlin
    ActorWebSocketHandler(streams, connector) { messages -> messages.bufferSize(64).completeWhen { it == "bye" } }
    ```

By default the reference buffers 256 messages the client has not taken yet, and closes the session with an
error when the buffer is full.

The [chat example](../../examples/chat.md) uses this bridge.

## gRPC

`ActorGrpc.respond` sends a `Flux` (or a `Mono`, or any Reactive Streams `Publisher`) to a gRPC response
stream. Call it in a server-streaming or unary method of a grpc-java service, or of a Spring gRPC service:

=== "Java"

    ```java
    @Service
    public class PriceService extends PriceServiceGrpc.PriceServiceImplBase {
        private final ActorStreams streams;
        private final SpringActorHandle<TickerActor.Command> ticker; // spawned at startup, set in the constructor

        @Override
        public void watch(WatchRequest request, StreamObserver<Price> responses) {
            ActorGrpc.respond(
                    streams.messages(Price.class).flux(ref -> ticker.tell(new Subscribe(ref))),
                    responses);
        }

        @Override
        public void getPrice(PriceRequest request, StreamObserver<Price> responses) {
            ActorGrpc.respond(streams.ask(ticker.ask(new GetPrice(request.getSymbol()))), responses);
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class PriceService(
        private val streams: ActorStreams,
        private val ticker: SpringActorHandle<TickerActor.Command>, // spawned at startup
    ) : PriceServiceGrpc.PriceServiceImplBase() {

        override fun watch(request: WatchRequest, responses: StreamObserver<Price>) {
            ActorGrpc.respond(
                streams.messages(Price::class.java).flux { ref -> ticker.tell(Subscribe(ref)) },
                responses,
            )
        }

        override fun getPrice(request: PriceRequest, responses: StreamObserver<Price>) {
            ActorGrpc.respond(streams.ask(ticker.ask(GetPrice(request.symbol))), responses)
        }
    }
    ```

- It requests the next element only while the call is ready to send, so a slow client slows the
  `Flux`. With [`ackTo`](reactor-streams.md#backpressure-to-the-sending-actor), the slowness reaches the
  sending actor.
- When the client cancels or disconnects, the `Flux` is cancelled and the reference stops.
- When the `Flux` fails, the call ends with the error's gRPC status (`UNKNOWN` unless the error is a
  `StatusException` or `StatusRuntimeException`).

`actor-streams` does not bring grpc-java: the application has it already (directly or through Spring gRPC).
The bridge is built against grpc-java 1.83 and uses only its stable `grpc-stub` API.

## Related

- [Reactor Streams](reactor-streams.md): `ask`, `askEach` and `messages`
- [Chat example](../../examples/chat.md): the WebSocket bridge in a full application
- [Kotlin](../kotlin.md): suspend functions and coroutine controllers
