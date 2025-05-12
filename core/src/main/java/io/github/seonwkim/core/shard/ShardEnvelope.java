package io.github.seonwkim.core.shard;

/**
 * A wrapper class that associates a message with an entity ID. This class is used to route messages
 * to the correct entity in a sharded actor system. It contains both the entity ID and the message
 * payload.
 *
 * @param <T> The type of the message payload
 */
public class ShardEnvelope<T> {
	private final String entityId;
	private final T payload;

	/**
	 * Creates a new ShardEnvelope with the given entity ID and payload.
	 *
	 * @param entityId The ID of the entity that should receive the message
	 * @param payload The message payload
	 */
	public ShardEnvelope(String entityId, T payload) {
		this.entityId = entityId;
		this.payload = payload;
	}

	/**
	 * Returns the entity ID associated with this envelope.
	 *
	 * @return The entity ID
	 */
	public String getEntityId() {
		return entityId;
	}

	/**
	 * Returns the message payload contained in this envelope.
	 *
	 * @return The message payload
	 */
	public T getPayload() {
		return payload;
	}
}
