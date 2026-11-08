package io.github.seonwkim.core;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for MDC (Mapped Diagnostic Context) values that will be included in actor logs.
 *
 * <p>MDC allows you to add contextual information to log entries. This is particularly useful
 * for adding request IDs, correlation IDs, user IDs, or any other contextual data that should
 * appear in all log entries from an actor.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Static MDC values passed at spawn time
 * Map<String, String> mdc = Map.of(
 *     "userId", "user-123",
 *     "requestId", "req-456",
 *     "service", "order-service"
 * );
 *
 * actorSystem.actor(MyActor.class)
 *     .withId("my-actor")
 *     .withMdc(MdcConfig.of(mdc))
 *     .spawn();
 * }</pre>
 *
 * <p>The MDC values will be available in the actor's {@link SpringActorContext} and can be
 * combined with dynamic per-message MDC values using
 * {@link SpringActorBehavior.Builder#withMdc(java.util.function.Function)}.
 *
 * <p>In logs, MDC values appear as additional context:
 * <pre>
 * [INFO] [userId=user-123] [requestId=req-456] [service=order-service] Processing order
 * </pre>
 *
 * @see SpringActorBehavior.Builder#withMdc(java.util.function.Function)
 */
public abstract class MdcConfig {

    /**
     * No MDC values. This is the default when MDC is not specified.
     *
     * @return An MDC configuration with no values
     */
    public static MdcConfig empty() {
        return NoMdc.INSTANCE;
    }

    /**
     * Create MDC configuration from a map of key-value pairs.
     *
     * @param mdc A map of MDC key-value pairs
     * @return An MDC configuration with the specified values
     * @throws IllegalArgumentException if mdc is null or empty
     */
    public static MdcConfig of(Map<String, String> mdc) {
        if (mdc == null || mdc.isEmpty()) {
            throw new IllegalArgumentException(
                    "MDC map must not be null or empty. Use MdcConfig.empty() for no MDC values.");
        }
        return new WithMdc(mdc);
    }

    // Package-private constructor to prevent external subclassing
    MdcConfig() {}

    /**
     * Returns true if this configuration has no MDC values.
     *
     * @return true if empty, false otherwise
     */
    public abstract boolean isEmpty();

    /**
     * Returns the map of MDC key-value pairs in this configuration.
     * Returns an empty map if no MDC values are configured.
     *
     * @return An immutable map of MDC values
     */
    public abstract Map<String, String> getMdc();

    /**
     * Returns a description of this MDC configuration for debugging.
     *
     * @return A human-readable description
     */
    public abstract String describe();

    // ========== Inner Classes ==========

    /**
     * No MDC configuration - the default when MDC is not specified.
     */
    private static final class NoMdc extends MdcConfig {
        static final NoMdc INSTANCE = new NoMdc();

        private NoMdc() {}

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Map<String, String> getMdc() {
            return Collections.emptyMap();
        }

        @Override
        public String describe() {
            return "No MDC values";
        }

        @Override
        public String toString() {
            return "MdcConfig.empty()";
        }
    }

    /**
     * MDC configuration with one or more key-value pairs.
     */
    private static final class WithMdc extends MdcConfig {
        private final Map<String, String> mdc;

        WithMdc(Map<String, String> mdc) {
            // Create immutable copy
            this.mdc = Map.copyOf(mdc);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public Map<String, String> getMdc() {
            return mdc;
        }

        @Override
        public String describe() {
            return "MDC: " + mdc.size() + " entries";
        }

        @Override
        public String toString() {
            return "MdcConfig.of(" + mdc + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WithMdc withMdc = (WithMdc) o;
            return mdc.equals(withMdc.mdc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mdc);
        }
    }
}
