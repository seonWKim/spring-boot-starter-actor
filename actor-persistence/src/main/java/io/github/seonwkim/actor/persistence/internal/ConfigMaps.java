package io.github.seonwkim.actor.persistence.internal;

import java.util.HashMap;
import java.util.Map;

/** Adds defaults to the nested map the actor system's config is built from, without changing the caller's maps. */
public final class ConfigMaps {

    private ConfigMaps() {}

    public static Map<String, Object> copy(Map<String, Object> config) {
        return new HashMap<>(config);
    }

    /** The map at {@code key} of {@code parent}, replaced by a copy that the caller may change. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> child(Map<String, Object> parent, String key) {
        Object existing = parent.get(key);
        Map<String, Object> copy =
                existing instanceof Map<?, ?> map ? new HashMap<>((Map<String, Object>) map) : new HashMap<>();
        parent.put(key, copy);
        return copy;
    }
}
