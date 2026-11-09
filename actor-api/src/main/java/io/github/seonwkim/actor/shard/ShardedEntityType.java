package io.github.seonwkim.actor.shard;

import java.util.Objects;

/**
 * Names a kind of sharded entity and the messages it accepts. Two nodes route to the same entities
 * when they use the same name, so the name must stay stable across deployments.
 *
 * <pre>{@code
 * public static final ShardedEntityType<Command> TYPE = ShardedEntityType.of(Command.class, "chat-room");
 * }</pre>
 *
 * @param messageType the entity's message type
 * @param name the cluster-wide name of the entity type
 * @param <T> the entity's message type
 */
public record ShardedEntityType<T>(Class<T> messageType, String name) {

    public ShardedEntityType {
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    public static <T> ShardedEntityType<T> of(Class<T> messageType, String name) {
        return new ShardedEntityType<>(messageType, name);
    }
}
