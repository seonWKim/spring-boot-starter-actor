package org.github.seonwkim.core;

import java.util.Map;
import java.util.function.Supplier;

import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.RootGuardian.Command;

public interface SpringActorSystemBuilder {

    SpringActorSystemBuilder withName(String name);

    SpringActorSystemBuilder withRootGuardianSupplier(Supplier<Behavior<Command>> supplier);

    SpringActorSystemBuilder withConfig(Map<String, Object> config);

    SpringActorSystem build();
}
