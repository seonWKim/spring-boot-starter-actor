package io.github.seonwkim.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
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
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for the unified child actor reference API (SpringChildActorReference).
 * This tests the new concise API: parent.child(Class, String).get/exists/spawn/getOrSpawn()
 */
class SpringChildActorReferenceTest {

    /**
     * A simple child actor for testing.
     */
    @Component
    static class SimpleChildActor implements SpringActorWithContext<SimpleChildActor.Command, SpringActorContext> {

        public interface Command {}

        public static class Ping implements Command {
            public final ActorRef<String> replyTo;

            public Ping(ActorRef<String> replyTo) {
                this.replyTo = replyTo;
            }
        }

        public static class GetId implements Command {
            public final ActorRef<String> replyTo;

            public GetId(ActorRef<String> replyTo) {
                this.replyTo = replyTo;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new SimpleChildBehavior(ctx, actorContext))
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
     * A parent actor that supports framework commands for child management.
     */
    @Component
    static class ParentActor implements SpringActorWithContext<ParentActor.Command, SpringActorContext> {

        public interface Command extends FrameworkCommand {}

        public static class DoNothing implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new ParentBehavior(ctx, actorContext))
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
    class GetOperationTests {

        @Test
        void testGetReturnsOptionalWithExistingChild(ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor with an existing child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-get-existing")
                    .spawnAndWait();

            // Spawn a child
            SpringActorRef<SimpleChildActor.Command> spawnedChild = parent
                    .child(SimpleChildActor.class, "existing-child")
                    .spawn(SupervisorStrategy.restart())
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // When: Getting the child using the unified API
            Optional<SpringActorRef<SimpleChildActor.Command>> retrievedChild = parent
                    .child(SimpleChildActor.class, "existing-child")
                    .get()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should retrieve the child in an Optional
            assertThat(retrievedChild).isPresent();
            assertThat(retrievedChild.get().getUnderlying()).isEqualTo(spawnedChild.getUnderlying());
        }

        @Test
        void testGetReturnsEmptyOptionalForNonExistent(ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor without children
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-get-empty")
                    .spawnAndWait();

            // When: Getting a non-existent child
            Optional<SpringActorRef<SimpleChildActor.Command>> child = parent
                    .child(SimpleChildActor.class, "non-existent-child")
                    .get()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should return empty Optional
            assertThat(child).isEmpty();
        }

        @Test
        void testGetWithCustomTimeout(ApplicationContext springContext) throws Exception {
            // Given: A parent actor with a child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-get-timeout")
                    .spawnAndWait();

            parent.child(SimpleChildActor.class, "timeout-child")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // When: Getting with custom timeout
            Optional<SpringActorRef<SimpleChildActor.Command>> child = parent
                    .child(SimpleChildActor.class, "timeout-child")
                    .withTimeout(Duration.ofSeconds(10))
                    .get()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should succeed
            assertThat(child).isPresent();
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ExistsOperationTests {

        @Test
        void testExistsReturnsTrueForExistingChild(ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor with an existing child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-exists-true")
                    .spawnAndWait();

            parent.child(SimpleChildActor.class, "existing-child-check")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // When: Checking if the child exists
            Boolean exists = parent
                    .child(SimpleChildActor.class, "existing-child-check")
                    .exists()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should return true
            assertThat(exists).isTrue();
        }

        @Test
        void testExistsReturnsFalseForNonExistent(ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor without children
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-exists-false")
                    .spawnAndWait();

            // When: Checking if a non-existent child exists
            Boolean exists = parent
                    .child(SimpleChildActor.class, "non-existent-check")
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
    class SpawnOperationTests {

        @Test
        void testSpawnCreatesNewChild(ApplicationContext springContext) throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-spawn-new")
                    .spawnAndWait();

            // When: Spawning a child with default strategy
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class, "new-child")
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
        void testSpawnWithCustomStrategy(ApplicationContext springContext) throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-spawn-strategy")
                    .spawnAndWait();

            // When: Spawning with custom supervision strategy
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class, "supervised-child")
                    .spawn(SupervisorStrategy.restart())
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Child should be spawned
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
        void testSpawnDuplicateReturnsExisting(ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor with an existing child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-spawn-duplicate")
                    .spawnAndWait();

            SpringActorRef<SimpleChildActor.Command> child1 = parent
                    .child(SimpleChildActor.class, "duplicate-child")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // When: Spawning a child with the same ID again
            SpringActorRef<SimpleChildActor.Command> child2 = parent
                    .child(SimpleChildActor.class, "duplicate-child")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // Then: Both references should point to the same actor
            assertThat(child1).isNotNull();
            assertThat(child2).isNotNull();
            assertThat(child1.getUnderlying()).isEqualTo(child2.getUnderlying());
        }

        @Test
        void testSpawnVerifiesChildId(ApplicationContext springContext) throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-spawn-verify-id")
                    .spawnAndWait();

            // When: Spawning a child with a specific ID
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class, "verify-id-child")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // Then: Child should have the correct ID
            String childId = child
                    .askBuilder(SimpleChildActor.GetId::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(childId).isEqualTo("verify-id-child");
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class GetOrSpawnOperationTests {

        @Test
        void testGetOrSpawnReturnsExistingChild(ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor with an existing child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-getorspawn-existing")
                    .spawnAndWait();

            SpringActorRef<SimpleChildActor.Command> spawnedChild = parent
                    .child(SimpleChildActor.class, "getorspawn-existing")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // When: Using getOrSpawn on an existing child
            SpringActorRef<SimpleChildActor.Command> retrievedChild = parent
                    .child(SimpleChildActor.class, "getorspawn-existing")
                    .getOrSpawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should return the existing child
            assertThat(retrievedChild).isNotNull();
            assertThat(retrievedChild.getUnderlying()).isEqualTo(spawnedChild.getUnderlying());
        }

        @Test
        void testGetOrSpawnCreatesNewChild(ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor without children
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-getorspawn-new")
                    .spawnAndWait();

            // When: Using getOrSpawn on a non-existent child
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class, "getorspawn-new")
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
        void testGetOrSpawnIdempotent(ApplicationContext springContext) throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-getorspawn-idempotent")
                    .spawnAndWait();

            // When: Calling getOrSpawn multiple times
            SpringActorRef<SimpleChildActor.Command> child1 = parent
                    .child(SimpleChildActor.class, "idempotent-child")
                    .getOrSpawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            SpringActorRef<SimpleChildActor.Command> child2 = parent
                    .child(SimpleChildActor.class, "idempotent-child")
                    .getOrSpawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Both should return the same child
            assertThat(child1).isNotNull();
            assertThat(child2).isNotNull();
            assertThat(child1.getUnderlying()).isEqualTo(child2.getUnderlying());
        }

        @Test
        void testGetOrSpawnWithCustomStrategy(ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-getorspawn-strategy")
                    .spawnAndWait();

            // When: Using getOrSpawn with custom strategy
            SpringActorRef<SimpleChildActor.Command> child = parent
                    .child(SimpleChildActor.class, "strategy-child")
                    .getOrSpawn(SupervisorStrategy.restart())
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
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ValidationTests {

        @Test
        void testNullChildIdThrowsException(ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-null-id")
                    .spawnAndWait();

            // When/Then: Creating reference with null ID should throw
            assertThatThrownBy(() -> parent.child(SimpleChildActor.class, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("childId must not be null or empty");
        }

        @Test
        void testEmptyChildIdThrowsException(ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-empty-id")
                    .spawnAndWait();

            // When/Then: Creating reference with empty ID should throw
            assertThatThrownBy(() -> parent.child(SimpleChildActor.class, ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("childId must not be null or empty");
        }

        @Test
        void testWithTimeoutThrowsOnNull(ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-timeout-null")
                    .spawnAndWait();

            // When/Then: Setting null timeout should throw
            assertThatThrownBy(() -> parent
                            .child(SimpleChildActor.class, "test-child")
                            .withTimeout(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("timeout must not be null");
        }

        @Test
        void testSpawnThrowsOnNullStrategy(ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-strategy-null")
                    .spawnAndWait();

            // When/Then: Spawning with null strategy should throw
            assertThatThrownBy(() -> parent
                            .child(SimpleChildActor.class, "test-child")
                            .spawn(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("strategy must not be null");
        }

        @Test
        void testGetOrSpawnThrowsOnNullStrategy(ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-getorspawn-strategy-null")
                    .spawnAndWait();

            // When/Then: getOrSpawn with null strategy should throw
            assertThatThrownBy(() -> parent
                            .child(SimpleChildActor.class, "test-child")
                            .getOrSpawn(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("strategy must not be null");
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ConciseAPIComparisonTests {

        @Test
        void testUnifiedAPIMoreConciseThanBuilder(ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-concise-comparison")
                    .spawnAndWait();

            // Unified API (concise) - single line for common case
            SpringActorRef<SimpleChildActor.Command> childUnified = parent
                    .child(SimpleChildActor.class, "concise-child")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // Builder API (verbose) - still available for advanced cases
            SpringActorRef<SimpleChildActor.Command> childBuilder = parent
                    .child(SimpleChildActor.class)
                    .withId("verbose-child")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // Both should work
            assertThat(childUnified).isNotNull();
            assertThat(childBuilder).isNotNull();
        }

        @Test
        void testUnifiedAPIEliminatesRedundancy(ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor with a spawned child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-redundancy")
                    .spawnAndWait();

            parent.child(SimpleChildActor.class, "redundancy-child")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // Unified API - class and ID specified once, used for all operations
            SpringChildActorReference<ParentActor.Command, SimpleChildActor.Command> childRef =
                    parent.child(SimpleChildActor.class, "redundancy-child");

            // All operations use the same reference
            Boolean exists = childRef.exists().toCompletableFuture().get();
            Optional<SpringActorRef<SimpleChildActor.Command>> gotten =
                    childRef.get().toCompletableFuture().get();
            SpringActorRef<SimpleChildActor.Command> gottenOrSpawned =
                    childRef.getOrSpawn().toCompletableFuture().get();

            // All should succeed
            assertThat(exists).isTrue();
            assertThat(gotten).isPresent();
            assertThat(gottenOrSpawned).isNotNull();
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class EdgeCaseTests {

        @Test
        void testMultipleChildrenWithDifferentIds(ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-multiple-children")
                    .spawnAndWait();

            // When: Spawning multiple children with different IDs using unified API
            SpringActorRef<SimpleChildActor.Command> child1 = parent
                    .child(SimpleChildActor.class, "child-1")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            SpringActorRef<SimpleChildActor.Command> child2 = parent
                    .child(SimpleChildActor.class, "child-2")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            SpringActorRef<SimpleChildActor.Command> child3 = parent
                    .child(SimpleChildActor.class, "child-3")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // Then: All children should be distinct and functional
            assertThat(child1.getUnderlying()).isNotEqualTo(child2.getUnderlying());
            assertThat(child2.getUnderlying()).isNotEqualTo(child3.getUnderlying());
            assertThat(child1.getUnderlying()).isNotEqualTo(child3.getUnderlying());

            // Verify all can respond
            assertThat(child1
                            .askBuilder(SimpleChildActor.Ping::new)
                            .withTimeout(Duration.ofSeconds(5))
                            .execute()
                            .toCompletableFuture()
                            .get())
                    .isEqualTo("pong");
            assertThat(child2
                            .askBuilder(SimpleChildActor.Ping::new)
                            .withTimeout(Duration.ofSeconds(5))
                            .execute()
                            .toCompletableFuture()
                            .get())
                    .isEqualTo("pong");
            assertThat(child3
                            .askBuilder(SimpleChildActor.Ping::new)
                            .withTimeout(Duration.ofSeconds(5))
                            .execute()
                            .toCompletableFuture()
                            .get())
                    .isEqualTo("pong");
        }

        @Test
        void testReferenceReuseForSameChild(ApplicationContext springContext)
                throws Exception {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentActor.Command> parent = actorSystem
                    .actor(ParentActor.class)
                    .withId("parent-reference-reuse")
                    .spawnAndWait();

            // Create multiple references to the same child (different reference objects)
            SpringActorRef<SimpleChildActor.Command> child1 = parent
                    .child(SimpleChildActor.class, "reuse-child")
                    .spawn()
                    .toCompletableFuture()
                    .get();

            SpringActorRef<SimpleChildActor.Command> child2 = parent
                    .child(SimpleChildActor.class, "reuse-child")
                    .getOrSpawn()
                    .toCompletableFuture()
                    .get();

            Optional<SpringActorRef<SimpleChildActor.Command>> child3 = parent
                    .child(SimpleChildActor.class, "reuse-child")
                    .get()
                    .toCompletableFuture()
                    .get();

            // All should point to the same actor
            assertThat(child1.getUnderlying()).isEqualTo(child2.getUnderlying());
            assertThat(child3).isPresent();
            assertThat(child1.getUnderlying()).isEqualTo(child3.get().getUnderlying());
        }
    }
}
