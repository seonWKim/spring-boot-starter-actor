package io.github.seonwkim.actor.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.github.seonwkim.actor.SpringActorRef;
import java.io.IOException;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Lets messages carry a {@link SpringActorRef} (e.g. {@code AskCommand}'s reply address) across
 * nodes: it is written as the Pekko ref it wraps, in Pekko's own format, and read back as a
 * {@link PekkoActorRef}.
 */
public final class SpringActorRefJacksonModule extends SimpleModule {

    public SpringActorRefJacksonModule() {
        super("SpringActorRefJacksonModule");
        addSerializer(new RefSerializer());
        addDeserializer(SpringActorRef.class, new RefDeserializer());
    }

    @SuppressWarnings("rawtypes")
    private static final class RefSerializer extends StdSerializer<SpringActorRef> {
        RefSerializer() {
            super(SpringActorRef.class);
        }

        @Override
        public void serialize(SpringActorRef value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            provider.findValueSerializer(ActorRef.class).serialize(PekkoActorRef.toPekko(value), gen, provider);
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class RefDeserializer extends StdDeserializer<SpringActorRef> {
        RefDeserializer() {
            super(SpringActorRef.class);
        }

        @Override
        @SuppressWarnings("unchecked")
        public SpringActorRef deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new PekkoActorRef<>(ctxt.readValue(p, ActorRef.class));
        }
    }
}
