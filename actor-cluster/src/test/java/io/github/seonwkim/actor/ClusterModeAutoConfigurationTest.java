package io.github.seonwkim.actor;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.ddata.ReplicatedData;
import io.github.seonwkim.actor.internal.shard.ShardedActorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** With actor-cluster on the classpath, the cluster auto-configuration applies only in cluster mode. */
class ClusterModeAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BootApp.class)
            .withPropertyValues(
                    "spring.actor.pekko.remote.artery.canonical.port=0", "spring.actor.pekko.management.enabled=false");

    @Test
    void theClusterAutoConfigurationStaysOffInLocalMode() {
        runner.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(SpringActorSystem.class)
                .doesNotHaveBean(ShardedActorRegistry.class)
                .doesNotHaveBean(ReplicatedData.class));
    }

    /** Cluster mode is set by the typed key or by the raw Pekko key. */
    @ParameterizedTest
    @ValueSource(strings = {"spring.actor.provider=cluster", "spring.actor.pekko.actor.provider=cluster"})
    void theClusterAutoConfigurationAppliesInClusterMode(String clusterMode) {
        runner.withPropertyValues(clusterMode).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ShardedActorRegistry.class);
            assertThat(context.getBean(SpringActorSystem.class).isClusterMode()).isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class BootApp {}
}
