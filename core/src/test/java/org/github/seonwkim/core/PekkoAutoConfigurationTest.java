package org.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.*;

import com.typesafe.config.Config;
import org.github.seonwkim.core.impl.DefaultActorSystemInstance;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

public class PekkoAutoConfigurationTest {

    @SpringBootApplication(scanBasePackages = "org.github.seonwkim.core")
    static class TestApp {}

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {
            "actor.pekko.enabled=true",
            "actor.pekko.loglevel=INFO",
            "actor.pekko.actor.provider=local"
    })
    class WhenEnabled {

        @Test
        void shouldCreateActorSystem(org.springframework.context.ApplicationContext context) {
            assertTrue(context.containsBean("actorSystem"), "ActorSystem bean should exist");
            DefaultActorSystemInstance systemInstance = context.getBean(DefaultActorSystemInstance.class);
            Config config = systemInstance.getRaw().settings().config();

            assertEquals("INFO", config.getString("pekko.loglevel"));
            assertEquals("local", config.getString("pekko.actor.provider"));
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {
            "actor.pekko.enabled=false",
            "actor.pekko.loglevel=DEBUG"
    })
    class WhenDisabled {

        @Test
        void shouldNotCreateActorSystem(org.springframework.context.ApplicationContext context) {
            assertFalse(context.containsBean("actorSystem"), "ActorSystem bean should NOT exist");
            assertThrows(
                    Exception.class,
                    () -> context.getBean(DefaultActorSystemInstance.class),
                    "Expected exception when accessing missing DefaultActorSystemInstance bean"
            );
        }
    }


}
