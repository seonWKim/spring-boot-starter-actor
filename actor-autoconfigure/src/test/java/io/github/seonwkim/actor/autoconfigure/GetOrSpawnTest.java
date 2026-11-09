package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The spawn builder's getOrSpawn() keeps the builder's settings and reuses a running actor. */
class GetOrSpawnTest {

    /** Counts its Hits and reports whether it runs on a virtual thread. */
    static class CounterActor implements SpringActor<CounterActor.Command> {
        public interface Command {}

        public record Hit() implements Command {}

        public static class Count extends AskCommand<Integer> implements Command {}

        public static class IsVirtual extends AskCommand<Boolean> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .withState(c -> new int[1])
                    .onMessage(Hit.class, (count, msg) -> {
                        count[0]++;
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Count.class, (count, msg) -> {
                        msg.reply(count[0]);
                        return SpringActorBehavior.same();
                    })
                    .onMessage(IsVirtual.class, (count, msg) -> {
                        msg.reply(Thread.currentThread().isVirtual());
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    @Configuration
    static class Beans {
        @Bean
        CounterActor counterActor() {
            return new CounterActor();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(ActorConfiguration.class, Beans.class))
            .withPropertyValues("spring.actor.virtual-threads.enabled=true");

    private static SpringActorHandle<CounterActor.Command> blockingCounter(ApplicationContext context)
            throws Exception {
        return context.getBean(SpringActorSystem.class)
                .actor(CounterActor.class)
                .withId("counter")
                .withBlockingDispatcher()
                .getOrSpawn()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    private static int count(SpringActorHandle<CounterActor.Command> actor) throws Exception {
        return actor.ask(new CounterActor.Count())
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    private static boolean isVirtual(SpringActorHandle<CounterActor.Command> actor) throws Exception {
        return actor.ask(new CounterActor.IsVirtual())
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void aSpawnedActorRunsOnTheBuildersDispatcher() {
        runner.run(context -> assertThat(isVirtual(blockingCounter(context))).isTrue());
    }

    @Test
    void aRunningActorIsReturnedInsteadOfSpawningAnother() {
        runner.run(context -> {
            blockingCounter(context).tell(new CounterActor.Hit());

            // A second actor would start at 0 and never see the Hit.
            SpringActorHandle<CounterActor.Command> again = blockingCounter(context);
            await().atMost(Duration.ofSeconds(5)).until(() -> count(again) == 1);
        });
    }
}
