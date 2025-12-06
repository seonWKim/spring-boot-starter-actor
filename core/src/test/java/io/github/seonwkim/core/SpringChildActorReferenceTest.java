package io.github.seonwkim.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for SpringChildActorReference - pure referencing operations for existing child actors.
 * This class tests get() and exists() operations only.
 * For spawning child actors, use SpringChildActorBuilder (tested in SpringChildActorBuilderTest).
 */
class SpringChildActorReferenceTest {

    /**
     * A simple child actor for testing reference operations.
     */
    @Component
    static class ReferenceTestChildActor
            implements SpringActorWithContext<ReferenceTestChildActor.Command, SpringActorContext> {

        public interface Command {}

        public static class Ping extends AskCommand<String> implements Command {
            public Ping() {}
        }

        public static class GetId extends AskCommand<String> implements Command {
            public GetId() {}
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new SimpleChildBehavior(actorContext))
                    .onMessage(Ping.class, SimpleChildBehavior::onPing)
                    .onMessage(GetId.class, SimpleChildBehavior::onGetId)
                    .build();
        }

        private static class SimpleChildBehavior {
            private final SpringActorContext actorContext;

            SimpleChildBehavior(SpringActorContext actorContext) {
                this.actorContext = actorContext;
            }

            private Behavior<Command> onPing(Ping msg) {
                msg.reply("pong");
                return Behaviors.same();
            }

            private Behavior<Command> onGetId(GetId msg) {
                msg.reply(actorContext.actorId());
                return Behaviors.same();
            }
        }
    }

    /**
     * A parent actor that supports framework commands for child management.
     */
    @Component
    static class ReferenceTestParentActor
            implements SpringActorWithContext<ReferenceTestParentActor.Command, SpringActorContext> {

        public interface Command extends FrameworkCommand {}

        public static class DoNothing implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(DoNothing.class, (ctx, msg) -> Behaviors.same())
                    .build();
        }
    }

    @SpringBootApplication
    @Import({ReferenceTestChildActor.class, ReferenceTestParentActor.class})
    static class ReferenceTestApp {}

    @Nested
    @SpringBootTest(classes = ReferenceTestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class GetOperationTests {

        @Test
        void testGetReturnsOptionalWithExistingChild(ApplicationContext springContext) throws Exception {
            // Given: A parent actor with an existing child (spawned using builder)
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorHandle<ReferenceTestParentActor.Command> parent = actorSystem
                    .actor(ReferenceTestParentActor.class)
                    .withId("parent-get-existing")
                    .spawnAndWait();

            // Spawn a child using the builder API
            SpringActorHandle<ReferenceTestChildActor.Command> spawnedChild = parent.child(ReferenceTestChildActor.class)
                    .withId("existing-child")
                    .spawnAndWait();

            // When: Getting the child using the reference API
            Optional<SpringActorHandle<ReferenceTestChildActor.Command>> retrievedChild = parent.child(
                            ReferenceTestChildActor.class, "existing-child")
                    .get()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should retrieve the child in an Optional
            assertThat(retrievedChild).isPresent();
            assertThat(retrievedChild.get().getUnderlying()).isEqualTo(spawnedChild.getUnderlying());
        }

        @Test
        void testGetReturnsEmptyOptionalForNonExistent(ApplicationContext springContext) throws Exception {
            // Given: A parent actor without children
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorHandle<ReferenceTestParentActor.Command> parent = actorSystem
                    .actor(ReferenceTestParentActor.class)
                    .withId("parent-get-empty")
                    .spawnAndWait();

            // When: Getting a non-existent child
            Optional<SpringActorHandle<ReferenceTestChildActor.Command>> child = parent.child(
                            ReferenceTestChildActor.class, "non-existent-child")
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
            SpringActorHandle<ReferenceTestParentActor.Command> parent = actorSystem
                    .actor(ReferenceTestParentActor.class)
                    .withId("parent-get-timeout")
                    .spawnAndWait();

            parent.child(ReferenceTestChildActor.class).withId("timeout-child").spawnAndWait();

            // When: Getting with custom timeout
            Optional<SpringActorHandle<ReferenceTestChildActor.Command>> child = parent.child(
                            ReferenceTestChildActor.class, "timeout-child")
                    .withTimeout(Duration.ofSeconds(10))
                    .get()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should succeed
            assertThat(child).isPresent();
        }
    }

    @Nested
    @SpringBootTest(classes = ReferenceTestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ExistsOperationTests {

        @Test
        void testExistsReturnsTrueForExistingChild(ApplicationContext springContext) throws Exception {
            // Given: A parent actor with an existing child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorHandle<ReferenceTestParentActor.Command> parent = actorSystem
                    .actor(ReferenceTestParentActor.class)
                    .withId("parent-exists-true")
                    .spawnAndWait();

            parent.child(ReferenceTestChildActor.class)
                    .withId("existing-child-check")
                    .spawnAndWait();

            // When: Checking if the child exists
            Boolean exists = parent.child(ReferenceTestChildActor.class, "existing-child-check")
                    .exists()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should return true
            assertThat(exists).isTrue();
        }

        @Test
        void testExistsReturnsFalseForNonExistent(ApplicationContext springContext) throws Exception {
            // Given: A parent actor without children
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorHandle<ReferenceTestParentActor.Command> parent = actorSystem
                    .actor(ReferenceTestParentActor.class)
                    .withId("parent-exists-false")
                    .spawnAndWait();

            // When: Checking if a non-existent child exists
            Boolean exists = parent.child(ReferenceTestChildActor.class, "non-existent-check")
                    .exists()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should return false
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @SpringBootTest(classes = ReferenceTestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ValidationTests {

        @Test
        void testNullChildIdThrowsException(ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorHandle<ReferenceTestParentActor.Command> parent = actorSystem
                    .actor(ReferenceTestParentActor.class)
                    .withId("parent-null-id")
                    .spawnAndWait();

            // When/Then: Creating reference with null ID should throw
            assertThatThrownBy(() -> parent.child(ReferenceTestChildActor.class, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("childId must not be null or empty");
        }

        @Test
        void testEmptyChildIdThrowsException(ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorHandle<ReferenceTestParentActor.Command> parent = actorSystem
                    .actor(ReferenceTestParentActor.class)
                    .withId("parent-empty-id")
                    .spawnAndWait();

            // When/Then: Creating reference with empty ID should throw
            assertThatThrownBy(() -> parent.child(ReferenceTestChildActor.class, ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("childId must not be null or empty");
        }

        @Test
        void testWithTimeoutThrowsOnNull(ApplicationContext springContext) {
            // Given: A parent actor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorHandle<ReferenceTestParentActor.Command> parent = actorSystem
                    .actor(ReferenceTestParentActor.class)
                    .withId("parent-timeout-null")
                    .spawnAndWait();

            // When/Then: Setting null timeout should throw
            assertThatThrownBy(() -> parent.child(ReferenceTestChildActor.class, "test-child")
                            .withTimeout(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("timeout must not be null");
        }
    }

    @Nested
    @SpringBootTest(classes = ReferenceTestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ReferenceReuseTests {

        @Test
        void testReferenceCanBeReusedForMultipleOperations(ApplicationContext springContext) throws Exception {
            // Given: A parent actor with a spawned child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorHandle<ReferenceTestParentActor.Command> parent = actorSystem
                    .actor(ReferenceTestParentActor.class)
                    .withId("parent-reuse")
                    .spawnAndWait();

            parent.child(ReferenceTestChildActor.class).withId("reuse-child").spawnAndWait();

            // When: Creating a single reference and using it for multiple operations
            SpringChildActorReference<ReferenceTestParentActor.Command, ReferenceTestChildActor.Command> childRef =
                    parent.child(ReferenceTestChildActor.class, "reuse-child");

            // All operations use the same reference
            Boolean exists = childRef.exists().toCompletableFuture().get();
            Optional<SpringActorHandle<ReferenceTestChildActor.Command>> gotten =
                    childRef.get().toCompletableFuture().get();

            // Then: All should succeed
            assertThat(exists).isTrue();
            assertThat(gotten).isPresent();
        }

        @Test
        void testMultipleReferencesToSameChildPointToSameActor(ApplicationContext springContext) throws Exception {
            // Given: A parent actor with a spawned child
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorHandle<ReferenceTestParentActor.Command> parent = actorSystem
                    .actor(ReferenceTestParentActor.class)
                    .withId("parent-multiple-refs")
                    .spawnAndWait();

            parent.child(ReferenceTestChildActor.class).withId("same-child").spawnAndWait();

            // When: Creating multiple references to the same child
            Optional<SpringActorHandle<ReferenceTestChildActor.Command>> child1 = parent.child(
                            ReferenceTestChildActor.class, "same-child")
                    .get()
                    .toCompletableFuture()
                    .get();

            Optional<SpringActorHandle<ReferenceTestChildActor.Command>> child2 = parent.child(
                            ReferenceTestChildActor.class, "same-child")
                    .get()
                    .toCompletableFuture()
                    .get();

            // Then: All should point to the same actor
            assertThat(child1).isPresent();
            assertThat(child2).isPresent();
            assertThat(child1.get().getUnderlying()).isEqualTo(child2.get().getUnderlying());
        }
    }
}
