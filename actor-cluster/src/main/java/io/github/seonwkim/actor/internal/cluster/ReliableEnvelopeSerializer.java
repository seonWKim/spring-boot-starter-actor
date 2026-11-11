package io.github.seonwkim.actor.internal.cluster;

import io.github.seonwkim.actor.internal.delivery.ReliableAck;
import io.github.seonwkim.actor.internal.delivery.ReliableEnvelope;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorRefResolver;
import org.apache.pekko.actor.typed.javadsl.Adapter;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;
import org.apache.pekko.serialization.Serializer;
import org.apache.pekko.serialization.SerializerWithStringManifest;
import org.apache.pekko.serialization.Serializers;

/**
 * Writes reliable delivery's {@link ReliableEnvelope} and {@link ReliableAck} between nodes: the
 * key and the reply-to ref as strings, and the message inside with whatever serializer it is bound to.
 * An ack is empty; another reply is its name, under its own manifest so a node that doesn't know it
 * drops it (the sender then retries) rather than reading it as an ack.
 */
public final class ReliableEnvelopeSerializer extends SerializerWithStringManifest {

    static final String NAME = "actor-reliable";
    private static final int IDENTIFIER = 1_390_140;
    private static final String ENVELOPE = "E";
    private static final String ACK = "A";
    private static final String REPLY = "R";

    private final ExtendedActorSystem system;

    @Nullable private volatile Serialization serialization;

    @Nullable private volatile ActorRefResolver resolver;

    public ReliableEnvelopeSerializer(ExtendedActorSystem system) {
        this.system = system;
    }

    @Override
    public int identifier() {
        return IDENTIFIER;
    }

    @Override
    public String manifest(Object o) {
        if (o instanceof ReliableAck reply) {
            return reply == ReliableAck.ACK ? ACK : REPLY;
        }
        return ENVELOPE;
    }

    @Override
    public byte[] toBinary(Object o) {
        if (o instanceof ReliableAck reply) {
            return reply == ReliableAck.ACK ? new byte[0] : reply.name().getBytes(StandardCharsets.UTF_8);
        }
        ReliableEnvelope envelope = (ReliableEnvelope) o;
        Object message = envelope.message();
        Serializer serializer = serialization().findSerializerFor(message);
        String manifest = Serializers.manifestFor(serializer, message);
        byte[] payload = serializer.toBinary(message);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(payload.length + 256);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(envelope.idempotencyKey());
            out.writeUTF(resolver().toSerializationFormat(envelope.replyTo()));
            out.writeInt(serializer.identifier());
            out.writeUTF(manifest);
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Object fromBinary(byte[] bytes, String manifest) throws NotSerializableException {
        if (ACK.equals(manifest)) {
            return ReliableAck.ACK;
        }
        if (REPLY.equals(manifest)) {
            try {
                return ReliableAck.valueOf(new String(bytes, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                throw new NotSerializableException("Unknown reliable delivery reply: " + e.getMessage());
            }
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            String key = in.readUTF();
            ActorRef<ReliableAck> replyTo = resolver().resolveActorRef(in.readUTF());
            int serializerId = in.readInt();
            String payloadManifest = in.readUTF();
            byte[] payload = new byte[in.readInt()];
            in.readFully(payload);
            Object message = serialization()
                    .deserialize(payload, serializerId, payloadManifest)
                    .get();
            return new ReliableEnvelope(key, message, replyTo);
        } catch (IOException e) {
            throw new NotSerializableException("Unreadable reliable message: " + e.getMessage());
        }
    }

    // Both looked up on first use: serializers are created while the extensions themselves start.
    private Serialization serialization() {
        Serialization result = serialization;
        if (result == null) {
            result = SerializationExtension.get(system);
            serialization = result;
        }
        return result;
    }

    private ActorRefResolver resolver() {
        ActorRefResolver result = resolver;
        if (result == null) {
            result = ActorRefResolver.get(Adapter.toTyped(system));
            resolver = result;
        }
        return result;
    }
}
