package io.github.seonwkim.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * Configuration properties for Pekko actor system. This class binds properties with the prefix
 * "spring.actor" to a map and normalizes comma-separated lists.
 */
public class PekkoProperties implements EnvironmentAware {
	private final Map<String, Object> config = new HashMap<>();

	/**
	 * Sets the environment and binds properties with the prefix "spring.actor" to the config map.
	 * This method is called by Spring to inject the environment.
	 *
	 * @param environment The Spring environment
	 */
	@Override
	public void setEnvironment(Environment environment) {
		if (!(environment instanceof ConfigurableEnvironment)) {
			return;
		}
		Binder binder = Binder.get(environment);

		Map<String, Object> actorConfig =
				binder
						.bind(
								ConfigurationPropertyName.of("spring.actor"),
								Bindable.mapOf(String.class, Object.class))
						.orElse(Collections.emptyMap());

		Map<String, Object> normalized = normalizeCommaSeparatedLists(actorConfig);
		this.config.putAll(normalized);
	}

	/**
	 * Normalizes comma-separated lists in the input map. If a string value contains commas, it is
	 * split into a list of strings. This method recursively processes nested maps.
	 *
	 * @param input The input map to normalize
	 * @return A new map with normalized values
	 */
	public Map<String, Object> normalizeCommaSeparatedLists(Map<String, Object> input) {
		Map<String, Object> normalized = new HashMap<>();

		for (Map.Entry<String, Object> entry : input.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();

			if (value instanceof Map) {
				// Safe cast: instanceof check ensures value is Map<String, Object>
				normalized.put(key, normalizeCommaSeparatedLists((Map<String, Object>) value));
			} else if (value instanceof String) {
				// Safe cast: instanceof check ensures value is String
				String str = ((String) value).trim();
				if (looksLikeCommaSeparatedList(str)) {
					List<String> items =
							Arrays.stream(str.split(","))
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

	/**
	 * Returns an unmodifiable view of the configuration map.
	 *
	 * @return An unmodifiable map containing the configuration properties
	 */
	public Map<String, Object> getConfig() {
		return Collections.unmodifiableMap(config);
	}
}
