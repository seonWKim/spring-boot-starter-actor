package io.github.seonwkim.example;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.ShardRegion.ClusterShardingStats;
import org.apache.pekko.cluster.sharding.ShardRegion.CurrentShardRegionState;
import org.apache.pekko.cluster.sharding.typed.ClusterShardingQuery;
import org.apache.pekko.cluster.sharding.typed.GetClusterShardingStats;
import org.apache.pekko.cluster.sharding.typed.GetShardRegionState;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.springframework.stereotype.Component;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSpawnContext;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.example.ClusterShardingMetricsActor.ClusterShardingMetricsExporterContext;
import io.github.seonwkim.example.ClusterShardingMetricsActorV2.ClusterShardingMetricsExporterV2Context;

@Component
public class ClusterShardingMetricsExporter {

    private final SpringActorSystem springActorSystem;
    private final SpringActorRef<ClusterShardingStats> metricsActors;
    private final SpringActorRef<CurrentShardRegionState> metricsActorsV2;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ClusterShardingMetricsExporter(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;

        final SpringActorSpawnContext<ClusterShardingMetricsActor, ClusterShardingStats> context =
                new SpringActorSpawnContext.Builder<ClusterShardingMetricsActor, ClusterShardingStats>()
                        .actorClass(ClusterShardingMetricsActor.class)
                        .actorContext(new ClusterShardingMetricsExporterContext("exporter", springActorSystem))
                        .build();
        final SpringActorSpawnContext<ClusterShardingMetricsActorV2, CurrentShardRegionState> contextV2 =
                new SpringActorSpawnContext.Builder<ClusterShardingMetricsActorV2, CurrentShardRegionState>()
                        .actorClass(ClusterShardingMetricsActorV2.class)
                        .actorContext(new ClusterShardingMetricsExporterV2Context("exporter", springActorSystem))
                        .build();

        this.metricsActors = springActorSystem.spawn(context).toCompletableFuture().join();
        this.metricsActorsV2 = springActorSystem.spawn(contextV2).toCompletableFuture().join();
    }

    @PostConstruct
    private void init() {
        final ActorRef<ClusterShardingQuery> shardStateActor =
                springActorSystem.getClusterSharding().shardState();

        // Schedule the stats retrieval every second
        scheduler.scheduleAtFixedRate(() -> {
            shardStateActor.tell(new GetClusterShardingStats(
                    ChatRoomActor.TYPE_KEY,
                    Duration.ofSeconds(3),
                    metricsActors.getRef()
            ));

            shardStateActor.tell(new GetShardRegionState(
                    ChatRoomActor.TYPE_KEY,
                    metricsActorsV2.getRef()
            ));
        }, 0, 1, TimeUnit.SECONDS);
    }
}
