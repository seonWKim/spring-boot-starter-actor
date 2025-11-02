package io.github.seonwkim.example.supervision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Actor Supervision Visualizer.
 *
 * <p>This application demonstrates hierarchical actor supervision with different strategies:
 * <ul>
 *   <li>Restart (unlimited): Restarts failed actors indefinitely</li>
 *   <li>Restart (limited): Restarts failed actors up to 3 times per minute</li>
 *   <li>Stop: Terminates failed actors permanently</li>
 *   <li>Resume: Ignores failures and continues processing</li>
 * </ul>
 *
 * <p>Access the web UI at: <a href="http://localhost:8080">http://localhost:8080</a>
 */
@SpringBootApplication(scanBasePackages = "io.github.seonwkim")
public class SupervisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupervisionApplication.class, args);
        System.out.println("\n" + "========================================\n"
                + "  Actor Supervision Visualizer Started\n"
                + "========================================\n"
                + "  Web UI: http://localhost:8080\n"
                + "  API:    http://localhost:8080/api\n"
                + "========================================\n");
    }
}
