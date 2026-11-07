package io.github.seonwkim.example.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.shard.SpringShardedActorBehavior;
import io.github.seonwkim.core.shard.SpringShardedActorContext;
import io.github.seonwkim.example.agent.llm.LlmClient;
import io.github.seonwkim.example.agent.persistence.AgentSnapshotStore;
import io.github.seonwkim.example.agent.tool.ToolRegistry;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.springframework.stereotype.Component;

// --8<-- [start:agent-actor]
/**
 * One sharded entity per agent id: the cluster keeps exactly one instance of each agent, on
 * some node, and routes every message for that id to it.
 *
 * <p>The entity is a thin shell. It spawns the agent's logic, {@link AgentBrain}, as a child
 * under a restart supervisor and forwards every command to it. If the brain crashes, the
 * supervisor restarts it and it reloads its conversation from the snapshot store.
 */
@Component
public class AgentActor implements SpringShardedActor<AgentActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "Agent");

    /** Commands may cross nodes, so they are JSON serializable. */
    public interface Command extends AgentBrain.Message, JsonSerializable {}

    /** A user message. The reply is the agent's final answer for this turn. */
    public static final class Chat extends AskCommand<AgentAnswer> implements Command {
        public final String text;

        @JsonCreator
        public Chat(@JsonProperty("text") String text) {
            this.text = text;
        }
    }

    /** Reads the agent's state. Answered at once, even while an LLM call is pending. */
    public static final class GetConversation extends AskCommand<ConversationView> implements Command {}

    /** Makes the agent throw, to show supervision. */
    public static final class SimulateCrash implements Command {
        @JsonCreator
        public SimulateCrash() {}
    }

    private final AgentBrain.Dependencies dependencies;

    public AgentActor(
            AgentRuntime runtime,
            LlmClient llm,
            ToolRegistry tools,
            AgentSnapshotStore snapshots,
            AgentProperties properties) {
        this.dependencies = new AgentBrain.Dependencies(
                llm,
                tools,
                snapshots,
                runtime.rateLimiter().getUnderlying(),
                runtime.approvals().getUnderlying(),
                properties.getApprovalTimeout(),
                properties.getMaxToolSteps());
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        String agentId = ctx.getEntityId();
        return SpringShardedActorBehavior.builder(Command.class, ctx)
                .withState(behaviorCtx -> {
                    ActorRef<AgentBrain.Message> brain =
                            behaviorCtx.spawn(AgentBrain.create(agentId, dependencies), "brain");
                    // If the brain stops for good (restart limit hit), the shell stops too and
                    // sharding starts a fresh agent on the next message.
                    behaviorCtx.getUnderlying().watch(brain);
                    return brain;
                })
                .onMessage(Command.class, (brain, command) -> {
                    brain.tell(command);
                    return Behaviors.same();
                })
                .build();
    }
}
// --8<-- [end:agent-actor]
