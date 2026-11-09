package io.github.seonwkim.actor.streams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.streams.TestActors.TickerActor;
import io.github.seonwkim.actor.streams.websocket.ActorWebSocketHandler;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;

/** A WebFlux WebSocket endpoint on {@link ActorWebSocketHandler}. */
class WebSocketBridgeTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @EnableAutoConfiguration
    @EnableActorSupport
    static class App {}

    /** Text from a client is published by the ticker to every client, this one included. */
    private static HandlerMapping tickerSocket(GenericApplicationContext context) {
        ActorWebSocketHandler handler = new ActorWebSocketHandler(
                context.getBean(ActorStreams.class),
                (session, client) -> SseBridgeTest.ticker(context.getBean(SpringActorSystem.class))
                        .thenApply(ticker -> {
                            ticker.tell(new TickerActor.Subscribe(client));
                            Consumer<String> publish = text -> ticker.tell(new TickerActor.Publish(text));
                            return publish;
                        }));
        return new SimpleUrlHandlerMapping(Map.of("/ticker", handler), 1);
    }

    @Test
    void framesReachTheActorAndAClientDisconnectStopsTheActorSideSubscription() throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class, TickerActor.class)
                .web(WebApplicationType.REACTIVE)
                .properties("server.port=0")
                .initializers((GenericApplicationContext ctx) ->
                        ctx.registerBean("tickerSocket", HandlerMapping.class, () -> tickerSocket(ctx)))
                .run()) {
            SpringActorHandle<TickerActor.Command> ticker = SseBridgeTest.ticker(
                            context.getBean(SpringActorSystem.class))
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            String port = context.getEnvironment().getProperty("local.server.port");
            List<String> received = new CopyOnWriteArrayList<>();

            // The client sends "hello", reads one frame and closes the connection.
            new ReactorNettyWebSocketClient()
                    .execute(URI.create("ws://localhost:" + port + "/ticker"), session -> session.send(
                                    Mono.just(session.textMessage("hello")))
                            .thenMany(session.receive()
                                    .map(WebSocketMessage::getPayloadAsText)
                                    .take(1))
                            .doOnNext(received::add)
                            .then())
                    .block(TIMEOUT);

            assertThat(received).containsExactly("hello");
            await().atMost(TIMEOUT).until(() -> TestActors.subscribers(ticker) == 0);
        }
    }
}
