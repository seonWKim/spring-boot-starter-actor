package io.github.seonwkim.actor.internal.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import io.github.seonwkim.actor.internal.TracedMessage;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import java.util.Map;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;
import org.apache.pekko.serialization.Serializer;
import org.apache.pekko.serialization.Serializers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A traced message keeps its trace fields and its message when it crosses nodes. */
class TracedMessageSerializationTest {

    public record Greeting(String text) implements JsonSerializable {}

    // As the actor system builder parses it: keys holding class names are not paths.
    private final ActorTestKit testKit = ActorTestKit.create(
            ConfigFactory.parseMap(ConfigValueFactory.fromMap(PekkoJackson.withSerializers(Map.of()))));

    @AfterEach
    void shutdown() {
        testKit.shutdownTestKit();
    }

    @Test
    void theTraceAndTheMessageSurviveSerialization() {
        Serialization serialization = SerializationExtension.get(testKit.system());
        TracedMessage sent = new TracedMessage(
                new Greeting("hello"),
                Map.of("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"));

        Serializer serializer = serialization.findSerializerFor(sent);
        byte[] bytes = serialization.serialize(sent).get();
        Object received = serialization
                .deserialize(bytes, serializer.identifier(), Serializers.manifestFor(serializer, sent))
                .get();

        assertThat(serializer).isInstanceOf(TracedMessageSerializer.class);
        TracedMessage traced = (TracedMessage) received;
        assertThat(traced.message()).isEqualTo(new Greeting("hello"));
        assertThat(traced.headers()).isEqualTo(sent.headers());
    }
}
