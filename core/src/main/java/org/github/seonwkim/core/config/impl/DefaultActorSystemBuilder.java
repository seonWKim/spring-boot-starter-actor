package org.github.seonwkim.core.config.impl;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.github.seonwkim.core.config.ActorSystemBuilder;
import org.github.seonwkim.core.config.ConfigUtils;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class DefaultActorSystemBuilder implements ActorSystemBuilder {

    private String name = "spring-boot-actor-system";
    private Supplier<Behavior<Void>> behaviorSupplier = Behaviors::empty;
    private Map<String, Object> configMap = Collections.emptyMap();

    @Override
    public ActorSystemBuilder withName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public ActorSystemBuilder withRootBehavior(Supplier<Behavior<Void>> behaviorSupplier) {
        this.behaviorSupplier = behaviorSupplier;
        return this;
    }

    @Override
    public ActorSystemBuilder withConfig(Map<String, Object> config) {
        this.configMap = config;
        return this;
    }

    @Override
    public ActorSystem<Void> build() {
        Config config = ConfigFactory.parseMap(ConfigUtils.flatten(configMap))
                .withFallback(ConfigFactory.load());

        return ActorSystem.create(behaviorSupplier.get(), name, config);
    }
}
