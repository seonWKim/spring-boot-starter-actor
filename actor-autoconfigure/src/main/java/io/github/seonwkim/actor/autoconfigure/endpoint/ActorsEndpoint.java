package io.github.seonwkim.actor.autoconfigure.endpoint;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.internal.cluster.ClusterStateView;
import io.github.seonwkim.actor.internal.shard.ShardingStats;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * The {@code actors} Actuator endpoint: this node's view of the cluster and of each sharded entity
 * type. Read-only. Exposed only when the application adds it, for example
 * {@code management.endpoints.web.exposure.include=health,actors}.
 */
@Endpoint(id = "actors")
public class ActorsEndpoint {

    /**
     * What the endpoint returns.
     *
     * @param node this node's address
     * @param cluster this node's view of the cluster
     * @param sharding each sharded entity type
     */
    public record Actors(String node, ClusterStateView cluster, List<ShardingStats.TypeStats> sharding) {}

    private final SpringActorSystem actorSystem;
    private final ShardingStats sharding;

    public ActorsEndpoint(SpringActorSystem actorSystem, ShardingStats sharding) {
        this.actorSystem = actorSystem;
        this.sharding = sharding;
    }

    /** Asks the shard regions now, so a read can take up to {@link ShardingStats#TIMEOUT}. */
    @ReadOperation
    public Actors actors() throws Exception {
        List<ShardingStats.TypeStats> types = sharding.query()
                .toCompletableFuture()
                .get(ShardingStats.TIMEOUT.toMillis() + 2000, TimeUnit.MILLISECONDS);
        return new Actors(actorSystem.nodeAddress(), ClusterStateView.of(actorSystem), types);
    }
}
