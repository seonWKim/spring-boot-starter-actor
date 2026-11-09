package io.github.seonwkim.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import io.github.seonwkim.actor.internal.ActorTypeRegistry;
import io.github.seonwkim.actor.internal.DefaultSpringActorContext;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import io.github.seonwkim.actor.internal.RootGuardian;
import io.github.seonwkim.actor.internal.RootGuardianSupplierWrapper;
import io.github.seonwkim.test.CustomOverrideConfiguration;
import io.github.seonwkim.test.CustomTestRootGuardian;
import org.apache.pekko.actor.typed.Behavior;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
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
            Config config = PekkoActorSystem.raw(systemInstance).settings().config();

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
                    .onMessage(TestHelloActor.SayHello.class, (ctx, msg) -> SpringActorBehavior.same())
                    .build();
        }
    }

    @Nested
    @SpringBootTest(classes = {TestApp.class, TestHelloActor.class})
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class SpringActorDiscoveryTest {
        @Test
        void shouldRegisterSpringActors(ApplicationContext context) {
            // Should be able to create the behavior by command class
            SpringActorBehavior<TestHelloActor.Command> behavior = context.getBean(ActorTypeRegistry.class)
                    .createTypedBehavior(TestHelloActor.class, new DefaultSpringActorContext("test-id"));

            assertNotNull(behavior, "Behavior for TestHelloActor should be registered and non-null");
        }

        @Test
        void actorSystemShouldStart(ApplicationContext context) {
            assertTrue(context.containsBean("actorSystem"));

            SpringActorSystem systemInstance = context.getBean(SpringActorSystem.class);
            assertNotNull(PekkoActorSystem.raw(systemInstance));
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
                        return SpringActorBehavior.same();
                    })
                    .build();
        }

        public SpringActorSystem getActorSystem() {
            return actorSystem;
        }
    }

    @Nested
    @SpringBootTest(classes = {TestApp.class, ActorWithSystemInjection.class})
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

            // Verify actor was registered in the context's registry
            SpringActorBehavior<ActorWithSystemInjection.Command> behavior = context.getBean(ActorTypeRegistry.class)
                    .createTypedBehavior(ActorWithSystemInjection.class, new DefaultSpringActorContext("test-actor"));
            assertNotNull(behavior, "Actor should be registered in ActorTypeRegistry");
        }
    }

    @Test
    void clusterModeWithoutTheClusterModuleFailsNamingTheStarterToAdd() {
        Throwable failure = catchThrowable(() -> new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE)
                .properties("spring.actor.pekko.actor.provider=cluster")
                .run()
                .close());

        assertThat(failure).rootCause().hasMessageContaining("actor-spring-boot-starter-cluster");
    }
}
