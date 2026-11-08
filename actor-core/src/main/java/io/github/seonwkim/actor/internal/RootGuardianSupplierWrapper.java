package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.internal.RootGuardian.Command;
import java.util.function.Supplier;
import org.apache.pekko.actor.typed.Behavior;

/**
 * A wrapper for a supplier of RootGuardian behaviors. This class is used to inject a supplier of
 * RootGuardian behaviors into the actor system.
 */
public class RootGuardianSupplierWrapper {
    private final Supplier<Behavior<RootGuardian.Command>> supplier;

    /**
     * Creates a new RootGuardianSupplierWrapper with the given supplier.
     *
     * @param supplier The supplier of RootGuardian behaviors
     */
    public RootGuardianSupplierWrapper(Supplier<Behavior<RootGuardian.Command>> supplier) {
        this.supplier = supplier;
    }

    /**
     * Returns the supplier of RootGuardian behaviors.
     *
     * @return The supplier of RootGuardian behaviors
     */
    public Behavior<Command> get() {
        return supplier.get();
    }
}
