package io.github.seonwkim.example.receptionist;

import io.github.seonwkim.core.EnableActorSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Example application demonstrating Pekko's Receptionist feature for dynamic actor discovery.
 *
 * <p>This example shows how to use the receptionist for:
 * <ul>
 *   <li>Dynamic worker pool management
 *   <li>Service discovery pattern
 *   <li>Load balancing across available workers
 *   <li>Monitoring worker availability
 * </ul>
 */
@SpringBootApplication
@EnableActorSupport
public class ReceptionistExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReceptionistExampleApplication.class, args);
    }
}
