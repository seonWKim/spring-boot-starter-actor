package io.github.seonwkim.actor.autoconfigure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.cluster.ClusterEvent;
import io.github.seonwkim.actor.fixture.TestShardedActor;
import io.github.seonwkim.actor.internal.shard.ShardedActorRegistry;
import io.github.seonwkim.actor.internal.shard.ShardingStats;
import io.github.seonwkim.actor.metrics.cluster.ClusterMeters;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.RequiredSearch;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** actor.cluster.* and actor.sharding.* meters follow this node's view of the cluster. */
class ClusterMetricsTest {

    @SpringBootApplication
    @EnableActorSupport
    static class TestApp {}

    private static ConfigurableApplicationContext start(String... properties) {
        // The sharded actor is a source, not a @Bean of TestApp: other tests' component scans find TestApp.
        return new SpringApplicationBuilder(TestApp.class, TestShardedActor.class)
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

    private static double sharding(ConfigurableApplicationContext context, String name) {
        return meter(context, name)
                .tag("type", TestShardedActor.TYPE_KEY.name())
                .gauge()
                .value();
    }

    private static void ping(ConfigurableApplicationContext context, String... entityIds) {
        SpringActorSystem system = context.getBean(SpringActorSystem.class);
        for (String id : entityIds) {
            system.sharded(TestShardedActor.class).withId(id).get().tell(new TestShardedActor.Ping("hi"));
        }
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
            assertThat(gauge(context, "actor.cluster.singleton.host")).isEqualTo(1);
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
                // The singletons run on the oldest member, which is the first node.
                await().atMost(Duration.ofSeconds(5)).until(() -> gauge(second, "actor.cluster.members") == 2);
                assertThat(gauge(first, "actor.cluster.singleton.host")).isEqualTo(1);
                assertThat(gauge(second, "actor.cluster.singleton.host")).isZero();
            }
        }
    }

    @Test
    void shardsAndEntitiesOfThisNodeAreCounted() {
        try (ConfigurableApplicationContext context = start()) {
            await().atMost(Duration.ofSeconds(20)).until(() -> gauge(context, "actor.cluster.leader") == 1);
            ping(context, "a", "b", "c");

            await().atMost(Duration.ofSeconds(20)).until(() -> sharding(context, "actor.sharding.entities") == 3);
            assertThat(sharding(context, "actor.sharding.regions")).isEqualTo(1);
            assertThat(sharding(context, "actor.sharding.shards")).isBetween(1.0, 3.0);
        }
    }

    @Test
    void eachNodeCountsOnlyItsOwnEntitiesButEveryRegion() throws Exception {
        try (ConfigurableApplicationContext first = start()) {
            await().atMost(Duration.ofSeconds(20)).until(() -> gauge(first, "actor.cluster.leader") == 1);
            String seed = first.getBean(SpringActorSystem.class).nodeAddress();

            try (ConfigurableApplicationContext second =
                    start("spring.actor.cluster.join-self=false", "spring.actor.cluster.seed-nodes=" + seed)) {
                await().atMost(Duration.ofSeconds(20))
                        .until(() ->
                                stats(first).regions() == 2 && stats(second).regions() == 2);
                SpringActorSystem system = first.getBean(SpringActorSystem.class);
                for (String id : List.of("a", "b", "c", "d", "e", "f", "g", "h")) {
                    // An answer means the entity runs on one of the two nodes.
                    system.sharded(TestShardedActor.class)
                            .withId(id)
                            .get()
                            .ask(new TestShardedActor.GetState())
                            .withTimeout(Duration.ofSeconds(10))
                            .execute()
                            .toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                }

                await().atMost(Duration.ofSeconds(20))
                        .until(() -> stats(first).entities() + stats(second).entities() == 8);
            }
        }
    }

    /** Asks the shard regions now, unlike the gauges, which show the answer to the previous read. */
    private static ShardingStats.TypeStats stats(ConfigurableApplicationContext context) throws Exception {
        List<ShardingStats.TypeStats> all = new ShardingStats(
                        context.getBean(SpringActorSystem.class), context.getBean(ShardedActorRegistry.class))
                .query()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        return all.get(0);
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
