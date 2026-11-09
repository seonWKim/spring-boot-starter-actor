package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.SpringActorSystem;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.ActorSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/** The Spring context follows the actor system: when Pekko stops by itself, the application stops. */
@ExtendWith(OutputCaptureExtension.class)
class ActorSystemTerminationTest {

    private static final String UNEXPECTED = "The actor system terminated while the application was running";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(ActorConfiguration.class))
            .withPropertyValues("spring.actor.shutdown.exit-process=false");

    @Test
    void whenPekkoTerminatesByItselfTheContextCloses(CapturedOutput output) {
        runner.run(context -> {
            ConfigurableApplicationContext app = context.getSourceApplicationContext();
            context.getBean(SpringActorSystem.class).unwrap(ActorSystem.class).terminate();

            await().atMost(Duration.ofSeconds(10)).until(() -> !app.isActive());
            assertThat(output).contains(UNEXPECTED);
        });
    }

    @Test
    void closingTheContextIsNotReportedAsAnUnexpectedTermination(CapturedOutput output) {
        runner.run(context -> {
            SpringActorSystem system = context.getBean(SpringActorSystem.class);

            context.close();

            system.whenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(output).doesNotContain(UNEXPECTED);
        });
    }
}
