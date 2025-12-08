package io.github.seonwkim.metrics.sampling;

import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.core.MetricsConfiguration.SamplingConfig;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Strategy for sampling actor instrumentation to reduce overhead.
 */
public interface SamplingStrategy {

    boolean shouldSample(ActorContext context);

    static SamplingStrategy from(SamplingConfig config) {
        switch (config.getStrategy().toLowerCase()) {
            case "always":
                return new AlwaysSample();
            case "never":
                return new NeverSample();
            case "rate-based":
                return new RateBased(config.getRate());
            case "adaptive":
                return new AdaptiveSampling(config.getTargetThroughput(), config.getMinRate(), config.getMaxRate());
            default:
                throw new IllegalArgumentException("Unknown sampling strategy: " + config.getStrategy());
        }
    }

    /**
     * Always sample - instrument all actors.
     */
    class AlwaysSample implements SamplingStrategy {
        @Override
        public boolean shouldSample(ActorContext context) {
            return true;
        }
    }

    /**
     * Never sample - instrument no actors.
     */
    class NeverSample implements SamplingStrategy {
        @Override
        public boolean shouldSample(ActorContext context) {
            return false;
        }
    }

    /**
     * Rate-based sampling - sample a fixed percentage of actors.
     */
    class RateBased implements SamplingStrategy {
        private final double rate;

        public RateBased(double rate) {
            if (rate < 0.0 || rate > 1.0) {
                throw new IllegalArgumentException("Sampling rate must be between 0.0 and 1.0, got: " + rate);
            }
            this.rate = rate;
        }

        @Override
        public boolean shouldSample(ActorContext context) {
            if (rate == 1.0) {
                return true;
            }
            if (rate == 0.0) {
                return false;
            }
            return ThreadLocalRandom.current().nextDouble() < rate;
        }
    }

    /**
     * Adaptive sampling - dynamically adjusts sampling rate based on throughput.
     * Reduces sampling when throughput is high to maintain target metrics throughput.
     */
    class AdaptiveSampling implements SamplingStrategy {
        private final long targetThroughput;
        private final double minRate;
        private final double maxRate;

        private final AtomicLong sampleCount = new AtomicLong(0);
        private final AtomicLong totalCount = new AtomicLong(0);
        private volatile double currentRate;
        private volatile long lastAdjustmentTime;
        private static final long ADJUSTMENT_INTERVAL_MS = 1000; // Adjust every second

        public AdaptiveSampling(long targetThroughput, double minRate, double maxRate) {
            if (minRate < 0.0 || minRate > 1.0) {
                throw new IllegalArgumentException("Min rate must be between 0.0 and 1.0, got: " + minRate);
            }
            if (maxRate < 0.0 || maxRate > 1.0) {
                throw new IllegalArgumentException("Max rate must be between 0.0 and 1.0, got: " + maxRate);
            }
            if (minRate > maxRate) {
                throw new IllegalArgumentException("Min rate cannot be greater than max rate");
            }
            this.targetThroughput = targetThroughput;
            this.minRate = minRate;
            this.maxRate = maxRate;
            this.currentRate = maxRate; // Start with maximum sampling
            this.lastAdjustmentTime = System.currentTimeMillis();
        }

        @Override
        public boolean shouldSample(ActorContext context) {
            totalCount.incrementAndGet();
            adjustRateIfNeeded();

            if (currentRate >= 1.0) {
                sampleCount.incrementAndGet();
                return true;
            }
            if (currentRate <= 0.0) {
                return false;
            }

            boolean sample = ThreadLocalRandom.current().nextDouble() < currentRate;
            if (sample) {
                sampleCount.incrementAndGet();
            }
            return sample;
        }

        private void adjustRateIfNeeded() {
            long now = System.currentTimeMillis();
            long timeSinceLastAdjustment = now - lastAdjustmentTime;

            if (timeSinceLastAdjustment < ADJUSTMENT_INTERVAL_MS) {
                return;
            }

            synchronized (this) {
                // Double-check after acquiring lock
                if (now - lastAdjustmentTime < ADJUSTMENT_INTERVAL_MS) {
                    return;
                }

                long samples = sampleCount.getAndSet(0);
                long total = totalCount.getAndSet(0);
                lastAdjustmentTime = now;

                if (total == 0) {
                    return;
                }

                // Calculate current throughput (samples per second)
                double currentThroughput = (samples * 1000.0) / timeSinceLastAdjustment;

                // Adjust rate to target throughput
                if (currentThroughput > targetThroughput * 1.1) {
                    // Too many samples, reduce rate
                    currentRate = Math.max(minRate, currentRate * 0.9);
                } else if (currentThroughput < targetThroughput * 0.9) {
                    // Too few samples, increase rate
                    currentRate = Math.min(maxRate, currentRate * 1.1);
                }
                // else: within 10% of target, keep current rate
            }
        }

        public double getCurrentRate() {
            return currentRate;
        }
    }
}
