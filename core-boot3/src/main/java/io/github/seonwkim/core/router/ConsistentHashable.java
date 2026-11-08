package io.github.seonwkim.core.router;

/**
 * Interface for messages that provide a consistent hash key for routing.
 *
 * <p>Messages implementing this interface can be routed using {@link
 * RoutingStrategy#consistentHashing()} to ensure that messages with the same hash key are always
 * sent to the same worker actor. This enables session affinity and stateful message processing.
 *
 * <p>Example use cases:
 *
 * <ul>
 *   <li>User session management (route all requests for userId=123 to the same worker)
 *   <li>Entity-based processing (route all updates for orderId=456 to the same worker)
 *   <li>Partition key routing (similar to Kafka partition keys)
 * </ul>
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class UserCommand implements ConsistentHashable {
 *     private final String userId;
 *     private final String action;
 *
 *     @Override
 *     public String getConsistentHashKey() {
 *         return userId;  // All commands for same user go to same worker
 *     }
 * }
 * }</pre>
 *
 * @see RoutingStrategy#consistentHashing()
 */
public interface ConsistentHashable {

    /**
     * Returns the hash key used for consistent routing. Messages with the same hash key will
     * always be routed to the same worker actor.
     *
     * <p>The hash key should be:
     *
     * <ul>
     *   <li><strong>Stable:</strong> The same logical message should always return the same key
     *   <li><strong>Well-distributed:</strong> Different entities should have different keys to
     *       avoid hotspots
     *   <li><strong>Non-null:</strong> Never return null
     * </ul>
     *
     * @return The consistent hash key (must not be null)
     */
    String getConsistentHashKey();
}
