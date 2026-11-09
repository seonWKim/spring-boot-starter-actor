package io.github.seonwkim.actor.autoconfigure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.ActorSystem;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.health.SystemHealth;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The actorSystem component of /actuator/health. */
class ActorSystemHealthTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HealthContributorAutoConfiguration.class, HealthEndpointAutoConfiguration.class))
            .withConfiguration(UserConfigurations.of(ActorConfiguration.class, ActorHealthConfiguration.class))
            .withPropertyValues("spring.actor.name=orders", "spring.actor.shutdown.exit-process=false");

    @Test
    void aRunningSystemIsUpInTheHealthEndpoint() {
        runner.run(context -> {
            HealthEndpoint endpoint = context.getBean(HealthEndpoint.class);

            assertThat(((SystemHealth) endpoint.health()).getComponents()).containsKey("actorSystem");
            HealthComponent actorSystem = endpoint.healthForPath("actorSystem");
            assertThat(actorSystem.getStatus()).isEqualTo(Status.UP);
        });
    }

    @Test
    void aTerminatedSystemIsDown() {
        runner.run(context -> {
            ActorSystemHealthIndicator indicator = context.getBean(ActorSystemHealthIndicator.class);
            SpringActorSystem system = context.getBean(SpringActorSystem.class);

            system.unwrap(ActorSystem.class).terminate();
            system.whenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);

            await().atMost(Duration.ofSeconds(5))
                    .until(() -> indicator.health().getStatus().equals(Status.DOWN));
            assertThat(indicator.health().getDetails()).containsEntry("name", "orders");
        });
    }

    @Test
    void withoutActuatorThereIsNoIndicator() {
        new ApplicationContextRunner()
                .withConfiguration(UserConfigurations.of(ActorConfiguration.class, ActorHealthConfiguration.class))
                .withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ActorSystemHealthIndicator.class);
                });
    }

    @Test
    void theEnabledPropertyTurnsTheIndicatorOff() {
        runner.withPropertyValues("management.health.actor.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ActorSystemHealthIndicator.class));
    }
}
