package io.github.seonwkim.actor.autoconfigure.projection;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.github.seonwkim.actor.autoconfigure.ActorProperties;
import io.github.seonwkim.actor.autoconfigure.ConditionalOnActorEnabled;
import io.github.seonwkim.actor.autoconfigure.SchemaScriptInitializer;
import io.github.seonwkim.actor.autoconfigure.persistence.PersistenceConfiguration;
import io.github.seonwkim.actor.projection.SpringProjection;
import io.github.seonwkim.actor.projection.internal.PekkoProjections;
import io.github.seonwkim.actor.projection.internal.ProjectionModuleSupport;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Projections ({@code actor-projection}): creates the offset tables on the application's
 * {@code DataSource} per {@code spring.actor.persistence.initialize-schema}, and starts every
 * {@link SpringProjection} bean when the context starts.
 */
@AutoConfiguration(
        after = {ActorConfiguration.class, PersistenceConfiguration.class},
        afterName = {
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration"
        })
@ConditionalOnActorEnabled
@ConditionalOnClass(
        name = {
            "io.github.seonwkim.actor.projection.internal.PekkoProjections",
            "org.springframework.jdbc.datasource.init.DatabasePopulator"
        })
public class ProjectionConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ProjectionTableInitializer actorProjectionTableInitializer(DataSource dataSource, ActorProperties properties) {
        return new ProjectionTableInitializer(
                dataSource, properties.getPersistence().getInitializeSchema());
    }

    @Bean
    ProjectionsLifecycle actorProjections(
            SpringActorSystem actorSystem,
            ObjectProvider<SpringProjection<?>> projections,
            DataSource dataSource,
            ObjectProvider<PlatformTransactionManager> transactionManager,
            ProjectionTableInitializer tableInitializer) {
        // Taking the initializer makes the offset tables exist before a projection reads them.
        return new ProjectionsLifecycle(
                actorSystem,
                projections.orderedStream().toList(),
                dataSource,
                transactionManager.getIfUnique(() -> new JdbcTransactionManager(dataSource)));
    }

    /** Creates the offset tables of actor-projection from its schema file for the database in use. */
    static class ProjectionTableInitializer extends SchemaScriptInitializer {

        ProjectionTableInitializer(DataSource dataSource, DatabaseInitializationMode mode) {
            super(dataSource, mode, ProjectionModuleSupport.SCHEMA_LOCATION);
        }
    }

    /** Starts the projections once, with the context; they stop with the actor system. */
    static class ProjectionsLifecycle implements SmartLifecycle {

        private final SpringActorSystem actorSystem;
        private final List<SpringProjection<?>> projections;
        private final DataSource dataSource;
        private final PlatformTransactionManager transactionManager;
        private volatile boolean running;

        ProjectionsLifecycle(
                SpringActorSystem actorSystem,
                List<SpringProjection<?>> projections,
                DataSource dataSource,
                PlatformTransactionManager transactionManager) {
            this.actorSystem = actorSystem;
            this.projections = projections;
            this.dataSource = dataSource;
            this.transactionManager = transactionManager;
        }

        @Override
        public synchronized void start() {
            if (running) {
                return;
            }
            for (SpringProjection<?> projection : projections) {
                PekkoProjections.start(actorSystem, projection, dataSource, transactionManager);
            }
            running = true;
        }

        @Override
        public void stop() {
            // Projections are actors of the actor system: they stop when it shuts down.
        }

        @Override
        public boolean isRunning() {
            return running;
        }
    }
}
