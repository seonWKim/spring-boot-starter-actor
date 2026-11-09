package io.github.seonwkim.actor.redis;

import io.github.seonwkim.actor.testing.DiscoveryClusterContract;
import java.util.List;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Nodes find each other through keys in one shared Redis. */
@Testcontainers(disabledWithoutDocker = true)
class RedisDiscoveryClusterTest extends DiscoveryClusterContract {

    @Override
    protected List<String> storeProperties() {
        var redis = RedisTestServer.started();
        return List.of(
                "spring.actor.cluster.discovery.method=redis",
                "spring.data.redis.host=" + redis.getHost(),
                "spring.data.redis.port=" + redis.getMappedPort(6379));
    }
}
