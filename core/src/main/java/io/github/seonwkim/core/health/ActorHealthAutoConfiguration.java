package io.github.seonwkim.core.health;

import io.github.seonwkim.core.SpringActorSystem;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.pekko.cluster.typed.Cluster;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.health.AdditionalHealthEndpointPath;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.HttpCodeStatusMapper;
import org.springframework.boot.actuate.health.StatusAggregator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Registers Actuator health indicators for the actor system and, in cluster mode, the cluster.
 *
 * <ul>
 *   <li>{@code actorSystem}: {@link ActorSystemHealthIndicator}, always.
 *   <li>{@code actorCluster}: {@link ClusterHealthIndicator}, only when {@code
 *       spring.actor.pekko.actor.provider=cluster}.
 * </ul>
 *
 * <p>Both are added to the {@code readiness} health group whenever that group exists (Kubernetes,
 * or {@code management.endpoint.health.probes.enabled=true}), unless the application sets {@code
 * management.endpoint.health.group.readiness.include} itself; then its list is used as written.
 *
 * <p>Active only with Actuator on the classpath and a {@link SpringActorSystem} bean (from {@code
 * @EnableActorSupport}). Set {@code management.health.actor.enabled=false} to remove both
 * indicators.
 *
 * <p>Registered as a Spring Boot auto-configuration (not through {@code @EnableActorSupport}) so it
 * runs after Boot's health endpoint and availability probe auto-configurations: its readiness
 * wrapper must see the readiness group those create.
 */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration",
            "org.springframework.boot.actuate.autoconfigure.availability.AvailabilityProbesAutoConfiguration"
        })
@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
@ConditionalOnBean(SpringActorSystem.class)
@ConditionalOnProperty(value = "management.health.actor.enabled", matchIfMissing = true)
public class ActorHealthAutoConfiguration {

    /** Health contributor name of {@link ActorSystemHealthIndicator}. */
    public static final String ACTOR_SYSTEM = "actorSystem";

    /** Health contributor name of {@link ClusterHealthIndicator}. */
    public static final String ACTOR_CLUSTER = "actorCluster";

    static final String READINESS_GROUP = "readiness";
    static final String READINESS_INCLUDE_PROPERTY = "management.endpoint.health.group.readiness.include";

    @Bean(name = ACTOR_SYSTEM + "HealthIndicator")
    @ConditionalOnMissingBean(name = ACTOR_SYSTEM + "HealthIndicator")
    public ActorSystemHealthIndicator actorSystemHealthIndicator(SpringActorSystem actorSystem) {
        return new ActorSystemHealthIndicator(actorSystem.getRaw());
    }

    @Bean(name = ACTOR_CLUSTER + "HealthIndicator")
    @ConditionalOnMissingBean(name = ACTOR_CLUSTER + "HealthIndicator")
    @ConditionalOnProperty(value = "spring.actor.pekko.actor.provider", havingValue = "cluster")
    public ClusterHealthIndicator actorClusterHealthIndicator(SpringActorSystem actorSystem) {
        Cluster cluster = actorSystem.getCluster();
        if (cluster == null) {
            throw new IllegalStateException(
                    "spring.actor.pekko.actor.provider=cluster but the actor system is not in cluster mode");
        }
        return new ClusterHealthIndicator(cluster);
    }

    /**
     * Adds the actor contributors to the readiness group by wrapping the {@link HealthEndpointGroups}
     * bean. Registered after Boot's own groups post-processor, so it wraps the groups that already
     * hold the availability probes' readiness group.
     */
    @Bean
    public static BeanPostProcessor actorReadinessGroupPostProcessor(Environment environment) {
        boolean includeSetByApplication = environment.containsProperty(READINESS_INCLUDE_PROPERTY);
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!includeSetByApplication && bean instanceof HealthEndpointGroups) {
                    return new ReadinessWithActorGroups((HealthEndpointGroups) bean);
                }
                return bean;
            }
        };
    }

    /** Health endpoint groups whose readiness group also includes the actor contributors. */
    static final class ReadinessWithActorGroups implements HealthEndpointGroups {

        private final HealthEndpointGroups delegate;

        ReadinessWithActorGroups(HealthEndpointGroups delegate) {
            this.delegate = delegate;
        }

        @Override
        public HealthEndpointGroup getPrimary() {
            return delegate.getPrimary();
        }

        @Override
        public Set<String> getNames() {
            return delegate.getNames();
        }

        @Override
        public HealthEndpointGroup get(String name) {
            HealthEndpointGroup group = delegate.get(name);
            if (group != null && READINESS_GROUP.equals(name)) {
                return new WithActorMembers(group);
            }
            return group;
        }
    }

    /** A health group that also includes the actor contributors. */
    static final class WithActorMembers implements HealthEndpointGroup {

        private static final Set<String> ACTOR_MEMBERS =
                Collections.unmodifiableSet(new HashSet<>(Arrays.asList(ACTOR_SYSTEM, ACTOR_CLUSTER)));

        private final HealthEndpointGroup delegate;

        WithActorMembers(HealthEndpointGroup delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isMember(String name) {
            return ACTOR_MEMBERS.contains(name) || delegate.isMember(name);
        }

        @Override
        public boolean showComponents(SecurityContext securityContext) {
            return delegate.showComponents(securityContext);
        }

        @Override
        public boolean showDetails(SecurityContext securityContext) {
            return delegate.showDetails(securityContext);
        }

        @Override
        public StatusAggregator getStatusAggregator() {
            return delegate.getStatusAggregator();
        }

        @Override
        public HttpCodeStatusMapper getHttpCodeStatusMapper() {
            return delegate.getHttpCodeStatusMapper();
        }

        @Override
        public AdditionalHealthEndpointPath getAdditionalPath() {
            return delegate.getAdditionalPath();
        }
    }
}
