package io.github.seonwkim.metrics.api;

import java.util.Objects;
import org.apache.pekko.actor.ActorCell;

/**
 * Immutable context of an actor for filtering and tagging.
 *
 * <p>Pekko actor paths follow the structure {@code pekko://<systemName>/<guardian>/<rest>}.
 * The guardian segment ("user", "system", "temp") determines the actor category.
 */
public final class ActorContext {

    private final String path;
    private final String actorClass;
    private final String guardian;

    /**
     * Create an ActorContext directly (for testing and manual construction).
     * The third parameter is ignored — kept for backward compatibility with tests.
     */
    public ActorContext(String path, String actorClass, Object ignored) {
        this(path, actorClass);
    }

    private ActorContext(String path, String actorClass) {
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.actorClass = Objects.requireNonNull(actorClass, "actorClass cannot be null");
        this.guardian = parseGuardian(path);
    }

    /**
     * Extract actor context from a Pekko ActorCell.
     */
    public static ActorContext from(Object actorCell) {
        try {
            ActorCell cell = (ActorCell) actorCell;
            String pathString = cell.self().path().toString();

            // actor() may not be available at all lifecycle points (e.g. pre-init)
            String actorClassName;
            try {
                actorClassName = cell.actor().getClass().getSimpleName();
            } catch (Exception e) {
                actorClassName = "Unknown";
            }

            return new ActorContext(pathString, actorClassName);
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

    /** True if this actor is under the /system guardian (Pekko internal actors). */
    public boolean isSystemActor() {
        return "system".equals(guardian);
    }

    /** True if this actor is under the /temp guardian (short-lived ask-pattern actors). */
    public boolean isTemporaryActor() {
        return "temp".equals(guardian);
    }

    /**
     * Convert to metric tags. Only includes actor.class to avoid high cardinality
     * (actor.path contains unique IDs per instance).
     */
    public Tags toTags() {
        return Tags.of("actor.class", actorClass);
    }

    /**
     * Parse the guardian segment from a Pekko actor path.
     * Path format: {@code pekko://<systemName>/<guardian>/<rest>}
     *
     * @return "user", "system", "temp", or empty string if unparseable
     */
    private static String parseGuardian(String path) {
        int schemeEnd = path.indexOf("://");
        if (schemeEnd < 0) return "";
        int guardianStart = path.indexOf('/', schemeEnd + 3);
        if (guardianStart < 0) return "";
        guardianStart++; // skip the '/'
        int guardianEnd = path.indexOf('/', guardianStart);
        return guardianEnd < 0 ? path.substring(guardianStart) : path.substring(guardianStart, guardianEnd);
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
