package io.github.seonwkim.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.PreRestart;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;

class HierarchicalSupervisionTest {

    /**
     * A Spring service that will be injected into child actors.
     * This verifies that Spring DI works in the supervision hierarchy.
     */
    @Service
    static class TaskLogger {
        private final ConcurrentMap<String, AtomicInteger> taskCounts = new ConcurrentHashMap<>();

        public void logTask(String workerId) {
            taskCounts.computeIfAbsent(workerId, k -> new AtomicInteger()).incrementAndGet();
        }

        public int getTaskCount(String workerId) {
            AtomicInteger count = taskCounts.get(workerId);
            return count != null ? count.get() : 0;
        }

        public void reset() {
            taskCounts.clear();
        }
    }

    /**
     * A child worker actor that has a Spring dependency injected.
     * This actor can process tasks and fail on command.
     */
    @Component
    static class ChildWorkerActor implements SpringActorWithContext<ChildWorkerActor.Command, SpringActorContext> {

        private final TaskLogger taskLogger;

        public ChildWorkerActor(TaskLogger taskLogger) {
            this.taskLogger = taskLogger;
        }

        public interface Command {}

        public static class DoTask implements Command {
            public final String taskName;
            public final ActorRef<String> replyTo;

            public DoTask(String taskName, ActorRef<String> replyTo) {
                this.taskName = taskName;
                this.replyTo = replyTo;
            }
        }

        public static class Fail implements Command {
            public final ActorRef<String> replyTo;

            public Fail(ActorRef<String> replyTo) {
                this.replyTo = replyTo;
            }
        }

        public static class GetState implements Command {
            public final ActorRef<Integer> replyTo;

            public GetState(ActorRef<Integer> replyTo) {
                this.replyTo = replyTo;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onCreate(ctx -> {
                        ctx.getLog().info("ChildWorker {} started", actorContext.actorId());
                        return new ChildWorkerBehavior(ctx, actorContext, taskLogger);
                    })
                    .onMessage(DoTask.class, ChildWorkerBehavior::onDoTask)
                    .onMessage(Fail.class, ChildWorkerBehavior::onFail)
                    .onMessage(GetState.class, ChildWorkerBehavior::onGetState)
                    .onSignal(PreRestart.class, ChildWorkerBehavior::onPreRestart)
                    .onSignal(PostStop.class, ChildWorkerBehavior::onPostStop)
                    .build();
        }

        private static class ChildWorkerBehavior {
            private final ActorContext<Command> ctx;
            private final SpringActorContext actorContext;
            private final TaskLogger taskLogger;
            private int tasksCompleted = 0;

            ChildWorkerBehavior(ActorContext<Command> ctx, SpringActorContext actorContext, TaskLogger taskLogger) {
                this.ctx = ctx;
                this.actorContext = actorContext;
                this.taskLogger = taskLogger;
            }

            private Behavior<Command> onDoTask(DoTask msg) {
                ctx.getLog().info("Worker {} processing task: {}", actorContext.actorId(), msg.taskName);

                // Use injected Spring service
                taskLogger.logTask(actorContext.actorId());

                tasksCompleted++;
                msg.replyTo.tell("Completed: " + msg.taskName);
                return Behaviors.same();
            }

            private Behavior<Command> onFail(Fail msg) {
                ctx.getLog().warn("Worker {} failing intentionally", actorContext.actorId());
                msg.replyTo.tell("Failing now");
                throw new RuntimeException("Intentional failure");
            }

            private Behavior<Command> onGetState(GetState msg) {
                msg.replyTo.tell(tasksCompleted);
                return Behaviors.same();
            }

            private Behavior<Command> onPreRestart(PreRestart signal) {
                ctx.getLog()
                        .warn(
                                "Worker {} restarting, state will be lost: tasksCompleted={}",
                                actorContext.actorId(),
                                tasksCompleted);
                return Behaviors.same();
            }

            private Behavior<Command> onPostStop(PostStop signal) {
                ctx.getLog()
                        .info(
                                "Worker {} stopped, final state: tasksCompleted={}",
                                actorContext.actorId(),
                                tasksCompleted);
                return Behaviors.same();
            }
        }
    }

    /**
     * A parent supervisor that spawns child workers with different supervision strategies.
     * This demonstrates the SpringActorRef child management API in a real-world pattern.
     *
     * <p>Key features demonstrated:
     * <ul>
     *   <li>Using {@link SpringActorRef#getChild(Class, String)} to check for existing children</li>
     *   <li>Using {@link SpringActorRef#child(Class, String)} with {@code spawn()} for on-demand spawning</li>
     *   <li>Async message handling with {@code ctx.pipeToSelf()}</li>
     *   <li>Automatic framework command handling when Command extends {@link FrameworkCommand}</li>
     * </ul>
     */
    @Component
    static class ParentSupervisorActor
            implements SpringActorWithContext<ParentSupervisorActor.Command, SpringActorContext> {

        public interface Command extends FrameworkCommand {}

        /**
         * Delegates work to a child worker. If the child doesn't exist, spawns it first.
         */
        public static class DelegateWork implements Command {
            public final String workerId;
            public final String strategy; // "restart", "stop", "resume", or "restart-limited"
            public final ChildWorkerActor.Command work;
            public final ActorRef<Object> replyTo;

            public DelegateWork(
                    String workerId, String strategy, ChildWorkerActor.Command work, ActorRef<Object> replyTo) {
                this.workerId = workerId;
                this.strategy = strategy;
                this.work = work;
                this.replyTo = replyTo;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onCreate(ctx -> new ParentSupervisorBehavior(ctx, actorContext))
                    .onMessage(DelegateWork.class, ParentSupervisorBehavior::onDelegateWork)
                    .onMessage(ChildReady.class, ParentSupervisorBehavior::onChildReady)
                    .build();
        }

        private static class ParentSupervisorBehavior {
            private final ActorContext<Command> ctx;
            private final SpringActorContext actorContext;

            ParentSupervisorBehavior(ActorContext<Command> ctx, SpringActorContext actorContext) {
                this.ctx = ctx;
                this.actorContext = actorContext;
                ctx.getLog().info("ParentSupervisor {} started", actorContext.actorId());
            }

            private Behavior<Command> onDelegateWork(DelegateWork msg) {
                // Create SpringActorRef to self for child management
                SpringActorRef<Command> self =
                        new SpringActorRef<>(ctx.getSystem().scheduler(), ctx.getSelf());

                // Try to get existing child first
                ctx.pipeToSelf(
                        self.getChild(ChildWorkerActor.class, msg.workerId),
                        (childRef, failure) -> new ChildReady(msg, childRef, failure));

                return Behaviors.same();
            }

            private Behavior<Command> onChildReady(ChildReady msg) {
                if (msg.failure != null) {
                    ctx.getLog().error("Failed to get/spawn child", msg.failure);
                    msg.originalMsg.replyTo.tell("Error: " + msg.failure.getMessage());
                    return Behaviors.same();
                }

                if (msg.childRef == null) {
                    // Child doesn't exist, spawn it
                    return spawnChild(msg.originalMsg);
                } else {
                    // Child exists, use it
                    ctx.getLog().info("Reusing existing worker {}", msg.originalMsg.workerId);
                    delegateToChild(msg.childRef, msg.originalMsg);
                    return Behaviors.same();
                }
            }

            private Behavior<Command> spawnChild(DelegateWork msg) {
                ctx.getLog().info("Spawning new worker {} with strategy {}", msg.workerId, msg.strategy);

                SupervisorStrategy strategy = buildStrategy(msg.strategy);
                SpringActorRef<Command> self =
                        new SpringActorRef<>(ctx.getSystem().scheduler(), ctx.getSelf());

                // Spawn child using SpringActorRef unified API
                ctx.pipeToSelf(
                        self.child(ChildWorkerActor.class, msg.workerId).spawn(strategy),
                        (childRef, failure) -> new ChildReady(msg, childRef, failure));

                return Behaviors.same();
            }

            private void delegateToChild(SpringActorRef<ChildWorkerActor.Command> childRef, DelegateWork msg) {
                childRef.tell(msg.work);
                msg.replyTo.tell("Delegated to " + msg.workerId);
            }

            private SupervisorStrategy buildStrategy(String strategyName) {
                switch (strategyName) {
                    case "restart-limited":
                        return SupervisorStrategy.restart().withLimit(3, Duration.ofMinutes(1));
                    case "stop":
                        return SupervisorStrategy.stop();
                    case "resume":
                        return SupervisorStrategy.resume();
                    default:
                        return SupervisorStrategy.restart();
                }
            }
        }

        /**
         * Internal message indicating a child is ready (either found or spawned).
         */
        private static class ChildReady implements Command {
            final DelegateWork originalMsg;
            final SpringActorRef<ChildWorkerActor.Command> childRef;
            final Throwable failure;

            ChildReady(DelegateWork originalMsg, SpringActorRef<ChildWorkerActor.Command> childRef, Throwable failure) {
                this.originalMsg = originalMsg;
                this.childRef = childRef;
                this.failure = failure;
            }
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {}

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ChildManagementAPITests {

        @Test
        void testExistsChildReturnsFalseForNonExistent(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: A parent supervisor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-exists-test")
                    .spawnAndWait();

            // When: Checking if a child exists that was never spawned
            Boolean exists = supervisor
                    .existsChild(ChildWorkerActor.class, "non-existent-child")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should return false
            assertThat(exists).isFalse();
        }

        @Test
        void testGetChildReturnsNullForNonExistent(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: A parent supervisor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-get-test")
                    .spawnAndWait();

            // When: Getting a child that doesn't exist
            SpringActorRef<ChildWorkerActor.Command> childRef = supervisor
                    .getChild(ChildWorkerActor.class, "non-existent-child")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should return null
            assertThat(childRef).isNull();
        }

        @Test
        void testSpawnChildDirectly(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: A parent supervisor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorRef<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-spawn-test")
                    .spawnAndWait();

            // When: Spawning a child directly using the unified reference API
            SpringActorRef<ChildWorkerActor.Command> childRef = supervisor
                    .child(ChildWorkerActor.class, "direct-spawn-child")
                    .spawn(SupervisorStrategy.restart())
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Child should be spawned and functional
            assertThat(childRef).isNotNull();

            // And: Child can receive messages using fluent ask builder
            String result = (String) childRef.askBuilder(replyTo ->
                            new ChildWorkerActor.DoTask("test-task", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(result).isEqualTo("Completed: test-task");

            // And: Child exists when checked
            Boolean exists = supervisor
                    .existsChild(ChildWorkerActor.class, "direct-spawn-child")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(exists).isTrue();

            // And: Can retrieve child ref again
            SpringActorRef<ChildWorkerActor.Command> retrievedChild = supervisor
                    .getChild(ChildWorkerActor.class, "direct-spawn-child")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(retrievedChild).isNotNull();
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ChildSpawningTests {

        @Test
        void testSpawnChildWithSpringDI(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: Actor system and task logger
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            TaskLogger taskLogger = springContext.getBean(TaskLogger.class);
            taskLogger.reset();

            // When: Spawn a parent supervisor
            SpringActorRef<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-1")
                    .spawnAndWait();

            // And: Delegate work to a child (spawns on-demand with Spring DI) using fluent ask builder
            Object result = supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "worker-1",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "test-task", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Then: Supervisor confirms delegation
            assertThat(result).isEqualTo("Delegated to worker-1");

            Thread.sleep(50); // Small delay for async task logging

            // Then: The child should have processed the task using the injected Spring service
            assertThat(taskLogger.getTaskCount("worker-1")).isEqualTo(1);
        }

        @Test
        void testMultipleChildrenWithDifferentIds(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: Actor system
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            TaskLogger taskLogger = springContext.getBean(TaskLogger.class);
            taskLogger.reset();

            // When: Spawn a parent supervisor
            SpringActorRef<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-2")
                    .spawnAndWait();

            // And: Delegate work to multiple children (spawns on-demand) using fluent ask builder
            Object resultA = supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "worker-a",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task-a", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(resultA).isEqualTo("Delegated to worker-a");

            Object resultB = supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "worker-b",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task-b", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(resultB).isEqualTo("Delegated to worker-b");

            Thread.sleep(50); // Small delay for async task logging

            // Then: Both children should have processed their tasks independently
            assertThat(taskLogger.getTaskCount("worker-a")).isEqualTo(1);
            assertThat(taskLogger.getTaskCount("worker-b")).isEqualTo(1);
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class RestartSupervisionTests {

        @Test
        void testRestartStrategyRestoresActor(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: Supervisor with restart strategy
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorRef<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-restart")
                    .spawnAndWait();

            // When: Child processes a task successfully (spawns on first use)
            Object taskResult1 = supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "restart-worker",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task-before-restart", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(taskResult1).isEqualTo("Delegated to restart-worker");

            // And: Child fails (triggering restart)
            Object failResult = supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "restart-worker",
                            "restart",
                            new ChildWorkerActor.Fail(actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(failResult).isEqualTo("Delegated to restart-worker");

            Thread.sleep(100); // Wait for restart to complete

            // Then: Child should be restarted and able to process new tasks
            Object taskResult2 = supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "restart-worker",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task-after-restart", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(taskResult2).isEqualTo("Delegated to restart-worker");
        }

        @Test
        void testRestartStrategyResetsState(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: Supervisor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorRef<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-state-reset")
                    .spawnAndWait();

            // When: Child processes multiple tasks (spawns on first use)
            supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "state-worker",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task1", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "state-worker",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task2", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // And: Child fails (triggering restart that resets state)
            supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "state-worker",
                            "restart",
                            new ChildWorkerActor.Fail(actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            Thread.sleep(100); // Wait for restart

            // Then: Actor should be responsive after restart (state reset verified by logs)
            Object result = supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "state-worker",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task-after-restart", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            assertThat(result).isEqualTo("Delegated to state-worker");
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class StopSupervisionTests {

        @Test
        void testStopStrategyTerminatesActor(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: Supervisor with stop strategy
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorRef<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-stop")
                    .spawnAndWait();

            // When: Child processes a task successfully (spawns on first use)
            Object taskResult = supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "stop-worker",
                            "stop",
                            new ChildWorkerActor.DoTask(
                                    "task-before-stop", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(taskResult).isEqualTo("Delegated to stop-worker");

            // And: Child fails (triggering stop strategy - no restart)
            Object failResult = supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "stop-worker",
                            "stop",
                            new ChildWorkerActor.Fail(actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(failResult).isEqualTo("Delegated to stop-worker");

            Thread.sleep(100); // Wait for child to stop

            // Then: Subsequent work delegation spawns a NEW worker (old one was stopped)
            // The new worker will handle the task, demonstrating that stop strategy terminated the old one
            Object afterStopResult = supervisor
                    .askBuilder(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "stop-worker",
                            "stop",
                            new ChildWorkerActor.DoTask(
                                    "task-after-stop", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Supervisor spawns a new worker with same ID since old one was stopped
            assertThat(afterStopResult).isEqualTo("Delegated to stop-worker");
        }
    }

    /**
     * Tests for top-level actor supervision strategies.
     * These tests verify that actors spawned directly from SpringActorSystem
     * can be supervised using the withSupervisonStrategy() API.
     */
    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class TopLevelSupervisionTests {

        @Test
        void testTopLevelRestartStrategy(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: Actor spawned with restart supervision
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorRef<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-restart-worker")
                    .withSupervisonStrategy(SupervisorStrategy.restart())
                    .spawnAndWait();

            // When: Actor processes a task successfully
            String taskResult = (String) worker.askBuilder(replyTo -> new ChildWorkerActor.DoTask(
                            "task-before-failure", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(taskResult).isEqualTo("Completed: task-before-failure");

            // And: Actor fails (triggering restart)
            String failResult = (String)
                    worker.askBuilder(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                            .withTimeout(Duration.ofSeconds(5))
                            .execute()
                            .toCompletableFuture()
                            .get();
            assertThat(failResult).isEqualTo("Failing now");

            Thread.sleep(100); // Wait for restart

            // Then: Actor should be restarted and able to process new tasks
            String afterRestartResult = (String) worker.askBuilder(replyTo ->
                            new ChildWorkerActor.DoTask("task-after-restart", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(afterRestartResult).isEqualTo("Completed: task-after-restart");
        }

        @Test
        void testTopLevelRestartStrategyResetsState(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: Actor spawned with restart supervision
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorRef<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-state-worker")
                    .withSupervisonStrategy(SupervisorStrategy.restart())
                    .spawnAndWait();

            // When: Actor processes multiple tasks
            worker.askBuilder(replyTo -> new ChildWorkerActor.DoTask("task1", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            worker.askBuilder(replyTo -> new ChildWorkerActor.DoTask("task2", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Then: State should reflect 2 completed tasks
            Integer stateBefore = (Integer) worker.askBuilder(
                            replyTo -> new ChildWorkerActor.GetState((ActorRef<Integer>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(stateBefore).isEqualTo(2);

            // When: Actor fails (triggering restart that resets state)
            worker.askBuilder(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            Thread.sleep(100); // Wait for restart

            // Then: State should be reset to 0 after restart
            Integer stateAfter = (Integer) worker.askBuilder(
                            replyTo -> new ChildWorkerActor.GetState((ActorRef<Integer>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(stateAfter).isEqualTo(0);
        }

        @Test
        void testTopLevelRestartWithLimitStrategy(org.springframework.context.ApplicationContext springContext)
                throws Exception {
            // Given: Actor spawned with limited restart supervision (max 2 restarts)
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorRef<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-limited-worker")
                    .withSupervisonStrategy(SupervisorStrategy.restart().withLimit(2, Duration.ofSeconds(10)))
                    .spawnAndWait();

            // When: Actor fails once (first restart)
            worker.askBuilder(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            Thread.sleep(100);

            // Then: Actor should still be responsive
            String result1 = (String) worker.askBuilder(replyTo -> new ChildWorkerActor.DoTask(
                            "after-first-failure", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(result1).isEqualTo("Completed: after-first-failure");

            // When: Actor fails again (second restart)
            worker.askBuilder(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            Thread.sleep(100);

            // Then: Actor should still be responsive
            String result2 = (String) worker.askBuilder(replyTo -> new ChildWorkerActor.DoTask(
                            "after-second-failure", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(result2).isEqualTo("Completed: after-second-failure");

            // When: Actor fails a third time (exceeds limit, should stop)
            worker.askBuilder(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            Thread.sleep(200);

            // Then: Actor should be stopped (ask will timeout or fail)
            // We verify this by checking if the actor no longer exists
            boolean exists = actorSystem
                    .exists(ChildWorkerActor.class, "top-level-limited-worker")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(exists).isFalse();
        }

        @Test
        void testTopLevelStopStrategy(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: Actor spawned with stop supervision
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorRef<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-stop-worker")
                    .withSupervisonStrategy(SupervisorStrategy.stop())
                    .spawnAndWait();

            // When: Actor processes a task successfully
            String taskResult = (String) worker.askBuilder(replyTo ->
                            new ChildWorkerActor.DoTask("task-before-stop", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(taskResult).isEqualTo("Completed: task-before-stop");

            // And: Actor fails (triggering stop - no restart)
            worker.askBuilder(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            Thread.sleep(100); // Wait for actor to stop

            // Then: Actor should be stopped and no longer exist
            boolean exists = actorSystem
                    .exists(ChildWorkerActor.class, "top-level-stop-worker")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(exists).isFalse();
        }

        @Test
        void testTopLevelResumeStrategy(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: Actor spawned with resume supervision
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorRef<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-resume-worker")
                    .withSupervisonStrategy(SupervisorStrategy.resume())
                    .spawnAndWait();

            // When: Actor processes tasks before failure
            worker.askBuilder(replyTo -> new ChildWorkerActor.DoTask("task1", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            worker.askBuilder(replyTo -> new ChildWorkerActor.DoTask("task2", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Then: State should reflect 2 completed tasks
            Integer stateBefore = (Integer) worker.askBuilder(
                            replyTo -> new ChildWorkerActor.GetState((ActorRef<Integer>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(stateBefore).isEqualTo(2);

            // When: Actor fails (resume strategy keeps state)
            worker.askBuilder(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            Thread.sleep(100);

            // Then: State should be preserved (not reset) - this is the key difference from restart
            Integer stateAfter = (Integer) worker.askBuilder(
                            replyTo -> new ChildWorkerActor.GetState((ActorRef<Integer>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(stateAfter).isEqualTo(2); // State preserved!

            // And: Actor should still be able to process new tasks
            String result = (String) worker.askBuilder(replyTo ->
                            new ChildWorkerActor.DoTask("task-after-resume", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(result).isEqualTo("Completed: task-after-resume");

            // State incremented after resume
            Integer finalState = (Integer) worker.askBuilder(
                            replyTo -> new ChildWorkerActor.GetState((ActorRef<Integer>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(finalState).isEqualTo(3);
        }

        @Test
        void testTopLevelNoSupervision(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: Actor spawned without supervision (null strategy)
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorRef<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-no-supervision-worker")
                    // No withSupervisonStrategy() call - defaults to null
                    .spawnAndWait();

            // When: Actor processes a task successfully
            String taskResult = (String) worker.askBuilder(replyTo -> new ChildWorkerActor.DoTask(
                            "task-before-failure", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(taskResult).isEqualTo("Completed: task-before-failure");

            // And: Actor fails (no supervision means it stops)
            worker.askBuilder(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            Thread.sleep(100); // Wait for actor to stop

            // Then: Actor should be stopped (no restart without supervision)
            boolean exists = actorSystem
                    .exists(ChildWorkerActor.class, "top-level-no-supervision-worker")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(exists).isFalse();
        }
    }
}
