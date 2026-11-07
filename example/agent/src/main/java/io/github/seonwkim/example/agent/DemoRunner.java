package io.github.seonwkim.example.agent;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Runs one conversation on start-up, with the fake LLM, and logs it. */
@Component
@ConditionalOnProperty(name = "agent.demo", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    private final AgentService agents;

    public DemoRunner(AgentService agents) {
        this.agents = agents;
    }

    @Override
    public void run(String... args) {
        Duration timeout = Duration.ofSeconds(10);
        AgentAnswer answer = agents.chat("demo-agent", "What is the weather in Seoul?", timeout)
                .toCompletableFuture()
                .join();
        log.info("Agent {} answered: {}", answer.agentId(), answer.text());
        agents.conversation("demo-agent", timeout)
                .toCompletableFuture()
                .join()
                .conversation()
                .forEach(message -> log.info("  {}", message));
    }
}
