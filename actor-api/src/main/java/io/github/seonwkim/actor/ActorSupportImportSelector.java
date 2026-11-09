package io.github.seonwkim.actor;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Imports the actor configuration for {@link EnableActorSupport} by class name, so {@code actor-api}
 * has no compile dependency on {@code actor-autoconfigure}. The cluster and management
 * configurations apply only when their modules are on the classpath ({@code @ConditionalOnClass}).
 */
final class ActorSupportImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[] {
            "io.github.seonwkim.actor.autoconfigure.ActorConfiguration",
            "io.github.seonwkim.actor.autoconfigure.cluster.ClusterConfiguration",
            "io.github.seonwkim.actor.autoconfigure.management.PekkoManagementAutoConfiguration",
            "io.github.seonwkim.actor.autoconfigure.health.ActorHealthConfiguration",
            "io.github.seonwkim.actor.autoconfigure.metrics.ClusterMetricsConfiguration"
        };
    }
}
