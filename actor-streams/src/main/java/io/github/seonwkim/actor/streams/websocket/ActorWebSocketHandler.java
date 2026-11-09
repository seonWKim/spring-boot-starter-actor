package io.github.seonwkim.actor.streams.websocket;

import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.streams.ActorStreams;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * A Spring WebFlux {@link WebSocketHandler} that connects each WebSocket session to actors: text an
 * actor sends to the session's {@code client} reference goes to the client, and text frames from the
 * client go to the consumer that {@link Connector#connect} returns.
 *
 * <pre>{@code
 * new ActorWebSocketHandler(streams, (session, client) -> actorSystem.actor(UserActor.class)
 *         .withContext(new UserContext(session.getId(), client))
 *         .spawn()
 *         .thenApply(user -> text -> user.tell(new Incoming(text))));
 * }</pre>
 *
 * <p>When the client disconnects, the {@code client} reference stops: an actor that {@code watch}es it
 * receives {@code ActorTerminated} and can stop itself. When an actor completes the stream (see
 * {@link #ActorWebSocketHandler(ActorStreams, Connector, Consumer)}) the session closes.
 *
 * <p>The client reference buffers up to 256 messages the client has not taken yet; when the buffer
 * is full, the session closes with an error.
 */
public final class ActorWebSocketHandler implements WebSocketHandler {

    /** Connects one WebSocket session to actors. */
    @FunctionalInterface
    public interface Connector {
        /**
         * Called once per session, before any frame from the client is handled.
         *
         * @param session the WebSocket session
         * @param client the reference that sends text to this client
         * @return where the client's text frames go, once the actors are ready
         */
        CompletionStage<Consumer<String>> connect(WebSocketSession session, SpringActorRef<String> client);
    }

    private final ActorStreams streams;
    private final Connector connector;
    private final Consumer<ActorStreams.Messages<String>> outgoing;

    public ActorWebSocketHandler(ActorStreams streams, Connector connector) {
        this(streams, connector, messages -> {});
    }

    /**
     * @param outgoing configures the client reference, e.g. {@code m -> m.bufferSize(16)} or
     *     {@code m -> m.completeWhen("bye"::equals)}; its {@code onClose} runs when the session ends
     */
    public ActorWebSocketHandler(
            ActorStreams streams, Connector connector, Consumer<ActorStreams.Messages<String>> outgoing) {
        this.streams = streams;
        this.connector = connector;
        this.outgoing = outgoing;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Sinks.One<Consumer<String>> inbound = Sinks.one();
        ActorStreams.Messages<String> messages = streams.messages(String.class);
        outgoing.accept(messages);
        Mono<Void> send = session.send(
                messages.flux(client -> connector.connect(session, client).whenComplete((target, error) -> {
                            if (error != null) {
                                inbound.tryEmitError(error);
                            } else {
                                inbound.tryEmitValue(target);
                            }
                        }))
                        .map(session::textMessage));
        Mono<Void> receive = inbound.asMono()
                .flatMapMany(target -> session.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(target))
                .then();
        // Whichever side ends first (the client closes, or the actors complete) cancels the other,
        // which stops the client reference.
        return Mono.zip(send, receive).then();
    }
}
