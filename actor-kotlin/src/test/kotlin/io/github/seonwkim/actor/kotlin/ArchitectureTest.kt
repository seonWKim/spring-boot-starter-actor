package io.github.seonwkim.actor.kotlin

import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.junit.ArchTests
import io.github.seonwkim.actor.architecture.LibraryRules
import io.github.seonwkim.actor.architecture.ModuleMainClasses

/** The layer rules of ARCHITECTURE.md, applied to this module's main classes. */
@AnalyzeClasses(locations = [ModuleMainClasses::class])
class ArchitectureTest {
    @ArchTest
    val rules: ArchTests = ArchTests.`in`(LibraryRules::class.java)
}
