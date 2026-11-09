package io.github.seonwkim.actor.redis;

import io.github.seonwkim.actor.spi.PeerRegistry;
import io.github.seonwkim.actor.spi.PeerRegistryContract;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Redis passes the PeerRegistry contract. */
@Testcontainers(disabledWithoutDocker = true)
class RedisPeerRegistryContractTest extends PeerRegistryContract {

    private LettuceConnectionFactory connection;

    @Override
    protected PeerRegistry registry() {
        var redis = RedisTestServer.started();
        connection = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connection.afterPropertiesSet();
        connection.start();
        return new RedisPeerRegistry(connection, Duration.ofMinutes(10));
    }

    @AfterEach
    void closeConnection() {
        if (connection != null) {
            connection.destroy();
        }
    }
}
