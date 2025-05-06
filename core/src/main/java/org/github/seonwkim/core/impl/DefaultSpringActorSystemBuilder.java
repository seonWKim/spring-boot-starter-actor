package org.github.seonwkim.core.impl;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.typed.Cluster;
import org.github.seonwkim.core.RootGuardian;
import org.github.seonwkim.core.RootGuardianSupplierWrapper;
import org.github.seonwkim.core.SpringActorSystem;
import org.github.seonwkim.core.SpringActorSystemBuilder;
import org.github.seonwkim.core.shard.ShardEnvelope;
import org.github.seonwkim.core.shard.ShardedActor;
import org.github.seonwkim.core.shard.ShardedActorRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

public class DefaultSpringActorSystemBuilder implements SpringActorSystemBuilder {

    private RootGuardianSupplierWrapper supplier;
    private Map<String, Object> configMap = Collections.emptyMap();
    @Nullable
    private ApplicationEventPublisher applicationEventPublisher;
    private ShardedActorRegistry shardedActorRegistry = ShardedActorRegistry.INSTANCE;
    private final String DEFAULT_SYSTEM_NAME = "system";

    @Override
    public SpringActorSystemBuilder withRootGuardianSupplier(RootGuardianSupplierWrapper supplier) {
        this.supplier = supplier;
        return this;
    }

    @Override
    public SpringActorSystemBuilder withConfig(Map<String, Object> config) {
        this.configMap = config;
        return this;
    }

    @Override
    public SpringActorSystemBuilder withApplicationEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
        return this;
    }

    @Override
    public SpringActorSystemBuilder withShardedActorRegistry(ShardedActorRegistry shardedActorRegistry) {
        this.shardedActorRegistry = shardedActorRegistry;
        return this;
    }

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

    private <T> void initShardedActor(
            ClusterSharding sharding,
            ShardedActor<T> actor
    ) {
        Entity<T, ShardEnvelope<T>> entity =
                Entity.of(actor.typeKey(), actor::create).withMessageExtractor(actor.extractor());
        sharding.init(entity);
    }
}
