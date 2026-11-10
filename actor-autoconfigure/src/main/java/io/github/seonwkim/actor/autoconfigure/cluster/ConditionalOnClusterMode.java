package io.github.seonwkim.actor.autoconfigure.cluster;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

/**
 * Applies only in cluster mode: {@code spring.actor.provider=cluster}, or the raw Pekko key
 * {@code spring.actor.pekko.actor.provider=cluster}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnClusterMode.OnClusterMode.class)
public @interface ConditionalOnClusterMode {

    /** Either key set to cluster. */
    class OnClusterMode extends AnyNestedCondition {
        OnClusterMode() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @ConditionalOnProperty(name = "spring.actor.provider", havingValue = "cluster")
        static class Typed {}

        @ConditionalOnProperty(name = "spring.actor.pekko.actor.provider", havingValue = "cluster")
        static class Raw {}
    }
}
