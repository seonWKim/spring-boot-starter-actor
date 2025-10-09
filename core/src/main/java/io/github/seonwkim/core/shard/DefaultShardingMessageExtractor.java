package io.github.seonwkim.core.shard;

import java.util.zip.CRC32;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;

/**
 * Default implementation of ShardingMessageExtractor for extracting entity IDs and shard IDs from
 * messages. This class is used to determine how messages are routed to shards and entities within a
 * cluster. It extracts entity IDs from ShardEnvelope objects and calculates shard IDs based on a
 * hash of the entity ID.
 *
 * @param <T> The type of messages that the actor can handle
 */
public class DefaultShardingMessageExtractor<T> extends ShardingMessageExtractor<ShardEnvelope<T>, T> {

    private final int numberOfShards;

    /**
     * Creates a new DefaultShardingMessageExtractor with the given number of shards.
     *
     * @param numberOfShards The number of shards to distribute entities across
     */
    public DefaultShardingMessageExtractor(int numberOfShards) {
        if (numberOfShards <= 0) {
            throw new IllegalArgumentException("numberOfShards must be positive");
        }
        this.numberOfShards = numberOfShards;
    }

    /**
     * Extracts the entity ID from a ShardEnvelope.
     *
     * @param envelope The envelope containing the message and entity ID
     * @return The entity ID from the envelope
     */
    @Override
    public String entityId(ShardEnvelope<T> envelope) {
        return envelope.getEntityId();
    }

    /**
     * Calculates the shard ID for a given entity ID. This method uses a hash of the entity ID modulo
     * the number of shards to distribute entities evenly.
     *
     * @param entityId The entity ID
     * @return The shard ID for the entity
     */
    @Override
    public String shardId(String entityId) {
        CRC32 crc32 = new CRC32();
        crc32.update(entityId.getBytes());
        long hash = crc32.getValue();
        if (hash < 0) {
            hash = -hash;
        }
        return String.valueOf(Math.abs(hash % numberOfShards));
    }

    /**
     * Extracts the message payload from a ShardEnvelope.
     *
     * @param envelope The envelope containing the message
     * @return The message payload from the envelope
     */
    @Override
    public T unwrapMessage(ShardEnvelope<T> envelope) {
        return envelope.getPayload();
    }
}
