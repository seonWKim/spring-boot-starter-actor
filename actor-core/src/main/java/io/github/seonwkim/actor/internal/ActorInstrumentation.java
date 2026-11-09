package io.github.seonwkim.actor.internal;

import com.typesafe.config.Config;
import java.util.List;
import org.apache.pekko.actor.setup.ActorSystemSetup;
import org.apache.pekko.actor.setup.Setup;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;

/**
 * Hooks into the actor system the starter builds, without a Java agent: Pekko config (a mailbox
 * type, dispatcher executors), setups those plugins read, and a wrapper around every actor's
 * behavior. Registered as beans (the metrics module's actor meters are one); the system builder
 * applies every one it finds.
 */
public interface ActorInstrumentation {

    /** {@code config} plus what this instrumentation needs. The application's own keys should win. */
    default Config configure(Config config) {
        return config;
    }

    /** {@code setup} plus the setups this instrumentation's Pekko plugins read. */
    default ActorSystemSetup setup(ActorSystemSetup setup) {
        return setup;
    }

    /**
     * {@code behavior}, instrumented, for a new actor of {@code actorClass}. For behaviors the
     * starter compiles, it runs inside the actor's supervision, so it sees failures and restarts.
     */
    default <T> Behavior<T> instrument(Class<?> actorClass, Behavior<T> behavior) {
        return behavior;
    }

    /** The instrumentations an actor system was built with. */
    final class Installed extends Setup {
        private final List<ActorInstrumentation> instrumentations;

        public Installed(List<ActorInstrumentation> instrumentations) {
            this.instrumentations = List.copyOf(instrumentations);
        }

        /** The system's instrumentations; none if it was built without the starter's builder. */
        public static Installed of(ActorSystem<?> system) {
            return system.settings().setup().get(Installed.class).orElseGet(() -> new Installed(List.of()));
        }

        <T> Behavior<T> instrument(Class<?> actorClass, Behavior<T> behavior) {
            Behavior<T> result = behavior;
            for (ActorInstrumentation instrumentation : instrumentations) {
                result = instrumentation.instrument(actorClass, result);
            }
            return result;
        }
    }
}
