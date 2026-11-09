package io.github.seonwkim.actor.jdbc;

import io.github.seonwkim.actor.testing.DiscoveryClusterContract;
import java.util.List;
import java.util.UUID;

/** Nodes find each other through the actor_peer table of one shared (H2) database. */
class JdbcDiscoveryClusterTest extends DiscoveryClusterContract {

    private final String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    @Override
    protected List<String> storeProperties() {
        return List.of("spring.actor.cluster.discovery.method=jdbc", "spring.datasource.url=" + url);
    }
}
