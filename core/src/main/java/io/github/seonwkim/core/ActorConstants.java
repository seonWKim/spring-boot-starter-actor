package io.github.seonwkim.core;

import java.time.Duration;

/**
 * Constants used throughout the Spring Boot Actor framework.
 *
 * <p>This class centralizes common default values and configuration constants to ensure
 * consistency across the framework.
 */
public final class ActorConstants {

    private ActorConstants() {
        // Prevent instantiation
    }

    /**
     * Default timeout in seconds for actor operations (ask, spawn, etc.).
     * This is used when no explicit timeout is specified.
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 3;

    /**
     * Default timeout duration for actor operations.
     * This is the Duration equivalent of {@link #DEFAULT_TIMEOUT_SECONDS}.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);

    /**
     * Default timeout in milliseconds for quick queries (e.g., exists checks).
     * Quick operations should complete faster than regular operations.
     */
    public static final int DEFAULT_QUERY_TIMEOUT_MILLIS = 100;

    /**
     * Default timeout duration for quick query operations.
     * This is the Duration equivalent of {@link #DEFAULT_QUERY_TIMEOUT_MILLIS}.
     */
    public static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofMillis(DEFAULT_QUERY_TIMEOUT_MILLIS);
}
