package io.github.seonwkim.example.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.example.agent.llm.ChatMessage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Stops the whole actor system (and Spring context), starts a new one, and finds the agent's state. */
class ActorSystemRestartTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final Path snapshots = Paths.get("build", "agent-snapshots", "restart-" + UUID.randomUUID());

    @Test
    void agentStateSurvivesActorSystemRestart() throws Exception {
        SpringActorSystem firstSystem;
        try (ConfigurableApplicationContext first = start()) {
            firstSystem = first.getBean(SpringActorSystem.class);
            first.getBean(AgentService.class)
                    .chat("durable-agent", "my name is Grace", TIMEOUT)
                    .toCompletableFuture()
                    .get(20, TimeUnit.SECONDS);
        }
        assertThat(firstSystem.getRaw().whenTerminated().isCompleted()).isTrue();

        try (ConfigurableApplicationContext second = start()) {
            AgentService agents = second.getBean(AgentService.class);
            assertThat(second.getBean(SpringActorSystem.class)).isNotSameAs(firstSystem);

            ConversationView view = agents.conversation("durable-agent", TIMEOUT)
                    .toCompletableFuture()
                    .get(20, TimeUnit.SECONDS);
            assertThat(view.conversation()).contains(ChatMessage.user("my name is Grace"));

            AgentAnswer answer = agents.chat("durable-agent", "what is my name?", TIMEOUT)
                    .toCompletableFuture()
                    .get(20, TimeUnit.SECONDS);
            assertThat(answer.text()).isEqualTo("Your name is Grace");
        }
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(AgentExampleApplication.class)
                .web(WebApplicationType.NONE)
                // A command-line argument, so it wins over application.yml: both systems share the directory.
                .run("--agent.snapshot-dir=" + snapshots);
    }
}
