package org.github.seonwkim.core.impl;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.cluster.typed.Cluster;
import org.github.seonwkim.core.RootGuardian;
import org.github.seonwkim.core.SpringActorSystem;
import org.github.seonwkim.core.SpringActorSystemBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

public class DefaultSpringActorSystemBuilder implements SpringActorSystemBuilder {

    private Supplier<Behavior<RootGuardian.Command>> supplier;
    private Map<String, Object> configMap = Collections.emptyMap();
    @Nullable
    private ApplicationEventPublisher applicationEventPublisher;
    private final String DEFAULT_SYSTEM_NAME = "system";

    @Override
    public SpringActorSystemBuilder withRootGuardianSupplier(
            Supplier<Behavior<RootGuardian.Command>> supplier) {
        this.supplier = supplier;
        return this;
    }

    @Override
    public SpringActorSystemBuilder withConfig(Map<String, Object> config) {
        this.configMap = config;
        return this;
    }

    @Override
    public SpringActorSystemBuilder withApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
        return this;
    }

    @Override
    public SpringActorSystem build() {
        final Config config = ConfigFactory.parseMap(ConfigValueFactory.fromMap(configMap))
                                           .withFallback(ConfigFactory.load());
        final String name = config.hasPath("pekko.name") ? config.getString("pekko.name") : DEFAULT_SYSTEM_NAME;

        final ActorSystem<RootGuardian.Command> actorSystem = ActorSystem.create(supplier.get(), name, config);
        final boolean isClusterMode = Objects.equals(config.getString("pekko.actor.provider"), "cluster");

        if (!isClusterMode) {
            return new SpringActorSystem(actorSystem);
        } else {
            if (applicationEventPublisher == null) {
                throw new IllegalArgumentException("ApplicationEventPublisher is not set");
            }
            final Cluster cluster = Cluster.get(actorSystem);
            return new SpringActorSystem(actorSystem, cluster, applicationEventPublisher);
        }
    }
}
