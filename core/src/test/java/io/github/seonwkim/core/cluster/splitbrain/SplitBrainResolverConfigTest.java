package io.github.seonwkim.core.cluster.splitbrain;

import static org.assertj.core.api.Assertions.assertThat;

import com.typesafe.config.Config;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import org.apache.pekko.cluster.Cluster;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for Split Brain Resolver configuration.
 *
 * <p>These tests verify that the Split Brain Resolver is correctly configured through Spring Boot
 * properties. They check that Pekko's configuration includes the correct strategy settings.
 *
 * <p>Note: These are configuration verification tests. Full multi-node partition testing requires
 * more complex infrastructure (see docs/clustering/testing-split-brain-resolver.md).
 */
@SpringBootTest(classes = SplitBrainResolverConfigTest.TestApp.class)
@TestPropertySource(
        properties = {
            "spring.actor.pekko.actor.provider=cluster",
            "spring.actor.pekko.remote.artery.canonical.port=0", // Random port
            "spring.actor.pekko.cluster.seed-nodes[0]=pekko://ActorSystem@127.0.0.1:25520",
            "spring.actor.pekko.cluster.downing-provider-class=org.apache.pekko.cluster.sbr.SplitBrainResolverProvider",
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy=keep-majority",
            "spring.actor.pekko.cluster.split-brain-resolver.stable-after=20s",
            "spring.actor.pekko.cluster.split-brain-resolver.down-all-when-unstable=on"
        })
public class SplitBrainResolverConfigTest {

    @Autowired
    private SpringActorSystem actorSystem;

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    public static class TestApp {}

    @Test
    public void testSplitBrainResolverIsConfigured() {
        // Given: ActorSystem with cluster mode
        Config config = actorSystem.getRaw().settings().config();

        // Then: Split Brain Resolver should be configured as downing provider
        String downingProvider = config.getString("pekko.cluster.downing-provider-class");
        assertThat(downingProvider)
                .isEqualTo("org.apache.pekko.cluster.sbr.SplitBrainResolverProvider");
    }

    @Test
    public void testKeepMajorityStrategyIsConfigured() {
        // Given: ActorSystem with split brain resolver
        Config config = actorSystem.getRaw().settings().config();

        // Then: Active strategy should be keep-majority
        String activeStrategy = config.getString("pekko.cluster.split-brain-resolver.active-strategy");
        assertThat(activeStrategy).isEqualTo("keep-majority");
    }

    @Test
    public void testStableAfterIsConfigured() {
        // Given: ActorSystem with split brain resolver
        Config config = actorSystem.getRaw().settings().config();

        // Then: Stable-after should be 20 seconds
        Duration stableAfter = config.getDuration("pekko.cluster.split-brain-resolver.stable-after");
        assertThat(stableAfter).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    public void testDownAllWhenUnstableIsConfigured() {
        // Given: ActorSystem with split brain resolver
        Config config = actorSystem.getRaw().settings().config();

        // Then: down-all-when-unstable should be enabled
        String downAllWhenUnstable =
                config.getString("pekko.cluster.split-brain-resolver.down-all-when-unstable");
        assertThat(downAllWhenUnstable).isEqualToIgnoringCase("on");
    }

    @Test
    public void testClusterIsConfigured() {
        // Given: ActorSystem with cluster
        Cluster cluster = Cluster.get(actorSystem.getRaw());

        // Then: Cluster should be initialized
        assertThat(cluster).isNotNull();
        assertThat(cluster.selfMember()).isNotNull();
    }
}
