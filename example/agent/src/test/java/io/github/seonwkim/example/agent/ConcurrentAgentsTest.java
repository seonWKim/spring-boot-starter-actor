package io.github.seonwkim.example.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.example.agent.llm.ChatMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext
class ConcurrentAgentsTest {

    private static final int AGENTS = 100;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private AgentService agents;

    @Test
    void eachOfOneHundredAgentsKeepsItsOwnState() throws Exception {
        List<CompletableFuture<AgentAnswer>> answers = new ArrayList<>();
        for (int i = 0; i < AGENTS; i++) {
            String agentId = "agent-" + i;
            answers.add(agents.chat(agentId, "my name is name-" + i, TIMEOUT)
                    .thenCompose(first -> agents.chat(agentId, "what is my name?", TIMEOUT))
                    .toCompletableFuture());
        }
        CompletableFuture.allOf(answers.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

        for (int i = 0; i < AGENTS; i++) {
            AgentAnswer answer = answers.get(i).join();
            assertThat(answer.agentId()).isEqualTo("agent-" + i);
            assertThat(answer.text()).isEqualTo("Your name is name-" + i);

            ConversationView view = agents.conversation("agent-" + i, TIMEOUT)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            List<String> userMessages = view.conversation().stream()
                    .filter(message -> message.role() == ChatMessage.Role.USER)
                    .map(ChatMessage::content)
                    .collect(Collectors.toList());
            assertThat(userMessages).containsExactly("my name is name-" + i, "what is my name?");
        }
    }
}
