package io.github.seonwkim.actor.redis;

import org.testcontainers.containers.GenericContainer;

/** One Redis container for this module's tests, started on first use. */
final class RedisTestServer {

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private RedisTestServer() {}

    static GenericContainer<?> started() {
        if (!REDIS.isRunning()) {
            REDIS.start();
        }
        return REDIS;
    }
}
