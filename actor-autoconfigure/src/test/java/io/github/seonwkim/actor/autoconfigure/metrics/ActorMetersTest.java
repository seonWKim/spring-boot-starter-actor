package io.github.seonwkim.actor.autoconfigure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.SupervisionStrategy;
import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.github.seonwkim.actor.internal.ActorInstrumentation;
import io.github.seonwkim.actor.metrics.internal.hooks.ActorMeters;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;

/** Actor meters come from the starter's own hooks, with no Java agent attached. */
class ActorMetersTest {

    /**
     * Answers {@code Ping}; {@code Boom} throws, and the behavior's own supervision restarts it;
     * {@code Block} waits until its latch opens.
     */
    public static class MeteredActor implements SpringActor<MeteredActor.Command> {
        public interface Command {}

        public static class Ping extends AskCommand<String> implements Command {}

        public static class Boom implements Command {}

        public static class Block implements Command {
            final CountDownLatch release;

            public Block(CountDownLatch release) {
                this.release = release;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .withSupervisionStrategy(SupervisionStrategy.restart())
                    .onMessage(Ping.class, (context, msg) -> {
                        msg.reply("pong");
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Boom.class, (context, msg) -> {
                        throw new IllegalStateException("boom");
                    })
                    .onMessage(Block.class, (context, msg) -> {
                        try {
                            msg.release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(ActorConfiguration.class, ActorMetricsConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(MeteredActor.class);

    private static SpringActorHandle<MeteredActor.Command> spawn(ConfigurableApplicationContext context, String id)
            throws Exception {
        return context.getBean(SpringActorSystem.class)
                .actor(MeteredActor.class)
                .withId(id)
                .spawn()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    private static void ping(SpringActorHandle<MeteredActor.Command> actor) throws Exception {
        actor.ask(new MeteredActor.Ping())
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    private static Search actorMeter(ConfigurableApplicationContext context, String name) {
        return context.getBean(MeterRegistry.class).find(name).tag("actor.class", "MeteredActor");
    }

    private static double counter(ConfigurableApplicationContext context, String name) {
        return actorMeter(context, name).meters().stream()
                .mapToDouble(ActorMetersTest::count)
                .sum();
    }

    /** The count of a counter or a function counter. */
    private static double count(Meter meter) {
        return meter.measure().iterator().next().getValue();
    }

    private static double gauge(ConfigurableApplicationContext context, String name) {
        var gauge = actorMeter(context, name).gauge();
        return gauge == null ? 0 : gauge.value();
    }

    @Test
    void messagesAreCountedAndTimedWithTheActorsClassDispatcherAndSystem() {
        runner.run(context -> {
            SpringActorHandle<MeteredActor.Command> actor = spawn(context, "a");
            for (int i = 0; i < 3; i++) {
                ping(actor);
            }

            await().until(() -> counter(context, ActorMeters.PROCESSED) == 3);
            var timer = actorMeter(context, ActorMeters.PROCESSING_TIME)
                    .tag("dispatcher", "pekko.actor.default-dispatcher")
                    .tag("system", context.getBean(SpringActorSystem.class).getName())
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(3);
        });
    }

    @Test
    void aFailingMessageIsCountedAndSoIsTheRestartItCauses() {
        runner.run(context -> {
            SpringActorHandle<MeteredActor.Command> actor = spawn(context, "a");
            actor.tell(new MeteredActor.Boom());

            await().until(() -> counter(context, ActorMeters.RESTARTS) == 1);
            assertThat(counter(context, ActorMeters.FAILURES)).isEqualTo(1);
            ping(actor); // still answers after the restart
        });
    }

    @Test
    void theMailboxCountsWaitingMessagesAndRunningActors() {
        runner.run(context -> {
            SpringActorHandle<MeteredActor.Command> busy = spawn(context, "busy");
            SpringActorHandle<MeteredActor.Command> idle = spawn(context, "idle");
            await().until(() -> gauge(context, ActorMeters.ACTIVE) == 2);

            CountDownLatch release = new CountDownLatch(1);
            busy.tell(new MeteredActor.Block(release));
            for (int i = 0; i < 4; i++) {
                busy.tell(new MeteredActor.Ping());
            }
            await().until(() -> gauge(context, ActorMeters.MAILBOX_SIZE) == 4);

            release.countDown();
            await().until(() -> gauge(context, ActorMeters.MAILBOX_SIZE) == 0);
            assertThat(actorMeter(context, ActorMeters.MAILBOX_TIME).timer().count())
                    .isEqualTo(5);

            idle.stop();
            await().until(() -> gauge(context, ActorMeters.ACTIVE) == 1);
        });
    }

    @Test
    void tenThousandActorsProduceAsManySeriesAsAHundred() {
        runner.run(context -> {
            pingNewActors(context, 0, 100);
            int seriesForAHundred = series(context);

            pingNewActors(context, 100, 10_000);

            assertThat(counter(context, ActorMeters.PROCESSED)).isEqualTo(10_000);
            assertThat(series(context)).isEqualTo(seriesForAHundred);
        });
    }

    private static void pingNewActors(ConfigurableApplicationContext context, int from, int to) {
        SpringActorSystem system = context.getBean(SpringActorSystem.class);
        List<CompletableFuture<String>> replies = new ArrayList<>();
        for (int i = from; i < to; i++) {
            replies.add(system.actor(MeteredActor.class)
                    .withId("entity-" + i)
                    .spawn()
                    .thenCompose(actor -> actor.ask(new MeteredActor.Ping())
                            .withTimeout(Duration.ofSeconds(30))
                            .execute())
                    .toCompletableFuture());
        }
        CompletableFuture.allOf(replies.toArray(CompletableFuture[]::new)).join();
        await().atMost(Duration.ofSeconds(30)).until(() -> counter(context, ActorMeters.PROCESSED) == to);
    }

    /** The actor meters' series: one per meter and tag set. */
    private static int series(ConfigurableApplicationContext context) {
        return (int) context.getBean(MeterRegistry.class).getMeters().stream()
                .map(Meter::getId)
                .filter(id -> id.getName().startsWith("actor."))
                .count();
    }

    @Test
    void pathTagsAreOptInAndCapped() {
        runner.withPropertyValues("spring.actor.metrics.path-tag-limit=3").run(context -> {
            for (int i = 0; i < 10; i++) {
                ping(spawn(context, "p" + i));
            }

            await().until(() -> counter(context, ActorMeters.PROCESSED) == 10);
            var paths = actorMeter(context, ActorMeters.PROCESSED).meters().stream()
                    .map(c -> Objects.requireNonNull(c.getId().getTag("actor.path")))
                    .collect(Collectors.toSet());
            assertThat(paths).hasSize(4).contains(ActorMeters.OTHER_PATH);
        });
    }

    @Test
    void theDefaultDispatchersPoolIsMeasured() {
        runner.run(context -> {
            ping(spawn(context, "a"));

            var threads = context.getBean(MeterRegistry.class)
                    .find(ActorMeters.DISPATCHER_THREADS)
                    .tag("dispatcher", "pekko.actor.default-dispatcher")
                    .gauge();
            assertThat(threads).isNotNull();
            assertThat(threads.value()).isPositive();
        });
    }

    @Test
    void withTheAgentAttachedOnlyTheMetersItLacksAreRegistered() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new ApplicationContextRunner()
                .withConfiguration(UserConfigurations.of(ActorConfiguration.class))
                .withBean(MeteredActor.class)
                .withBean(ActorInstrumentation.class, () -> new ActorMeters(registry, true, 0))
                .run(context -> {
                    SpringActorHandle<MeteredActor.Command> actor = spawn(context, "a");
                    actor.tell(new MeteredActor.Boom());
                    ping(actor);

                    await().until(() -> registry.find(ActorMeters.FAILURES).counter() != null);
                    assertThat(registry.getMeters().stream().map(m -> m.getId().getName()))
                            .doesNotContain(
                                    ActorMeters.PROCESSED,
                                    ActorMeters.PROCESSING_TIME,
                                    ActorMeters.RESTARTS,
                                    ActorMeters.MAILBOX_SIZE,
                                    ActorMeters.MAILBOX_TIME,
                                    ActorMeters.ACTIVE)
                            .contains(ActorMeters.DISPATCHER_THREADS);
                });
    }

    @Test
    void theMetricsSwitchTurnsThemOff() {
        runner.withPropertyValues("spring.actor.metrics.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ActorInstrumentation.class));
    }
}
