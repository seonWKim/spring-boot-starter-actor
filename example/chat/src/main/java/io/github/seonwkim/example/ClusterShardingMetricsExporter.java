package io.github.seonwkim.example;

import java.time.Duration;

import javax.annotation.PostConstruct;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.ShardRegion.ClusterShardingStats;
import org.apache.pekko.cluster.sharding.typed.ClusterShardingQuery;
import org.apache.pekko.cluster.sharding.typed.GetClusterShardingStats;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.springframework.stereotype.Component;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSpawnContext;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.example.ClusterShardingMetricsActor.ClusterShardingMetricsExporterContext;

@Component
public class ClusterShardingMetricsExporter {

    private final SpringActorSystem springActorSystem;
    private final SpringActorRef<ClusterShardingStats> metricsActors;
    private final EntityTypeKey<ClusterShardingStats> entityTypeKey = EntityTypeKey.create(ClusterShardingStats.class, "exporter");

    public ClusterShardingMetricsExporter(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;

        final SpringActorSpawnContext<ClusterShardingMetricsActor, ClusterShardingStats> context =
                new SpringActorSpawnContext.Builder<ClusterShardingMetricsActor, ClusterShardingStats>()
                        .actorClass(ClusterShardingMetricsActor.class)
                        .actorContext(new ClusterShardingMetricsExporterContext("exporter", springActorSystem))
                        .build();
        this.metricsActors = springActorSystem.spawn(context).toCompletableFuture().join();
    }

    @PostConstruct
    private void init() {
        final ActorRef<ClusterShardingQuery> shardStateActor =
                springActorSystem.getClusterSharding().shardState();

        shardStateActor.tell(
                new GetClusterShardingStats(
                        entityTypeKey,
                        Duration.ofSeconds(3),
                        metricsActors.getRef()
                )
        );
    }
}
