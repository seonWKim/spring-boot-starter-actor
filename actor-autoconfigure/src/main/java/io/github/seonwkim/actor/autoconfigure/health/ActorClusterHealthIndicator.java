package io.github.seonwkim.actor.autoconfigure.health;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.cluster.MemberStatus;
import io.github.seonwkim.actor.internal.cluster.ClusterHealthView;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * This node's cluster membership: {@code UP} when it is an Up member, {@code DOWN} when it was downed,
 * removed or is unreachable, and {@code OUT_OF_SERVICE} in between (not joined yet, joining, weakly up,
 * leaving or shutting down). Unreachable peers are a count in the details: one partitioned peer does not
 * make every node unhealthy. The details hold no addresses.
 */
public class ActorClusterHealthIndicator extends AbstractHealthIndicator {

    private final SpringActorSystem actorSystem;

    public ActorClusterHealthIndicator(SpringActorSystem actorSystem) {
        super("Actor cluster health check failed");
        this.actorSystem = actorSystem;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        ClusterHealthView view = ClusterHealthView.of(actorSystem);
        MemberStatus self = view.selfStatus();
        builder.status(statusOf(view))
                .withDetail("selfStatus", self == null ? "NOT_JOINED" : self.name())
                .withDetail("selfUnreachable", view.selfUnreachable())
                .withDetail("members", view.members())
                .withDetail("unreachable", view.unreachable());
    }

    static Status statusOf(ClusterHealthView view) {
        MemberStatus self = view.selfStatus();
        if (view.terminated() || view.selfUnreachable() || self == MemberStatus.DOWN || self == MemberStatus.REMOVED) {
            return Status.DOWN;
        }
        return self == MemberStatus.UP ? Status.UP : Status.OUT_OF_SERVICE;
    }
}
