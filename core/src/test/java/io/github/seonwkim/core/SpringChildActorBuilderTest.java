package io.github.seonwkim.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

class SpringChildActorBuilderTest {

    /**
     * A simple child actor for testing the builder.
     */
    @Component
    static class SimpleChildActor implements SpringActorWithContext<SimpleChildActor.Command, SpringActorContext> {

        public interface Command {}

        public static class Ping implements Command {
            public final ActorRef<String> replyTo;

            @SuppressWarnings("unchecked")
            public Ping(ActorRef<String> replyTo) {
                this.replyTo = replyTo;
            }
        }

        public static class GetId implements Command {
            public final ActorRef<String> replyTo;

            @SuppressWarnings("unchecked")
            public GetId(ActorRef<String> replyTo) {
                this.replyTo = replyTo;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onCreate(ctx -> new SimpleChildBehavior(ctx, actorContext))
                    .onMessage(Ping.class, SimpleChildBehavior::onPing)
                    .onMessage(GetId.class, SimpleChildBehavior::onGetId)
                    .build();
        }

        private static class SimpleChildBehavior {
            private final ActorContext<Command> ctx;
            private final SpringActorContext actorContext;

            SimpleChildBehavior(ActorContext<Command> ctx, SpringActorContext actorContext) {
                this.ctx = ctx;
                this.actorContext = actorContext;
            }

            private Behavior<Command> onPing(Ping msg) {
                msg.replyTo.tell("pong");
                return Behaviors.same();
            }

            private Behavior<Command> onGetId(GetId msg) {
                msg.replyTo.tell(actorContext.actorId());
                return Behaviors.same();
            }
        }
    }

    /**
     * A parent actor that supports framework commands for child spawning.
     */
    @Component
    static class ParentActor implements SpringActorWithContext<ParentActor.Command, SpringActorContext> {

        public interface Command extends FrameworkCommand {}

        public static class DoNothing implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withFrameworkCommands() // Enable framework command handling
                    .onCreate(ctx -> new ParentBehavior(ctx, actorContext))
                    .onMessage(DoNothing.class, ParentBehavior::onDoNothing)
                    .build();
        }

        private static class ParentBehavior {
            private final ActorContext<Command> ctx;
            private final SpringActorContext actorContext;

            ParentBehavior(ActorContext<Command> ctx, SpringActorContext actorContext) {
                this.ctx = ctx;
                this.actorContext = actorContext;
            }

            private Behavior<Command> onDoNothing(DoNothing msg) {
                return Behaviors.same();
            }
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {}

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class BuilderConfigurationTests {

        @Test
        void testWithId(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-with-id")
                    .spawnAndWait();

            // When: Using the builder with withId()
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class)
                    .withId("test-child-1")
                    .spawnAndWait();

            // Then: Child should be spawned with the correct ID
            assertThat(child).isNotNull();
            String childId = child
                    .askBuilder(SimpleChildActor.GetId::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(childId).isEqualTo("test-child-1");
        }

        @Test
        void testWithContext(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-with-context")
                    .spawnAndWait();

            // When: Using the builder with withContext()
            SpringActorContext customContext = new DefaultSpringActorContext("custom-child-1");
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class)
                    .withContext(customContext)
                    .spawnAndWait();

            // Then: Child should be spawned with the custom context
            assertThat(child).isNotNull();
            String childId = child
                    .askBuilder(SimpleChildActor.GetId::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(childId).isEqualTo("custom-child-1");
        }

        @Test
        void testWithSupervisionStrategy(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-with-strategy")
                    .spawnAndWait();

            // When: Using the builder with withSupervisionStrategy()
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class)
                    .withId("supervised-child")
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .spawnAndWait();

            // Then: Child should be spawned (strategy is applied internally)
            assertThat(child).isNotNull();
            String response = child
                    .askBuilder(SimpleChildActor.Ping::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(response).isEqualTo("pong");
        }

        @Test
        void testWithTimeout(org.springframework.context.ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-with-timeout")
                    .spawnAndWait();

            // When: Using the builder with withTimeout()
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class)
                    .withId("timeout-child")
                    .withTimeout(Duration.ofSeconds(10))
                    .spawnAndWait();

            // Then: Child should be spawned successfully
            assertThat(child).isNotNull();
        }

        @Test
        void testFluentChaining(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-fluent")
                    .spawnAndWait();

            // When: Chaining multiple builder methods
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class)
                    .withId("fluent-child")
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .withTimeout(Duration.ofSeconds(5))
                    .spawnAndWait();

            // Then: All configurations should be applied
            assertThat(child).isNotNull();
            String response = child
                    .askBuilder(SimpleChildActor.Ping::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(response).isEqualTo("pong");
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ValidationTests {

        @Test
        void testWithTimeoutThrowsOnNull(org.springframework.context.ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-validation")
                    .spawnAndWait();

            // When/Then: Calling withTimeout with null should throw
            assertThatThrownBy(() -> parent
                            .child(SimpleChildActor.class)
                            .withTimeout(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("timeout must not be null");
        }

        @Test
        void testSpawnThrowsWhenNoIdOrContext(org.springframework.context.ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-no-id")
                    .spawnAndWait();

            // When/Then: Calling spawn without setting ID or context should throw
            assertThatThrownBy(() -> parent.child(SimpleChildActor.class).spawn())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Either childId or childContext must be set");
        }

        @Test
        void testGetThrowsWhenNoIdOrContext(org.springframework.context.ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-get-no-id")
                    .spawnAndWait();

            // When/Then: Calling get without setting ID or context should throw
            assertThatThrownBy(() -> parent.child(SimpleChildActor.class).get())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Either childId or childContext must be set");
        }

        @Test
        void testExistsThrowsWhenNoIdOrContext(org.springframework.context.ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-exists-no-id")
                    .spawnAndWait();

            // When/Then: Calling exists without setting ID or context should throw
            assertThatThrownBy(() -> parent.child(SimpleChildActor.class).exists())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Either childId or childContext must be set");
        }

        @Test
        void testGetOrSpawnThrowsWhenNoIdOrContext(org.springframework.context.ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-getorspawn-no-id")
                    .spawnAndWait();

            // When/Then: Calling getOrSpawn without setting ID or context should throw
            assertThatThrownBy(() -> parent.child(SimpleChildActor.class).getOrSpawn())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Either childId or childContext must be set");
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class SpawnOperationTests {

        @Test
        void testSpawnAsync(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-spawn-async")
                    .spawnAndWait();

            // When: Using spawn() to get a CompletionStage
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class)
                    .withId("async-child")
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Child should be spawned and functional
            assertThat(child).isNotNull();
            String response = child
                    .askBuilder(SimpleChildActor.Ping::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(response).isEqualTo("pong");
        }

        @Test
        void testSpawnAndWait(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-spawn-wait")
                    .spawnAndWait();

            // When: Using spawnAndWait() to spawn synchronously
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class)
                    .withId("sync-child")
                    .spawnAndWait();

            // Then: Child should be spawned and functional
            assertThat(child).isNotNull();
            String response = child
                    .askBuilder(SimpleChildActor.Ping::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(response).isEqualTo("pong");
        }

        @Test
        void testSpawnDuplicateReturnsExisting(org.springframework.context.ApplicationContext springContext) {
            // Given: A parent actor with an existing child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-duplicate")
                    .spawnAndWait();

            SpringActorRef<SimpleChildActor.Command> child1 = parent
                    .child(SimpleChildActor.class)
                    .withId("duplicate-child")
                    .spawnAndWait();

            // When: Spawning a child with the same ID again
            SpringActorRef<SimpleChildActor.Command> child2 = parent
                    .child(SimpleChildActor.class)
                    .withId("duplicate-child")
                    .spawnAndWait();

            // Then: Both references should point to the same actor
            assertThat(child1).isNotNull();
            assertThat(child2).isNotNull();
            assertThat(child1.getUnderlying()).isEqualTo(child2.getUnderlying());
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class GetOperationTests {

        @Test
        void testGetReturnsExistingChild(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: A parent actor with an existing child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-get-existing")
                    .spawnAndWait();

            SpringActorRef<SimpleChildActor.Command> spawnedChild = parent
                    .child(SimpleChildActor.class)
                    .withId("existing-child")
                    .spawnAndWait();

            // When: Getting the child using the builder
            SpringActorRef<SimpleChildActor.Command> retrievedChild = parent
                    .child(SimpleChildActor.class)
                    .withId("existing-child")
                    .get()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should retrieve the same child
            assertThat(retrievedChild).isNotNull();
            assertThat(retrievedChild.getUnderlying()).isEqualTo(spawnedChild.getUnderlying());
        }

        @Test
        void testGetReturnsNullForNonExistent(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor without children
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-get-null")
                    .spawnAndWait();

            // When: Getting a non-existent child
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class)
                    .withId("non-existent-child")
                    .get()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should return null
            assertThat(child).isNull();
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ExistsOperationTests {

        @Test
        void testExistsReturnsTrueForExistingChild(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor with an existing child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-exists-true")
                    .spawnAndWait();

            parent.child(SimpleChildActor.class)
                    .withId("existing-child-check")
                    .spawnAndWait();

            // When: Checking if the child exists
            Boolean exists = parent
                    .child(SimpleChildActor.class)
                    .withId("existing-child-check")
                    .exists()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should return true
            assertThat(exists).isTrue();
        }

        @Test
        void testExistsReturnsFalseForNonExistent(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor without children
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-exists-false")
                    .spawnAndWait();

            // When: Checking if a non-existent child exists
            Boolean exists = parent
                    .child(SimpleChildActor.class)
                    .withId("non-existent-check")
                    .exists()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should return false
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class GetOrSpawnOperationTests {

        @Test
        void testGetOrSpawnReturnsExistingChild(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor with an existing child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-getorspawn-existing")
                    .spawnAndWait();

            SpringActorRef<SimpleChildActor.Command> spawnedChild = parent
                    .child(SimpleChildActor.class)
                    .withId("getorspawn-existing")
                    .spawnAndWait();

            // When: Using getOrSpawn on an existing child
            SpringActorRef<SimpleChildActor.Command> retrievedChild = parent
                    .child(SimpleChildActor.class)
                    .withId("getorspawn-existing")
                    .getOrSpawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should return the existing child
            assertThat(retrievedChild).isNotNull();
            assertThat(retrievedChild.getUnderlying()).isEqualTo(spawnedChild.getUnderlying());
        }

        @Test
        void testGetOrSpawnCreatesNewChild(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor without children
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-getorspawn-new")
                    .spawnAndWait();

            // When: Using getOrSpawn on a non-existent child
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class)
                    .withId("getorspawn-new")
                    .getOrSpawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should create and return a new child
            assertThat(child).isNotNull();
            String response = child
                    .askBuilder(SimpleChildActor.Ping::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(response).isEqualTo("pong");
        }

        @Test
        void testGetOrSpawnIdempotent(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-getorspawn-idempotent")
                    .spawnAndWait();

            // When: Calling getOrSpawn multiple times
            SpringActorRef<SimpleChildActor.Command> child1 = parent
                    .child(SimpleChildActor.class)
                    .withId("idempotent-child")
                    .getOrSpawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            SpringActorRef<SimpleChildActor.Command> child2 = parent
                    .child(SimpleChildActor.class)
                    .withId("idempotent-child")
                    .getOrSpawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Both should return the same child
            assertThat(child1).isNotNull();
            assertThat(child2).isNotNull();
            assertThat(child1.getUnderlying()).isEqualTo(child2.getUnderlying());
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class EdgeCaseTests {

        @Test
        void testMultipleChildrenWithDifferentIds(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-multiple-children")
                    .spawnAndWait();

            // When: Spawning multiple children with different IDs
            SpringActorRef<SimpleChildActor.Command> child1 = parent
                    .child(SimpleChildActor.class)
                    .withId("child-1")
                    .spawnAndWait();

            SpringActorRef<SimpleChildActor.Command> child2 = parent
                    .child(SimpleChildActor.class)
                    .withId("child-2")
                    .spawnAndWait();

            SpringActorRef<SimpleChildActor.Command> child3 = parent
                    .child(SimpleChildActor.class)
                    .withId("child-3")
                    .spawnAndWait();

            // Then: All children should be distinct and functional
            assertThat(child1.getUnderlying()).isNotEqualTo(child2.getUnderlying());
            assertThat(child2.getUnderlying()).isNotEqualTo(child3.getUnderlying());
            assertThat(child1.getUnderlying()).isNotEqualTo(child3.getUnderlying());

            // Verify all can respond
            String response1 = child1
                    .askBuilder(SimpleChildActor.Ping::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            String response2 = child2
                    .askBuilder(SimpleChildActor.Ping::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            String response3 = child3
                    .askBuilder(SimpleChildActor.Ping::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            assertThat(response1).isEqualTo("pong");
            assertThat(response2).isEqualTo("pong");
            assertThat(response3).isEqualTo("pong");
        }

        @Test
        void testBuilderReuseCreatesIndependentOperations(
                org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-builder-reuse")
                    .spawnAndWait();

            // When: Creating a builder and using it for different children
            // (Note: In practice, each builder instance should be used for one child,
            // but this tests that multiple calls with different IDs work correctly)
            parent.child(SimpleChildActor.class)
                    .withId("reuse-child-1")
                    .spawnAndWait();

            parent.child(SimpleChildActor.class)
                    .withId("reuse-child-2")
                    .spawnAndWait();

            // Then: Both children should exist independently
            Boolean exists1 = parent
                    .child(SimpleChildActor.class)
                    .withId("reuse-child-1")
                    .exists()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            Boolean exists2 = parent
                    .child(SimpleChildActor.class)
                    .withId("reuse-child-2")
                    .exists()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertThat(exists1).isTrue();
            assertThat(exists2).isTrue();
        }

        @Test
        void testCustomContextTakesPrecedenceOverId(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-context-precedence")
                    .spawnAndWait();

            // When: Setting both ID and custom context (context should be used)
            SpringActorContext customContext = new DefaultSpringActorContext("custom-context-child");
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class)
                    .withId("this-id-should-be-ignored")
                    .withContext(customContext)
                    .spawnAndWait();

            // Then: Child should use the custom context ID
            assertThat(child).isNotNull();
            String childId = child
                    .askBuilder(SimpleChildActor.GetId::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(childId).isEqualTo("custom-context-child");
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class IntegrationWithSpringActorRefTests {

        @Test
        void testBuilderMethodMatchesDirectSpawnChild(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-comparison")
                    .spawnAndWait();

            // When: Spawning children using both methods
            SpringActorRef<SimpleChildActor.Command> childViaBuilder = parent
                    .child(SimpleChildActor.class)
                    .withId("builder-child")
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .spawnAndWait();

            SpringActorRef<SimpleChildActor.Command> childViaDirectMethod = parent
                    .spawnChild(SimpleChildActor.class, "direct-child", SupervisorStrategy.restart())
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Both should work identically
            assertThat(childViaBuilder).isNotNull();
            assertThat(childViaDirectMethod).isNotNull();

            String response1 = childViaBuilder
                    .askBuilder(SimpleChildActor.Ping::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            String response2 = childViaDirectMethod
                    .askBuilder(SimpleChildActor.Ping::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            assertThat(response1).isEqualTo("pong");
            assertThat(response2).isEqualTo("pong");
        }

        @Test
        void testGetMethodMatchesGetChild(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor with a child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-get-comparison")
                    .spawnAndWait();

            parent.child(SimpleChildActor.class)
                    .withId("comparison-child")
                    .spawnAndWait();

            // When: Getting the child using both methods
            SpringActorRef<SimpleChildActor.Command> childViaBuilder = parent
                    .child(SimpleChildActor.class)
                    .withId("comparison-child")
                    .get()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            SpringActorRef<SimpleChildActor.Command> childViaDirectMethod = parent
                    .getChild(SimpleChildActor.class, "comparison-child")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Both should return the same child reference
            assertThat(childViaBuilder).isNotNull();
            assertThat(childViaDirectMethod).isNotNull();
            assertThat(childViaBuilder.getUnderlying()).isEqualTo(childViaDirectMethod.getUnderlying());
        }

        @Test
        void testExistsMethodMatchesExistsChild(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor with a child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-exists-comparison")
                    .spawnAndWait();

            parent.child(SimpleChildActor.class)
                    .withId("exists-comparison-child")
                    .spawnAndWait();

            // When: Checking existence using both methods
            Boolean existsViaBuilder = parent
                    .child(SimpleChildActor.class)
                    .withId("exists-comparison-child")
                    .exists()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            Boolean existsViaDirectMethod = parent
                    .existsChild(SimpleChildActor.class, "exists-comparison-child")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Both should return the same result
            assertThat(existsViaBuilder).isTrue();
            assertThat(existsViaDirectMethod).isTrue();
        }
    }
}