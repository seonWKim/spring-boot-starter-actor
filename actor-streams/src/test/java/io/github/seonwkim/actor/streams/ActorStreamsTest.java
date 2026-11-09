package io.github.seonwkim.actor.streams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.streams.TestActors.CounterActor;
import io.github.seonwkim.actor.streams.TestActors.GateActor;
import io.github.seonwkim.actor.streams.TestActors.TickerActor;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ActorStreamsTest {

    /** An application with the starter and actor-streams on its classpath. */
    @EnableActorSupport
    static class App {}

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(App.class)
            .withBean(GateActor.class)
            .withBean(TickerActor.class)
            .withBean(CounterActor.class);

    private static <C> SpringActorHandle<C> spawn(
            ApplicationContext context, Class<? extends SpringActorWithContext<C, ?>> type) throws Exception {
        @SuppressWarnings("unchecked")
        Class<SpringActorWithContext<C, ?>> actorClass = (Class<SpringActorWithContext<C, ?>>) type;
        return context.getBean(SpringActorSystem.class)
                .actor(actorClass)
                .withId(type.getSimpleName())
                .spawn()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
    }

    private static int ask(SpringActorHandle<?> actor, AskCommand<Integer> question) throws Exception {
        return actor.ask(question)
                .withTimeout(TIMEOUT)
                .execute()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
    }

    @Test
    void theAskIsSentOnSubscribeNotWhenTheMonoIsCreated() {
        runner.run(context -> {
            ActorStreams streams = context.getBean(ActorStreams.class);
            SpringActorHandle<GateActor.Command> gate = spawn(context, GateActor.class);
            gate.tell(new GateActor.Open());

            Mono<Integer> doubled =
                    streams.ask(gate.ask(new GateActor.Double(21)).withTimeout(TIMEOUT));
            assertThat(ask(gate, new GateActor.Received())).isZero();

            assertThat(doubled.block(TIMEOUT)).isEqualTo(42);
            assertThat(ask(gate, new GateActor.Received())).isEqualTo(1);
        });
    }

    @Test
    void aSlowActorSlowsTheFeedingFluxInsteadOfFillingItsMailbox() {
        runner.run(context -> {
            ActorStreams streams = context.getBean(ActorStreams.class);
            SpringActorHandle<GateActor.Command> gate = spawn(context, GateActor.class);
            AtomicInteger produced = new AtomicInteger();
            Flux<Integer> numbers = Flux.range(1, 100).doOnNext(n -> produced.incrementAndGet());

            CompletableFuture<List<Integer>> replies = streams.askEach(
                            numbers, 4, n -> gate.ask(new GateActor.Double(n)).withTimeout(TIMEOUT))
                    .collectList()
                    .toFuture();

            // The gate holds every reply: only 4 asks are in flight, and the producer waits.
            await().atMost(TIMEOUT).until(() -> ask(gate, new GateActor.Received()) == 4);
            await().during(Duration.ofMillis(300))
                    .atMost(TIMEOUT)
                    .until(() -> produced.get() == 4 && ask(gate, new GateActor.Received()) == 4);

            gate.tell(new GateActor.Open());
            assertThat(replies.get(10, TimeUnit.SECONDS))
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, 100)
                            .map(n -> n * 2)
                            .boxed()
                            .toList());
        });
    }

    @Test
    void cancellingTheMessagesFluxStopsTheActorsReference() {
        runner.run(context -> {
            ActorStreams streams = context.getBean(ActorStreams.class);
            SpringActorHandle<TickerActor.Command> ticker = spawn(context, TickerActor.class);
            List<String> received = new CopyOnWriteArrayList<>();

            Disposable subscription = streams.messages(String.class)
                    .flux(ref -> ticker.tell(new TickerActor.Subscribe(ref)))
                    .subscribe(received::add);
            await().atMost(TIMEOUT).until(() -> ask(ticker, new TickerActor.Subscribers()) == 1);
            ticker.tell(new TickerActor.Publish("a"));
            await().atMost(TIMEOUT).until(() -> received.equals(List.of("a")));

            subscription.dispose();
            // The ticker watches the reference and forgets it once it has stopped.
            await().atMost(TIMEOUT).until(() -> ask(ticker, new TickerActor.Subscribers()) == 0);
        });
    }

    @Test
    void theLastMessageCompletesTheFluxAndOnCloseGetsTheReference() {
        runner.run(context -> {
            ActorStreams streams = context.getBean(ActorStreams.class);
            SpringActorHandle<TickerActor.Command> ticker = spawn(context, TickerActor.class);
            List<SpringActorRef<String>> subscribed = new CopyOnWriteArrayList<>();
            List<SpringActorRef<String>> closed = new CopyOnWriteArrayList<>();

            Flux<String> flux = streams.messages(String.class)
                    .completeWhen("end"::equals)
                    .onClose(closed::add)
                    .flux(ref -> {
                        subscribed.add(ref);
                        ticker.tell(new TickerActor.Subscribe(ref));
                    });

            StepVerifier.create(flux)
                    .then(() -> {
                        await().atMost(TIMEOUT).until(() -> ask(ticker, new TickerActor.Subscribers()) == 1);
                        ticker.tell(new TickerActor.Publish("a"));
                        ticker.tell(new TickerActor.Publish("b"));
                        ticker.tell(new TickerActor.Publish("end"));
                    })
                    .expectNext("a", "b")
                    .expectComplete()
                    .verify(TIMEOUT);
            assertThat(closed).containsExactlyElementsOf(subscribed);
        });
    }

    @Test
    void aFullBufferFailsTheFluxByDefault() {
        runner.run(context -> {
            ActorStreams streams = context.getBean(ActorStreams.class);
            SpringActorHandle<TickerActor.Command> ticker = spawn(context, TickerActor.class);

            Flux<String> flux = streams.messages(String.class)
                    .bufferSize(2)
                    .flux(ref -> ticker.tell(new TickerActor.Subscribe(ref)));

            // Nothing requested: the messages wait in the buffer until it overflows.
            StepVerifier.create(flux, 0)
                    .then(() -> {
                        await().atMost(TIMEOUT).until(() -> ask(ticker, new TickerActor.Subscribers()) == 1);
                        IntStream.rangeClosed(1, 10).forEach(n -> ticker.tell(new TickerActor.Publish("m" + n)));
                    })
                    .expectError()
                    .verify(TIMEOUT);
        });
    }

    @Test
    void anAcknowledgingSenderWaitsForTheSubscribersDemand() {
        runner.run(context -> {
            ActorStreams streams = context.getBean(ActorStreams.class);
            SpringActorHandle<CounterActor.Command> counter = spawn(context, CounterActor.class);
            List<Integer> received = new CopyOnWriteArrayList<>();
            BaseSubscriber<Integer> subscriber = new BaseSubscriber<>() {
                @Override
                protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
                    request(3);
                }

                @Override
                protected void hookOnNext(Integer value) {
                    received.add(value);
                }
            };

            streams.messages(Integer.class)
                    .ackTo(counter, new CounterActor.Ack())
                    .flux(ref -> counter.tell(new CounterActor.Start(ref)))
                    .subscribe(subscriber);

            await().atMost(TIMEOUT).until(() -> received.equals(List.of(1, 2, 3)));
            // The 3 requested and 1 waiting in the stream: the counter does not run ahead.
            await().during(Duration.ofMillis(300))
                    .atMost(TIMEOUT)
                    .until(() -> ask(counter, new CounterActor.Sent()) == 4);

            subscriber.request(2);
            await().atMost(TIMEOUT).until(() -> received.equals(List.of(1, 2, 3, 4, 5)));
            subscriber.dispose();
        });
    }
}
