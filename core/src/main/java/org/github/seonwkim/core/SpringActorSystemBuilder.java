package org.github.seonwkim.core;

import java.util.Map;
import java.util.function.Supplier;

import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.RootGuardian.Command;
import org.springframework.context.ApplicationEventPublisher;

public interface SpringActorSystemBuilder {

    SpringActorSystemBuilder withRootGuardianSupplier(Supplier<Behavior<Command>> supplier);

    SpringActorSystemBuilder withConfig(Map<String, Object> config);

    SpringActorSystemBuilder withApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher);

    SpringActorSystem build();
}
