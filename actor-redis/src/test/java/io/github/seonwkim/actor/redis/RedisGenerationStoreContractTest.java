package io.github.seonwkim.actor.redis;

import io.github.seonwkim.actor.spi.GenerationStore;
import io.github.seonwkim.actor.test.GenerationStoreContract;
import org.junit.jupiter.api.AfterEach;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Redis passes the GenerationStore contract. */
@Testcontainers(disabledWithoutDocker = true)
class RedisGenerationStoreContractTest extends GenerationStoreContract {

    private LettuceConnectionFactory connection;

    @Override
    protected GenerationStore store() {
        connection = RedisTestServer.connection();
        return new RedisGenerationStore(connection);
    }

    @AfterEach
    void closeConnection() {
        if (connection != null) {
            connection.destroy();
        }
    }
}
