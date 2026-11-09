package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.ActorTerminated;
import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.SpringBehaviorContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Timers, watching another actor and message adapters, through SpringBehaviorContext. */
class BehaviorContextTest {

    /** Records what happened to it; each test drives it with one command and reads the log. */
    static class ProbeActor implements SpringActor<ProbeActor.Command> {
        public interface Command {}

        /** Logs {@code name} when it arrives. */
        public record Mark(String name) implements Command {}

        public record ScheduleOnce(String key, String name, Duration delay) implements Command {}

        public record ScheduleRepeated(String key, String name, Duration interval) implements Command {}

        public record Cancel(String key) implements Command {}

        public record Watch(SpringActorRef<?> other) implements Command {}

        /** Replies with a ref that turns a String into Mark("adapted:" + s). */
        public static class Adapter extends AskCommand<SpringActorRef<String>> implements Command {}

        public static class Log extends AskCommand<List<String>> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new State(ctx))
                    .onMessage(Mark.class, (state, msg) -> state.log(msg.name()))
                    .onMessage(ScheduleOnce.class, (state, msg) -> {
                        state.ctx.startSingleTimer(msg.key(), new Mark(msg.name()), msg.delay());
                        return SpringActorBehavior.same();
                    })
                    .onMessage(ScheduleRepeated.class, (state, msg) -> {
                        state.ctx.startTimerAtFixedRate(msg.key(), new Mark(msg.name()), msg.interval());
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Cancel.class, (state, msg) -> {
                        state.ctx.cancelTimer(msg.key());
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Watch.class, (state, msg) -> {
                        state.ctx.watch(msg.other());
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Adapter.class, (state, msg) -> {
                        msg.reply(state.ctx.messageAdapter(String.class, s -> new Mark("adapted:" + s)));
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Log.class, (state, msg) -> {
                        msg.reply(List.copyOf(state.log));
                        return SpringActorBehavior.same();
                    })
                    .onSignal(
                            ActorTerminated.class,
                            (state, signal) ->
                                    state.log("terminated:" + signal.ref().path()))
                    .build();
        }

        static final class State {
            final SpringBehaviorContext<Command> ctx;
            final List<String> log = new ArrayList<>();

            State(SpringBehaviorContext<Command> ctx) {
                this.ctx = ctx;
            }

            SpringActorBehavior<Command> log(String entry) {
                log.add(entry);
                return SpringActorBehavior.same();
            }
        }
    }

    @Configuration
    static class Beans {
        @Bean
        ProbeActor probeActor() {
            return new ProbeActor();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(ActorConfiguration.class, Beans.class));

    private static SpringActorHandle<ProbeActor.Command> probe(ApplicationContext context, String id) throws Exception {
        return context.getBean(SpringActorSystem.class)
                .actor(ProbeActor.class)
                .withId(id)
                .spawn()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    private static List<String> log(SpringActorHandle<ProbeActor.Command> probe) throws Exception {
        return probe.ask(new ProbeActor.Log())
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void aSingleTimerDeliversItsMessageOnce() {
        runner.run(context -> {
            SpringActorHandle<ProbeActor.Command> probe = probe(context, "once");
            probe.tell(new ProbeActor.ScheduleOnce("t", "tick", Duration.ofMillis(20)));
            probe.tell(new ProbeActor.ScheduleOnce("end", "end", Duration.ofMillis(200)));

            await().atMost(Duration.ofSeconds(5)).until(() -> log(probe).contains("end"));
            assertThat(log(probe)).containsExactly("tick", "end");
        });
    }

    @Test
    void aCancelledTimerNeverFires() {
        runner.run(context -> {
            SpringActorHandle<ProbeActor.Command> probe = probe(context, "cancelled");
            probe.tell(new ProbeActor.ScheduleOnce("t", "tick", Duration.ofMillis(100)));
            probe.tell(new ProbeActor.Cancel("t"));
            probe.tell(new ProbeActor.ScheduleOnce("end", "end", Duration.ofMillis(300)));

            await().atMost(Duration.ofSeconds(5)).until(() -> log(probe).contains("end"));
            assertThat(log(probe)).containsExactly("end");
        });
    }

    @Test
    void aTimerAtFixedRateKeepsFiring() {
        runner.run(context -> {
            SpringActorHandle<ProbeActor.Command> probe = probe(context, "repeated");
            probe.tell(new ProbeActor.ScheduleRepeated("t", "tick", Duration.ofMillis(20)));

            await().atMost(Duration.ofSeconds(5))
                    .until(() -> log(probe).stream().filter("tick"::equals).count() >= 3);
        });
    }

    @Test
    void aWatchedActorThatStopsIsReportedWithItsPath() {
        runner.run(context -> {
            SpringActorHandle<ProbeActor.Command> watcher = probe(context, "watcher");
            SpringActorHandle<ProbeActor.Command> watched = probe(context, "watched");
            watcher.tell(new ProbeActor.Watch(watched));
            log(watcher); // the Watch was processed before the stop

            watched.stop();

            await().atMost(Duration.ofSeconds(5)).until(() -> log(watcher).contains("terminated:" + watched.path()));
        });
    }

    @Test
    void aMessageAdapterTurnsForeignMessagesIntoCommands() {
        runner.run(context -> {
            SpringActorHandle<ProbeActor.Command> probe = probe(context, "adapted");
            SpringActorRef<String> adapter = probe.ask(new ProbeActor.Adapter())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            adapter.tell("hello");

            await().atMost(Duration.ofSeconds(5)).until(() -> log(probe).contains("adapted:hello"));
        });
    }
}
