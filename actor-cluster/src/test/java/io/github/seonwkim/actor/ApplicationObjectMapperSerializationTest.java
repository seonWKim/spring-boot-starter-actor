package io.github.seonwkim.actor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import java.nio.charset.StandardCharsets;
import org.apache.pekko.actor.typed.javadsl.Adapter;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;

/** Messages between nodes are written with the application's ObjectMapper (its modules and settings). */
@SpringBootTest(classes = {ActorConfiguration.class, ApplicationObjectMapperSerializationTest.TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationObjectMapperSerializationTest {

    @Configuration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        }
    }

    public record Greeting(String userName) implements JsonSerializable {}

    @Autowired
    private SpringActorSystem actorSystem;

    @Test
    void jsonMessagesUseTheApplicationObjectMapper() {
        Serialization serialization = SerializationExtension.get(Adapter.toClassic(PekkoActorSystem.raw(actorSystem)));

        byte[] bytes = serialization.serialize(new Greeting("ada")).get();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("\"user_name\":\"ada\"");
    }
}
