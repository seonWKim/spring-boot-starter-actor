package io.github.seonwkim.actor.autoconfigure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.cluster.ClusterEvent;
import io.github.seonwkim.actor.metrics.cluster.ClusterMeters;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.RequiredSearch;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** actor.cluster.* meters follow this node's view of the cluster. */
class ClusterMetricsTest {

    @SpringBootApplication
    @EnableActorSupport
    static class TestApp {}

    private static ConfigurableApplicationContext start(String... properties) {
        return new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.actor.provider=cluster",
                        "spring.actor.remote.port=0",
                        "spring.actor.pekko.management.enabled=false",
                        "spring.actor.cluster.join-self=true")
                .properties(properties)
                .run();
    }

    private static RequiredSearch meter(ConfigurableApplicationContext context, String name) {
        return context.getBean(MeterRegistry.class).get(name);
    }

    private static double gauge(ConfigurableApplicationContext context, String name) {
        return meter(context, name).gauge().value();
    }

    private static double events(ConfigurableApplicationContext context, String event) {
        return meter(context, "actor.cluster.events")
                .tag("event", event)
                .functionCounter()
                .count();
    }

    @Test
    void aOneNodeClusterHasOneMemberThatLeads() {
        try (ConfigurableApplicationContext context = start()) {
            await().atMost(Duration.ofSeconds(20)).until(() -> gauge(context, "actor.cluster.leader") == 1);

            assertThat(gauge(context, "actor.cluster.members")).isEqualTo(1);
            assertThat(gauge(context, "actor.cluster.unreachable")).isZero();
        }
    }

    @Test
    void aNodeThatJoinsLaterIsCountedAndSeen() {
        try (ConfigurableApplicationContext first = start()) {
            await().atMost(Duration.ofSeconds(20)).until(() -> gauge(first, "actor.cluster.leader") == 1);
            double upBefore = events(first, "MemberUp");
            String seed = first.getBean(SpringActorSystem.class).nodeAddress();

            try (ConfigurableApplicationContext second =
                    start("spring.actor.cluster.join-self=false", "spring.actor.cluster.seed-nodes=" + seed)) {
                await().atMost(Duration.ofSeconds(20)).until(() -> gauge(first, "actor.cluster.members") == 2);
                await().atMost(Duration.ofSeconds(5)).until(() -> events(first, "MemberUp") == upBefore + 1);
                assertThat(gauge(second, "actor.cluster.leader")).isZero();
            }
        }
    }

    @Test
    void everyEventTypeHasACounterFromTheStart() {
        try (ConfigurableApplicationContext context = start()) {
            String[] tags = meter(context, "actor.cluster.events").functionCounters().stream()
                    .map(counter -> counter.getId().getTag("event"))
                    .sorted()
                    .toArray(String[]::new);

            assertThat(tags)
                    .containsExactlyInAnyOrder(Arrays.stream(ClusterEvent.class.getPermittedSubclasses())
                            .map(Class::getSimpleName)
                            .toArray(String[]::new));
        }
    }

    @Test
    void theMetricsSwitchTurnsThemOff() {
        try (ConfigurableApplicationContext context = start("spring.actor.metrics.enabled=false")) {
            assertThat(context.getBeanNamesForType(ClusterMeters.class)).isEmpty();
        }
    }

    @Test
    void localModeHasNoClusterMeters() {
        try (ConfigurableApplicationContext context =
                start("spring.actor.provider=local", "spring.actor.cluster.join-self=false")) {
            assertThat(context.getBeanNamesForType(ClusterMeters.class)).isEmpty();
        }
    }
}
