package org.github.seonwkim.core;

import java.util.HashMap;
import java.util.Map;

public class ConfigUtils {

    public static Map<String, Object> flatten(Map<String, Object> source) {
        return flatten("", source);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> flatten(String prefix, Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String fullKey = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                result.putAll(flatten(fullKey, (Map<String, Object>) value));
            } else {
                result.put(fullKey, value);
            }
        }
        return result;
    }
}
