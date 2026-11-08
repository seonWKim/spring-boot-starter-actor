package io.github.seonwkim.core.router.strategy;

import io.github.seonwkim.core.router.ConsistentHashable;
import io.github.seonwkim.core.router.RoutingStrategy;
import org.apache.pekko.actor.typed.javadsl.PoolRouter;

/**
 * Consistent Hashing routing strategy ensures messages with the same hash key always go to the
 * same worker, enabling session affinity and stateful processing.
 *
 * <p>This strategy uses a consistent hashing algorithm to map hash keys to workers. When workers
 * are added or removed, only a small portion of keys are remapped, providing stability.
 *
 * <p>Hash Key Extraction:
 *
 * <ul>
 *   <li>If message implements {@link ConsistentHashable}, uses {@code getConsistentHashKey()}
 *   <li>Otherwise, falls back to {@code message.toString()}
 * </ul>
 *
 * <p>Virtual Nodes Factor:
 *
 * <ul>
 *   <li>Higher values (e.g., 10) provide better distribution but use more memory
 *   <li>Lower values (e.g., 1) use less memory but may have hotspots
 *   <li>Default: 10 (good balance for most use cases)
 * </ul>
 *
 * <p>Best for:
 *
 * <ul>
 *   <li>User session management (same user → same worker)
 *   <li>Entity-based processing (same orderId → same worker)
 *   <li>Stateful message processing
 *   <li>Cache locality optimization
 * </ul>
 *
 * @see RoutingStrategy#consistentHashing()
 * @see ConsistentHashable
 */
public final class ConsistentHashingRoutingStrategy implements RoutingStrategy {

    private final int virtualNodesFactor;

    /**
     * Creates a Consistent Hashing routing strategy with the default virtual nodes factor of 10.
     */
    public ConsistentHashingRoutingStrategy() {
        this(10); // Default virtual nodes factor
    }

    /**
     * Creates a Consistent Hashing routing strategy with a custom virtual nodes factor.
     *
     * @param virtualNodesFactor Number of virtual nodes per worker (higher = better distribution,
     *     more memory)
     */
    public ConsistentHashingRoutingStrategy(int virtualNodesFactor) {
        if (virtualNodesFactor < 1) {
            throw new IllegalArgumentException("virtualNodesFactor must be >= 1, got: " + virtualNodesFactor);
        }
        this.virtualNodesFactor = virtualNodesFactor;
    }

    @Override
    public String getName() {
        return "ConsistentHashing";
    }

    @Override
    public <T> PoolRouter<T> applyToPool(PoolRouter<T> poolRouter) {
        return poolRouter.withConsistentHashingRouting(virtualNodesFactor, this::extractHashKey);
    }

    /**
     * Extracts the hash key from a message. If the message implements {@link ConsistentHashable},
     * uses the provided key. Otherwise, falls back to {@code toString()}.
     */
    private <T> String extractHashKey(T message) {
        if (message instanceof ConsistentHashable) {
            String key = ((ConsistentHashable) message).getConsistentHashKey();
            if (key == null) {
                throw new IllegalStateException(
                        "ConsistentHashable.getConsistentHashKey() returned null for message: " + message);
            }
            return key;
        }
        // Fallback: use toString() for non-ConsistentHashable messages
        return message.toString();
    }

    @Override
    public String toString() {
        return "ConsistentHashingRoutingStrategy(virtualNodes=" + virtualNodesFactor + ")";
    }

    public int getVirtualNodesFactor() {
        return virtualNodesFactor;
    }
}
