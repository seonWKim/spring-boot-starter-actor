package io.github.seonwkim.actor.persistence;

import java.util.Objects;

/**
 * The key of an event-sourced actor's events in the journal: {@code entityType|entityId}. It must be
 * unique across the application and stay the same across restarts, or the actor won't find its
 * events.
 *
 * @param entityType what kind of entity, such as {@code Account}; must not contain {@code |}
 * @param entityId which one, such as the actor id
 */
public record PersistenceId(String entityType, String entityId) {

    /** Separates the entity type from the id in {@link #id()}. */
    public static final String SEPARATOR = "|";

    public PersistenceId {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        if (entityType.isEmpty() || entityType.contains(SEPARATOR)) {
            throw new IllegalArgumentException(
                    "entityType must be non-empty and must not contain '" + SEPARATOR + "': " + entityType);
        }
        if (entityId.isEmpty()) {
            throw new IllegalArgumentException("entityId must be non-empty");
        }
    }

    public static PersistenceId of(String entityType, String entityId) {
        return new PersistenceId(entityType, entityId);
    }

    /** The id stored in the journal: {@code entityType|entityId}. */
    public String id() {
        return entityType + SEPARATOR + entityId;
    }
}
