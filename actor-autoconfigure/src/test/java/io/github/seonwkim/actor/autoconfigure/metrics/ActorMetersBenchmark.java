package io.github.seonwkim.actor.autoconfigure.metrics;

import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Overhead of the agent-free actor meters per message: one actor receives bursts of tells, in two
 * actor systems side by side, one with the meters off and one with them on. Rounds alternate between
 * the two so that load from other processes hits both alike. Run it with
 * {@code ./gradlew :actor-autoconfigure:actorMetersBenchmark}; the result goes into the metrics guide.
 */
public final class ActorMetersBenchmark {

    private static final int MESSAGES = 1_000_000;
    private static final int WARMUP_ROUNDS = 5;
    private static final int ROUNDS = 15;

    /** Counts {@code Tick}s and completes the {@code Expect}ed future after the given number. */
    public static class CountingActor implements SpringActor<CountingActor.Command> {
        public interface Command {}

        public enum Tick implements Command {
            INSTANCE
        }

        public record Expect(int count, CompletableFuture<Void> done) implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            int[] remaining = {0};
            CompletableFuture<?>[] done = {null};
            return SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(Expect.class, (context, msg) -> {
                        remaining[0] = msg.count();
                        done[0] = msg.done();
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Tick.class, (context, msg) -> {
                        if (--remaining[0] == 0) {
                            done[0].complete(null);
                        }
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    public static void main(String[] args) {
        runner("false").run(off -> runner("true").run(on -> {
            SpringActorHandle<CountingActor.Command> plain = spawn(off);
            SpringActorHandle<CountingActor.Command> metered = spawn(on);
            for (int i = 0; i < WARMUP_ROUNDS; i++) {
                round(plain);
                round(metered);
            }
            double[] plainRounds = new double[ROUNDS];
            double[] meteredRounds = new double[ROUNDS];
            for (int i = 0; i < ROUNDS; i++) {
                plainRounds[i] = round(plain);
                meteredRounds[i] = round(metered);
            }
            double withoutMeters = median(plainRounds);
            double withMeters = median(meteredRounds);
            System.out.printf(
                    "%,d messages per round, median of %d rounds:%n  meters off: %.0f ns/message%n"
                            + "  meters on:  %.0f ns/message%n  overhead:   %.0f ns/message%n",
                    MESSAGES, ROUNDS, withoutMeters, withMeters, withMeters - withoutMeters);
        }));
        System.exit(0);
    }

    private static ApplicationContextRunner runner(String metricsEnabled) {
        return new ApplicationContextRunner()
                .withConfiguration(UserConfigurations.of(ActorConfiguration.class, ActorMetricsConfiguration.class))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(CountingActor.class)
                .withPropertyValues(
                        "spring.actor.metrics.enabled=" + metricsEnabled,
                        "spring.actor.name=metrics-" + metricsEnabled);
    }

    private static SpringActorHandle<CountingActor.Command> spawn(ConfigurableApplicationContext context) {
        return context.getBean(SpringActorSystem.class)
                .actor(CountingActor.class)
                .withId("bench")
                .spawnAndWait();
    }

    private static double median(double[] rounds) {
        double[] sorted = rounds.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    /** Nanoseconds per message for one burst. */
    private static double round(SpringActorHandle<CountingActor.Command> actor) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        actor.tell(new CountingActor.Expect(MESSAGES, done));
        long start = System.nanoTime();
        for (int i = 0; i < MESSAGES; i++) {
            actor.tell(CountingActor.Tick.INSTANCE);
        }
        done.get(60, TimeUnit.SECONDS);
        return (double) (System.nanoTime() - start) / MESSAGES;
    }
}
