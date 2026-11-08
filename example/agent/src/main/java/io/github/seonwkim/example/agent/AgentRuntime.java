package io.github.seonwkim.example.agent;

import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.example.agent.approval.ApprovalActor;
import io.github.seonwkim.example.agent.ratelimit.RateLimiterActor;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Join;
import org.springframework.stereotype.Component;

// --8<-- [start:runtime]
/**
 * Node-local actors that every agent on this node shares: the LLM rate limiter and the approval
 * gateway. Each node spawns its own, so the rate limit is per node.
 */
@Component
public class AgentRuntime {

    private final SpringActorHandle<RateLimiterActor.Command> rateLimiter;
    private final SpringActorHandle<ApprovalActor.Command> approvals;

    // The actor beans are parameters so Spring registers them before they are spawned here.
    public AgentRuntime(
            SpringActorSystem actorSystem,
            AgentProperties properties,
            RateLimiterActor rateLimiterActor,
            ApprovalActor approvalActor) {
        this.rateLimiter = actorSystem
                .actor(RateLimiterActor.class)
                .withId("llm-rate-limiter")
                .spawnAndWait();
        this.approvals =
                actorSystem.actor(ApprovalActor.class).withId("approvals").spawnAndWait();

        Cluster cluster = actorSystem.unwrap(Cluster.class);
        if (properties.isSelfJoin() && cluster != null) {
            cluster.manager().tell(Join.create(cluster.selfMember().address()));
        }
    }
    // --8<-- [end:runtime]

    public SpringActorHandle<RateLimiterActor.Command> rateLimiter() {
        return rateLimiter;
    }

    public SpringActorHandle<ApprovalActor.Command> approvals() {
        return approvals;
    }
}
