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
        // Temporarily disabled to test reactive WebSocket implementation
        // Will re-enable after confirming WebSocket works
        /*
        BlockHound.builder()
                .with(new MinimalBlockHoundIntegration())
                .install();
        */
    }

    /**
     * Minimal integration to allow blocking during startup/initialization.
     * We want to catch blocking in actor threads during message processing.
     */
    static class MinimalBlockHoundIntegration implements BlockHoundIntegration {
        @Override
        public void applyTo(BlockHound.Builder builder) {
            // Allow blocking during Pekko actor system initialization only
            builder.allowBlockingCallsInside(
                "org.apache.pekko.actor.typed.ActorSystem",
                "create"
            );

            // Allow logging (it sometimes blocks)
            builder.allowBlockingCallsInside("org.slf4j.Logger", "info");
            builder.allowBlockingCallsInside("org.slf4j.Logger", "debug");
            builder.allowBlockingCallsInside("org.slf4j.Logger", "warn");
            builder.allowBlockingCallsInside("org.slf4j.Logger", "error");

            // Allow Pekko serialization infrastructure
            builder.allowBlockingCallsInside(
                "org.apache.pekko.serialization.Serialization",
                "serialize"
            );
            builder.allowBlockingCallsInside(
                "org.apache.pekko.serialization.Serialization",
                "deserialize"
            );
        }
    }

    @PostConstruct
    public void init() {
        System.out.println("⚠ BlockHound temporarily disabled - testing reactive WebSocket implementation");
    }
}
