package org.github.seonwkim.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "actor.pekko")
public class PekkoProperties implements EnvironmentAware {

    private static final String CONFIG_PREFIX = "spring.actor.pekko.";
    private static final String TARGET_PREFIX = "pekko.";

    private final Map<String, Object> config = new HashMap<>();

    @Override
    public void setEnvironment(Environment environment) {
        if (!(environment instanceof ConfigurableEnvironment)) {
            return;
        }
        Binder binder = Binder.get(environment);

        Map<String, Object> actorConfig = binder.bind(
                ConfigurationPropertyName.of("spring.actor"),
                Bindable.mapOf(String.class, Object.class)
        ).orElse(Collections.emptyMap());

        Map<String, Object> normalized = normalizeCommaSeparatedLists(actorConfig);
        this.config.putAll(normalized);
    }

    public Map<String, Object> normalizeCommaSeparatedLists(Map<String, Object> input) {
        Map<String, Object> normalized = new HashMap<>();

        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                normalized.put(key, normalizeCommaSeparatedLists((Map<String, Object>) value));
            } else if (value instanceof String) {
                String str = ((String) value).trim();
                if (looksLikeCommaSeparatedList(str)) {
                    List<String> items = Arrays.stream(str.split(","))
                                               .map(String::trim)
                                               .filter(s -> !s.isEmpty())
                                               .collect(Collectors.toList());
                    normalized.put(key, items);
                } else {
                    normalized.put(key, str);
                }
            } else {
                normalized.put(key, value);
            }
        }

        return normalized;
    }

    private boolean looksLikeCommaSeparatedList(String str) {
        return str.contains(",") && !(str.startsWith("[") && str.endsWith("]"));
    }

    public Map<String, Object> getConfig() {
        System.out.println("HELLO!!!!!");
        System.out.println(config);
        return Collections.unmodifiableMap(config);
    }
}
