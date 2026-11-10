package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.internal.AotHints;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

/** A native image contains every schema file SchemaScriptInitializer may run. */
class SchemaScriptRuntimeHintsTest {

    static Stream<String> schemaFiles() {
        return Stream.of("jdbc", "persistence", "projection")
                .flatMap(module -> Stream.of("h2", "postgresql", "mysql", "mariadb")
                        .map(platform -> "io/github/seonwkim/actor/" + module + "/schema-" + platform + ".sql"));
    }

    @ParameterizedTest
    @MethodSource("schemaFiles")
    void schemaFileIsIncluded(String resource) {
        assertThat(RuntimeHintsPredicates.resource().forResource(resource)).accepts(AotHints.collect());
    }
}
