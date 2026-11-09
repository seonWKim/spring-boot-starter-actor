package io.github.seonwkim.actor.jdbc;

import io.github.seonwkim.actor.spi.LeaseStore;
import io.github.seonwkim.actor.spi.LeaseStoreContract;
import org.junit.jupiter.api.Nested;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The actor_lease table passes the LeaseStore contract on each database it ships a schema for. */
class JdbcLeaseStoreContractTest {

    @Nested
    class H2 extends LeaseStoreContract {
        private final JdbcLeaseStore store = new JdbcLeaseStore(TestDatabases.h2());

        @Override
        protected LeaseStore store() {
            return store;
        }
    }

    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class PostgreSql extends LeaseStoreContract {
        @Override
        protected LeaseStore store() {
            return new JdbcLeaseStore(TestDatabases.postgres());
        }
    }

    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class MySql extends LeaseStoreContract {
        @Override
        protected LeaseStore store() {
            return new JdbcLeaseStore(TestDatabases.mysql());
        }
    }
}
