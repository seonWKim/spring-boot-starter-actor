package io.github.seonwkim.actor.autoconfigure.cluster;

import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.github.seonwkim.actor.autoconfigure.ActorProperties;
import io.github.seonwkim.actor.autoconfigure.ConditionalOnActorEnabled;
import io.github.seonwkim.actor.autoconfigure.SchemaScriptInitializer;
import io.github.seonwkim.actor.jdbc.JdbcLeaseStore;
import io.github.seonwkim.actor.jdbc.JdbcPeerRegistry;
import io.github.seonwkim.actor.redis.RedisLeaseStore;
import io.github.seonwkim.actor.redis.RedisPeerRegistry;
import io.github.seonwkim.actor.spi.LeaseStore;
import io.github.seonwkim.actor.spi.PeerRegistry;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * The built-in stores of the SPIs: {@link PeerRegistry} for
 * {@code spring.actor.cluster.discovery.method} and {@link LeaseStore} for
 * {@code spring.actor.cluster.lease.store}, each jdbc or redis. An application's own bean of either
 * type replaces the built-in one. Each store applies only when its module ({@code actor-jdbc},
 * {@code actor-redis}) and the Spring library it uses are on the classpath.
 */
@AutoConfiguration(
        after = ActorConfiguration.class,
        afterName = {
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
        })
@ConditionalOnActorEnabled
public class StoreConfiguration {

    private static final String DISCOVERY_METHOD = "spring.actor.cluster.discovery.method";
    private static final String LEASE_STORE = "spring.actor.cluster.lease.store";

    /** The actor_peer and actor_lease tables on the application's DataSource. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
            name = {
                "io.github.seonwkim.actor.internal.cluster.PekkoClusterSupport",
                "io.github.seonwkim.actor.jdbc.JdbcPeerRegistry",
                "org.springframework.jdbc.datasource.init.DatabasePopulator"
            })
    @Conditional(Jdbc.Selected.class)
    static class Jdbc {

        /** discovery.method or lease.store is jdbc. */
        static class Selected extends AnyNestedCondition {
            Selected() {
                super(ConfigurationPhase.REGISTER_BEAN);
            }

            @ConditionalOnProperty(name = DISCOVERY_METHOD, havingValue = "jdbc")
            static class Discovery {}

            @ConditionalOnProperty(name = LEASE_STORE, havingValue = "jdbc")
            static class Lease {}
        }

        @Bean
        @ConditionalOnProperty(name = DISCOVERY_METHOD, havingValue = "jdbc")
        @ConditionalOnMissingBean(PeerRegistry.class)
        JdbcPeerRegistry jdbcPeerRegistry(DataSource dataSource, ActorTableInitializer tableInitializer) {
            // Taking the initializer makes the tables exist before the store is used.
            return new JdbcPeerRegistry(dataSource);
        }

        @Bean
        @ConditionalOnProperty(name = LEASE_STORE, havingValue = "jdbc")
        @ConditionalOnMissingBean(LeaseStore.class)
        JdbcLeaseStore jdbcLeaseStore(DataSource dataSource, ActorTableInitializer tableInitializer) {
            return new JdbcLeaseStore(dataSource);
        }

        @Bean
        @ConditionalOnMissingBean
        ActorTableInitializer actorTableInitializer(DataSource dataSource, ActorProperties properties) {
            return new ActorTableInitializer(dataSource, properties.getJdbc().getInitializeSchema());
        }

        /** Creates the actor-jdbc tables from its schema file for the database in use. */
        static class ActorTableInitializer extends SchemaScriptInitializer {

            ActorTableInitializer(DataSource dataSource, DatabaseInitializationMode mode) {
                super(dataSource, mode, JdbcPeerRegistry.SCHEMA_LOCATION);
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
    static class Redis {

        @Bean
        @ConditionalOnProperty(name = DISCOVERY_METHOD, havingValue = "redis")
        @ConditionalOnMissingBean(PeerRegistry.class)
        RedisPeerRegistry redisPeerRegistry(RedisConnectionFactory connectionFactory, ActorProperties properties) {
            return new RedisPeerRegistry(
                    connectionFactory, properties.getCluster().getDiscovery().getTtl());
        }

        @Bean
        @ConditionalOnProperty(name = LEASE_STORE, havingValue = "redis")
        @ConditionalOnMissingBean(LeaseStore.class)
        RedisLeaseStore redisLeaseStore(RedisConnectionFactory connectionFactory) {
            return new RedisLeaseStore(connectionFactory);
        }
    }
}
