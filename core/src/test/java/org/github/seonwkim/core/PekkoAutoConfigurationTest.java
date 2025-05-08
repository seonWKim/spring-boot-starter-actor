package org.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

import com.typesafe.config.Config;

public class PekkoAutoConfigurationTest {

    @SpringBootApplication(scanBasePackages = "org.github.seonwkim.core")
    static class TestApp {}

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {
            "spring.actor-enabled=true",
            "spring.actor.pekko.loglevel=INFO",
            "spring.actor.pekko.actor.provider=local"
    })
    class WhenEnabled {

        @Test
        void shouldCreateActorSystem(org.springframework.context.ApplicationContext context) {
            assertTrue(context.containsBean("actorSystem"), "ActorSystem bean should exist");
            SpringActorSystem systemInstance = context.getBean(SpringActorSystem.class);
            Config config = systemInstance.getRaw().settings().config();

            assertEquals("INFO", config.getString("pekko.loglevel"));
            assertEquals("local", config.getString("pekko.actor.provider"));
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {
            "spring.actor-enabled=false",
            "spring.actor.pekko.loglevel=DEBUG"
    })
    class WhenDisabled {

        @Test
        void shouldNotCreateActorSystem(org.springframework.context.ApplicationContext context) {
            assertFalse(context.containsBean("actorSystem"), "ActorSystem bean should NOT exist");
            assertThrows(
                    Exception.class,
                    () -> context.getBean(SpringActorSystem.class),
                    "Expected exception when accessing missing DefaultActorSystemInstance bean"
            );
        }
    }

    @Component
    static class TestHelloActor implements SpringActor {
        @Override
        public Class<?> commandClass() {
            return Command.class;
        }

        public interface Command {}

        public static class SayHello implements TestHelloActor.Command {}

        public static Behavior<TestHelloActor.Command> create(String id) {
            return Behaviors.setup(ctx ->
                                           Behaviors.receive(TestHelloActor.Command.class)
                                                    .onMessage(TestHelloActor.SayHello.class,
                                                               msg -> Behaviors.same())
                                                    .build()
            );
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {
            "spring.actor-enabled=true",
            "spring.actor.pekko.loglevel=INFO",
            "spring.actor.pekko.actor.provider=local"
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

            SpringActorSystem systemInstance = context.getBean(SpringActorSystem.class);
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
            "spring.actor-enabled=true",
            "spring.actor.pekko.loglevel=INFO",
            "spring.actor.pekko.actor.provider=local"
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

    @Nested
    @SpringBootTest(classes = { TestApp.class })
    @TestPropertySource(properties = {
            "spring.actor-enabled=true",
            "spring.actor.pekko.loglevel=INFO",
            "spring.actor.pekko.actor.provider=cluster"
    })
    class ClusterConfigurationTest {
        @Test
        void clusterShouldBeConfigured(ApplicationContext context) {
            SpringActorSystem springActorSystem = context.getBean(SpringActorSystem.class);
            assertNotNull(springActorSystem.getCluster());
        }
    }
}
