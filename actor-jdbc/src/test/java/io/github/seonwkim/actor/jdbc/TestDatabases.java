package io.github.seonwkim.actor.jdbc;

import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The databases this module ships a schema for, with the schema applied. The containers start on
 * first use and are shared by this module's tests (callers skip without Docker).
 */
final class TestDatabases {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private TestDatabases() {}

    static DataSource h2() {
        return withSchema(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"), "h2");
    }

    static DataSource postgres() {
        return withSchema(started(POSTGRES), "postgresql");
    }

    static DataSource mysql() {
        return withSchema(started(MYSQL), "mysql");
    }

    private static synchronized DataSource started(JdbcDatabaseContainer<?> database) {
        if (!database.isRunning()) {
            database.start();
        }
        return new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

    private static DataSource withSchema(DataSource dataSource, String platform) {
        new ResourceDatabasePopulator(
                        new ClassPathResource("io/github/seonwkim/actor/jdbc/schema-" + platform + ".sql"))
                .execute(dataSource);
        return dataSource;
    }
}
