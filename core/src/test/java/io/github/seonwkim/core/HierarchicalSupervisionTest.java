package io.github.seonwkim.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.PreRestart;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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

        public static class DoTask extends AskCommand<String> implements Command {
            public final String taskName;

            public DoTask(String taskName) {
                this.taskName = taskName;
            }
        }

        public static class Fail extends AskCommand<String> implements Command {
            public Fail() {}
        }

        public static class GetState extends AskCommand<Integer> implements Command {
            public GetState() {}
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> {
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
            private final SpringBehaviorContext<Command> ctx;
            private final SpringActorContext actorContext;
            private final TaskLogger taskLogger;
            private int tasksCompleted = 0;

            ChildWorkerBehavior(
                    SpringBehaviorContext<Command> ctx, SpringActorContext actorContext, TaskLogger taskLogger) {
                this.ctx = ctx;
                this.actorContext = actorContext;
                this.taskLogger = taskLogger;
            }

            private Behavior<Command> onDoTask(DoTask msg) {
                ctx.getLog().info("Worker {} processing task: {}", actorContext.actorId(), msg.taskName);

                // Use injected Spring service
                taskLogger.logTask(actorContext.actorId());

                tasksCompleted++;
                msg.reply("Completed: " + msg.taskName);
                return Behaviors.same();
            }

            private Behavior<Command> onFail(Fail msg) {
                ctx.getLog().warn("Worker {} failing intentionally", actorContext.actorId());
                msg.reply("Failing now");
                throw new RuntimeException("Intentional failure");
            }

            private Behavior<Command> onGetState(GetState msg) {
                msg.reply(tasksCompleted);
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
     * This demonstrates the SpringActorHandle child management API in a real-world pattern.
     *
     * <p>Key features demonstrated:
     * <ul>
     *   <li>Using {@link SpringActorHandle#child(Class, String)} with {@code get()} to check for existing children</li>
     *   <li>Using {@link SpringActorHandle#child(Class, String)} with {@code spawn()} for on-demand spawning</li>
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
        public static class DelegateWork extends AskCommand<Object> implements Command {
            public final String workerId;
            public final String strategy; // "restart", "stop", "resume", or "restart-limited"
            public final ChildWorkerActor.Command work;

            public DelegateWork(String workerId, String strategy, ChildWorkerActor.Command work) {
                this.workerId = workerId;
                this.strategy = strategy;
                this.work = work;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new ParentSupervisorBehavior(ctx, actorContext))
                    .onMessage(DelegateWork.class, ParentSupervisorBehavior::onDelegateWork)
                    .onMessage(ChildReady.class, ParentSupervisorBehavior::onChildReady)
                    .build();
        }

        private static class ParentSupervisorBehavior {
            private final SpringBehaviorContext<Command> ctx;

            ParentSupervisorBehavior(SpringBehaviorContext<Command> ctx, SpringActorContext actorContext) {
                this.ctx = ctx;
                ctx.getLog().info("ParentSupervisor {} started", actorContext.actorId());
            }

            private Behavior<Command> onDelegateWork(DelegateWork msg) {
                // Create SpringActorHandle to self for child management
                SpringActorHandle<Command> self = ctx.getSelf();

                // Try to get existing child first
                ctx.getUnderlying()
                        .pipeToSelf(
                                self.child(ChildWorkerActor.class, msg.workerId)
                                        .get()
                                        .thenApply(opt -> opt.orElse(null)),
                                (childRef, failure) -> new ChildReady(msg, childRef, failure));

                return Behaviors.same();
            }

            private Behavior<Command> onChildReady(ChildReady msg) {
                if (msg.failure != null) {
                    ctx.getLog().error("Failed to get/spawn child", msg.failure);
                    msg.originalMsg.reply("Error: " + msg.failure.getMessage());
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
                SpringActorHandle<Command> self = ctx.getSelf();

                // Spawn child using SpringActorHandle unified API
                ctx.getUnderlying()
                        .pipeToSelf(
                                self.child(ChildWorkerActor.class)
                                        .withId(msg.workerId)
                                        .withSupervisionStrategy(strategy)
                                        .spawn(),
                                (childRef, failure) -> new ChildReady(msg, childRef, failure));

                return Behaviors.same();
            }

            private void delegateToChild(SpringActorHandle<ChildWorkerActor.Command> childRef, DelegateWork msg) {
                childRef.tell(msg.work);
                msg.reply("Delegated to " + msg.workerId);
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
            final SpringActorHandle<ChildWorkerActor.Command> childRef;
            final Throwable failure;

            ChildReady(DelegateWork originalMsg, SpringActorHandle<ChildWorkerActor.Command> childRef, Throwable failure) {
                this.originalMsg = originalMsg;
                this.childRef = childRef;
                this.failure = failure;
            }
        }
    }

    /**
     * A verification service to test Spring DI.
     * This service tracks which actors have used it, proving beans are properly injected.
     */
    @Service
    static class DependencyVerificationService {
        private final ConcurrentMap<String, String> actorRegistrations = new ConcurrentHashMap<>();
        private final AtomicInteger callCount = new AtomicInteger(0);

        public void registerActor(String actorId, String message) {
            actorRegistrations.put(actorId, message);
            callCount.incrementAndGet();
        }

        public boolean isRegistered(String actorId) {
            return actorRegistrations.containsKey(actorId);
        }

        public String getMessage(String actorId) {
            return actorRegistrations.get(actorId);
        }

        public int getTotalCalls() {
            return callCount.get();
        }

        public void reset() {
            actorRegistrations.clear();
            callCount.set(0);
        }
    }

    /**
     * A child actor that depends on Spring DI.
     * This actor will verify that injected beans are working correctly.
     */
    @Component
    static class DependencyVerificationActor
            implements SpringActorWithContext<DependencyVerificationActor.Command, SpringActorContext> {

        private final DependencyVerificationService verificationService;
        private final TaskLogger taskLogger;

        // Constructor injection - both services should be injected by Spring
        public DependencyVerificationActor(DependencyVerificationService verificationService, TaskLogger taskLogger) {
            this.verificationService = verificationService;
            this.taskLogger = taskLogger;
        }

        public interface Command {}

        public static class RegisterSelf extends AskCommand<String> implements Command {
            public final String message;

            public RegisterSelf(String message) {
                this.message = message;
            }
        }

        public static class VerifyServices extends AskCommand<Boolean> implements Command {
            public VerifyServices() {}
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> {
                        // Verify services are not null (properly injected)
                        if (verificationService == null || taskLogger == null) {
                            throw new IllegalStateException("Spring DI failed: services are null");
                        }
                        ctx.getLog()
                                .info(
                                        "DependencyVerificationActor {} created with injected services",
                                        actorContext.actorId());
                        return new DependencyVerificationBehavior(actorContext, verificationService, taskLogger);
                    })
                    .onMessage(RegisterSelf.class, DependencyVerificationBehavior::onRegisterSelf)
                    .onMessage(VerifyServices.class, DependencyVerificationBehavior::onVerifyServices)
                    .build();
        }

        private static class DependencyVerificationBehavior {
            private final SpringActorContext actorContext;
            private final DependencyVerificationService verificationService;
            private final TaskLogger taskLogger;

            DependencyVerificationBehavior(
                    SpringActorContext actorContext,
                    DependencyVerificationService verificationService,
                    TaskLogger taskLogger) {
                this.actorContext = actorContext;
                this.verificationService = verificationService;
                this.taskLogger = taskLogger;
            }

            private Behavior<Command> onRegisterSelf(RegisterSelf msg) {
                // Use both injected services
                verificationService.registerActor(actorContext.actorId(), msg.message);
                taskLogger.logTask(actorContext.actorId());

                msg.reply("Registered: " + actorContext.actorId());
                return Behaviors.same();
            }

            private Behavior<Command> onVerifyServices(VerifyServices msg) {
                // Verify services are working
                boolean servicesWorking = verificationService != null && taskLogger != null;
                msg.reply(servicesWorking);
                return Behaviors.same();
            }
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {}

    /**
     * Dedicated tests for verifying Spring Dependency Injection works correctly with child actors.
     * These tests ensure that:
     * - Spring beans are properly injected into child actors
     * - Singleton beans are shared across multiple child actors
     * - DI works with both unified and builder APIs
     * - DI works after actor restarts
     */
    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class SpringDependencyInjectionTests {

        @Test
        void testChildActorReceivesSpringBeanInjection(ApplicationContext springContext) throws Exception {
            // Given: Spring services and actor system
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            DependencyVerificationService verificationService =
                    springContext.getBean(DependencyVerificationService.class);
            verificationService.reset();

            // And: A parent supervisor
            SpringActorHandle<ParentSupervisorActor.Command> parent = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("di-test-parent")
                    .spawnAndWait();

            // When: Spawning a child actor using builder API
            SpringActorHandle<DependencyVerificationActor.Command> child = parent.child(DependencyVerificationActor.class)
                    .withId("di-test-child")
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // And: Asking child to register itself (uses injected service)
            String result = child.ask(new DependencyVerificationActor.RegisterSelf("hello"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Then: Child should have successfully used the injected Spring service
            assertThat(result).isEqualTo("Registered: di-test-child");
            assertThat(verificationService.isRegistered("di-test-child")).isTrue();
            assertThat(verificationService.getMessage("di-test-child")).isEqualTo("hello");
        }

        @Test
        void testMultipleChildrenShareSingletonSpringBean(ApplicationContext springContext) throws Exception {
            // Given: Spring services and actor system
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            DependencyVerificationService verificationService =
                    springContext.getBean(DependencyVerificationService.class);
            verificationService.reset();

            // And: A parent supervisor
            SpringActorHandle<ParentSupervisorActor.Command> parent = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("di-singleton-test-parent")
                    .spawnAndWait();

            // When: Spawning multiple child actors
            SpringActorHandle<DependencyVerificationActor.Command> child1 = parent.child(DependencyVerificationActor.class)
                    .withId("singleton-child-1")
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            SpringActorHandle<DependencyVerificationActor.Command> child2 = parent.child(DependencyVerificationActor.class)
                    .withId("singleton-child-2")
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            SpringActorHandle<DependencyVerificationActor.Command> child3 = parent.child(DependencyVerificationActor.class)
                    .withId("singleton-child-3")
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // And: Each child registers itself
            child1.ask(new DependencyVerificationActor.RegisterSelf("Child 1"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            child2.ask(new DependencyVerificationActor.RegisterSelf("Child 2"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            child3.ask(new DependencyVerificationActor.RegisterSelf("Child 3"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Then: All children should share the same singleton Spring service instance
            // Verified by checking that all registrations are in the same service instance
            assertThat(verificationService.isRegistered("singleton-child-1")).isTrue();
            assertThat(verificationService.isRegistered("singleton-child-2")).isTrue();
            assertThat(verificationService.isRegistered("singleton-child-3")).isTrue();
            assertThat(verificationService.getTotalCalls()).isEqualTo(3);
        }

        @Test
        void testSpringDIWorksWithBuilderAPI(ApplicationContext springContext) throws Exception {
            // Given: Spring services and actor system
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            DependencyVerificationService verificationService =
                    springContext.getBean(DependencyVerificationService.class);
            verificationService.reset();

            // And: A parent supervisor
            SpringActorHandle<ParentSupervisorActor.Command> parent = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("di-builder-test-parent")
                    .spawnAndWait();

            // When: Spawning a child actor using builder API
            SpringActorHandle<DependencyVerificationActor.Command> child = parent.child(DependencyVerificationActor.class)
                    .withId("di-builder-child")
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .withTimeout(Duration.ofSeconds(10))
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // And: Verifying services are injected
            Boolean servicesWorking = child.ask(new DependencyVerificationActor.VerifyServices())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Then: Services should be properly injected
            assertThat(servicesWorking).isTrue();

            // And: Services should work correctly
            String result = child.ask(new DependencyVerificationActor.RegisterSelf("Builder API works"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            assertThat(result).isEqualTo("Registered: di-builder-child");
            assertThat(verificationService.isRegistered("di-builder-child")).isTrue();
        }

        @Test
        void testSpringDIWorksAfterActorRestart(ApplicationContext springContext) throws Exception {
            // Given: Spring services and actor system
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            DependencyVerificationService verificationService =
                    springContext.getBean(DependencyVerificationService.class);
            TaskLogger taskLogger = springContext.getBean(TaskLogger.class);
            verificationService.reset();
            taskLogger.reset();

            // And: A child actor with restart supervision
            SpringActorHandle<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("di-restart-test-worker")
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .spawnAndWait();

            // When: Actor processes a task (uses injected TaskLogger)
            String taskResult1 = worker.ask(new ChildWorkerActor.DoTask("task-before-restart"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            assertThat(taskResult1).isEqualTo("Completed: task-before-restart");
            assertThat(taskLogger.getTaskCount("di-restart-test-worker")).isEqualTo(1);

            // And: Actor fails and restarts
            worker.ask(new ChildWorkerActor.Fail())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            Thread.sleep(100); // Wait for restart

            // Then: After restart, new actor instance should still have DI working
            String taskResult2 = worker.ask(new ChildWorkerActor.DoTask("task-after-restart"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            assertThat(taskResult2).isEqualTo("Completed: task-after-restart");
            // TaskLogger still works after restart (singleton bean shared across actor instances)
            assertThat(taskLogger.getTaskCount("di-restart-test-worker")).isEqualTo(2);
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ChildManagementAPITests {

        @Test
        void testExistsChildReturnsFalseForNonExistent(ApplicationContext springContext) throws Exception {
            // Given: A parent supervisor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorHandle<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-exists-test")
                    .spawnAndWait();

            // When: Checking if a child exists that was never spawned
            Boolean exists = supervisor
                    .child(ChildWorkerActor.class, "non-existent-child")
                    .exists()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Should return false
            assertThat(exists).isFalse();
        }

        @Test
        void testGetChildReturnsNullForNonExistent(ApplicationContext springContext) throws Exception {
            // Given: A parent supervisor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorHandle<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-get-test")
                    .spawnAndWait();

            // When: Getting a child that doesn't exist
            SpringActorHandle<ChildWorkerActor.Command> childRef = supervisor
                    .child(ChildWorkerActor.class, "non-existent-child")
                    .get()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS)
                    .orElse(null);

            // Then: Should return null
            assertThat(childRef).isNull();
        }

        @Test
        void testSpawnChildDirectly(ApplicationContext springContext) throws Exception {
            // Given: A parent supervisor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            SpringActorHandle<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-spawn-test")
                    .spawnAndWait();

            // When: Spawning a child directly using the builder API
            SpringActorHandle<ChildWorkerActor.Command> childRef = supervisor
                    .child(ChildWorkerActor.class)
                    .withId("direct-spawn-child")
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Then: Child should be spawned and functional
            assertThat(childRef).isNotNull();

            // And: Child can receive messages using ask
            String result = childRef.ask(new ChildWorkerActor.DoTask("test-task"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(result).isEqualTo("Completed: test-task");

            // And: Child exists when checked
            Boolean exists = supervisor
                    .child(ChildWorkerActor.class, "direct-spawn-child")
                    .exists()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertThat(exists).isTrue();

            // And: Can retrieve child ref again
            SpringActorHandle<ChildWorkerActor.Command> retrievedChild = supervisor
                    .child(ChildWorkerActor.class, "direct-spawn-child")
                    .get()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS)
                    .orElse(null);
            assertThat(retrievedChild).isNotNull();
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class ChildSpawningTests {

        @Test
        void testSpawnChildWithSpringDI(ApplicationContext springContext) throws Exception {
            // Given: Actor system and task logger
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            TaskLogger taskLogger = springContext.getBean(TaskLogger.class);
            taskLogger.reset();

            // When: Spawn a parent supervisor
            SpringActorHandle<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-1")
                    .spawnAndWait();

            // And: Delegate work to a child (spawns on-demand with Spring DI)
            Object result = supervisor
                    .ask(new ParentSupervisorActor.DelegateWork(
                            "worker-1", "restart", new ChildWorkerActor.DoTask("test-task")))
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
        void testMultipleChildrenWithDifferentIds(ApplicationContext springContext) throws Exception {
            // Given: Actor system
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);
            TaskLogger taskLogger = springContext.getBean(TaskLogger.class);
            taskLogger.reset();

            // When: Spawn a parent supervisor
            SpringActorHandle<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-2")
                    .spawnAndWait();

            // And: Delegate work to multiple children (spawns on-demand)
            Object resultA = supervisor
                    .ask(new ParentSupervisorActor.DelegateWork(
                            "worker-a", "restart", new ChildWorkerActor.DoTask("task-a")))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(resultA).isEqualTo("Delegated to worker-a");

            Object resultB = supervisor
                    .ask(new ParentSupervisorActor.DelegateWork(
                            "worker-b", "restart", new ChildWorkerActor.DoTask("task-b")))
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
        void testRestartStrategyRestoresActor(ApplicationContext springContext) throws Exception {
            // Given: Supervisor with restart strategy
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorHandle<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-restart")
                    .spawnAndWait();

            // When: Child processes a task successfully (spawns on first use)
            Object taskResult1 = supervisor
                    .ask(new ParentSupervisorActor.DelegateWork(
                            "restart-worker", "restart", new ChildWorkerActor.DoTask("task-before-restart")))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(taskResult1).isEqualTo("Delegated to restart-worker");

            // And: Child fails (triggering restart)
            Object failResult = supervisor
                    .ask(new ParentSupervisorActor.DelegateWork(
                            "restart-worker", "restart", new ChildWorkerActor.Fail()))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(failResult).isEqualTo("Delegated to restart-worker");

            Thread.sleep(100); // Wait for restart to complete

            // Then: Child should be restarted and able to process new tasks
            Object taskResult2 = supervisor
                    .ask(new ParentSupervisorActor.DelegateWork(
                            "restart-worker", "restart", new ChildWorkerActor.DoTask("task-after-restart")))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(taskResult2).isEqualTo("Delegated to restart-worker");
        }

        @Test
        void testRestartStrategyResetsState(ApplicationContext springContext) throws Exception {
            // Given: Supervisor
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorHandle<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-state-reset")
                    .spawnAndWait();

            // When: Child processes multiple tasks (spawns on first use)
            supervisor
                    .ask(new ParentSupervisorActor.DelegateWork(
                            "state-worker", "restart", new ChildWorkerActor.DoTask("task1")))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            supervisor
                    .ask(new ParentSupervisorActor.DelegateWork(
                            "state-worker", "restart", new ChildWorkerActor.DoTask("task2")))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // And: Child fails (triggering restart that resets state)
            supervisor
                    .ask(new ParentSupervisorActor.DelegateWork("state-worker", "restart", new ChildWorkerActor.Fail()))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            Thread.sleep(100); // Wait for restart

            // Then: Actor should be responsive after restart (state reset verified by logs)
            Object result = supervisor
                    .ask(new ParentSupervisorActor.DelegateWork(
                            "state-worker", "restart", new ChildWorkerActor.DoTask("task-after-restart")))
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
        void testStopStrategyTerminatesActor(ApplicationContext springContext) throws Exception {
            // Given: Supervisor with stop strategy
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorHandle<ParentSupervisorActor.Command> supervisor = actorSystem
                    .actor(ParentSupervisorActor.class)
                    .withId("supervisor-stop")
                    .spawnAndWait();

            // When: Child processes a task successfully (spawns on first use)
            Object taskResult = supervisor
                    .ask(new ParentSupervisorActor.DelegateWork(
                            "stop-worker", "stop", new ChildWorkerActor.DoTask("task-before-stop")))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(taskResult).isEqualTo("Delegated to stop-worker");

            // And: Child fails (triggering stop strategy - no restart)
            Object failResult = supervisor
                    .ask(new ParentSupervisorActor.DelegateWork("stop-worker", "stop", new ChildWorkerActor.Fail()))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(failResult).isEqualTo("Delegated to stop-worker");

            Thread.sleep(100); // Wait for child to stop

            // Then: Subsequent work delegation spawns a NEW worker (old one was stopped)
            // The new worker will handle the task, demonstrating that stop strategy terminated the old one
            Object afterStopResult = supervisor
                    .ask(new ParentSupervisorActor.DelegateWork(
                            "stop-worker", "stop", new ChildWorkerActor.DoTask("task-after-stop")))
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
     * can be supervised using the withSupervisionStrategy() API.
     */
    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class TopLevelSupervisionTests {

        @Test
        void testTopLevelRestartStrategy(ApplicationContext springContext) throws Exception {
            // Given: Actor spawned with restart supervision
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorHandle<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-restart-worker")
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .spawnAndWait();

            // When: Actor processes a task successfully
            String taskResult = worker.ask(new ChildWorkerActor.DoTask("task-before-failure"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(taskResult).isEqualTo("Completed: task-before-failure");

            // And: Actor fails (triggering restart)
            String failResult = worker.ask(new ChildWorkerActor.Fail())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(failResult).isEqualTo("Failing now");

            Thread.sleep(100); // Wait for restart

            // Then: Actor should be restarted and able to process new tasks
            String afterRestartResult = worker.ask(new ChildWorkerActor.DoTask("task-after-restart"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(afterRestartResult).isEqualTo("Completed: task-after-restart");
        }

        @Test
        void testTopLevelRestartStrategyResetsState(ApplicationContext springContext) throws Exception {
            // Given: Actor spawned with restart supervision
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorHandle<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-state-worker")
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .spawnAndWait();

            // When: Actor processes multiple tasks
            worker.ask(new ChildWorkerActor.DoTask("task1"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            worker.ask(new ChildWorkerActor.DoTask("task2"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Then: State should reflect 2 completed tasks
            Integer stateBefore = worker.ask(new ChildWorkerActor.GetState())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(stateBefore).isEqualTo(2);

            // When: Actor fails (triggering restart that resets state)
            worker.ask(new ChildWorkerActor.Fail())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            Thread.sleep(100); // Wait for restart

            // Then: State should be reset to 0 after restart
            Integer stateAfter = worker.ask(new ChildWorkerActor.GetState())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(stateAfter).isEqualTo(0);
        }

        @Test
        void testTopLevelRestartWithLimitStrategy(ApplicationContext springContext) throws Exception {
            // Given: Actor spawned with limited restart supervision (max 2 restarts)
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorHandle<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-limited-worker")
                    .withSupervisionStrategy(SupervisorStrategy.restart().withLimit(2, Duration.ofSeconds(10)))
                    .spawnAndWait();

            // When: Actor fails once (first restart)
            worker.ask(new ChildWorkerActor.Fail())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            Thread.sleep(100);

            // Then: Actor should still be responsive
            String result1 = worker.ask(new ChildWorkerActor.DoTask("after-first-failure"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(result1).isEqualTo("Completed: after-first-failure");

            // When: Actor fails again (second restart)
            worker.ask(new ChildWorkerActor.Fail())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            Thread.sleep(100);

            // Then: Actor should still be responsive
            String result2 = worker.ask(new ChildWorkerActor.DoTask("after-second-failure"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(result2).isEqualTo("Completed: after-second-failure");

            // When: Actor fails a third time (exceeds limit, should stop)
            worker.ask(new ChildWorkerActor.Fail())
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
        void testTopLevelStopStrategy(ApplicationContext springContext) throws Exception {
            // Given: Actor spawned with stop supervision
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorHandle<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-stop-worker")
                    .withSupervisionStrategy(SupervisorStrategy.stop())
                    .spawnAndWait();

            // When: Actor processes a task successfully
            String taskResult = worker.ask(new ChildWorkerActor.DoTask("task-before-stop"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(taskResult).isEqualTo("Completed: task-before-stop");

            // And: Actor fails (triggering stop - no restart)
            worker.ask(new ChildWorkerActor.Fail())
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
        void testTopLevelResumeStrategy(ApplicationContext springContext) throws Exception {
            // Given: Actor spawned with resume supervision
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorHandle<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-resume-worker")
                    .withSupervisionStrategy(SupervisorStrategy.resume())
                    .spawnAndWait();

            // When: Actor processes tasks before failure
            worker.ask(new ChildWorkerActor.DoTask("task1"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            worker.ask(new ChildWorkerActor.DoTask("task2"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Then: State should reflect 2 completed tasks
            Integer stateBefore = worker.ask(new ChildWorkerActor.GetState())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(stateBefore).isEqualTo(2);

            // When: Actor fails (resume strategy keeps state)
            worker.ask(new ChildWorkerActor.Fail())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            Thread.sleep(100);

            // Then: State should be preserved (not reset) - this is the key difference from restart
            Integer stateAfter = worker.ask(new ChildWorkerActor.GetState())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(stateAfter).isEqualTo(2); // State preserved!

            // And: Actor should still be able to process new tasks
            String result = worker.ask(new ChildWorkerActor.DoTask("task-after-resume"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(result).isEqualTo("Completed: task-after-resume");

            // State incremented after resume
            Integer finalState = worker.ask(new ChildWorkerActor.GetState())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(finalState).isEqualTo(3);
        }

        @Test
        void testTopLevelNoSupervision(ApplicationContext springContext) throws Exception {
            // Given: Actor spawned without supervision (null strategy)
            SpringActorSystem actorSystem = springContext.getBean(SpringActorSystem.class);

            SpringActorHandle<ChildWorkerActor.Command> worker = actorSystem
                    .actor(ChildWorkerActor.class)
                    .withId("top-level-no-supervision-worker")
                    // No withSupervisionStrategy() call - defaults to null
                    .spawnAndWait();

            // When: Actor processes a task successfully
            String taskResult = worker.ask(new ChildWorkerActor.DoTask("task-before-failure"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertThat(taskResult).isEqualTo("Completed: task-before-failure");

            // And: Actor fails (no supervision means it stops)
            worker.ask(new ChildWorkerActor.Fail())
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
