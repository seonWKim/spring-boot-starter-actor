package io.github.seonwkim.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.seonwkim.actor.architecture.ModuleMainClasses;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNullApi;
import org.springframework.lang.NonNullFields;

/**
 * Kotlin reads the API's nullability from package defaults: a package without them shows every
 * type as a platform type (no null safety), and {@code @Nullable} alone can't say what is non-null.
 */
class NullabilityDefaultsTest {

    @Test
    void everyApiPackageDeclaresNonNullDefaults() {
        List<String> missing = new ClassFileImporter()
                .importLocations(new ModuleMainClasses().get(getClass())).stream()
                        .map(JavaClass::getPackage)
                        .distinct()
                        .filter(pkg ->
                                !pkg.isAnnotatedWith(NonNullApi.class) || !pkg.isAnnotatedWith(NonNullFields.class))
                        .map(pkg -> pkg.getName())
                        .sorted()
                        .toList();

        assertEquals(List.of(), missing, "packages without @NonNullApi and @NonNullFields in package-info.java");
    }
}
