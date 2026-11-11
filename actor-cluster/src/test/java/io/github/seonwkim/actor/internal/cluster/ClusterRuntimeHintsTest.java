package io.github.seonwkim.actor.internal.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.internal.AotHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

/**
 * Every class the cluster module's configuration names is in the native image hints Spring AOT
 * collects from {@code aot.factories}: Pekko creates or loads each of them by name.
 */
class ClusterRuntimeHintsTest {

    private final RuntimeHints hints = AotHints.collect();

    @Test
    void pekkoCanCreateEveryClassTheClusterConfigurationNames() {
        Config reference = ConfigFactory.defaultReference();
        List<String> created = new ArrayList<>(actorConfig("serializers").values());
        created.add(reference.getString("pekko.discovery.spring-actor-peer-registry.class"));
        created.add(reference.getString("spring-actor.lease.lease-class"));
        created.add(PekkoClusterSupport.BOUND_PORT_BOOTSTRAP_ROUTES);

        assertThat(created).isNotEmpty().allSatisfy(className -> assertThat(RuntimeHintsPredicates.reflection()
                        .onType(TypeReference.of(className))
                        .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
                .as(className)
                .accepts(hints));
    }

    @Test
    void pekkoCanLoadEveryTypeTheSerializationBindingsName() {
        assertThat(actorConfig("serialization-bindings").keySet()).isNotEmpty().allSatisfy(className -> assertThat(
                        RuntimeHintsPredicates.reflection().onType(TypeReference.of(className)))
                .as(className)
                .accepts(hints));
    }

    @Test
    void jacksonCanReadAndWriteTheReplyAddressOfAnAsk() {
        assertThat(RuntimeHintsPredicates.reflection().onField(AskCommand.class, "replyTo"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                        .onMethod(AskCommand.class, "getReplyTo")
                        .invoke())
                .accepts(hints);
    }

    @Test
    void pekkoManagementVersionFileIsIncluded() {
        assertThat(RuntimeHintsPredicates.resource().forResource("pekko-http-version.conf"))
                .accepts(hints);
    }

    /** {@code pekko.actor.<section>} of the defaults the starter adds to an empty configuration. */
    @SuppressWarnings("unchecked")
    private static Map<String, String> actorConfig(String section) {
        Map<String, Object> pekko =
                (Map<String, Object>) PekkoJackson.withSerializers(Map.of()).get("pekko");
        Map<String, Object> actor = (Map<String, Object>) pekko.get("actor");
        return (Map<String, String>) actor.get(section);
    }
}
