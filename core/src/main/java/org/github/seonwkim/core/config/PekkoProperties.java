package org.github.seonwkim.core.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

@ConfigurationProperties(prefix = "actor.pekko")
public class PekkoProperties implements EnvironmentAware {

    private final Map<String, Object> config = new HashMap<>();

    @Override
    public void setEnvironment(Environment environment) {
        if (!(environment instanceof ConfigurableEnvironment)) {
            return;
        }

        MutablePropertySources propertySources = ((ConfigurableEnvironment) environment).getPropertySources();

        for (PropertySource<?> source : propertySources) {
            if (source.getSource() instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) source.getSource();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    if (key.startsWith("actor.pekko.") && !key.equals("actor.pekko.enabled")) {
                        String strippedKey = key.substring("actor.pekko.".length());
                        config.put(strippedKey, entry.getValue());
                    }
                }
            }
        }
    }

    public Map<String, Object> getConfig() {
        return Collections.unmodifiableMap(config);
    }
}
