package io.github.seonwkim.example.agent;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.github.seonwkim.actor.architecture.ExampleRules;
import io.github.seonwkim.actor.architecture.ModuleMainClasses;

/** Rule 5 of ARCHITECTURE.md: this example uses only the public API. */
@AnalyzeClasses(locations = ModuleMainClasses.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests rules = ArchTests.in(ExampleRules.class);
}
