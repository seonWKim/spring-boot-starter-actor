package org.github.seonwkim.core;

import java.util.Map;
import java.util.function.Supplier;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.RootGuardian.Command;

public interface ActorSystemBuilder {

    ActorSystemBuilder withName(String name);

    ActorSystemBuilder withRootGuardianSupplier(Supplier<Behavior<Command>> supplier);

    ActorSystemBuilder withConfig(Map<String, String> config);

    ActorSystem<RootGuardian.Command> build();
}
