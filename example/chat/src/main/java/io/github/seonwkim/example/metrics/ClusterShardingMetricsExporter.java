package io.github.seonwkim.example.metrics;

import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.example.HelloActor;
import io.github.seonwkim.example.metrics.ClusterShardingMetricsActor.ClusterShardingMetricsExporterContext;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.ShardRegion.CurrentShardRegionState;
import org.apache.pekko.cluster.sharding.typed.ClusterShardingQuery;
import org.apache.pekko.cluster.sharding.typed.GetShardRegionState;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
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
        assert springActorSystem.unwrap(ClusterSharding.class) != null;
        final ActorRef<ClusterShardingQuery> shardStateActor =
                springActorSystem.unwrap(ClusterSharding.class).shardState();

        scheduler.scheduleAtFixedRate(
                () -> {
                    shardStateActor.tell(new GetShardRegionState(
                            EntityTypeKey.create(HelloActor.Command.class, HelloActor.TYPE_KEY.name()),
                            metricsActors.unwrap(ActorRef.class)));
                },
                0,
                1,
                TimeUnit.SECONDS);
    }
}
