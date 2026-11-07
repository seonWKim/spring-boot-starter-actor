package io.github.seonwkim.example.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.example.agent.llm.ChatMessage;
import io.github.seonwkim.example.agent.llm.LlmClient;
import io.github.seonwkim.example.agent.llm.LlmReply;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@Import(SlowLlmTest.Config.class)
@DirtiesContext
class SlowLlmTest {

    /** An LLM whose replies the test releases by hand. */
    static class GatedLlmClient implements LlmClient {
        final BlockingQueue<CompletableFuture<LlmReply>> calls = new LinkedBlockingQueue<>();

        @Override
        public CompletionStage<LlmReply> complete(List<ChatMessage> conversation) {
            CompletableFuture<LlmReply> call = new CompletableFuture<>();
            calls.add(call);
            return call;
        }
    }

    /** Not a @TestConfiguration: component scanning outside @SpringBootTest would pick that up. */
    static class Config {
        @Bean
        @Primary
        GatedLlmClient gatedLlmClient() {
            return new GatedLlmClient();
        }
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration QUICK = Duration.ofSeconds(1);

    @Autowired
    private AgentService agents;

    @Autowired
    private GatedLlmClient llm;

    @Test
    void agentKeepsAnsweringWhileTheLlmCallIsPending() throws Exception {
        CompletableFuture<AgentAnswer> first =
                agents.chat("slow-agent", "first", TIMEOUT).toCompletableFuture();
        CompletableFuture<LlmReply> firstCall = llm.calls.poll(15, TimeUnit.SECONDS);
        assertThat(firstCall).isNotNull();

        // The LLM call is pending, and the agent still answers within a second.
        ConversationView pending = conversation(QUICK);
        assertThat(pending.status()).isEqualTo("LLM");
        assertThat(first).isNotDone();

        // A second chat is accepted and queued behind the pending turn.
        CompletableFuture<AgentAnswer> second =
                agents.chat("slow-agent", "second", TIMEOUT).toCompletableFuture();
        await().atMost(5, TimeUnit.SECONDS).until(() -> conversation(QUICK).queued() == 1);
        assertThat(llm.calls).isEmpty();

        firstCall.complete(LlmReply.answer("first answer"));
        assertThat(first.get(10, TimeUnit.SECONDS).text()).isEqualTo("first answer");

        CompletableFuture<LlmReply> secondCall = llm.calls.poll(10, TimeUnit.SECONDS);
        assertThat(secondCall).isNotNull();
        secondCall.complete(LlmReply.answer("second answer"));
        assertThat(second.get(10, TimeUnit.SECONDS).text()).isEqualTo("second answer");
    }

    private ConversationView conversation(Duration timeout) throws Exception {
        return agents.conversation("slow-agent", timeout).toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
