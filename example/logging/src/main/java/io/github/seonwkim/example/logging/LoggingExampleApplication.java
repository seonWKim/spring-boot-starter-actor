package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.EnableActorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Production-ready logging example application.
 *
 * This example demonstrates:
 * 1. Static MDC - set at actor spawn time
 * 2. Dynamic MDC - computed per message
 * 3. Actor tags - for categorization and filtering
 * 4. Async logging - for better performance
 * 5. JSON logging - for structured logs
 * 6. Request tracing - across multiple actors
 *
 * Run the application and use the REST API to see logging in action:
 * - POST /api/orders - Process an order (dynamic MDC)
 * - POST /api/payments - Process a payment (static + dynamic MDC)
 * - POST /api/notifications - Send a notification (tags + MDC)
 * - POST /api/checkout - End-to-end workflow (request tracing)
 *
 * Check the logs in:
 * - Console output (human-readable)
 * - logs/application.log (formatted with MDC)
 * - logs/application-json.log (JSON structured logs)
 */
@SpringBootApplication
@EnableActorSupport
public class LoggingExampleApplication {

    private static final Logger log = LoggerFactory.getLogger(LoggingExampleApplication.class);

    public static void main(String[] args) {
        log.info("Starting Logging Example Application...");
        SpringApplication.run(LoggingExampleApplication.class, args);
        log.info("Logging Example Application started successfully!");
        log.info("Try the following endpoints:");
        log.info("curl -X POST http://localhost:8080/api/orders");
        log.info("curl -X POST http://localhost:8080/api/payments");
        log.info("curl -X POST http://localhost:8080/api/notifications");
        log.info("curl -X POST http://localhost:8080/api/checkout");
    }
}
