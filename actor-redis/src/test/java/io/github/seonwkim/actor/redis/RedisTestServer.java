package io.github.seonwkim.actor.redis;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;

/** One Redis container for this module's tests, started on first use. */
final class RedisTestServer {

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private RedisTestServer() {}

    /** A started connection to the server; the caller destroys it. */
    static LettuceConnectionFactory connection() {
        GenericContainer<?> redis = started();
        LettuceConnectionFactory connection = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connection.afterPropertiesSet();
        connection.start();
        return connection;
    }

    static synchronized GenericContainer<?> started() {
        if (!REDIS.isRunning()) {
            REDIS.start();
        }
        return REDIS;
    }
}
