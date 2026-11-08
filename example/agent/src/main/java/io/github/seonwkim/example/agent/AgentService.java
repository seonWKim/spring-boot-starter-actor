package io.github.seonwkim.example.agent;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.shard.SpringShardedActorHandle;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Service;

// --8<-- [start:service]
/** Talks to agents by id. The agent may live on any node of the cluster. */
@Service
public class AgentService {

    private final SpringActorSystem actorSystem;

    public AgentService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public CompletionStage<AgentAnswer> chat(String agentId, String text, Duration timeout) {
        return agent(agentId)
                .ask(new AgentActor.Chat(text))
                .withTimeout(timeout)
                .execute();
    }

    public CompletionStage<ConversationView> conversation(String agentId, Duration timeout) {
        return agent(agentId)
                .ask(new AgentActor.GetConversation())
                .withTimeout(timeout)
                .execute();
    }

    public void simulateCrash(String agentId) {
        agent(agentId).tell(new AgentActor.SimulateCrash());
    }

    private SpringShardedActorHandle<AgentActor.Command> agent(String agentId) {
        return actorSystem.sharded(AgentActor.class).withId(agentId).get();
    }
}
// --8<-- [end:service]
