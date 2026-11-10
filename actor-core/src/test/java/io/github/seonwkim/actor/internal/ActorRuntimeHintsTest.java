package io.github.seonwkim.actor.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.serialization.CborSerializable;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

/** The native image hints Spring AOT collects from {@code aot.factories}, as a native build would. */
class ActorRuntimeHintsTest {

    private final RuntimeHints hints = AotHints.collect();

    @ParameterizedTest
    @ValueSource(strings = {"reference.conf", "version.conf"})
    void pekkoConfigFilesAreIncluded(String resource) {
        assertThat(RuntimeHintsPredicates.resource().forResource(resource)).accepts(hints);
    }

    @Test
    void pekkoCanCreateTheExecutorOfABeanBackedDispatcher() {
        assertThat(RuntimeHintsPredicates.reflection()
                        .onType(SpringExecutorConfigurator.class)
                        .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
                .accepts(hints);
    }

    @ParameterizedTest
    @ValueSource(classes = {JsonSerializable.class, CborSerializable.class})
    void pekkoCanLoadTheSerializationMarkersByName(Class<?> marker) {
        assertThat(RuntimeHintsPredicates.reflection().onType(marker)).accepts(hints);
    }
}
