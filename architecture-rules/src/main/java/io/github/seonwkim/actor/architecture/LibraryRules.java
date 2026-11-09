package io.github.seonwkim.actor.architecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Rules 2 to 4 of ARCHITECTURE.md for a module that has no violations to freeze. A module's test
 * includes them with {@code ArchTests.in(LibraryRules.class)}.
 */
public final class LibraryRules {

    @ArchTest
    static final ArchRule nothingDependsOnAutoconfigure = ArchitectureRules.nothingDependsOnAutoconfigure();

    @ArchTest
    static final ArchRule apiAndSpiNeverUseInternal = ArchitectureRules.apiAndSpiNeverUseInternal();

    @ArchTest
    static final ArchRule onlyInternalUsesPekko = ArchitectureRules.onlyInternalUsesPekko();

    @ArchTest
    static final ArchRule noPekkoInPublicSignatures = ArchitectureRules.noPekkoInPublicSignatures();

    private LibraryRules() {}
}
