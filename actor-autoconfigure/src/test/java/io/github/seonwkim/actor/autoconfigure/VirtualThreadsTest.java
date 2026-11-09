package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSpawnBuilder;
import io.github.seonwkim.actor.SpringActorSystem;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** spring.actor.virtual-threads.enabled and dispatchers.<name>.virtual choose virtual threads. */
@ExtendWith(OutputCaptureExtension.class)
class VirtualThreadsTest {

    static class ThreadKindActor implements SpringActor<ThreadKindActor.Command> {
        public interface Command {}

        public static class IsVirtual extends AskCommand<Boolean> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(IsVirtual.class, (context, msg) -> {
                        msg.reply(Thread.currentThread().isVirtual());
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    @Configuration
    static class Beans {
        @Bean
        ThreadKindActor threadKindActor() {
            return new ThreadKindActor();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(ActorConfiguration.class, Beans.class));

    private static boolean runsOnVirtualThread(
            ApplicationContext context,
            String id,
            UnaryOperator<SpringActorSpawnBuilder<ThreadKindActor, ThreadKindActor.Command>> dispatcher)
            throws Exception {
        SpringActorHandle<ThreadKindActor.Command> actor = dispatcher
                .apply(context.getBean(SpringActorSystem.class)
                        .actor(ThreadKindActor.class)
                        .withId(id))
                .spawn()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        return actor.ask(new ThreadKindActor.IsVirtual())
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    private static boolean blockingIsVirtual(ApplicationContext context) throws Exception {
        return runsOnVirtualThread(context, "blocking", SpringActorSpawnBuilder::withBlockingDispatcher);
    }

    @Test
    void enabledPutsTheBlockingDispatcherOnVirtualThreads() {
        runner.withPropertyValues("spring.actor.virtual-threads.enabled=true")
                .run(context -> assertThat(blockingIsVirtual(context)).isTrue());
    }

    @Test
    void unsetLeavesTheBlockingDispatcherOnPlatformThreads() {
        runner.run(context -> assertThat(blockingIsVirtual(context)).isFalse());
    }

    @Test
    void falseLeavesTheBlockingDispatcherOnPlatformThreads() {
        runner.withPropertyValues("spring.actor.virtual-threads.enabled=false")
                .run(context -> assertThat(blockingIsVirtual(context)).isFalse());
    }

    @Test
    void autoFollowsSpringVirtualThreadsWhenTrue() {
        runner.withPropertyValues("spring.actor.virtual-threads.enabled=auto", "spring.threads.virtual.enabled=true")
                .run(context -> assertThat(blockingIsVirtual(context)).isTrue());
    }

    @Test
    void autoFollowsSpringVirtualThreadsWhenFalse() {
        runner.withPropertyValues("spring.actor.virtual-threads.enabled=auto", "spring.threads.virtual.enabled=false")
                .run(context -> assertThat(blockingIsVirtual(context)).isFalse());
    }

    @Test
    void theDefaultIsAuto() {
        runner.withPropertyValues("spring.threads.virtual.enabled=true")
                .run(context -> assertThat(blockingIsVirtual(context)).isTrue());
    }

    @Test
    void falseWinsOverSpringVirtualThreads() {
        runner.withPropertyValues("spring.actor.virtual-threads.enabled=false", "spring.threads.virtual.enabled=true")
                .run(context -> assertThat(blockingIsVirtual(context)).isFalse());
    }

    @Test
    void theDefaultDispatcherStaysOnPlatformThreads() {
        runner.withPropertyValues("spring.actor.virtual-threads.enabled=true").run(context -> assertThat(
                        runsOnVirtualThread(context, "default", SpringActorSpawnBuilder::withDefaultDispatcher))
                .isFalse());
    }

    @Test
    void aDispatcherMarkedVirtualRunsOnVirtualThreads() {
        runner.withPropertyValues("spring.actor.dispatchers.io.virtual=true")
                .run(context -> assertThat(runsOnVirtualThread(context, "io", b -> b.withDispatcherFromConfig("io")))
                        .isTrue());
    }

    @Test
    void aDispatcherCannotBeBothVirtualAndOnAnExecutorBean() {
        runner.withPropertyValues(
                        "spring.actor.dispatchers.io.virtual=true", "spring.actor.dispatchers.io.executor=someBean")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "spring.actor.dispatchers.io sets both executor and virtual; choose one"));
    }

    @Test
    void aRawPekkoKeyWinsOverTheSwitchAndSaysSo(CapturedOutput output) {
        runner.withPropertyValues(
                        "spring.actor.virtual-threads.enabled=true",
                        "spring.actor.pekko.actor.default-blocking-io-dispatcher.executor=thread-pool-executor")
                .run(context -> {
                    assertThat(blockingIsVirtual(context)).isFalse();
                    assertThat(output)
                            .contains("spring.actor.pekko.actor.default-blocking-io-dispatcher.executor="
                                    + "thread-pool-executor overrides spring.actor.virtual-threads.enabled");
                });
    }
}
