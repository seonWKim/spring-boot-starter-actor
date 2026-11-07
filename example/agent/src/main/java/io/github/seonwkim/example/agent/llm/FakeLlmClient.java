package io.github.seonwkim.example.agent.llm;

import io.github.seonwkim.example.agent.AgentProperties;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

// --8<-- [start:fake-llm]
/**
 * A scripted model, so the example runs without an API key or network.
 *
 * <ul>
 *   <li>"weather in &lt;city&gt;" asks for the {@code weather} tool;
 *   <li>"refund &lt;order&gt;" asks for the {@code refund} tool, which needs a human approval;
 *   <li>"my name is &lt;name&gt;" is remembered, and "what is my name?" answers from the conversation;
 *   <li>a tool result is turned into the final answer;
 *   <li>anything else is echoed.
 * </ul>
 */
@Component
public class FakeLlmClient implements LlmClient {

    private final AgentProperties properties;

    public FakeLlmClient(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public CompletionStage<LlmReply> complete(List<ChatMessage> conversation) {
        long latencyMillis = properties.getFakeLlmLatency().toMillis();
        return CompletableFuture.supplyAsync(
                () -> reply(conversation), CompletableFuture.delayedExecutor(latencyMillis, TimeUnit.MILLISECONDS));
    }

    static LlmReply reply(List<ChatMessage> conversation) {
        ChatMessage last = conversation.get(conversation.size() - 1);
        if (last.role() == ChatMessage.Role.TOOL_RESULT) {
            return LlmReply.answer("Tool " + last.toolName() + " says: " + last.content());
        }
        String text = last.content().trim();
        String lower = text.toLowerCase(Locale.ROOT);
        int weather = lower.indexOf("weather in ");
        if (weather >= 0) {
            String city = text.substring(weather + "weather in ".length()).replaceAll("[?.!]+$", "");
            return LlmReply.callTool("weather", city);
        }
        if (lower.startsWith("refund ")) {
            return LlmReply.callTool("refund", text.substring("refund ".length()));
        }
        if (lower.startsWith("what is my name")) {
            for (int i = conversation.size() - 1; i >= 0; i--) {
                ChatMessage message = conversation.get(i);
                if (message.role() == ChatMessage.Role.USER
                        && message.content().toLowerCase(Locale.ROOT).startsWith("my name is ")) {
                    return LlmReply.answer(
                            "Your name is " + message.content().substring("my name is ".length()));
                }
            }
            return LlmReply.answer("You did not tell me your name.");
        }
        if (lower.startsWith("my name is ")) {
            return LlmReply.answer("Nice to meet you, " + text.substring("my name is ".length()));
        }
        return LlmReply.answer("You said: " + text);
    }
}
// --8<-- [end:fake-llm]
