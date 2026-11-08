package io.github.seonwkim.actor.router;

/**
 * How a router picks the worker for each message.
 *
 * <pre>{@code
 * SpringRouterBehavior.builder(Command.class, ctx)
 *         .withRoutingStrategy(RoutingStrategy.roundRobin())
 *         .withPoolSize(5)
 *         .withWorkerActors(Worker.class)
 *         .build();
 * }</pre>
 */
public sealed interface RoutingStrategy {

    /** Each worker in turn. */
    static RoutingStrategy roundRobin() {
        return RoundRobin.INSTANCE;
    }

    /** A random worker. */
    static RoutingStrategy random() {
        return Random.INSTANCE;
    }

    /** Every worker gets every message. */
    static RoutingStrategy broadcast() {
        return Broadcast.INSTANCE;
    }

    /**
     * Messages with the same key go to the same worker: the key of a {@link ConsistentHashable}
     * message, otherwise {@code toString()}. 10 virtual nodes per worker.
     */
    static RoutingStrategy consistentHashing() {
        return new ConsistentHashing(ConsistentHashing.DEFAULT_VIRTUAL_NODES_FACTOR);
    }

    /** {@link #consistentHashing()} with {@code virtualNodesFactor} virtual nodes per worker. */
    static RoutingStrategy consistentHashing(int virtualNodesFactor) {
        return new ConsistentHashing(virtualNodesFactor);
    }

    enum RoundRobin implements RoutingStrategy {
        INSTANCE
    }

    enum Random implements RoutingStrategy {
        INSTANCE
    }

    enum Broadcast implements RoutingStrategy {
        INSTANCE
    }

    record ConsistentHashing(int virtualNodesFactor) implements RoutingStrategy {
        public static final int DEFAULT_VIRTUAL_NODES_FACTOR = 10;

        public ConsistentHashing {
            if (virtualNodesFactor < 1) {
                throw new IllegalArgumentException("virtualNodesFactor must be >= 1, got: " + virtualNodesFactor);
            }
        }
    }
}
