package io.github.seonwkim.actor.internal.cluster;

import io.github.seonwkim.actor.internal.TracedMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;
import org.apache.pekko.serialization.Serializer;
import org.apache.pekko.serialization.SerializerWithStringManifest;
import org.apache.pekko.serialization.Serializers;

/**
 * Writes a {@link TracedMessage} between nodes: its trace fields (W3C {@code traceparent} and the
 * like) as strings, and the message inside with whatever serializer the message is bound to.
 */
public final class TracedMessageSerializer extends SerializerWithStringManifest {

    static final String NAME = "actor-traced";
    private static final int IDENTIFIER = 1_390_139;

    private final ExtendedActorSystem system;

    @Nullable private volatile Serialization serialization;

    public TracedMessageSerializer(ExtendedActorSystem system) {
        this.system = system;
    }

    @Override
    public int identifier() {
        return IDENTIFIER;
    }

    @Override
    public String manifest(Object o) {
        return "";
    }

    @Override
    public byte[] toBinary(Object o) {
        TracedMessage traced = (TracedMessage) o;
        Object message = traced.message();
        Serializer serializer = serialization().findSerializerFor(message);
        String manifest = Serializers.manifestFor(serializer, message);
        byte[] payload = serializer.toBinary(message);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(payload.length + 128);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(traced.headers().size());
            for (Map.Entry<String, String> header : traced.headers().entrySet()) {
                out.writeUTF(header.getKey());
                out.writeUTF(header.getValue());
            }
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
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            int count = in.readInt();
            Map<String, String> headers = new HashMap<>(count * 2);
            for (int i = 0; i < count; i++) {
                headers.put(in.readUTF(), in.readUTF());
            }
            int serializerId = in.readInt();
            String payloadManifest = in.readUTF();
            byte[] payload = new byte[in.readInt()];
            in.readFully(payload);
            Object message = serialization()
                    .deserialize(payload, serializerId, payloadManifest)
                    .get();
            return new TracedMessage(message, headers);
        } catch (IOException e) {
            throw new NotSerializableException("Unreadable traced message: " + e.getMessage());
        }
    }

    private Serialization serialization() {
        // Looked up on first use: serializers are created while the extension itself starts.
        Serialization result = serialization;
        if (result == null) {
            result = SerializationExtension.get(system);
            serialization = result;
        }
        return result;
    }
}
