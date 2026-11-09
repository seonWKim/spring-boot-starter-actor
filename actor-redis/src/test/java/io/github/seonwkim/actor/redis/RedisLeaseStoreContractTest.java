package io.github.seonwkim.actor.redis;

import io.github.seonwkim.actor.spi.LeaseStore;
import io.github.seonwkim.actor.spi.LeaseStoreContract;
import org.junit.jupiter.api.AfterEach;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Redis passes the LeaseStore contract. */
@Testcontainers(disabledWithoutDocker = true)
class RedisLeaseStoreContractTest extends LeaseStoreContract {

    private LettuceConnectionFactory connection;

    @Override
    protected LeaseStore store() {
        connection = RedisTestServer.connection();
        return new RedisLeaseStore(connection);
    }

    @AfterEach
    void closeConnection() {
        if (connection != null) {
            connection.destroy();
        }
    }
}
