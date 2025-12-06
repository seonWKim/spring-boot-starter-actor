package io.github.seonwkim.example.metrics;

import io.github.seonwkim.core.SpringActorHandle;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.example.HelloActor;
import io.github.seonwkim.example.metrics.ClusterShardingMetricsActor.ClusterShardingMetricsExporterContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.ShardRegion.CurrentShardRegionState;
import org.apache.pekko.cluster.sharding.typed.ClusterShardingQuery;
import org.apache.pekko.cluster.sharding.typed.GetShardRegionState;
import org.springframework.stereotype.Component;

@Component
public class ClusterShardingMetricsExporter {

    private final SpringActorSystem springActorSystem;
    private final SpringActorHandle<CurrentShardRegionState> metricsActors;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ClusterShardingMetricsExporter(MeterRegistry meterRegistry, SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
        this.metricsActors = springActorSystem
                .actor(ClusterShardingMetricsActor.class)
                .withContext(new ClusterShardingMetricsExporterContext("exporter", meterRegistry))
                .spawnAndWait();
    }

    @PostConstruct
    private void init() {
        assert springActorSystem.getClusterSharding() != null;
        final ActorRef<ClusterShardingQuery> shardStateActor =
                springActorSystem.getClusterSharding().shardState();

        scheduler.scheduleAtFixedRate(
                () -> {
                    shardStateActor.tell(new GetShardRegionState(HelloActor.TYPE_KEY, metricsActors.getUnderlying()));
                },
                0,
                1,
                TimeUnit.SECONDS);
    }
}
