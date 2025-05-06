package org.github.seonwkim.core.impl;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.ActorSystemBuilder;
import org.github.seonwkim.core.RootGuardian;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

public class DefaultActorSystemBuilder implements ActorSystemBuilder {

    private String name;
    private Supplier<Behavior<RootGuardian.Command>> supplier;
    private Map<String, String> configMap = Collections.emptyMap();

    @Override
    public ActorSystemBuilder withName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public ActorSystemBuilder withRootGuardianSupplier(Supplier<Behavior<RootGuardian.Command>> supplier) {
        this.supplier = supplier;
        return this;
    }

    @Override
    public ActorSystemBuilder withConfig(Map<String, String> config) {
        this.configMap = config;
        return this;
    }

    @Override
    public ActorSystem<RootGuardian.Command> build() {
        Config config = ConfigFactory.parseMap(ConfigValueFactory.fromMap(configMap))
                                     .withFallback(ConfigFactory.load());

        return ActorSystem.create(supplier.get(), name, config);
    }
}
