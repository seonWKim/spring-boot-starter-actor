package io.github.seonwkim.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.apache.pekko.actor.typed.ActorTags;
import org.apache.pekko.actor.typed.Props;

/**
 * Type-safe configuration for actor tags.
 *
 * <p>ActorTags are used to logically group actors and add tags to the MDC (Mapped Diagnostic Context)
 * for logging purposes. Tags appear in the {@code pekkoTags} MDC attribute as a comma-separated list,
 * making it easy to filter logs by actor category.
 *
 * <p>Example usage:
 * <pre>{@code
 * // No tags (default)
 * actorSystem.spawn(MyActor.class)
 *     .withId("my-actor")
 *     .spawn();
 *
 * // Single tag
 * actorSystem.spawn(MyActor.class)
 *     .withId("my-actor")
 *     .withTags(TagsConfig.of("worker"))
 *     .spawn();
 *
 * // Multiple tags
 * actorSystem.spawn(MyActor.class)
 *     .withId("my-actor")
 *     .withTags(TagsConfig.of("worker", "high-priority", "cpu-intensive"))
 *     .spawn();
 *
 * // Tags from a set
 * Set<String> tags = Set.of("worker", "backend");
 * actorSystem.spawn(MyActor.class)
 *     .withId("my-actor")
 *     .withTags(TagsConfig.of(tags))
 *     .spawn();
 * }</pre>
 *
 * <p>In logs, tags appear in the MDC:
 * <pre>
 * [INFO] [pekkoSource=pekko://MySystem/user/my-actor] [pekkoTags=worker,high-priority] Processing task
 * </pre>
 *
 * <p>Use tags to categorize actors by:
 * <ul>
 *   <li>Role: "worker", "supervisor", "coordinator"</li>
 *   <li>Priority: "critical", "high-priority", "low-priority"</li>
 *   <li>Service: "order-service", "user-service", "payment-service"</li>
 *   <li>Workload: "cpu-intensive", "io-bound", "memory-intensive"</li>
 *   <li>Environment: "production", "staging", "development"</li>
 * </ul>
 *
 * @see MailboxConfig for mailbox configuration
 * @see DispatcherConfig for dispatcher configuration
 */
public abstract class TagsConfig {

    /**
     * No tags. This is the default when tags are not specified.
     *
     * @return A tags configuration with no tags
     */
    public static TagsConfig empty() {
        return NoTags.INSTANCE;
    }

    /**
     * Create tags from one or more tag strings.
     *
     * @param tags One or more tag strings
     * @return A tags configuration with the specified tags
     * @throws IllegalArgumentException if tags is null or empty
     */
    public static TagsConfig of(String... tags) {
        if (tags == null || tags.length == 0) {
            throw new IllegalArgumentException(
                "Tags must not be null or empty. Use TagsConfig.empty() for no tags."
            );
        }
        return new WithTags(Set.of(tags));
    }

    /**
     * Create tags from a set of tag strings.
     *
     * @param tags A set of tag strings (must not be empty)
     * @return A tags configuration with the specified tags
     * @throws IllegalArgumentException if tags is null or empty
     */
    public static TagsConfig of(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException(
                "Tags set must not be null or empty. Use TagsConfig.empty() for no tags."
            );
        }
        return new WithTags(tags);
    }

    // Package-private constructor to prevent external subclassing
    TagsConfig() {}

    /**
     * Returns true if this configuration has no tags.
     *
     * @return true if empty, false otherwise
     */
    public abstract boolean isEmpty();

    /**
     * Returns the set of tags in this configuration.
     * Returns an empty set if no tags are configured.
     *
     * @return An immutable set of tags
     */
    public abstract Set<String> getTags();

    /**
     * Applies this tags configuration to the given Props.
     * If no tags are configured, returns the props unchanged.
     *
     * @param props The Props to apply tags configuration to
     * @return Props with tags configuration applied
     */
    public abstract Props applyToProps(Props props);

    /**
     * Returns a description of this tags configuration for debugging.
     *
     * @return A human-readable description
     */
    public abstract String describe();

    // ========== Inner Classes ==========

    /**
     * No tags configuration - the default when tags are not specified.
     */
    private static final class NoTags extends TagsConfig {
        static final NoTags INSTANCE = new NoTags();

        private NoTags() {}

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Set<String> getTags() {
            return Collections.emptySet();
        }

        @Override
        public Props applyToProps(Props props) {
            // No tags to apply
            return props;
        }

        @Override
        public String describe() {
            return "No tags";
        }

        @Override
        public String toString() {
            return "TagsConfig.empty()";
        }
    }

    /**
     * Tags configuration with one or more tags.
     */
    private static final class WithTags extends TagsConfig {
        private final Set<String> tags;

        WithTags(Set<String> tags) {
            // Create immutable copy
            this.tags = Set.copyOf(tags);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public Set<String> getTags() {
            return tags;
        }

        @Override
        public Props applyToProps(Props props) {
            // Combine ActorTags with existing props using withNext
            // This allows tags to be applied along with dispatcher and mailbox configurations
            return ActorTags.create(tags).withNext(props);
        }

        @Override
        public String describe() {
            return "Tags: " + String.join(", ", tags);
        }

        @Override
        public String toString() {
            return "TagsConfig.of(" + String.join(", ", tags) + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WithTags withTags = (WithTags) o;
            return tags.equals(withTags.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tags);
        }
    }
}
