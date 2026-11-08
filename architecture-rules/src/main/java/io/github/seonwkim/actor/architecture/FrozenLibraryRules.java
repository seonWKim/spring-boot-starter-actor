package io.github.seonwkim.actor.architecture;

import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rules 2 to 4 of ARCHITECTURE.md for a module that still has violations from before the
 * migration. The rules that public types still break are frozen: the violations recorded in the
 * module's store ({@code src/test/archunit-store}, see {@code archunit.properties}) pass, and any
 * new one fails. The store may only shrink: {@link #frozenViolationsNeverGrow} caps its size with
 * the number in {@code src/test/archunit-store-max-lines.txt}.
 */
public final class FrozenLibraryRules {

    static final Path STORE = Path.of("src/test/archunit-store");
    static final Path MAX_LINES = Path.of("src/test/archunit-store-max-lines.txt");

    @ArchTest
    static final ArchRule nothingDependsOnAutoconfigure = ArchitectureRules.nothingDependsOnAutoconfigure();

    @ArchTest
    static final ArchRule apiAndSpiNeverUseInternal = freeze(ArchitectureRules.apiAndSpiNeverUseInternal());

    @ArchTest
    static final ArchRule onlyInternalUsesPekko = freeze(ArchitectureRules.onlyInternalUsesPekko());

    @ArchTest
    static final ArchRule noPekkoInPublicSignatures = freeze(ArchitectureRules.noPekkoInPublicSignatures());

    private FrozenLibraryRules() {}

    /**
     * Fails when the store holds more violations than the checked-in cap, e.g. after someone
     * refroze it to accept a new violation. When violations are fixed, lower the cap with them.
     */
    @ArchTest
    static void frozenViolationsNeverGrow(JavaClasses classes) {
        long lines = countViolationLines(STORE);
        long max = readMaxLines(MAX_LINES);
        if (lines > max) {
            throw new AssertionError("The ArchUnit freeze store " + STORE.toAbsolutePath() + " has " + lines
                    + " violation lines, more than the cap of " + max + " in " + MAX_LINES
                    + ". Frozen violations may only shrink: fix the new violation instead of refreezing.");
        }
    }

    static long countViolationLines(Path store) {
        try (Stream<Path> files = Files.list(store)) {
            return files.filter(file -> !file.getFileName().toString().equals("stored.rules"))
                    .mapToLong(FrozenLibraryRules::countLines)
                    .sum();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static long readMaxLines(Path file) {
        try {
            List<String> values = Files.readAllLines(file).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
            if (values.size() != 1) {
                throw new IllegalStateException(file + " must hold exactly one number");
            }
            return Long.parseLong(values.get(0));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long countLines(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.filter(line -> !line.isBlank()).count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
