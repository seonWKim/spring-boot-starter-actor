package io.github.seonwkim.actor;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Imports the actor configuration for {@link EnableActorSupport} by class name, so {@code actor-api}
 * has no compile dependency on {@code actor-autoconfigure}.
 */
final class ActorSupportImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[] {
            "io.github.seonwkim.actor.autoconfigure.ActorConfiguration",
            "io.github.seonwkim.actor.autoconfigure.management.PekkoManagementAutoConfiguration"
        };
    }
}
