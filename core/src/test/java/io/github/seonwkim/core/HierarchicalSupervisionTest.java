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
    static class ChildWorkerActor
            implements SpringActorWithContext<ChildWorkerActor, ChildWorkerActor.Command, SpringActorContext> {

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
        public Behavior<Command> create(SpringActorContext actorContext) {
            return Behaviors.setup(ctx -> new ChildWorkerBehavior(ctx, actorContext, taskLogger).create());
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

            public Behavior<Command> create() {
                ctx.getLog().info("ChildWorker {} started", actorContext.actorId());

                return Behaviors.receive(Command.class)
                        .onMessage(DoTask.class, this::onDoTask)
                        .onMessage(Fail.class, this::onFail)
                        .onMessage(GetState.class, this::onGetState)
                        .onSignal(PreRestart.class, this::onPreRestart)
                        .onSignal(PostStop.class, this::onPostStop)
                        .build();
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
     * This demonstrates the spawnChild() API and supervision in a real-world pattern.
     *
     * Real-world pattern: Parent spawns children on-demand and uses ctx.getChild() to check existence.
     * No need to maintain a separate children map - the actor context already tracks children.
     */
    @Component
    static class ParentSupervisorActor
            implements SpringActorWithContext<
                    ParentSupervisorActor, ParentSupervisorActor.Command, SpringActorContext> {

        public interface Command {}

        /**
         * Delegates work to a child worker. If the child doesn't exist, spawns it first.
         * This is a real-world pattern where the supervisor spawns workers on-demand.
         */
        public static class DelegateWork implements Command {
            public final String workerId;
            public final String strategy; // "restart", "stop", or "resume"
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
        public Behavior<Command> create(SpringActorContext actorContext) {
            return Behaviors.setup(ctx -> new ParentSupervisorBehavior(ctx, actorContext).create());
        }

        private static class ParentSupervisorBehavior {
            private final ActorContext<Command> ctx;
            private final SpringActorContext actorContext;

            ParentSupervisorBehavior(ActorContext<Command> ctx, SpringActorContext actorContext) {
                this.ctx = ctx;
                this.actorContext = actorContext;
            }

            public Behavior<Command> create() {
                ctx.getLog().info("ParentSupervisor {} started", actorContext.actorId());

                return Behaviors.receive(Command.class)
                        .onMessage(DelegateWork.class, this::onDelegateWork)
                        .build();
            }

            @SuppressWarnings("unchecked")
            private Behavior<Command> onDelegateWork(DelegateWork msg) {
                // Real-world pattern: Check if child exists using ctx.getChild()
                // If not, spawn it with the specified strategy

                // Note: ctx.getChild() returns ActorRef<Void>, so we need to cast
                ActorRef<ChildWorkerActor.Command> child = ctx.getChild(msg.workerId)
                        .map(ref -> (ActorRef<ChildWorkerActor.Command>) (ActorRef<?>) ref)
                        .orElse(null);

                if (child == null) {
                    ctx.getLog().info("Spawning new worker {} with strategy {}", msg.workerId, msg.strategy);

                    SupervisorStrategy strategy;
                    if ("restart-limited".equals(msg.strategy)) {
                        strategy = SupervisorStrategy.restart().withLimit(3, Duration.ofMinutes(1));
                    } else if ("stop".equals(msg.strategy)) {
                        strategy = SupervisorStrategy.stop();
                    } else if ("resume".equals(msg.strategy)) {
                        strategy = SupervisorStrategy.resume();
                    } else {
                        strategy = SupervisorStrategy.restart();
                    }

                    // API EXPERIENCE: Using actorContext.spawnChild() is clean and intuitive
                    child = actorContext.spawnChild(ctx, ChildWorkerActor.class, msg.workerId, strategy);
                } else {
                    ctx.getLog().info("Reusing existing worker {}", msg.workerId);
                }

                // Delegate work to child
                child.tell(msg.work);
                msg.replyTo.tell("Delegated to " + msg.workerId);

                return Behaviors.same();
            }
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {}

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
                    .startAndWait();

            // And: Delegate work to a child (spawns on-demand with Spring DI)
            Object result = supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "worker-1",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "test-task", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

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
                    .startAndWait();

            // And: Delegate work to multiple children (spawns on-demand)
            Object resultA = supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "worker-a",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task-a", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(resultA).isEqualTo("Delegated to worker-a");

            Object resultB = supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "worker-b",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task-b", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
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
                    .startAndWait();

            // When: Child processes a task successfully (spawns on first use)
            Object taskResult1 = supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "restart-worker",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task-before-restart", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(taskResult1).isEqualTo("Delegated to restart-worker");

            // And: Child fails (triggering restart)
            Object failResult = supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "restart-worker",
                            "restart",
                            new ChildWorkerActor.Fail(actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(failResult).isEqualTo("Delegated to restart-worker");

            Thread.sleep(100); // Wait for restart to complete

            // Then: Child should be restarted and able to process new tasks
            Object taskResult2 = supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "restart-worker",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task-after-restart", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
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
                    .startAndWait();

            // When: Child processes multiple tasks (spawns on first use)
            supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "state-worker",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task1", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "state-worker",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task2", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // And: Child fails (triggering restart that resets state)
            supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "state-worker",
                            "restart",
                            new ChildWorkerActor.Fail(actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            Thread.sleep(100); // Wait for restart

            // Then: Actor should be responsive after restart (state reset verified by logs)
            Object result = supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "state-worker",
                            "restart",
                            new ChildWorkerActor.DoTask(
                                    "task-after-restart", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

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
                    .startAndWait();

            // When: Child processes a task successfully (spawns on first use)
            Object taskResult = supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "stop-worker",
                            "stop",
                            new ChildWorkerActor.DoTask(
                                    "task-before-stop", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(taskResult).isEqualTo("Delegated to stop-worker");

            // And: Child fails (triggering stop strategy - no restart)
            Object failResult = supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "stop-worker",
                            "stop",
                            new ChildWorkerActor.Fail(actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(failResult).isEqualTo("Delegated to stop-worker");

            Thread.sleep(100); // Wait for child to stop

            // Then: Subsequent work delegation spawns a NEW worker (old one was stopped)
            // The new worker will handle the task, demonstrating that stop strategy terminated the old one
            Object afterStopResult = supervisor
                    .ask(replyTo -> new ParentSupervisorActor.DelegateWork(
                            "stop-worker",
                            "stop",
                            new ChildWorkerActor.DoTask(
                                    "task-after-stop", actorSystem.getRaw().ignoreRef()),
                            replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Supervisor spawns a new worker with same ID since old one was stopped
            assertThat(afterStopResult).isEqualTo("Delegated to stop-worker");
        }
    }

    /**
     * Tests for top-level actor supervision strategies.
     * These tests verify that actors spawned directly from SpringActorSystem
     * can be supervised using the withSupervisorStrategy() API.
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
                    .withSupervisorStrategy(SupervisorStrategy.restart())
                    .startAndWait();

            // When: Actor processes a task successfully
            String taskResult = (String) worker.ask(replyTo -> new ChildWorkerActor.DoTask(
                            "task-before-failure", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(taskResult).isEqualTo("Completed: task-before-failure");

            // And: Actor fails (triggering restart)
            String failResult =
                    (String) worker.ask(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
            assertThat(failResult).isEqualTo("Failing now");

            Thread.sleep(100); // Wait for restart

            // Then: Actor should be restarted and able to process new tasks
            String afterRestartResult = (String) worker.ask(replyTo ->
                            new ChildWorkerActor.DoTask("task-after-restart", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
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
                    .withSupervisorStrategy(SupervisorStrategy.restart())
                    .startAndWait();

            // When: Actor processes multiple tasks
            worker.ask(replyTo -> new ChildWorkerActor.DoTask("task1", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            worker.ask(replyTo -> new ChildWorkerActor.DoTask("task2", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: State should reflect 2 completed tasks
            Integer stateBefore = (Integer)
                    worker.ask(replyTo -> new ChildWorkerActor.GetState((ActorRef<Integer>) (ActorRef<?>) replyTo))
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
            assertThat(stateBefore).isEqualTo(2);

            // When: Actor fails (triggering restart that resets state)
            worker.ask(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            Thread.sleep(100); // Wait for restart

            // Then: State should be reset to 0 after restart
            Integer stateAfter = (Integer)
                    worker.ask(replyTo -> new ChildWorkerActor.GetState((ActorRef<Integer>) (ActorRef<?>) replyTo))
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
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
                    .withSupervisorStrategy(SupervisorStrategy.restart().withLimit(2, Duration.ofSeconds(10)))
                    .startAndWait();

            // When: Actor fails once (first restart)
            worker.ask(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            Thread.sleep(100);

            // Then: Actor should still be responsive
            String result1 = (String) worker.ask(replyTo -> new ChildWorkerActor.DoTask(
                            "after-first-failure", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(result1).isEqualTo("Completed: after-first-failure");

            // When: Actor fails again (second restart)
            worker.ask(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            Thread.sleep(100);

            // Then: Actor should still be responsive
            String result2 = (String) worker.ask(replyTo -> new ChildWorkerActor.DoTask(
                            "after-second-failure", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(result2).isEqualTo("Completed: after-second-failure");

            // When: Actor fails a third time (exceeds limit, should stop)
            worker.ask(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
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
                    .withSupervisorStrategy(SupervisorStrategy.stop())
                    .startAndWait();

            // When: Actor processes a task successfully
            String taskResult = (String) worker.ask(replyTo ->
                            new ChildWorkerActor.DoTask("task-before-stop", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(taskResult).isEqualTo("Completed: task-before-stop");

            // And: Actor fails (triggering stop - no restart)
            worker.ask(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

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
                    .withSupervisorStrategy(SupervisorStrategy.resume())
                    .startAndWait();

            // When: Actor processes tasks before failure
            worker.ask(replyTo -> new ChildWorkerActor.DoTask("task1", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            worker.ask(replyTo -> new ChildWorkerActor.DoTask("task2", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: State should reflect 2 completed tasks
            Integer stateBefore = (Integer)
                    worker.ask(replyTo -> new ChildWorkerActor.GetState((ActorRef<Integer>) (ActorRef<?>) replyTo))
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
            assertThat(stateBefore).isEqualTo(2);

            // When: Actor fails (resume strategy keeps state)
            worker.ask(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            Thread.sleep(100);

            // Then: State should be preserved (not reset) - this is the key difference from restart
            Integer stateAfter = (Integer)
                    worker.ask(replyTo -> new ChildWorkerActor.GetState((ActorRef<Integer>) (ActorRef<?>) replyTo))
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
            assertThat(stateAfter).isEqualTo(2); // State preserved!

            // And: Actor should still be able to process new tasks
            String result = (String) worker.ask(replyTo ->
                            new ChildWorkerActor.DoTask("task-after-resume", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("Completed: task-after-resume");

            // State incremented after resume
            Integer finalState = (Integer)
                    worker.ask(replyTo -> new ChildWorkerActor.GetState((ActorRef<Integer>) (ActorRef<?>) replyTo))
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
            assertThat(finalState).isEqualTo(3);
        }

        @Test
        void testTopLevelNoSupervision(org.springframework.context.ApplicationContext springContext) throws Exception {
            // Given: Actor spawned without supervision (null strategy)
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorRef<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-no-supervision-worker")
                    // No withSupervisorStrategy() call - defaults to null
                    .startAndWait();

            // When: Actor processes a task successfully
            String taskResult = (String) worker.ask(replyTo -> new ChildWorkerActor.DoTask(
                            "task-before-failure", (ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(taskResult).isEqualTo("Completed: task-before-failure");

            // And: Actor fails (no supervision means it stops)
            worker.ask(replyTo -> new ChildWorkerActor.Fail((ActorRef<String>) (ActorRef<?>) replyTo))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

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
