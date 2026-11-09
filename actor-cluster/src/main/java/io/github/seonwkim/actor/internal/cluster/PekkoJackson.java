package io.github.seonwkim.actor.internal.cluster;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.pekko.serialization.jackson.JacksonObjectMapperFactory;
import org.apache.pekko.serialization.jackson.JacksonObjectMapperProviderSetup;

/** Jackson serialization for messages that cross nodes. */
final class PekkoJackson {

    static final String JSON = "jackson-json";
    static final String CBOR = "jackson-cbor";

    private PekkoJackson() {}

    /**
     * {@code configMap} plus the Jackson JSON and CBOR serializers and the bindings for {@code
     * JsonSerializable} and {@code CborSerializable}, where the application hasn't set them.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> withSerializers(Map<String, Object> configMap) {
        Map<String, Object> result = new HashMap<>(configMap);
        Map<String, Object> pekko = (Map<String, Object>) result.computeIfAbsent("pekko", k -> new HashMap<>());
        Map<String, Object> actor = (Map<String, Object>) pekko.computeIfAbsent("actor", k -> new HashMap<>());
        Map<String, Object> serializers =
                (Map<String, Object>) actor.computeIfAbsent("serializers", k -> new HashMap<>());
        Map<String, Object> bindings =
                (Map<String, Object>) actor.computeIfAbsent("serialization-bindings", k -> new HashMap<>());

        serializers.putIfAbsent(JSON, "org.apache.pekko.serialization.jackson.JacksonJsonSerializer");
        serializers.putIfAbsent(CBOR, "org.apache.pekko.serialization.jackson.JacksonCborSerializer");
        bindings.putIfAbsent("io.github.seonwkim.actor.serialization.JsonSerializable", JSON);
        bindings.putIfAbsent("io.github.seonwkim.actor.serialization.CborSerializable", CBOR);
        return result;
    }

    /**
     * The JSON binding starts from a copy of {@code applicationMapper} when there is one, so messages
     * use the application's modules and settings; Pekko then applies its configured features on top.
     * CBOR needs a CBOR factory, so it keeps Pekko's own mapper. Both get the module that writes
     * {@code SpringActorRef}s.
     */
    static JacksonObjectMapperProviderSetup setup(@Nullable ObjectMapper applicationMapper) {
        return new JacksonObjectMapperProviderSetup(new JacksonObjectMapperFactory() {
            @Override
            public ObjectMapper newObjectMapper(String bindingName, JsonFactory jsonFactory) {
                ObjectMapper mapper = applicationMapper != null && JSON.equals(bindingName)
                        ? applicationMapper.copy()
                        : super.newObjectMapper(bindingName, jsonFactory);
                return mapper.registerModule(new SpringActorRefJacksonModule());
            }
        });
    }
}
