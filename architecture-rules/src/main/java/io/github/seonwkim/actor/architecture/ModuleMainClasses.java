package io.github.seonwkim.actor.architecture;

import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.LocationProvider;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The main classes of the module under test: the directories the Gradle build passes in the
 * {@code architecture.classes} system property. Other modules on the test classpath are not
 * analyzed, so each module checks only its own code.
 */
public final class ModuleMainClasses implements LocationProvider {

    static final String PROPERTY = "architecture.classes";

    @Override
    public Set<Location> get(Class<?> testClass) {
        String paths = System.getProperty(PROPERTY);
        if (paths == null || paths.isBlank()) {
            throw new IllegalStateException(
                    "System property '" + PROPERTY + "' is not set; the Gradle build sets it for every test task");
        }
        return Arrays.stream(paths.split(File.pathSeparator))
                .map(Path::of)
                .filter(path -> path.toFile().isDirectory())
                .map(Location::of)
                .collect(Collectors.toSet());
    }
}
