package io.github.seonwkim.example.agent.llm;

import java.util.List;
import java.util.concurrent.CompletionStage;

// --8<-- [start:llm-client]
/**
 * The only thing an agent knows about the model. Swap the fake for a real provider by adding
 * another bean that implements this interface; the agent code does not change.
 */
public interface LlmClient {

    /**
     * Sends the conversation so far and returns the model's next step. Must not block the
     * caller: the agent pipes the result back to itself.
     */
    CompletionStage<LlmReply> complete(List<ChatMessage> conversation);
}
// --8<-- [end:llm-client]
