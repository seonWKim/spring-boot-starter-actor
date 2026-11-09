package io.github.seonwkim.actor.metrics.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the actor metrics auto-configuration, bound to the prefix
 * {@code spring.actor.metrics}.
 *
 * <p>This class exists so the key is described in Spring Boot configuration metadata. Whether
 * {@link ActorMetricsAutoConfiguration} activates is still decided by its
 * {@code @ConditionalOnProperty}.
 */
@ConfigurationProperties(prefix = "spring.actor.metrics")
public class ActorMetricsProperties {

    /**
     * Whether to register the actor MetricsRegistry bean that publishes actor metrics to the
     * application's Micrometer MeterRegistry.
     */
    private boolean enabled = true;

    /**
     * How many actor paths get their own actor.path tag on the actor message meters; the rest share
     * actor.path=other. 0 (the default) adds no actor.path tag, so the series count never grows with
     * the number of actors.
     */
    private int pathTagLimit = 0;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPathTagLimit() {
        return pathTagLimit;
    }

    public void setPathTagLimit(int pathTagLimit) {
        this.pathTagLimit = pathTagLimit;
    }
}
