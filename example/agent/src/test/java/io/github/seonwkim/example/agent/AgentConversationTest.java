package io.github.seonwkim.example.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.example.agent.llm.ChatMessage;
import io.github.seonwkim.example.agent.llm.ChatMessage.Role;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext
class AgentConversationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private AgentService agents;

    @Test
    void userMessageLlmToolCallToolResultFinalAnswer() throws Exception {
        AgentAnswer answer = agents.chat("weather-agent", "What is the weather in Seoul?", TIMEOUT)
                .toCompletableFuture()
                .get(20, TimeUnit.SECONDS);

        assertThat(answer.agentId()).isEqualTo("weather-agent");
        assertThat(answer.text()).isEqualTo("Tool weather says: sunny and 21C in Seoul");

        ConversationView view = agents.conversation("weather-agent", TIMEOUT)
                .toCompletableFuture()
                .get(20, TimeUnit.SECONDS);
        assertThat(view.status()).isEqualTo("IDLE");
        assertThat(view.conversation())
                .containsExactly(
                        ChatMessage.user("What is the weather in Seoul?"),
                        ChatMessage.toolCall("weather", "Seoul"),
                        ChatMessage.toolResult("weather", "sunny and 21C in Seoul"),
                        ChatMessage.assistant("Tool weather says: sunny and 21C in Seoul"));
        assertThat(view.conversation())
                .extracting(ChatMessage::role)
                .containsExactly(Role.USER, Role.TOOL_CALL, Role.TOOL_RESULT, Role.ASSISTANT);
    }

    @Test
    void answerWithoutToolCall() throws Exception {
        AgentAnswer answer = agents.chat("echo-agent", "hello", TIMEOUT)
                .toCompletableFuture()
                .get(20, TimeUnit.SECONDS);

        assertThat(answer.text()).isEqualTo("You said: hello");
    }
}
