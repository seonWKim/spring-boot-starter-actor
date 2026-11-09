package io.github.seonwkim.actor.autoconfigure.persistence;

import io.github.seonwkim.actor.autoconfigure.ActorProperties;
import io.github.seonwkim.actor.persistence.internal.PersistenceModuleSupport;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.jdbc.init.PlatformPlaceholderDatabaseDriverResolver;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Event-sourced actors ({@code actor-persistence}): creates the journal and snapshot tables on the
 * application's {@code DataSource} per {@code spring.actor.persistence.initialize-schema}. The actor
 * system starts after them ({@link ActorSystemDependsOnDatabaseInitialization}).
 */
@Configuration(proxyBeanMethods = false)
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
    static class PersistenceTableInitializer extends DataSourceScriptDatabaseInitializer {

        PersistenceTableInitializer(DataSource dataSource, DatabaseInitializationMode mode) {
            super(dataSource, settings(dataSource, mode));
        }

        private static DatabaseInitializationSettings settings(DataSource dataSource, DatabaseInitializationMode mode) {
            DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
            settings.setMode(mode);
            if (mode != DatabaseInitializationMode.NEVER) {
                // Finding the database's schema file opens a connection: only when it may run.
                settings.setSchemaLocations(new PlatformPlaceholderDatabaseDriverResolver()
                        .resolveAll(dataSource, PersistenceModuleSupport.SCHEMA_LOCATION));
            }
            return settings;
        }
    }
}
