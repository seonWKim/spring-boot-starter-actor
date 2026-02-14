package io.github.seonwkim.metrics.autoconfigure;

import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.micrometer.MicrometerMetricsRegistryBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for actor metrics.
 *
 * <p>Registers MetricsRegistry when:
 * <ul>
 *   <li>MeterRegistry is present (from spring-boot-starter-actuator or micrometer-registry-*)
 *   <li>Metrics are enabled (spring.actor.metrics.enabled=true, default)
 *   <li>No custom MetricsRegistry bean is defined
 * </ul>
 *
 * <p>Ensures actorMetricsRegistry is created before the core's actorSystem via a
 * BeanDefinitionRegistryPostProcessor that adds depends-on. This guarantees
 * {@link io.github.seonwkim.metrics.agent.MetricsAgent#setRegistry} is called before
 * ActorSystem creation (which loads instrumented classes).
 *
 * <p>Note: {@code @ConditionalOnBean} is intentionally on the {@code @Bean} method
 * (not the class) so the condition is evaluated during bean creation rather than
 * configuration class processing. This ensures the MeterRegistry bean from
 * {@link CompositeMeterRegistryAutoConfiguration} is visible regardless of whether
 * this class is loaded via {@code spring.factories} or {@code @Import}.
 *
 * <p>Requires the metrics agent at JVM startup:
 * <pre>java -javaagent:spring-boot-starter-actor-metrics-{version}-agent.jar -jar app.jar</pre>
 */
@Configuration
@AutoConfigureAfter(CompositeMeterRegistryAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(name = "spring.actor.metrics.enabled", havingValue = "true", matchIfMissing = true)
public class ActorMetricsAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(MetricsRegistry.class)
    public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
        return MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry).build();
    }

    /**
     * Adds depends-on="actorMetricsRegistry" to the core's actorSystem bean so the registry
     * is created before the ActorSystem.
     */
    @Bean
    public static ActorMetricsBeanDefinitionRegistryPostProcessor actorMetricsBeanDefinitionRegistryPostProcessor() {
        return new ActorMetricsBeanDefinitionRegistryPostProcessor();
    }
}
