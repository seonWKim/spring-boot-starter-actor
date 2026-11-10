package io.github.seonwkim.actor.persistence.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.internal.AotHints;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

/**
 * Every class the persistence module's defaults name is in the native image hints Spring AOT collects
 * from {@code aot.factories}: Pekko creates or loads each of them by name.
 */
class PersistenceRuntimeHintsTest {

    private final RuntimeHints hints = AotHints.collect();
    private final Map<String, Object> defaults = new PersistenceModuleSupport().withDefaults(Map.of(), null);

    @Test
    void pekkoCanCreateTheDatabaseProvider() {
        String provider =
                (String) ConfigMaps.child(defaults, "pekko-persistence-jdbc").get("database-provider-fqcn");

        assertThat(RuntimeHintsPredicates.reflection()
                        .onType(TypeReference.of(provider))
                        .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
                .accepts(hints);
    }

    @Test
    void pekkoCanLoadEveryTypeTheSerializationBindingsName() {
        Map<String, Object> actor = ConfigMaps.child(ConfigMaps.child(defaults, "pekko"), "actor");

        assertThat(ConfigMaps.child(actor, "serialization-bindings").keySet())
                .isNotEmpty()
                .allSatisfy(className -> assertThat(
                                RuntimeHintsPredicates.reflection().onType(TypeReference.of(className)))
                        .as(className)
                        .accepts(hints));
    }
}
