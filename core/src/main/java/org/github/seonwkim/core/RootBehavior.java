package org.github.seonwkim.core;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

public class RootBehavior {
    public static Behavior<Void> behaviorSupplier(BehaviorContext context) {
        return Behaviors.setup(
                actorContext -> {
                    actorContext.getLog().debug("DefaultRootBehavior initializing");

                    // TODO: add necessary beans by using context.registerBean(...);

                    // Root behavior doesn't have to receive message. Let's return empty.
                    return Behaviors.empty();
                }
        );
    }
}
