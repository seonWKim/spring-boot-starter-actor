package io.github.seonwkim.actor.autoconfigure.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.cluster.MemberStatus;
import io.github.seonwkim.actor.fixture.TestShardedActor;
import io.github.seonwkim.actor.internal.shard.ShardingStats;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** The actors endpoint reports this node, the members it sees and each sharded entity type. */
class ActorsEndpointTest {

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

    @Test
    void itReportsTheNodeItsMembersAndItsEntities() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            SpringActorSystem system = context.getBean(SpringActorSystem.class);
            ActorsEndpoint endpoint = context.getBean(ActorsEndpoint.class);
            await().atMost(Duration.ofSeconds(20))
                    .until(() -> endpoint.actors().cluster().selfStatus() == MemberStatus.UP);
            system.sharded(TestShardedActor.class).withId("a").get().tell(new TestShardedActor.Ping("hi"));

            await().atMost(Duration.ofSeconds(20)).until(() -> endpoint.actors()
                    .sharding()
                    .equals(List.of(new ShardingStats.TypeStats(TestShardedActor.TYPE_KEY.name(), 1, 1, 1))));
            ActorsEndpoint.Actors actors = endpoint.actors();
            assertThat(actors.node()).isEqualTo(system.nodeAddress());
            assertThat(actors.cluster().nodes()).singleElement().satisfies(node -> {
                assertThat(node.address()).isEqualTo(system.nodeAddress());
                assertThat(node.status()).isEqualTo(MemberStatus.UP);
                assertThat(node.roles()).isEmpty();
                assertThat(node.reachable()).isTrue();
            });
        }
    }

    @Test
    void localModeHasNoEndpoint() {
        try (ConfigurableApplicationContext context =
                start("spring.actor.provider=local", "spring.actor.cluster.join-self=false")) {
            assertThat(context.getBeanNamesForType(ActorsEndpoint.class)).isEmpty();
        }
    }
}
