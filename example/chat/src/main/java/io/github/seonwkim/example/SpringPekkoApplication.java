package io.github.seonwkim.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.seonwkim.core.EnableActorSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main application class for the chat example. This class serves as the entry point for the Spring
 * Boot application.
 */
@SpringBootApplication
@EnableActorSupport
public class SpringPekkoApplication {

    /**
     * Main method to start the application.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringPekkoApplication.class, args);
    }

    /**
     * Creates an ObjectMapper bean for JSON serialization/deserialization.
     *
     * @return The ObjectMapper instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
