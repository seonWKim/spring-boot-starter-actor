package io.github.seonwkim.actor.autoconfigure.persistence;

import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.github.seonwkim.actor.autoconfigure.ActorProperties;
import io.github.seonwkim.actor.autoconfigure.ConditionalOnActorEnabled;
import io.github.seonwkim.actor.autoconfigure.SchemaScriptInitializer;
import io.github.seonwkim.actor.persistence.internal.PersistenceModuleSupport;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.context.annotation.Bean;

/**
 * Event-sourced actors ({@code actor-persistence}): creates the journal and snapshot tables on the
 * application's {@code DataSource} per {@code spring.actor.persistence.initialize-schema}. The actor
 * system starts after them ({@link ActorSystemDependsOnDatabaseInitialization}).
 */
@AutoConfiguration(
        after = ActorConfiguration.class,
        afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnActorEnabled
@ConditionalOnClass(
        name = {
            "io.github.seonwkim.actor.persistence.internal.PersistenceModuleSupport",
            "org.springframework.jdbc.datasource.init.DatabasePopulator"
        })
public class PersistenceConfiguration {

    @Bean
    @ConditionalOnMissingBean
    PersistenceTableInitializer actorPersistenceTableInitializer(DataSource dataSource, ActorProperties properties) {
        return new PersistenceTableInitializer(
                dataSource, properties.getPersistence().getInitializeSchema());
    }

    /** Creates the actor-persistence tables from its schema file for the database in use. */
    static class PersistenceTableInitializer extends SchemaScriptInitializer {

        PersistenceTableInitializer(DataSource dataSource, DatabaseInitializationMode mode) {
            super(dataSource, mode, PersistenceModuleSupport.SCHEMA_LOCATION);
        }
    }
}
