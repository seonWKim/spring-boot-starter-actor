package io.github.seonwkim.actor.internal.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import io.github.seonwkim.actor.internal.delivery.ReliableAck;
import java.util.Map;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;
import org.apache.pekko.serialization.Serializer;
import org.apache.pekko.serialization.Serializers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * A reliable consumer's reply keeps its meaning between nodes, and only an ack uses the ack's
 * manifest, so a node that doesn't know a newer reply can't read it as an ack.
 */
class ReliableAckSerializationTest {

    private final ActorTestKit testKit = ActorTestKit.create(
            ConfigFactory.parseMap(ConfigValueFactory.fromMap(PekkoJackson.withSerializers(Map.of()))));

    @AfterEach
    void shutdown() {
        testKit.shutdownTestKit();
    }

    @ParameterizedTest
    @EnumSource(ReliableAck.class)
    void aReplySurvivesSerialization(ReliableAck reply) {
        Serialization serialization = SerializationExtension.get(testKit.system());
        Serializer serializer = serialization.findSerializerFor(reply);
        String manifest = Serializers.manifestFor(serializer, reply);
        byte[] bytes = serialization.serialize(reply).get();

        assertThat(serialization
                        .deserialize(bytes, serializer.identifier(), manifest)
                        .get())
                .isEqualTo(reply);
        assertThat(manifest.equals("A")).isEqualTo(reply == ReliableAck.ACK);
    }
}
