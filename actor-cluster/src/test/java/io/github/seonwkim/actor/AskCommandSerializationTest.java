package io.github.seonwkim.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import io.github.seonwkim.actor.serialization.CborSerializable;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import org.apache.pekko.actor.typed.javadsl.Adapter;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;
import org.apache.pekko.serialization.Serializer;
import org.apache.pekko.serialization.Serializers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;

/**
 * An ask's reply address and its fail-fast flag survive the serializers that carry commands between
 * nodes.
 */
@SpringBootTest(classes = {ActorConfiguration.class, AskCommandSerializationTest.TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AskCommandSerializationTest {

    @Autowired
    private SpringActorSystem actorSystem;

    @Test
    void replyToAndFailFastSurviveJson() {
        assertAskFieldsSurvive(new JsonAsk("hello"), "json");
    }

    @Test
    void replyToAndFailFastSurviveCbor() {
        assertAskFieldsSurvive(new CborAsk("hello"), "cbor");
    }

    private void assertAskFieldsSurvive(AskCommand<String> command, String id) {
        SpringActorHandle<String> replyTo =
                actorSystem.actor(SinkActor.class).withId(id).spawnAndWait();
        AskCommand<?> back = roundTrip(command.copyForAsk(replyTo, true));

        assertEquals(replyTo, back.getReplyTo());
        assertTrue(back.failsFast(), "an entity on another node must still fail the ask fast");
    }

    private AskCommand<?> roundTrip(AskCommand<?> command) {
        Serialization serialization = SerializationExtension.get(Adapter.toClassic(PekkoActorSystem.raw(actorSystem)));
        Serializer serializer = serialization.findSerializerFor(command);
        byte[] bytes = serialization.serialize(command).get();
        return (AskCommand<?>) serialization
                .deserialize(bytes, serializer.identifier(), Serializers.manifestFor(serializer, command))
                .get();
    }

    public static class JsonAsk extends AskCommand<String> implements JsonSerializable {
        public String text;

        public JsonAsk() {}

        JsonAsk(String text) {
            this.text = text;
        }
    }

    public static class CborAsk extends AskCommand<String> implements CborSerializable {
        public String text;

        public CborAsk() {}

        CborAsk(String text) {
            this.text = text;
        }
    }

    static class SinkActor implements SpringActor<String> {
        @Override
        public SpringActorBehavior<String> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(String.class, actorContext)
                    .onMessage(String.class, (ctx, msg) -> SpringActorBehavior.same())
                    .build();
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        SinkActor sinkActor() {
            return new SinkActor();
        }
    }
}
