package io.github.seonwkim.core;

import io.github.seonwkim.core.RootGuardian.Command;
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
    public Supplier<Behavior<Command>> getSupplier() {
        return supplier;
    }
}
