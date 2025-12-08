package io.github.seonwkim.metrics.api;

import io.micrometer.core.instrument.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.pekko.actor.ActorCell;

/**
 * Represents the context of an actor for metrics tagging.
 */
public final class ActorContext {

    private final String path;
    private final String actorClass;
    private final Object actorCell;
    private final boolean isSystemActor;
    private final boolean isTemporaryActor;

    public ActorContext(String path, String actorClass, Object actorCell) {
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.actorClass = Objects.requireNonNull(actorClass, "actorClass cannot be null");
        this.actorCell = actorCell;
        this.isSystemActor = path.startsWith("pekko://") && path.contains("/system/");
        this.isTemporaryActor = path.contains("/temp/") || path.contains("$");
    }

    public static ActorContext from(Object actorCell) {
        try {
            ActorCell cell = (ActorCell) actorCell;
            String pathString = cell.self().path().toString();

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

    public boolean isSystemActor() {
        return isSystemActor;
    }

    public boolean isUserActor() {
        return path.startsWith("pekko://") && path.contains("/user/");
    }

    public boolean isTemporaryActor() {
        return isTemporaryActor;
    }

    public List<Tag> toTags() {
        List<Tag> tags = new ArrayList<>(3);
        tags.add(Tag.of("actor.class", actorClass));

        if (isSystemActor) {
            tags.add(Tag.of("actor.system", "true"));
        }

        if (isTemporaryActor) {
            tags.add(Tag.of("actor.temporary", "true"));
        }

        return tags;
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
