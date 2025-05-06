package org.github.seonwkim.core.impl;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.SpringActorSystem;
import org.github.seonwkim.core.SpringActorSystemBuilder;
import org.github.seonwkim.core.RootGuardian;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

public class DefaultSpringActorSystemBuilder implements SpringActorSystemBuilder {

    private String name;
    private Supplier<Behavior<RootGuardian.Command>> supplier;
    private Map<String, Object> configMap = Collections.emptyMap();

    @Override
    public SpringActorSystemBuilder withName(String name) {
        this.name = name;
        return this;
    }

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
    public SpringActorSystem build() {
        final Config config = ConfigFactory.parseMap(ConfigValueFactory.fromMap(configMap))
                                           .withFallback(ConfigFactory.load());

        final ActorSystem<RootGuardian.Command> actorSystem = ActorSystem.create(supplier.get(), name, config);
        final boolean isClusterMode = Objects.equals(config.getString("pekko.actor.provider"), "cluster");

        return new SpringActorSystem(actorSystem, isClusterMode);
    }
}
