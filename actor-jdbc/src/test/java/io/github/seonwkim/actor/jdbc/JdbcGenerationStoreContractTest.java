package io.github.seonwkim.actor.jdbc;

import io.github.seonwkim.actor.spi.GenerationStore;
import io.github.seonwkim.actor.test.GenerationStoreContract;
import org.junit.jupiter.api.Nested;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The actor_generation table passes the GenerationStore contract on each database it ships a schema for. */
class JdbcGenerationStoreContractTest {

    @Nested
    class H2 extends GenerationStoreContract {
        private final JdbcGenerationStore store = new JdbcGenerationStore(TestDatabases.h2());

        @Override
        protected GenerationStore store() {
            return store;
        }
    }

    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class PostgreSql extends GenerationStoreContract {
        @Override
        protected GenerationStore store() {
            return new JdbcGenerationStore(TestDatabases.postgres());
        }
    }

    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class MySql extends GenerationStoreContract {
        @Override
        protected GenerationStore store() {
            return new JdbcGenerationStore(TestDatabases.mysql());
        }
    }
}
