package io.github.seonwkim.example;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.ShardRegion.ClusterShardingStats;
import org.springframework.stereotype.Component;

import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorSystem;

@Component
public class ClusterShardingMetricsActor
        implements SpringActor<ClusterShardingMetricsActor, ClusterShardingStats> {

    public static class ClusterShardingMetricsExporterContext implements SpringActorContext {

        private final String actorId;
        private final SpringActorSystem springActorSystem;

        public ClusterShardingMetricsExporterContext(String actorId, SpringActorSystem springActorSystem) {
            this.actorId = actorId;
            this.springActorSystem = springActorSystem;
        }

        @Override
        public String actorId() {
            return actorId;
        }
    }

    @Override
    public Behavior<ClusterShardingStats> create(SpringActorContext actorContext) {
        if (!(actorContext instanceof ClusterShardingMetricsExporterContext)) {
            throw new IllegalStateException("Must be ClusterShardingMetricsExporterContext");
        }

        final ClusterShardingMetricsExporterContext context =
                (ClusterShardingMetricsExporterContext) actorContext;
        return Behaviors.setup(ctx -> Behaviors.receive(ClusterShardingStats.class)
                                               .onMessage(ClusterShardingStats.class,
                                                          msg -> handle(msg, context.springActorSystem))
                                               .build()
        );
    }

    private Behavior<ClusterShardingStats> handle(ClusterShardingStats msg,
                                                  SpringActorSystem springActorSystem) {
        System.out.println("MSG: " + msg);
        return Behaviors.same();
    }
}
