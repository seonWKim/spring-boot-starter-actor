package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import io.github.seonwkim.core.impl.DefaultSpringActorContext;
import io.github.seonwkim.core.shard.*;
import io.github.seonwkim.test.CustomOverrideConfiguration;
import io.github.seonwkim.test.CustomTestRootGuardian;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
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
            SpringActorBehavior<TestHelloActor.Command> behavior = ActorTypeRegistry.createTypedBehavior(
                    TestHelloActor.class, new DefaultSpringActorContext("test-id"));

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

    /**
     * Test actor that injects SpringActorSystem to verify no circular dependency.
     */
    @Component
    static class ActorWithSystemInjection implements SpringActor<ActorWithSystemInjection.Command> {

        private final SpringActorSystem actorSystem;

        public ActorWithSystemInjection(SpringActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        public interface Command {}

        public static class GetSystemName implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(GetSystemName.class, (context, msg) -> {
                        context.getLog().info("Actor system name: {}", actorSystem.getName());
                        return Behaviors.same();
                    })
                    .build();
        }

        public SpringActorSystem getActorSystem() {
            return actorSystem;
        }
    }

    /**
     * Test sharded actor that injects SpringActorSystem to verify no circular dependency.
     */
    @Component
    static class ShardedActorWithSystemInjection
            implements io.github.seonwkim.core.shard.SpringShardedActor<ShardedActorWithSystemInjection.Command> {

        private final SpringActorSystem actorSystem;

        public static final EntityTypeKey<Command> TYPE_KEY =
                EntityTypeKey.create(Command.class, "ShardedActorWithSystemInjection");

        public ShardedActorWithSystemInjection(SpringActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        public interface Command extends io.github.seonwkim.core.serialization.JsonSerializable {}

        public static class GetSystemName implements Command {}

        @Override
        public EntityTypeKey<Command> typeKey() {
            return TYPE_KEY;
        }

        @Override
        public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
            return SpringShardedActorBehavior.builder(Command.class, ctx)
                    .onMessage(GetSystemName.class, (context, msg) -> {
                        context.getLog().info("Sharded actor system name: {}", actorSystem.getName());
                        return Behaviors.same();
                    })
                    .build();
        }

        @Override
        public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
            return new DefaultShardingMessageExtractor<>(5);
        }

        public SpringActorSystem getActorSystem() {
            return actorSystem;
        }
    }

    @Nested
    @SpringBootTest(classes = {TestApp.class, ActorWithSystemInjection.class, ShardedActorWithSystemInjection.class})
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ActorSystemDependencyInjectionTest {

        /**
         * Verifies that a SpringActor can inject SpringActorSystem without circular dependency.
         * This test ensures the static registry refactoring successfully breaks the dependency cycle.
         */
        @Test
        void springActorCanInjectActorSystem(ApplicationContext context) {
            // Verify context loads successfully (no circular dependency exception - this is the main test!)
            assertNotNull(context, "Application context should load without circular dependency");

            // Verify actor bean exists by type
            ActorWithSystemInjection actor = context.getBean(ActorWithSystemInjection.class);
            assertNotNull(actor, "Actor bean should exist");

            // Verify actor has SpringActorSystem injected
            assertNotNull(actor.getActorSystem(), "SpringActorSystem should be injected");

            // Verify it's the same instance as the system bean
            SpringActorSystem systemBean = context.getBean(SpringActorSystem.class);
            assertEquals(systemBean, actor.getActorSystem(), "Should be the same SpringActorSystem instance");

            // Verify actor was registered in the static registry
            SpringActorBehavior<ActorWithSystemInjection.Command> behavior = ActorTypeRegistry.createTypedBehavior(
                    ActorWithSystemInjection.class, new DefaultSpringActorContext("test-actor"));
            assertNotNull(behavior, "Actor should be registered in ActorTypeRegistry");
        }

        /**
         * Verifies that a SpringShardedActor can inject SpringActorSystem without circular dependency.
         * This test ensures the static registry refactoring works for sharded actors too.
         */
        @Test
        void springShardedActorCanInjectActorSystem(ApplicationContext context) {
            // Verify context loads successfully (no circular dependency exception - this is the main test!)
            assertNotNull(context, "Application context should load without circular dependency");

            // Verify sharded actor bean exists by type
            ShardedActorWithSystemInjection actor = context.getBean(ShardedActorWithSystemInjection.class);
            assertNotNull(actor, "Sharded actor bean should exist");

            // Verify sharded actor has SpringActorSystem injected
            assertNotNull(actor.getActorSystem(), "SpringActorSystem should be injected");

            // Verify it's the same instance as the system bean
            SpringActorSystem systemBean = context.getBean(SpringActorSystem.class);
            assertEquals(systemBean, actor.getActorSystem(), "Should be the same SpringActorSystem instance");

            // Verify actor was registered in the static sharded actor registry
            SpringShardedActor<?> registeredActor = ShardedActorRegistry.get(ShardedActorWithSystemInjection.TYPE_KEY);
            assertNotNull(registeredActor, "Sharded actor should be registered in ShardedActorRegistry");
            assertEquals(actor, registeredActor, "Should be the same sharded actor instance");
        }
    }
}
