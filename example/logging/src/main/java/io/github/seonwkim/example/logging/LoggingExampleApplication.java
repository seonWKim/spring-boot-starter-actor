package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.EnableActorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Logging example demonstrating:
 * - Static and dynamic MDC in actors
 * - Actor tags for categorization
 * - Request tracing across multiple actors
 * - Async and JSON logging
 */
@SpringBootApplication
@EnableActorSupport
public class LoggingExampleApplication {

    private static final Logger log = LoggerFactory.getLogger(LoggingExampleApplication.class);

    public static void main(String[] args) {
        log.info("Starting Logging Example Application...");
        SpringApplication.run(LoggingExampleApplication.class, args);
        log.info("Application started - check logs/ directory for output");
    }
}
