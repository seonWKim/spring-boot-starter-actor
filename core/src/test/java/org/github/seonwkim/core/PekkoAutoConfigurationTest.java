package org.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.*;

import com.typesafe.config.Config;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
            ActorSystemInstance systemInstance = context.getBean(ActorSystemInstance.class);
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
                    () -> context.getBean(ActorSystemInstance.class),
                    "Expected exception when accessing missing DefaultActorSystemInstance bean"
            );
        }
    }

    @SpringActor
    static class TestHelloActor {
        public interface Command {}

        public static class SayHello implements TestHelloActor.Command {}

        public static Behavior<TestHelloActor.Command> create(String id) {
            return Behaviors.setup(ctx ->
                                           Behaviors.receive(
                                                            TestHelloActor.Command.class)
                                                    .onMessage(
                                                            TestHelloActor.SayHello.class, msg -> {
                                                                System.out.println("Hello from " + id);
                                                                return Behaviors.same();
                                                            })
                                                    .build()
            );
        }
    }
    
    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {
            "actor.pekko.enabled=true",
            "actor.pekko.loglevel=INFO",
            "actor.pekko.actor.provider=local"
    })
    class SpringActorDiscoveryTest {
        @Test
        void shouldRegisterSpringActors(ApplicationContext context) {
            assertTrue(context.containsBean("actorTypeRegistry"));

            ActorTypeRegistry registry = context.getBean(ActorTypeRegistry.class);

            // Should be able to create the behavior by command class
            Behavior<TestHelloActor.Command> behavior = registry.createBehavior(
                    TestHelloActor.Command.class,
                    "test-id");

            assertNotNull(behavior, "Behavior for TestHelloActor should be registered and non-null");
        }

        @Test
        void actorSystemShouldStart(ApplicationContext context) {
            assertTrue(context.containsBean("actorSystem"));

            ActorSystemInstance systemInstance = context.getBean(ActorSystemInstance.class);
            assertNotNull(systemInstance.getRaw());
        } 
    }

    static class CustomTestRootGuardian {
        public static Behavior<RootGuardian.Command> create() {
            return Behaviors.setup(ctx -> Behaviors.receive(RootGuardian.Command.class).build());
        }
    }

    @Configuration
    static class CustomOverrideConfiguration {
        @Bean
        public RootGuardianSupplierWrapper customRootGuardianSupplierWrapper() {
            return new RootGuardianSupplierWrapper(CustomTestRootGuardian::create);
        }
    }

    @Nested
    @SpringBootTest(classes = { TestApp.class, CustomOverrideConfiguration.class })
    @TestPropertySource(properties = {
            "actor.pekko.enabled=true",
            "actor.pekko.loglevel=INFO",
            "actor.pekko.actor.provider=local"
    })
    class CustomRootGuardianSupplierWrapperTest {
        @Test
        void shouldUseCustomRootGuardian(ApplicationContext context) {
            RootGuardianSupplierWrapper wrapper = context.getBean(RootGuardianSupplierWrapper.class);
            assertNotNull(wrapper);

            Behavior<RootGuardian.Command> behavior = wrapper.getSupplier().get();
            assertNotNull(behavior);

            assertEquals(CustomTestRootGuardian.create().getClass(), behavior.getClass(),
                         "Expected custom RootGuardian behavior to be used");
        }
    }
}
