package io.github.seonwkim.actor.jdbc;

import io.github.seonwkim.actor.spi.PeerRegistry;
import io.github.seonwkim.actor.test.PeerRegistryContract;
import org.junit.jupiter.api.Nested;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The actor_peer table passes the PeerRegistry contract on each database it ships a schema for. */
class JdbcPeerRegistryContractTest {

    @Nested
    class H2 extends PeerRegistryContract {
        private final JdbcPeerRegistry registry = new JdbcPeerRegistry(TestDatabases.h2());

        @Override
        protected PeerRegistry registry() {
            return registry;
        }
    }

    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class PostgreSql extends PeerRegistryContract {
        @Override
        protected PeerRegistry registry() {
            return new JdbcPeerRegistry(TestDatabases.postgres());
        }
    }

    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class MySql extends PeerRegistryContract {
        @Override
        protected PeerRegistry registry() {
            return new JdbcPeerRegistry(TestDatabases.mysql());
        }
    }
}
