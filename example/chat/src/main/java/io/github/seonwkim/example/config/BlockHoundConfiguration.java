package io.github.seonwkim.example.config;

import org.springframework.context.annotation.Configuration;
import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

import javax.annotation.PostConstruct;

/**
 * Configuration for BlockHound to detect blocking calls in non-blocking threads.
 * BlockHound helps identify accidental blocking operations in reactive/actor-based code.
 */
@Configuration
public class BlockHoundConfiguration {

    static {
        // Install BlockHound only if explicitly enabled via system property
        // Usage: java -Dblockhound.enabled=true -jar app.jar
        String enabled = System.getProperty("blockhound.enabled", "false");
        if ("true".equalsIgnoreCase(enabled)) {
            BlockHound.builder()
                    .with(new MinimalBlockHoundIntegration())
                    .install();
            System.out.println("✓ BlockHound enabled via system property - monitoring Pekko dispatcher threads");
        }
    }

    /**
     * Custom integration that only monitors Pekko dispatcher threads.
     * This allows blocking during startup while catching violations in actor message processing.
     */
    static class MinimalBlockHoundIntegration implements BlockHoundIntegration {
        @Override
        public void applyTo(BlockHound.Builder builder) {
            // Only monitor Pekko dispatcher threads (where actor message processing happens)
            // All other threads (main, Netty, Spring init, etc.) are allowed to block
            builder.nonBlockingThreadPredicate(current -> t -> {
                String name = t.getName();
                // ONLY Pekko dispatcher threads are marked as non-blocking
                // All other threads (main, Netty, etc.) can block freely
                return name != null && (
                    name.contains("-dispatcher-") ||
                    name.contains("pekko.actor.default-dispatcher")
                );
            });

            // Allow logging even in dispatcher threads (it can sometimes block)
            builder.allowBlockingCallsInside("org.slf4j.Logger", "info");
            builder.allowBlockingCallsInside("org.slf4j.Logger", "debug");
            builder.allowBlockingCallsInside("org.slf4j.Logger", "warn");
            builder.allowBlockingCallsInside("org.slf4j.Logger", "error");
            builder.allowBlockingCallsInside("org.slf4j.LoggerFactory", "getLogger");

            // Allow Pekko serialization and core infrastructure
            builder.allowBlockingCallsInside("org.apache.pekko.serialization.Serialization", "serialize");
            builder.allowBlockingCallsInside("org.apache.pekko.serialization.Serialization", "deserialize");
            builder.allowBlockingCallsInside("org.apache.pekko.actor.ActorSystem", "<init>");
            builder.allowBlockingCallsInside("org.apache.pekko.actor.ActorSystemImpl", "<init>");

            // Allow classloading and reflection (needed for initialization)
            builder.allowBlockingCallsInside("java.lang.ClassLoader", "loadClass");
            builder.allowBlockingCallsInside("java.lang.Class", "forName");
            builder.allowBlockingCallsInside("java.lang.Class", "getMethod");
            builder.allowBlockingCallsInside("java.lang.Class", "getDeclaredMethod");

            // Allow file I/O for configuration loading
            builder.allowBlockingCallsInside("java.io.FileInputStream", "read");
            builder.allowBlockingCallsInside("java.io.FileInputStream", "readBytes");
            builder.allowBlockingCallsInside("java.nio.file.Files", "readAllBytes");

            // Allow socket operations for Pekko remoting initialization
            builder.allowBlockingCallsInside("java.net.Socket", "<init>");
            builder.allowBlockingCallsInside("java.net.ServerSocket", "<init>");
            builder.allowBlockingCallsInside("java.net.InetAddress", "getByName");
        }
    }

    @PostConstruct
    public void init() {
        String enabled = System.getProperty("blockhound.enabled", "false");
        if ("true".equalsIgnoreCase(enabled)) {
            System.out.println("✓ BlockHound is active - monitoring Pekko dispatcher threads for blocking operations");
        } else {
            System.out.println("ℹ BlockHound is disabled - enable with -Dblockhound.enabled=true for development/testing");
        }
    }
}
