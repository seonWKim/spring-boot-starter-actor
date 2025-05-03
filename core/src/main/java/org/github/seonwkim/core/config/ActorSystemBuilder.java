package org.github.seonwkim.core.config;

import java.util.Map;
import java.util.function.Supplier;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;

public interface ActorSystemBuilder {

    ActorSystemBuilder withName(String name);

    ActorSystemBuilder withRootBehavior(Supplier<Behavior<Void>> behavior);

    ActorSystemBuilder withConfig(Map<String, String> config);

    ActorSystem<Void> build();
}
