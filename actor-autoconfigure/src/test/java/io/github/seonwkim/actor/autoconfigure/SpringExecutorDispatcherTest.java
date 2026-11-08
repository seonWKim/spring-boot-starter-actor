package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** A dispatcher declared with spring.actor.dispatchers.<name>.executor runs actors on that Executor bean. */
class SpringExecutorDispatcherTest {

    static class ThreadNameActor implements SpringActor<ThreadNameActor.Command> {
        public interface Command {}

        public static class WhichThread extends AskCommand<String> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(WhichThread.class, (context, msg) -> {
                        msg.reply(Thread.currentThread().getName());
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    @Configuration
    static class Beans {
        @Bean
        ThreadNameActor threadNameActor() {
            return new ThreadNameActor();
        }

        @Bean
        ThreadPoolTaskExecutor actorExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setThreadNamePrefix("spring-pool-");
            executor.setCorePoolSize(2);
            return executor;
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(ActorConfiguration.class, Beans.class))
            .withPropertyValues("spring.actor.dispatchers.on-spring.executor=actorExecutor");

    private static String threadOf(ConfigurableApplicationContext context, String dispatcher) throws Exception {
        SpringActorHandle<ThreadNameActor.Command> actor = context.getBean(SpringActorSystem.class)
                .actor(ThreadNameActor.class)
                .withId("on-" + dispatcher)
                .withDispatcherFromConfig(dispatcher)
                .spawn()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        return actor.ask(new ThreadNameActor.WhichThread())
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void actorsOnTheDispatcherRunOnTheExecutorBean() {
        runner.run(context -> assertThat(threadOf(context, "on-spring")).startsWith("spring-pool-"));
    }

    @Test
    void closingTheContextStillShutsTheActorSystemDownCleanly() {
        runner.run(context -> {
            threadOf(context, "on-spring");
            ActorSystemShutdownLifecycle lifecycle = context.getBean(ActorSystemShutdownLifecycle.class);

            context.close();

            assertThat(lifecycle.getLastOutcome()).isEqualTo(SpringActorSystem.ShutdownOutcome.TERMINATED);
        });
    }

    @Test
    void aDispatcherWithoutExecutorFailsStartupNamingTheKey() {
        new ApplicationContextRunner()
                .withConfiguration(UserConfigurations.of(ActorConfiguration.class))
                .withPropertyValues("spring.actor.dispatchers.on-spring.throughput=10")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "spring.actor.dispatchers.on-spring.executor must name an Executor bean"));
    }
}
