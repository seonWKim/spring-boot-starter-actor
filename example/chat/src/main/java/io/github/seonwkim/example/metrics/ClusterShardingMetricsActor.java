package io.github.seonwkim.example.metrics;

import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorWithContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.ShardRegion.CurrentShardRegionState;
import org.springframework.stereotype.Component;

@Component
public class ClusterShardingMetricsActor
        implements SpringActorWithContext<
                CurrentShardRegionState, ClusterShardingMetricsActor.ClusterShardingMetricsExporterContext> {

    public static class ClusterShardingMetricsExporterContext extends SpringActorContext {

        private final String actorId;
        private final MeterRegistry meterRegistry;

        public ClusterShardingMetricsExporterContext(String actorId, MeterRegistry meterRegistry) {
            this.actorId = actorId;
            this.meterRegistry = meterRegistry;
        }

        @Override
        public String actorId() {
            return actorId;
        }
    }

    @Override
    public SpringActorBehavior<CurrentShardRegionState> create(ClusterShardingMetricsExporterContext context) {
        return SpringActorBehavior.builder(CurrentShardRegionState.class, context)
                .onMessage(CurrentShardRegionState.class, (ctx, msg) -> handle(msg, context.meterRegistry))
                .build();
    }

    private final Map<String, AtomicInteger> shardEntityCounters = new ConcurrentHashMap<>();

    private Behavior<CurrentShardRegionState> handle(CurrentShardRegionState msg, MeterRegistry meterRegistry) {
        msg.shards().foreach(shard -> {
            final String shardId = shard.shardId();
            final int entitiesCount = shard.entityIds().size();
            shardEntityCounters
                    .computeIfAbsent(shardId, key -> {
                        AtomicInteger gauge = new AtomicInteger(entitiesCount);
                        meterRegistry.gauge("pekko.shard.entities", Tags.of("shardId", shardId), gauge);
                        return gauge;
                    })
                    .set(entitiesCount);

            return null;
        });

        return Behaviors.same();
    }
}
