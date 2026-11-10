package io.github.seonwkim.actor.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.test.ActorCluster;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;

/**
 * A node that joins itself starts with Pekko Management on the classpath: Cluster Bootstrap, which
 * would need a discovery method, is not started for it. {@link ActorCluster}'s first node joins itself
 * on port 0 (#224). This module's tests have Management on the classpath, for discovery.
 */
class JoinSelfWithManagementTest {

    @SpringBootConfiguration
    @EnableActorSupport
    static class App {}

    @Test
    void aClusterWhoseFirstNodeJoinsItselfFormsWithManagementOnTheClasspath() {
        try (ActorCluster cluster = ActorCluster.builder(App.class).nodes(2).start()) {
            assertThat(cluster.upMembers(1)).isEqualTo(2);
            assertThat(cluster.upMembers(2)).isEqualTo(2);
        }
    }
}
