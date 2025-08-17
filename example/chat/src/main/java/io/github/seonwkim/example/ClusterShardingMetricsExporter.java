package io.github.seonwkim.example;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.ShardRegion.CurrentShardRegionState;
import org.apache.pekko.cluster.sharding.typed.ClusterShardingQuery;
import org.apache.pekko.cluster.sharding.typed.GetShardRegionState;
import org.springframework.stereotype.Component;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSpawnContext;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.example.ClusterShardingMetricsActor.ClusterShardingMetricsExporterContext;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class ClusterShardingMetricsExporter {

    private final SpringActorSystem springActorSystem;
    private final SpringActorRef<CurrentShardRegionState> metricsActors;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ClusterShardingMetricsExporter(MeterRegistry meterRegistry, SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
        final SpringActorSpawnContext<ClusterShardingMetricsActor, CurrentShardRegionState> context =
                new SpringActorSpawnContext.Builder<ClusterShardingMetricsActor, CurrentShardRegionState>()
                        .actorClass(ClusterShardingMetricsActor.class)
                        .actorContext(new ClusterShardingMetricsExporterContext("exporter", meterRegistry))
                        .build();

        this.metricsActors = springActorSystem.spawn(context).toCompletableFuture().join();
    }

    @PostConstruct
    private void init() {
        final ActorRef<ClusterShardingQuery> shardStateActor =
                springActorSystem.getClusterSharding().shardState();

        scheduler.scheduleAtFixedRate(() -> {
            shardStateActor.tell(new GetShardRegionState(
                    HelloActor.TYPE_KEY,
                    metricsActors.getUnderlying()
            ));
        }, 0, 1, TimeUnit.SECONDS);
    }
}
