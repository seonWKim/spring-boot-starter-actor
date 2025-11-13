package io.github.seonwkim.core.circuitbreaker;

import java.time.Duration;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.javadsl.ActorContext;

/**
 * Configuration for circuit breaker applied to actor message processing.
 *
 * <p>Circuit breakers prevent cascading failures by failing fast when a service is unavailable.
 * This configuration allows per-actor circuit breaker setup with fallback strategies.
 *
 * <p>Example usage:
 * <pre>{@code
 * ActorCircuitBreakerConfig config = ActorCircuitBreakerConfig.builder()
 *     .name("payment-service")
 *     .failureRateThreshold(50.0f)
 *     .waitDurationInOpenState(Duration.ofSeconds(30))
 *     .fallback((ctx, msg, ex) -> {
 *         ctx.getLog().warn("Circuit open, using fallback");
 *         return Behaviors.same();
 *     })
 *     .build();
 * }</pre>
 */
public final class ActorCircuitBreakerConfig {

    private final String name;
    @Nullable private final Float failureRateThreshold;
    @Nullable private final Duration waitDurationInOpenState;
    @Nullable private final Integer slidingWindowSize;
    @Nullable private final Integer minimumNumberOfCalls;
    @Nullable private final Integer permittedNumberOfCallsInHalfOpenState;
    @Nullable private final Duration slowCallDurationThreshold;
    @Nullable private final Float slowCallRateThreshold;
    @Nullable private final CircuitBreakerFallback<?, ?> fallback;
    private final boolean recordExceptions;

    private ActorCircuitBreakerConfig(Builder builder) {
        this.name = builder.name;
        this.failureRateThreshold = builder.failureRateThreshold;
        this.waitDurationInOpenState = builder.waitDurationInOpenState;
        this.slidingWindowSize = builder.slidingWindowSize;
        this.minimumNumberOfCalls = builder.minimumNumberOfCalls;
        this.permittedNumberOfCallsInHalfOpenState = builder.permittedNumberOfCallsInHalfOpenState;
        this.slowCallDurationThreshold = builder.slowCallDurationThreshold;
        this.slowCallRateThreshold = builder.slowCallRateThreshold;
        this.fallback = builder.fallback;
        this.recordExceptions = builder.recordExceptions;
    }

    public String getName() {
        return name;
    }

    @Nullable public Float getFailureRateThreshold() {
        return failureRateThreshold;
    }

    @Nullable public Duration getWaitDurationInOpenState() {
        return waitDurationInOpenState;
    }

    @Nullable public Integer getSlidingWindowSize() {
        return slidingWindowSize;
    }

    @Nullable public Integer getMinimumNumberOfCalls() {
        return minimumNumberOfCalls;
    }

    @Nullable public Integer getPermittedNumberOfCallsInHalfOpenState() {
        return permittedNumberOfCallsInHalfOpenState;
    }

    @Nullable public Duration getSlowCallDurationThreshold() {
        return slowCallDurationThreshold;
    }

    @Nullable public Float getSlowCallRateThreshold() {
        return slowCallRateThreshold;
    }

    @Nullable public CircuitBreakerFallback<?, ?> getFallback() {
        return fallback;
    }

    public boolean isRecordExceptions() {
        return recordExceptions;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Fallback handler invoked when circuit breaker is open or call fails.
     *
     * @param <C> Command type
     * @param <M> Message type
     */
    @FunctionalInterface
    public interface CircuitBreakerFallback<C, M> {
        org.apache.pekko.actor.typed.Behavior<C> onFailure(
                ActorContext<C> context, M message, Throwable throwable);
    }

    public static final class Builder {
        private final String name;
        @Nullable private Float failureRateThreshold = null;
        @Nullable private Duration waitDurationInOpenState = null;
        @Nullable private Integer slidingWindowSize = null;
        @Nullable private Integer minimumNumberOfCalls = null;
        @Nullable private Integer permittedNumberOfCallsInHalfOpenState = null;
        @Nullable private Duration slowCallDurationThreshold = null;
        @Nullable private Float slowCallRateThreshold = null;
        @Nullable private CircuitBreakerFallback<?, ?> fallback = null;
        private boolean recordExceptions = true;

        private Builder(String name) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Circuit breaker name cannot be null or empty");
            }
            this.name = name;
        }

        /**
         * Sets the failure rate threshold (percentage) that triggers the circuit to open.
         *
         * @param failureRateThreshold threshold between 0 and 100
         * @return this builder
         */
        public Builder failureRateThreshold(float failureRateThreshold) {
            if (failureRateThreshold < 0 || failureRateThreshold > 100) {
                throw new IllegalArgumentException("Failure rate threshold must be between 0 and 100");
            }
            this.failureRateThreshold = failureRateThreshold;
            return this;
        }

        /**
         * Sets the duration the circuit breaker stays open before transitioning to half-open.
         *
         * @param duration wait duration
         * @return this builder
         */
        public Builder waitDurationInOpenState(Duration duration) {
            if (duration == null || duration.isNegative()) {
                throw new IllegalArgumentException("Wait duration must be positive");
            }
            this.waitDurationInOpenState = duration;
            return this;
        }

        /**
         * Sets the size of the sliding window used to record call outcomes.
         *
         * @param size window size
         * @return this builder
         */
        public Builder slidingWindowSize(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("Sliding window size must be positive");
            }
            this.slidingWindowSize = size;
            return this;
        }

        /**
         * Sets the minimum number of calls before the circuit breaker can calculate error rate.
         *
         * @param calls minimum calls
         * @return this builder
         */
        public Builder minimumNumberOfCalls(int calls) {
            if (calls <= 0) {
                throw new IllegalArgumentException("Minimum number of calls must be positive");
            }
            this.minimumNumberOfCalls = calls;
            return this;
        }

        /**
         * Sets the number of permitted calls when the circuit breaker is half-open.
         *
         * @param calls permitted calls
         * @return this builder
         */
        public Builder permittedNumberOfCallsInHalfOpenState(int calls) {
            if (calls <= 0) {
                throw new IllegalArgumentException("Permitted calls must be positive");
            }
            this.permittedNumberOfCallsInHalfOpenState = calls;
            return this;
        }

        /**
         * Sets the threshold duration above which calls are considered slow.
         *
         * @param duration slow call threshold
         * @return this builder
         */
        public Builder slowCallDurationThreshold(Duration duration) {
            if (duration == null || duration.isNegative()) {
                throw new IllegalArgumentException("Slow call duration must be positive");
            }
            this.slowCallDurationThreshold = duration;
            return this;
        }

        /**
         * Sets the slow call rate threshold (percentage) that triggers the circuit to open.
         *
         * @param slowCallRateThreshold threshold between 0 and 100
         * @return this builder
         */
        public Builder slowCallRateThreshold(float slowCallRateThreshold) {
            if (slowCallRateThreshold < 0 || slowCallRateThreshold > 100) {
                throw new IllegalArgumentException("Slow call rate threshold must be between 0 and 100");
            }
            this.slowCallRateThreshold = slowCallRateThreshold;
            return this;
        }

        /**
         * Sets a fallback handler to invoke when the circuit breaker is open or a call fails.
         *
         * @param fallback the fallback handler
         * @param <C> command type
         * @param <M> message type
         * @return this builder
         */
        public <C, M> Builder fallback(CircuitBreakerFallback<C, M> fallback) {
            this.fallback = fallback;
            return this;
        }

        /**
         * Sets whether to record exceptions for circuit breaker metrics.
         *
         * @param recordExceptions true to record exceptions
         * @return this builder
         */
        public Builder recordExceptions(boolean recordExceptions) {
            this.recordExceptions = recordExceptions;
            return this;
        }

        public ActorCircuitBreakerConfig build() {
            return new ActorCircuitBreakerConfig(this);
        }
    }
}
