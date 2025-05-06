package org.github.seonwkim.core;

import java.util.*;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

@ConfigurationProperties(prefix = "actor.pekko")
public class PekkoProperties implements EnvironmentAware {

    private static final String CONFIG_PREFIX = "actor.pekko.";
    private static final String TARGET_PREFIX = "pekko.";

    private final Map<String, Object> config = new HashMap<>();

    @Override
    public void setEnvironment(Environment environment) {
        if (!(environment instanceof ConfigurableEnvironment)) return;

        MutablePropertySources propertySources = ((ConfigurableEnvironment) environment).getPropertySources();

        for (PropertySource<?> source : propertySources) {
            Object rawSource = source.getSource();
            if (rawSource instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) rawSource;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    if (key.startsWith(CONFIG_PREFIX) && !key.equals(CONFIG_PREFIX + "enabled")) {
                        String subKey = key.substring(CONFIG_PREFIX.length()); // e.g. remote.artery.port
                        Object value = entry.getValue();
                        insertNestedValue(config, TARGET_PREFIX + subKey, value);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void insertNestedValue(Map<String, Object> root, String fullKey, Object value) {
        String[] parts = fullKey.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new HashMap<>());
        }
        current.put(parts[parts.length - 1], value);
    }

    public Map<String, Object> getConfig() {
        return Collections.unmodifiableMap(config);
    }
}
