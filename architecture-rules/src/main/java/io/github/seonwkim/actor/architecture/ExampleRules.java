package io.github.seonwkim.actor.architecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Rule 5 of ARCHITECTURE.md, for every module under {@code example/}. */
public final class ExampleRules {

    @ArchTest
    static final ArchRule examplesNeverUseInternal = ArchitectureRules.examplesNeverUseInternal();

    private ExampleRules() {}
}
