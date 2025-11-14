package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import io.github.seonwkim.core.impl.DefaultSpringActorContext;
import io.github.seonwkim.test.CustomOverrideConfiguration;
import io.github.seonwkim.test.CustomTestRootGuardian;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

public class ActorAutoConfigurationTest {

    @SpringBootApplication
    static class TestApp {}

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class WhenEnabled {

        @Test
        void shouldCreateActorSystem(ApplicationContext context) {
            assertTrue(context.containsBean("actorSystem"), "ActorSystem bean should exist");
            SpringActorSystem systemInstance = context.getBean(SpringActorSystem.class);
            Config config = systemInstance.getRaw().settings().config();

            assertEquals("INFO", config.getString("pekko.loglevel"));
            assertEquals("local", config.getString("pekko.actor.provider"));
        }
    }

    @Component
    static class TestHelloActor implements SpringActor<TestHelloActor.Command> {

        public interface Command {}

        public static class SayHello implements TestHelloActor.Command {}

        @Override
        public SpringActorBehavior<TestHelloActor.Command> create(SpringActorContext id) {
            return SpringActorBehavior.builder(TestHelloActor.Command.class, id)
                    .onMessage(TestHelloActor.SayHello.class, (ctx, msg) -> Behaviors.same())
                    .build();
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class SpringActorDiscoveryTest {
        @Test
        void shouldRegisterSpringActors(ApplicationContext context) {
            // Should be able to create the behavior by command class
            SpringActorBehavior<TestHelloActor.Command> behavior =
                    ActorTypeRegistry.createTypedBehavior(TestHelloActor.class, new DefaultSpringActorContext("test-id"));

            assertNotNull(behavior, "Behavior for TestHelloActor should be registered and non-null");
        }

        @Test
        void actorSystemShouldStart(ApplicationContext context) {
            assertTrue(context.containsBean("actorSystem"));

            SpringActorSystem systemInstance = context.getBean(SpringActorSystem.class);
            assertNotNull(systemInstance.getRaw());
        }
    }

    @Nested
    @SpringBootTest(classes = {TestApp.class, CustomOverrideConfiguration.class})
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class CustomRootGuardianSupplierWrapperTest {
        @Test
        void shouldUseCustomRootGuardian(ApplicationContext context) {
            RootGuardianSupplierWrapper wrapper = context.getBean(RootGuardianSupplierWrapper.class);
            assertNotNull(wrapper);

            Behavior<RootGuardian.Command> behavior = wrapper.get();
            assertNotNull(behavior);

            assertEquals(
                    CustomTestRootGuardian.create().getClass(),
                    behavior.getClass(),
                    "Expected custom RootGuardian behavior to be used");
        }
    }

    @Nested
    @SpringBootTest(classes = {TestApp.class})
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=cluster"})
    class ClusterConfigurationTest {
        @Test
        void clusterShouldBeConfigured(ApplicationContext context) {
            SpringActorSystem springActorSystem = context.getBean(SpringActorSystem.class);
            assertNotNull(springActorSystem.getCluster());
        }
    }
}
