package io.github.seonwkim.example.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorContext;
import io.github.seonwkim.example.agent.llm.LlmClient;
import io.github.seonwkim.example.agent.persistence.AgentSnapshotStore;
import io.github.seonwkim.example.agent.tool.ToolRegistry;
import org.springframework.stereotype.Component;

// --8<-- [start:agent-actor]
/**
 * One sharded entity per agent id: the cluster keeps exactly one instance of each agent, on
 * some node, and routes every message for that id to it.
 *
 * <p>The agent's logic is {@link AgentBrain}, run under a restart supervisor. If it crashes, the
 * supervisor restarts it and it reloads its conversation from the snapshot store.
 */
@Component
public class AgentActor implements SpringShardedActor<AgentActor.Command> {

    public static final ShardedEntityType<Command> TYPE_KEY = ShardedEntityType.of(Command.class, "Agent");

    /** Commands may cross nodes, so they are JSON serializable. */
    public interface Command extends JsonSerializable {}

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
                runtime.rateLimiter(),
                runtime.approvals(),
                properties.getApprovalTimeout(),
                properties.getMaxToolSteps());
    }

    @Override
    public ShardedEntityType<Command> entityType() {
        return TYPE_KEY;
    }

    @Override
    public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        return AgentBrain.behavior(SpringActorBehavior.builder(Command.class, ctx), ctx.getEntityId(), dependencies);
    }
}
// --8<-- [end:agent-actor]
