package io.github.seonwkim.example.ai.ratelimit;

import java.time.Duration;
import java.time.Instant;

/**
 * Token bucket algorithm for rate limiting.
 * Allows burst capacity while maintaining average rate.
 *
 * <p>Thread-safe when used within a single actor (actors are single-threaded).
 * This class is designed to be used as state within an actor.
 *
 * <p>Algorithm:
 * - Tokens are added at a constant rate (refillRate per minute)
 * - Maximum tokens = burstCapacity
 * - Each request consumes 1 token
 * - If no tokens available, request is rate limited
 */
public class TokenBucket {

    private final int refillRate; // tokens per minute
    private final int maxTokens; // burst capacity
    private int tokens;
    private Instant lastRefill;

    /**
     * Create a new token bucket.
     *
     * @param refillRate Number of tokens to add per minute (e.g., 60 = 1 per second)
     * @param burstCapacity Maximum tokens that can accumulate (allows bursts)
     */
    public TokenBucket(int refillRate, int burstCapacity) {
        this.refillRate = refillRate;
        this.maxTokens = burstCapacity;
        this.tokens = burstCapacity; // Start with full capacity
        this.lastRefill = Instant.now();
    }

    /**
     * Try to consume one token.
     *
     * @return true if token was consumed, false if rate limited
     */
    public boolean tryConsume() {
        refill();
        if (tokens > 0) {
            tokens--;
            return true;
        }
        return false;
    }

    /**
     * Try to consume multiple tokens.
     *
     * @param count Number of tokens to consume
     * @return true if all tokens were consumed, false if not enough tokens
     */
    public boolean tryConsume(int count) {
        refill();
        if (tokens >= count) {
            tokens -= count;
            return true;
        }
        return false;
    }

    /**
     * Get current number of available tokens.
     *
     * @return Number of tokens available
     */
    public int availableTokens() {
        refill();
        return tokens;
    }

    /**
     * Reset the bucket to full capacity.
     * Useful for testing or manual resets.
     */
    public void reset() {
        tokens = maxTokens;
        lastRefill = Instant.now();
    }

    /**
     * Refill tokens based on time elapsed.
     * Called automatically before token consumption.
     */
    private void refill() {
        Instant now = Instant.now();
        long secondsElapsed = Duration.between(lastRefill, now).toSeconds();

        if (secondsElapsed > 0) {
            // Calculate tokens to add based on refill rate per minute
            double tokensToAdd = (secondsElapsed / 60.0) * refillRate;
            if (tokensToAdd >= 1) {
                tokens = Math.min(maxTokens, tokens + (int) tokensToAdd);
                lastRefill = now;
            }
        }
    }

    /**
     * Get time until next token is available.
     *
     * @return Duration until next refill, or Duration.ZERO if tokens available
     */
    public Duration timeUntilNextToken() {
        if (tokens > 0) {
            return Duration.ZERO;
        }

        // Calculate how long until next token
        double secondsPerToken = 60.0 / refillRate;
        long secondsSinceLastRefill = Duration.between(lastRefill, Instant.now()).toSeconds();
        long secondsUntilNextToken = (long) Math.ceil(secondsPerToken - secondsSinceLastRefill);

        return Duration.ofSeconds(Math.max(0, secondsUntilNextToken));
    }

    @Override
    public String toString() {
        return String.format(
                "TokenBucket{tokens=%d/%d, refillRate=%d/min}",
                availableTokens(), maxTokens, refillRate);
    }
}
