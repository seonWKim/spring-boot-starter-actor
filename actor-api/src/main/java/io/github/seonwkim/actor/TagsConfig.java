package io.github.seonwkim.actor;

import java.util.Set;

/**
 * Tags attached to an actor. They appear in the {@code pekkoTags} MDC attribute of its log lines,
 * as a comma-separated list.
 *
 * @param tags the tags; empty for none
 */
public record TagsConfig(Set<String> tags) {

    private static final TagsConfig EMPTY = new TagsConfig(Set.of());

    public TagsConfig {
        tags = Set.copyOf(tags);
    }

    /** No tags. */
    public static TagsConfig empty() {
        return EMPTY;
    }

    public static TagsConfig of(String... tags) {
        if (tags == null || tags.length == 0) {
            throw new IllegalArgumentException("Tags must not be null or empty. Use TagsConfig.empty() for no tags.");
        }
        return new TagsConfig(Set.of(tags));
    }

    public static TagsConfig of(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tags set must not be null or empty. Use TagsConfig.empty() for no tags.");
        }
        return new TagsConfig(tags);
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }
}
