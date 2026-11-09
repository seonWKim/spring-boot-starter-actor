package io.github.seonwkim.actor.autoconfigure.persistence;

import io.github.seonwkim.actor.SpringActorSystem;
import java.util.Set;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitializationDetector;
import org.springframework.util.ClassUtils;

/**
 * With {@code actor-persistence} on the classpath, the actor system starts after the database is
 * initialized (the journal tables, Flyway, Liquibase): event-sourced actors may recover as soon as
 * they are spawned. Registered in {@code META-INF/spring.factories}.
 */
public class ActorSystemDependsOnDatabaseInitialization implements DependsOnDatabaseInitializationDetector {

    private static final String PERSISTENCE_MODULE =
            "io.github.seonwkim.actor.persistence.internal.PersistenceModuleSupport";

    @Override
    public Set<String> detect(ConfigurableListableBeanFactory beanFactory) {
        if (!ClassUtils.isPresent(PERSISTENCE_MODULE, getClass().getClassLoader())) {
            return Set.of();
        }
        return Set.of(beanFactory.getBeanNamesForType(SpringActorSystem.class, true, false));
    }
}
