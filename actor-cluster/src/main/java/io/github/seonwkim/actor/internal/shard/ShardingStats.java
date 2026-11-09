package io.github.seonwkim.actor.internal.shard;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.pekko.actor.Address;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.ShardRegion.ClusterShardingStats;
import org.apache.pekko.cluster.sharding.ShardRegion.ShardRegionStats;
import org.apache.pekko.cluster.sharding.typed.GetClusterShardingStats;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.typed.Cluster;

/**
 * Cluster Sharding statistics of each sharded entity type, read for metrics and the {@code actors}
 * endpoint. Asks every shard region in the cluster, so a query takes up to {@link #TIMEOUT}.
 */
public final class ShardingStats {

    /** How long one query waits for the shard regions to answer. */
    public static final Duration TIMEOUT = Duration.ofSeconds(3);

    /**
     * One entity type, as the cluster reports it.
     *
     * @param type the entity type's name
     * @param regions the shard regions that host the type in the cluster
     * @param shards the type's shards on this node
     * @param entities the type's running entities on this node
     */
    public record TypeStats(String type, int regions, int shards, int entities) {
        static TypeStats empty(String type) {
            return new TypeStats(type, 0, 0, 0);
        }
    }

    private final ActorSystem<?> system;
    private final ShardedActorRegistry registry;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile Map<String, TypeStats> latest = Map.of();

    public ShardingStats(SpringActorSystem system, ShardedActorRegistry registry) {
        this.system = system.unwrap(ActorSystem.class);
        this.registry = registry;
    }

    /** The names of the sharded entity types this node knows. */
    public List<String> types() {
        return registry.getAll().stream()
                .map(actor -> actor.entityType().name())
                .sorted()
                .toList();
    }

    /** Asks the cluster now. A type whose regions did not answer in time reports zeros. */
    public CompletionStage<List<TypeStats>> query() {
        List<CompletableFuture<TypeStats>> all = new ArrayList<>();
        for (SpringShardedActor<?> actor : registry.getAll()) {
            all.add(query(actor).toCompletableFuture());
        }
        return CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).thenApply(done -> {
            List<TypeStats> stats = all.stream().map(CompletableFuture::join).collect(Collectors.toList());
            latest = stats.stream().collect(Collectors.toUnmodifiableMap(TypeStats::type, s -> s));
            return stats;
        });
    }

    /**
     * The last answer for {@code type}, without waiting: starts a new query unless one is running, so
     * a reader sees the answer to the previous read. Zeros until the first query has answered.
     */
    public TypeStats latest(String type) {
        if (refreshing.compareAndSet(false, true)) {
            query().whenComplete((stats, error) -> refreshing.set(false));
        }
        return latest.getOrDefault(type, TypeStats.empty(type));
    }

    private CompletionStage<TypeStats> query(SpringShardedActor<?> actor) {
        String type = actor.entityType().name();
        Address self = Cluster.get(system).selfMember().address();
        return AskPattern.<org.apache.pekko.cluster.sharding.typed.ClusterShardingQuery, ClusterShardingStats>ask(
                        ClusterSharding.get(system).shardState(),
                        replyTo -> new GetClusterShardingStats(
                                PekkoSharding.typeKey(actor.entityType()), TIMEOUT, replyTo),
                        TIMEOUT.plusSeconds(1),
                        system.scheduler())
                .thenApply(stats -> of(type, stats, self))
                .exceptionally(error -> TypeStats.empty(type));
    }

    private static TypeStats of(String type, ClusterShardingStats stats, Address self) {
        Map<Address, ShardRegionStats> regions = stats.getRegions();
        ShardRegionStats local = regions.get(self);
        Map<String, Object> shards = local == null ? Map.of() : local.getStats();
        int entities =
                shards.values().stream().mapToInt(count -> (Integer) count).sum();
        return new TypeStats(type, regions.size(), shards.size(), entities);
    }
}
