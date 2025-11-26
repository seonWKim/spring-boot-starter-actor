package io.github.seonwkim.metrics.api;

import java.util.Objects;
import org.apache.pekko.actor.ActorCell;

/**
 * Represents the context of an actor for filtering and tagging purposes.
 * Extracted from Pekko ActorCell via reflection.
 */
public final class ActorContext {

    private final String path;
    private final String actorClass;
    private final Object actorCell;

    public ActorContext(String path, String actorClass, Object actorCell) {
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.actorClass = Objects.requireNonNull(actorClass, "actorClass cannot be null");
        this.actorCell = actorCell;
    }

    /**
     * Extract actor context from Pekko ActorCell.
     */
    public static ActorContext from(Object actorCell) {
        try {
            ActorCell cell = (ActorCell) actorCell;

            // Get actor path
            String pathString = cell.self().path().toString();

            // Get actor class directly from the actor instance
            // Note: actor() might not be available at all lifecycle points, fallback to "Unknown"
            String actorClassName;
            try {
                actorClassName = cell.actor().getClass().getSimpleName();
            } catch (Exception e) {
                actorClassName = "Unknown";
            }

            return new ActorContext(pathString, actorClassName, actorCell);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to extract actor context from cell", e);
        }
    }

    public String getPath() {
        return path;
    }

    public String getActorClass() {
        return actorClass;
    }

    public Object getActorCell() {
        return actorCell;
    }

    /**
     * Check if this is a system actor (path starts with /system).
     */
    public boolean isSystemActor() {
        return path.startsWith("pekko://") && path.contains("/system/");
    }

    /**
     * Check if this is a user actor (path starts with /user).
     */
    public boolean isUserActor() {
        return path.startsWith("pekko://") && path.contains("/user/");
    }

    /**
     * Check if this is a temporary actor (contains /temp/ or $).
     */
    public boolean isTemporaryActor() {
        return path.contains("/temp/") || path.contains("$");
    }

    /**
     * Convert to tags for metrics.
     * Only includes actor.class to avoid high cardinality (actor.path has unique IDs).
     */
    public Tags toTags() {
        return Tags.of("actor.class", actorClass);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActorContext that = (ActorContext) o;
        return Objects.equals(path, that.path) && Objects.equals(actorClass, that.actorClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, actorClass);
    }

    @Override
    public String toString() {
        return "ActorContext{path='" + path + "', class='" + actorClass + "'}";
    }
}
