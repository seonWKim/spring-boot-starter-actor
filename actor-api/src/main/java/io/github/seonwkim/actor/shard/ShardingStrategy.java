package io.github.seonwkim.actor.shard;

/**
 * How entity ids are spread over shards. Every node of the cluster must use the same strategy for
 * an entity type, or messages for one entity reach different shards.
 */
public sealed interface ShardingStrategy {

    /** 100 shards, entity ids hashed with CRC32. */
    int DEFAULT_SHARDS = 100;

    static ShardingStrategy defaults() {
        return hashed(DEFAULT_SHARDS);
    }

    static ShardingStrategy hashed(int numberOfShards) {
        return new Hashed(numberOfShards);
    }

    /**
     * Hashes the entity id with CRC32 into one of {@code numberOfShards} shards. Use roughly ten
     * times the maximum number of nodes.
     */
    record Hashed(int numberOfShards) implements ShardingStrategy {
        public Hashed {
            if (numberOfShards <= 0) {
                throw new IllegalArgumentException("numberOfShards must be positive");
            }
        }
    }
}
