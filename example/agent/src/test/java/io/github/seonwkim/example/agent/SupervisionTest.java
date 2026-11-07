package io.github.seonwkim.example.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.example.agent.llm.ChatMessage;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext
class SupervisionTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private AgentService agents;

    @Test
    void crashedAgentRestartsWithItsConversation() throws Exception {
        agents.chat("crash-agent", "my name is Ada", TIMEOUT).toCompletableFuture().get(20, TimeUnit.SECONDS);
        String before = conversation().incarnation();

        agents.simulateCrash("crash-agent");

        ConversationView after = await().atMost(10, TimeUnit.SECONDS)
                .until(this::conversation, view -> !view.incarnation().equals(before));
        assertThat(after.conversation()).contains(ChatMessage.user("my name is Ada"));

        AgentAnswer answer = agents.chat("crash-agent", "what is my name?", TIMEOUT)
                .toCompletableFuture()
                .get(20, TimeUnit.SECONDS);
        assertThat(answer.text()).isEqualTo("Your name is Ada");
    }

    private ConversationView conversation() throws Exception {
        return agents.conversation("crash-agent", TIMEOUT).toCompletableFuture().get(20, TimeUnit.SECONDS);
    }
}
