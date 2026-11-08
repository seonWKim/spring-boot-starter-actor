package io.github.seonwkim.core.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.core.EnableActorSupport;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityProbesAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Checks that the actor health indicators are registered with Actuator, join the readiness group,
 * can be switched off, and stay out of the way when Actuator is absent.
 *
 * <p>The application class is deliberately not annotated with {@code @Configuration}, so other
 * tests' component scans do not pick it up.
 */
class ActorHealthAutoConfigurationTest {

    @EnableActorSupport
    static class LocalApp {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocalApp.class)
            .withConfiguration(AutoConfigurations.of(
                    ApplicationAvailabilityAutoConfiguration.class,
                    HealthContributorAutoConfiguration.class,
                    HealthEndpointAutoConfiguration.class,
                    AvailabilityProbesAutoConfiguration.class,
                    ActorHealthAutoConfiguration.class))
            .withPropertyValues(
                    "spring.actor.pekko.actor.provider=local",
                    "spring.actor.pekko.coordinated.shutdown.run-by-jvm-shutdown-hook=off",
                    "management.endpoint.health.show-components=always");

    @Test
    void isRegisteredAsSpringBootAutoConfiguration() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(ActorHealthAutoConfiguration.class.getName());
    }

    @Test
    void registersNothingWithoutActorSystem() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ActorHealthAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ActorSystemHealthIndicator.class);
                });
    }

    @Test
    void healthEndpointIncludesActorSystemIndicator() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            HealthComponent actorSystem = components(context.getBean(HealthEndpoint.class).health())
                    .get(ActorHealthAutoConfiguration.ACTOR_SYSTEM);

            assertThat(actorSystem).isNotNull();
            assertThat(actorSystem.getStatus()).isEqualTo(Status.UP);
        });
    }

    @Test
    void clusterIndicatorIsNotRegisteredInLocalMode() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ClusterHealthIndicator.class));
    }

    @Test
    void readinessGroupIncludesActorIndicatorsByDefault() {
        runner.withPropertyValues("management.endpoint.health.probes.enabled=true").run(context -> {
            HealthEndpointGroups groups = context.getBean(HealthEndpointGroups.class);

            assertThat(groups.get("readiness").isMember("readinessState")).isTrue();
            assertThat(groups.get("readiness").isMember(ActorHealthAutoConfiguration.ACTOR_SYSTEM)).isTrue();
            assertThat(groups.get("readiness").isMember(ActorHealthAutoConfiguration.ACTOR_CLUSTER)).isTrue();
            assertThat(groups.get("liveness").isMember(ActorHealthAutoConfiguration.ACTOR_SYSTEM)).isFalse();

            HealthComponent readiness = context.getBean(HealthEndpoint.class).healthForPath("readiness");
            assertThat(components(readiness).get(ActorHealthAutoConfiguration.ACTOR_SYSTEM).getStatus())
                    .isEqualTo(Status.UP);
        });
    }

    @Test
    void readinessIncludeSetByApplicationIsKept() {
        runner.withPropertyValues(
                        "management.endpoint.health.probes.enabled=true",
                        "management.endpoint.health.group.readiness.include=readinessState")
                .run(context -> {
                    HealthEndpointGroups groups = context.getBean(HealthEndpointGroups.class);

                    assertThat(groups.get("readiness").isMember("readinessState")).isTrue();
                    assertThat(groups.get("readiness").isMember(ActorHealthAutoConfiguration.ACTOR_SYSTEM))
                            .isFalse();
                });
    }

    @Test
    void propertyDefaultsToEnabledAndFalseRemovesIndicators() {
        runner.withPropertyValues("management.health.actor.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ActorSystemHealthIndicator.class));

        runner.withPropertyValues("management.health.actor.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ActorSystemHealthIndicator.class);
            assertThat(context).doesNotHaveBean(ClusterHealthIndicator.class);
            assertThat(components(context.getBean(HealthEndpoint.class).health()))
                    .doesNotContainKey(ActorHealthAutoConfiguration.ACTOR_SYSTEM);
        });
    }

    @Test
    void startsWithoutActuatorAndRegistersNoIndicator() {
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate"))
                .withUserConfiguration(LocalApp.class)
                .withConfiguration(AutoConfigurations.of(ActorHealthAutoConfiguration.class))
                .withPropertyValues(
                        "spring.actor.pekko.actor.provider=local",
                        "spring.actor.pekko.coordinated.shutdown.run-by-jvm-shutdown-hook=off")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SpringActorSystem.class);
                    assertThat(context).doesNotHaveBean("actorSystemHealthIndicator");
                    assertThat(context).doesNotHaveBean("actorClusterHealthIndicator");
                    assertThat(context).doesNotHaveBean("actorReadinessGroupPostProcessor");
                });
    }

    @Test
    void actorSystemIndicatorIsUpWhileRunningAndDownOnceTerminated() {
        ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "health-indicator-test");
        ActorSystemHealthIndicator indicator = new ActorSystemHealthIndicator(system);
        try {
            assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        } finally {
            system.terminate();
        }

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN));
    }

    private static Map<String, HealthComponent> components(HealthComponent health) {
        assertThat(health).isInstanceOf(CompositeHealth.class);
        return ((CompositeHealth) health).getComponents();
    }
}
