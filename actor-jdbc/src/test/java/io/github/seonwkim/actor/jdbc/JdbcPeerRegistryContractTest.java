package io.github.seonwkim.actor.jdbc;

import io.github.seonwkim.actor.spi.PeerRegistry;
import io.github.seonwkim.actor.spi.PeerRegistryContract;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The actor_peer table passes the PeerRegistry contract on each database it ships a schema for. */
class JdbcPeerRegistryContractTest {

    /** A registry on {@code dataSource}, with the table created from the database's schema file. */
    static JdbcPeerRegistry registryOn(DataSource dataSource, String platform) {
        new ResourceDatabasePopulator(
                        new ClassPathResource("io/github/seonwkim/actor/jdbc/schema-" + platform + ".sql"))
                .execute(dataSource);
        return new JdbcPeerRegistry(dataSource);
    }

    static DataSource dataSource(JdbcDatabaseContainer<?> database) {
        return new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

    @Nested
    class H2 extends PeerRegistryContract {
        private final JdbcPeerRegistry registry = registryOn(
                new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"), "h2");

        @Override
        protected PeerRegistry registry() {
            return registry;
        }
    }

    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class PostgreSql extends PeerRegistryContract {
        @Container
        static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

        @Override
        protected PeerRegistry registry() {
            return registryOn(dataSource(DATABASE), "postgresql");
        }
    }

    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class MySql extends PeerRegistryContract {
        @Container
        static final MySQLContainer<?> DATABASE = new MySQLContainer<>("mysql:8.4");

        @Override
        protected PeerRegistry registry() {
            return registryOn(dataSource(DATABASE), "mysql");
        }
    }
}
