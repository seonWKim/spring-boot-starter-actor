package org.github.seonwkim.core;

import java.util.function.Supplier;

import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.RootGuardian.Command;

public class RootGuardianSupplierWrapper {
    private final Supplier<Behavior<RootGuardian.Command>> supplier;

    public RootGuardianSupplierWrapper(Supplier<Behavior<RootGuardian.Command>> supplier) {
        this.supplier = supplier;
    }

    public Supplier<Behavior<Command>> getSupplier() {
        return supplier;
    }
}
