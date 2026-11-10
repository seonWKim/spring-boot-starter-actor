package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.internal.RootGuardian;
import io.github.seonwkim.actor.internal.RootGuardianSupplierWrapper;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** {@code find} and {@code exists} on {@link SpringActorSystem}: Optional results, and timeouts that fail. */
class LookupTest {

    static class EchoActor implements SpringActor<EchoActor.Command> {
        public interface Command {}

        public static class Ping extends AskCommand<String> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(Ping.class, (context, msg) -> {
                        msg.reply("pong");
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    @Configuration
    static class Beans {
        @Bean
        EchoActor echoActor() {
            return new EchoActor();
        }
    }

    /** Answers lookups after {@link #DELAY}, like a root guardian behind a long queue of messages. */
    @Configuration
    static class SlowGuardian {
        static final Duration DELAY = Duration.ofMillis(300);

        @Bean
        @Primary
        RootGuardianSupplierWrapper slowRootGuardian() {
            return new RootGuardianSupplierWrapper(
                    () -> Behaviors.setup(ctx -> Behaviors.receive(RootGuardian.Command.class)
                            .onMessage(RootGuardian.GetActor.class, msg -> {
                                ctx.scheduleOnce(DELAY, msg.replyTo, new RootGuardian.GetActorResponse<>(null));
                                return Behaviors.same();
                            })
                            .onMessage(RootGuardian.CheckExists.class, msg -> {
                                ctx.scheduleOnce(DELAY, msg.replyTo, new RootGuardian.ExistsResponse(false));
                                return Behaviors.same();
                            })
                            .build()));
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(ActorConfiguration.class, Beans.class));

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    void findIsEmptyForNoActorAndHoldsTheRunningOne() {
        runner.run(context -> {
            SpringActorSystem system = context.getBean(SpringActorSystem.class);
            assertThat(await(system.find(EchoActor.class, "echo"))).isEmpty();

            system.actor(EchoActor.class).withId("echo").spawnAndWait();

            Optional<SpringActorHandle<EchoActor.Command>> found = await(system.find(EchoActor.class, "echo"));
            assertThat(found).isPresent();
            assertThat(await(found.get().ask(new EchoActor.Ping()).execute())).isEqualTo("pong");
        });
    }

    @Test
    @SuppressWarnings("deprecation")
    void aLookupThatTimesOutFailsInsteadOfReportingNoActor() {
        runner.withUserConfiguration(SlowGuardian.class).run(context -> {
            SpringActorSystem system = context.getBean(SpringActorSystem.class);
            Duration tooShort = Duration.ofMillis(50);

            assertThatThrownBy(() -> await(system.find(EchoActor.class, "echo", tooShort)))
                    .hasRootCauseInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> await(system.exists(EchoActor.class, "echo", tooShort)))
                    .hasRootCauseInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> await(system.get(EchoActor.class, "echo", tooShort)))
                    .hasRootCauseInstanceOf(TimeoutException.class);
        });
    }

    @Test
    void theDefaultTimeoutWaitsForASlowAnswer() {
        runner.withUserConfiguration(SlowGuardian.class).run(context -> {
            SpringActorSystem system = context.getBean(SpringActorSystem.class);

            // The guardian answers after 300 ms: within the default timeout (3 s), so the answer arrives.
            assertThat(await(system.find(EchoActor.class, "echo"))).isEmpty();
            assertThat(await(system.exists(EchoActor.class, "echo"))).isFalse();
        });
    }
}
