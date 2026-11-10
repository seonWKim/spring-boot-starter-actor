package io.github.seonwkim.actor.internal.cluster;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.internal.TracedMessage;
import io.github.seonwkim.actor.internal.delivery.ReliableAck;
import io.github.seonwkim.actor.internal.delivery.ReliableEnvelope;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * GraalVM native image hints for the cluster module: the classes its configuration names (this
 * module's {@code reference.conf} and {@code PekkoJackson}), which Pekko creates by name, and {@link
 * AskCommand}, whose reply address Jackson writes into every ask that crosses nodes.
 */
public final class ClusterRuntimeHints implements RuntimeHintsRegistrar {

    static final List<String> CREATED_BY_NAME = List.of(
            "io.github.seonwkim.actor.internal.cluster.discovery.PeerRegistryServiceDiscovery",
            "io.github.seonwkim.actor.internal.cluster.lease.LeaseStoreLease",
            "io.github.seonwkim.actor.internal.cluster.TracedMessageSerializer",
            "io.github.seonwkim.actor.internal.cluster.ReliableEnvelopeSerializer",
            "org.apache.pekko.serialization.jackson.JacksonJsonSerializer",
            "org.apache.pekko.serialization.jackson.JacksonCborSerializer");

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        // Included by pekko-http-core's reference.conf (Pekko Management).
        hints.resources().registerPattern("pekko-http-version.conf");
        // By name: pekko-discovery comes with Pekko Management, an optional dependency, and a class
        // literal of a type whose superclass is missing would fail this registrar.
        for (String className : CREATED_BY_NAME) {
            hints.reflection().registerType(TypeReference.of(className), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
        hints.reflection().registerType(TracedMessage.class);
        hints.reflection().registerType(ReliableEnvelope.class);
        hints.reflection().registerType(ReliableAck.class);
        hints.reflection()
                .registerType(AskCommand.class, MemberCategory.DECLARED_FIELDS, MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
