package io.github.seonwkim.actor.autoconfigure.cluster;

import io.github.seonwkim.actor.autoconfigure.ActorProperties;
import io.github.seonwkim.actor.jdbc.JdbcPeerRegistry;
import io.github.seonwkim.actor.redis.RedisPeerRegistry;
import io.github.seonwkim.actor.spi.PeerRegistry;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.jdbc.init.PlatformPlaceholderDatabaseDriverResolver;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * The built-in {@link PeerRegistry} stores for {@code spring.actor.cluster.discovery.method} jdbc
 * and redis. An application's own {@code PeerRegistry} bean replaces them. Each store applies only
 * when its module ({@code actor-jdbc}, {@code actor-redis}) and the Spring library it uses are on
 * the classpath.
 */
@Configuration(proxyBeanMethods = false)
public class PeerRegistryConfiguration {

    /** The actor_peer table on the application's DataSource. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
            name = {
                "io.github.seonwkim.actor.internal.cluster.PekkoClusterSupport",
                "io.github.seonwkim.actor.jdbc.JdbcPeerRegistry",
                "org.springframework.jdbc.datasource.init.DatabasePopulator"
            })
    @ConditionalOnProperty(name = "spring.actor.cluster.discovery.method", havingValue = "jdbc")
    static class Jdbc {

        @Bean
        @ConditionalOnMissingBean(PeerRegistry.class)
        JdbcPeerRegistry jdbcPeerRegistry(DataSource dataSource, PeerTableInitializer tableInitializer) {
            // Taking the initializer makes the table exist before the registry is used.
            return new JdbcPeerRegistry(dataSource);
        }

        @Bean
        @ConditionalOnMissingBean
        PeerTableInitializer peerTableInitializer(DataSource dataSource, ActorProperties properties) {
            return new PeerTableInitializer(
                    dataSource, properties.getCluster().getDiscovery().getJdbc().getInitializeSchema());
        }

        /** Creates the actor_peer table from the actor-jdbc schema file for the database in use. */
        static class PeerTableInitializer extends DataSourceScriptDatabaseInitializer {

            PeerTableInitializer(DataSource dataSource, DatabaseInitializationMode mode) {
                super(dataSource, settings(dataSource, mode));
            }

            private static DatabaseInitializationSettings settings(
                    DataSource dataSource, DatabaseInitializationMode mode) {
                DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
                settings.setMode(mode);
                if (mode != DatabaseInitializationMode.NEVER) {
                    // Finding the database's schema file opens a connection: only when it may run.
                    settings.setSchemaLocations(new PlatformPlaceholderDatabaseDriverResolver()
                            .resolveAll(dataSource, JdbcPeerRegistry.SCHEMA_LOCATION));
                }
                return settings;
            }
        }
    }

    /** Keys with a TTL on the application's RedisConnectionFactory. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
            name = {
                "io.github.seonwkim.actor.internal.cluster.PekkoClusterSupport",
                "io.github.seonwkim.actor.redis.RedisPeerRegistry",
                "org.springframework.data.redis.connection.RedisConnectionFactory"
            })
    @ConditionalOnProperty(name = "spring.actor.cluster.discovery.method", havingValue = "redis")
    static class Redis {

        @Bean
        @ConditionalOnMissingBean(PeerRegistry.class)
        RedisPeerRegistry redisPeerRegistry(RedisConnectionFactory connectionFactory, ActorProperties properties) {
            return new RedisPeerRegistry(
                    connectionFactory, properties.getCluster().getDiscovery().getTtl());
        }
    }
}
