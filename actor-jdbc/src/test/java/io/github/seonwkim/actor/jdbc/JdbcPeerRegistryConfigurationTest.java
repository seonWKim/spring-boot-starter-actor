package io.github.seonwkim.actor.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.autoconfigure.ActorProperties;
import io.github.seonwkim.actor.autoconfigure.cluster.PeerRegistryConfiguration;
import io.github.seonwkim.actor.spi.InMemoryPeerRegistry;
import io.github.seonwkim.actor.spi.Peer;
import io.github.seonwkim.actor.spi.PeerRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** spring.actor.cluster.discovery.method=jdbc wires the actor_peer table, unless the application has its own registry. */
class JdbcPeerRegistryConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withConfiguration(UserConfigurations.of(PeerRegistryConfiguration.class))
            .withBean(ActorProperties.class)
            .withPropertyValues(
                    "spring.actor.cluster.discovery.method=jdbc",
                    "spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");

    @Test
    void theJdbcMethodUsesTheActorPeerTableItCreatesInAnEmbeddedDatabase() {
        runner.run(context -> {
            PeerRegistry registry = context.getBean(PeerRegistry.class);
            Peer peer = new Peer("orders", "node-1", "10.0.0.7", 7626);

            registry.register(peer);

            assertThat(registry).isInstanceOf(JdbcPeerRegistry.class);
            assertThat(registry.peers("orders", Duration.ofMinutes(1))).containsExactly(peer);
        });
    }

    @Test
    void anApplicationRegistryReplacesTheTable() {
        runner.withBean(PeerRegistry.class, InMemoryPeerRegistry::new)
                .run(context ->
                        assertThat(context.getBean(PeerRegistry.class)).isInstanceOf(InMemoryPeerRegistry.class));
    }
}
