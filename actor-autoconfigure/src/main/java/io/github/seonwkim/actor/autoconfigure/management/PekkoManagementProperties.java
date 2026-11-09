package io.github.seonwkim.actor.autoconfigure.management;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Pekko Management auto-configuration, bound to the prefix
 * {@code spring.actor.pekko.management}.
 *
 * <p>This class exists so the key is described in Spring Boot configuration metadata. Whether
 * {@link PekkoManagementAutoConfiguration} activates is still decided by its
 * {@code @ConditionalOnProperty}.
 */
@ConfigurationProperties(prefix = "spring.actor.pekko.management")
public class PekkoManagementProperties {

    /**
     * Whether to start the Pekko Management HTTP server and Cluster Bootstrap when the actor system
     * runs in cluster mode and the Pekko Management libraries are on the classpath.
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
