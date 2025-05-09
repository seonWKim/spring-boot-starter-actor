package io.github.seonwkim.core.impl;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.typed.Cluster;
import io.github.seonwkim.core.RootGuardian;
import io.github.seonwkim.core.RootGuardianSupplierWrapper;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.SpringActorSystemBuilder;
import io.github.seonwkim.core.shard.ShardEnvelope;
import io.github.seonwkim.core.shard.ShardedActor;
import io.github.seonwkim.core.shard.ShardedActorRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * Default implementation of the SpringActorSystemBuilder interface.
 * This class builds SpringActorSystem instances with the configured settings.
 * It supports both local and cluster modes.
 */
public class DefaultSpringActorSystemBuilder implements SpringActorSystemBuilder {

    /** The root guardian supplier wrapper */
    private RootGuardianSupplierWrapper supplier;
    /** The configuration map */
    private Map<String, Object> configMap = Collections.emptyMap();
    /** The Spring application event publisher */
    @Nullable
    private ApplicationEventPublisher applicationEventPublisher;
    /** The sharded actor registry */
    private ShardedActorRegistry shardedActorRegistry = ShardedActorRegistry.INSTANCE;
    /** The default actor system name */
    private final String DEFAULT_SYSTEM_NAME = "system";

    /**
     * Sets the root guardian supplier for the actor system.
     *
     * @param supplier The root guardian supplier wrapper
     * @return This builder for method chaining
     */
    @Override
    public SpringActorSystemBuilder withRootGuardianSupplier(RootGuardianSupplierWrapper supplier) {
        this.supplier = supplier;
        return this;
    }

    /**
     * Sets the configuration for the actor system.
     *
     * @param config The configuration map
     * @return This builder for method chaining
     */
    @Override
    public SpringActorSystemBuilder withConfig(Map<String, Object> config) {
        this.configMap = config;
        return this;
    }

    /**
     * Sets the application event publisher for the actor system.
     * This is required for cluster mode to publish cluster events.
     *
     * @param applicationEventPublisher The Spring application event publisher
     * @return This builder for method chaining
     */
    @Override
    public SpringActorSystemBuilder withApplicationEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
        return this;
    }

    /**
     * Sets the sharded actor registry for the actor system.
     * This is used in cluster mode to register sharded actors.
     *
     * @param shardedActorRegistry The sharded actor registry
     * @return This builder for method chaining
     */
    @Override
    public SpringActorSystemBuilder withShardedActorRegistry(ShardedActorRegistry shardedActorRegistry) {
        this.shardedActorRegistry = shardedActorRegistry;
        return this;
    }

    /**
     * Builds a SpringActorSystem with the configured settings.
     * This method creates either a local or cluster mode actor system based on the configuration.
     * In cluster mode, it initializes all sharded actors from the registry.
     *
     * @return A new SpringActorSystem
     * @throws IllegalArgumentException If in cluster mode and the application event publisher is not set
     */
    @Override
    public SpringActorSystem build() {
        final Config config = ConfigFactory.parseMap(ConfigValueFactory.fromMap(configMap))
                                           .withFallback(ConfigFactory.load());
        final String name = config.hasPath("pekko.name") ? config.getString("pekko.name") : DEFAULT_SYSTEM_NAME;

        final ActorSystem<RootGuardian.Command> actorSystem = ActorSystem.create(supplier.getSupplier().get(),
                                                                                 name, config);
        final boolean isClusterMode = Objects.equals(config.getString("pekko.actor.provider"), "cluster");

        if (!isClusterMode) {
            return new SpringActorSystem(actorSystem);
        } else {
            if (applicationEventPublisher == null) {
                throw new IllegalArgumentException("ApplicationEventPublisher is not set");
            }
            final Cluster cluster = Cluster.get(actorSystem);
            final ClusterSharding clusterSharding = ClusterSharding.get(actorSystem);
            for (ShardedActor actor : shardedActorRegistry.getAll()) {
                initShardedActor(clusterSharding, actor);
            }
            return new SpringActorSystem(
                    actorSystem,
                    cluster,
                    clusterSharding,
                    applicationEventPublisher
            );
        }
    }

    /**
     * Initializes a sharded actor with the given cluster sharding.
     * This method creates an entity for the sharded actor and initializes it with the cluster sharding.
     *
     * @param sharding The cluster sharding
     * @param actor The sharded actor to initialize
     * @param <T> The type of messages that the actor can handle
     */
    private <T> void initShardedActor(
            ClusterSharding sharding,
            ShardedActor<T> actor
    ) {
        Entity<T, ShardEnvelope<T>> entity =
                Entity.of(actor.typeKey(), actor::create).withMessageExtractor(actor.extractor());
        sharding.init(entity);
    }
}
