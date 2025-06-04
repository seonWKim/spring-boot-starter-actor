package io.github.seonwkim.example;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.ShardRegion.ClusterShardingStats;
import org.apache.pekko.cluster.sharding.ShardRegion.CurrentShardRegionState;
import org.springframework.stereotype.Component;

import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorSystem;

@Component
public class ClusterShardingMetricsActorV2
        implements SpringActor<ClusterShardingMetricsActorV2, CurrentShardRegionState> {

    public static class ClusterShardingMetricsExporterV2Context implements SpringActorContext {

        private final String actorId;
        private final SpringActorSystem springActorSystem;

        public ClusterShardingMetricsExporterV2Context(String actorId, SpringActorSystem springActorSystem) {
            this.actorId = actorId;
            this.springActorSystem = springActorSystem;
        }

        @Override
        public String actorId() {
            return actorId;
        }
    }

    @Override
    public Behavior<CurrentShardRegionState> create(SpringActorContext actorContext) {
        if (!(actorContext instanceof ClusterShardingMetricsExporterV2Context)) {
            throw new IllegalStateException("Must be ClusterShardingMetricsExporterContext");
        }

        final ClusterShardingMetricsExporterV2Context context =
                (ClusterShardingMetricsExporterV2Context) actorContext;
        return Behaviors.setup(ctx -> Behaviors.receive(CurrentShardRegionState.class)
                                               .onMessage(CurrentShardRegionState.class,
                                                          msg -> handle(msg, context.springActorSystem))
                                               .build()
        );
    }

    private Behavior<CurrentShardRegionState> handle(CurrentShardRegionState msg,
                                                  SpringActorSystem springActorSystem) {
        System.out.println("MSGV2: " + msg);
        return Behaviors.same();
    }
}
