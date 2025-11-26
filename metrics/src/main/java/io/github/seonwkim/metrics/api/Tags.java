package io.github.seonwkim.metrics.api;

import java.util.*;

/**
 * Immutable collection of key-value tags for metrics.
 * Tags provide dimensional data for metrics, enabling filtering and aggregation.
 */
public final class Tags implements Iterable<Tag> {

    private static final Tags EMPTY = new Tags(Collections.emptyList());

    private final List<Tag> tags;

    private Tags(List<Tag> tags) {
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
    }

    /**
     * Create empty tags.
     */
    public static Tags empty() {
        return EMPTY;
    }

    /**
     * Create tags from key-value pairs.
     * @param keyValues alternating key-value pairs (must be even length)
     */
    public static Tags of(String... keyValues) {
        if (keyValues.length == 0) {
            return EMPTY;
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must have even length");
        }

        List<Tag> tagList = new ArrayList<>(keyValues.length / 2);
        for (int i = 0; i < keyValues.length; i += 2) {
            tagList.add(new Tag(keyValues[i], keyValues[i + 1]));
        }
        return new Tags(tagList);
    }

    /**
     * Create tags from map.
     */
    public static Tags of(Map<String, String> tags) {
        if (tags.isEmpty()) {
            return EMPTY;
        }
        List<Tag> tagList = new ArrayList<>(tags.size());
        tags.forEach((k, v) -> tagList.add(new Tag(k, v)));
        return new Tags(tagList);
    }

    /**
     * Add a tag, returning a new Tags instance.
     */
    public Tags and(String key, String value) {
        List<Tag> newTags = new ArrayList<>(tags);
        newTags.add(new Tag(key, value));
        return new Tags(newTags);
    }

    /**
     * Merge with another Tags, returning a new Tags instance.
     */
    public Tags and(Tags other) {
        if (other.tags.isEmpty()) {
            return this;
        }
        if (this.tags.isEmpty()) {
            return other;
        }
        List<Tag> newTags = new ArrayList<>(this.tags);
        newTags.addAll(other.tags);
        return new Tags(newTags);
    }

    /**
     * Get tag value by key.
     */
    public Optional<String> get(String key) {
        return tags.stream()
                .filter(t -> t.getKey().equals(key))
                .map(Tag::getValue)
                .findFirst();
    }

    @Override
    public Iterator<Tag> iterator() {
        return tags.iterator();
    }

    public int size() {
        return tags.size();
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tags tags1 = (Tags) o;
        return Objects.equals(tags, tags1.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tags);
    }

    @Override
    public String toString() {
        return "Tags" + tags;
    }
}
