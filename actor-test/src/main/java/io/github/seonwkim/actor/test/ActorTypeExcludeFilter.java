package io.github.seonwkim.actor.test;

import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import java.util.Set;
import org.springframework.boot.test.autoconfigure.filter.StandardAnnotationCustomizableTypeExcludeFilter;

/** Keeps the actor beans in an {@link ActorTest}'s component scan: all of them, or the named ones. */
final class ActorTypeExcludeFilter extends StandardAnnotationCustomizableTypeExcludeFilter<ActorTest> {

    private static final Set<Class<?>> ACTOR_TYPES = Set.of(SpringActorWithContext.class, SpringShardedActor.class);

    private final Set<Class<?>> actors;

    ActorTypeExcludeFilter(Class<?> testClass) {
        super(testClass);
        this.actors = Set.of(getAnnotation().getValue("actors", Class[].class).orElse(new Class<?>[0]));
    }

    @Override
    protected Set<Class<?>> getDefaultIncludes() {
        return actors.isEmpty() ? ACTOR_TYPES : Set.of();
    }

    @Override
    protected Set<Class<?>> getComponentIncludes() {
        return actors;
    }
}
