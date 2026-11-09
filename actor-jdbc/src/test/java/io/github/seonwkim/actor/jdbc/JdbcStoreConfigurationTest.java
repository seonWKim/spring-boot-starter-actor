package io.github.seonwkim.actor.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.autoconfigure.ActorProperties;
import io.github.seonwkim.actor.autoconfigure.cluster.StoreConfiguration;
import io.github.seonwkim.actor.spi.InMemoryPeerRegistry;
import io.github.seonwkim.actor.spi.LeaseStore;
import io.github.seonwkim.actor.spi.Peer;
import io.github.seonwkim.actor.spi.PeerRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** discovery.method=jdbc and lease.store=jdbc wire the actor-jdbc tables, unless the application has its own store. */
class JdbcStoreConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withConfiguration(UserConfigurations.of(StoreConfiguration.class))
            .withBean(ActorProperties.class)
            .withPropertyValues(
                    "spring.actor.cluster.discovery.method=jdbc",
                    "spring.actor.cluster.lease.store=jdbc",
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
    void theJdbcLeaseStoreUsesTheActorLeaseTable() {
        runner.run(context -> {
            LeaseStore store = context.getBean(LeaseStore.class);

            assertThat(store).isInstanceOf(JdbcLeaseStore.class);
            assertThat(store.tryAcquire("orders-singleton", "node-1", Duration.ofMinutes(1)))
                    .isTrue();
        });
    }

    @Test
    void anApplicationRegistryReplacesTheTable() {
        runner.withBean(PeerRegistry.class, InMemoryPeerRegistry::new)
                .run(context ->
                        assertThat(context.getBean(PeerRegistry.class)).isInstanceOf(InMemoryPeerRegistry.class));
    }
}
