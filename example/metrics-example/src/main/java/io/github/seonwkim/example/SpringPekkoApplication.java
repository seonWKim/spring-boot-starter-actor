package io.github.seonwkim.example;

import io.github.seonwkim.core.EnableActorSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application demonstrating actors with metrics.
 *
 * <p><b>Running with Gradle:</b> {@code ./gradlew :example:metrics-example:bootRun}
 * (the agent is attached automatically via the bootRun task configuration).
 *
 * <p><b>Running from IntelliJ:</b> The metrics Java agent must be added manually.
 * Build the agent first ({@code ./gradlew :metrics:agentJar}), then add this VM option
 * to your Run Configuration (Run &gt; Edit Configurations &gt; VM options):
 * <pre>-javaagent:metrics/build/libs/spring-boot-starter-actor-metrics-0.8.0-agent.jar</pre>
 * Without the agent, the application starts normally but no actor metrics are collected.
 */
@SpringBootApplication
@EnableActorSupport
public class SpringPekkoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringPekkoApplication.class, args);
    }
}
