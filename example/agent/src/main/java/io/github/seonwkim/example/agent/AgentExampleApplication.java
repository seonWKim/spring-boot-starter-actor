package io.github.seonwkim.example.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AgentProperties.class)
public class AgentExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentExampleApplication.class, args);
    }
}
