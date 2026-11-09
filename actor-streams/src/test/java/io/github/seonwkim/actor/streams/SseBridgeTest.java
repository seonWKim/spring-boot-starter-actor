package io.github.seonwkim.actor.streams;

import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.streams.TestActors.TickerActor;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** An SSE endpoint that returns a messages Flux, on Spring MVC and on WebFlux. */
class SseBridgeTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @EnableAutoConfiguration
    @EnableActorSupport
    static class App {}

    @RestController
    static class PricesController {
        private final ActorStreams streams;
        private final SpringActorSystem actorSystem;

        PricesController(ActorStreams streams, SpringActorSystem actorSystem) {
            this.streams = streams;
            this.actorSystem = actorSystem;
        }

        @GetMapping(path = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        Flux<String> prices() {
            return Mono.fromCompletionStage(ticker(actorSystem)).flatMapMany(ticker -> streams.messages(String.class)
                    .flux(ref -> ticker.tell(new TickerActor.Subscribe(ref))));
        }
    }

    static java.util.concurrent.CompletionStage<SpringActorHandle<TickerActor.Command>> ticker(
            SpringActorSystem actorSystem) {
        return actorSystem.actor(TickerActor.class).withId("ticker").getOrSpawn();
    }

    @ParameterizedTest
    @EnumSource(
            value = WebApplicationType.class,
            names = {"SERVLET", "REACTIVE"})
    void aClientDisconnectStopsTheActorSideSubscription(WebApplicationType stack) throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                        App.class, PricesController.class, TickerActor.class)
                .web(stack)
                .properties("server.port=0")
                .run()) {
            SpringActorHandle<TickerActor.Command> ticker = ticker(context.getBean(SpringActorSystem.class))
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            String port = context.getEnvironment().getProperty("local.server.port");
            List<String> received = new CopyOnWriteArrayList<>();

            Disposable client = WebClient.create("http://localhost:" + port)
                    .get()
                    .uri("/prices")
                    .retrieve()
                    .bodyToFlux(String.class)
                    .subscribe(received::add);
            await().atMost(TIMEOUT).until(() -> TestActors.subscribers(ticker) == 1);
            ticker.tell(new TickerActor.Publish("a"));
            await().atMost(TIMEOUT).until(() -> received.contains("a"));

            client.dispose();
            // A servlet container sees the disconnect only when it writes: keep publishing.
            await().atMost(TIMEOUT).until(() -> {
                ticker.tell(new TickerActor.Publish("tick"));
                return TestActors.subscribers(ticker) == 0;
            });
        }
    }
}
