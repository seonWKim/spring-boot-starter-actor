package io.github.seonwkim.core.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;

/**
 * Edge case tests for router functionality including supervision, high load, and failure
 * scenarios.
 */
@SpringBootTest(classes = RouterEdgeCaseTest.TestApp.class)
class RouterEdgeCaseTest {

    // Shared state components for tracking across workers
    @Component
    static class SupervisionState {
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);

        public void recordSuccess() {
            successCount.incrementAndGet();
        }

        public void recordFailure() {
            failureCount.incrementAndGet();
        }

        public int getSuccessCount() {
            return successCount.get();
        }

        public int getFailureCount() {
            return failureCount.get();
        }

        public void reset() {
            successCount.set(0);
            failureCount.set(0);
        }
    }

    @Component
    static class HighVolumeState {
        private final AtomicInteger processedCount = new AtomicInteger(0);

        public void incrementProcessed() {
            processedCount.incrementAndGet();
        }

        public int getProcessedCount() {
            return processedCount.get();
        }

        public void reset() {
            processedCount.set(0);
        }
    }

    // Worker actors
    @Component
    static class SupervisedWorkerActor implements SpringActor<SupervisedRouterActor.Command> {

        @Autowired private SupervisionState state;

        @Override
        public SpringActorBehavior<SupervisedRouterActor.Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(SupervisedRouterActor.Command.class, ctx)
                    .onMessage(
                            SupervisedRouterActor.ProcessCommand.class,
                            (context, msg) -> {
                                if (msg.data.contains("fail")) {
                                    state.recordFailure();
                                    throw new RuntimeException("Intentional failure");
                                }
                                state.recordSuccess();
                                return Behaviors.same();
                            })
                    .onMessage(
                            SupervisedRouterActor.GetSuccessCount.class,
                            (context, msg) -> {
                                msg.reply(state.getSuccessCount());
                                return Behaviors.same();
                            })
                    .onMessage(
                            SupervisedRouterActor.GetFailureCount.class,
                            (context, msg) -> {
                                msg.reply(state.getFailureCount());
                                return Behaviors.same();
                            })
                    .build();
        }
    }

    @Component
    static class HighVolumeWorkerActor implements SpringActor<HighVolumeRouterActor.Command> {

        @Autowired private HighVolumeState state;

        @Override
        public SpringActorBehavior<HighVolumeRouterActor.Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(HighVolumeRouterActor.Command.class, ctx)
                    .onMessage(
                            HighVolumeRouterActor.BulkMessage.class,
                            (context, msg) -> {
                                state.incrementProcessed();
                                return Behaviors.same();
                            })
                    .onMessage(
                            HighVolumeRouterActor.GetProcessedCount.class,
                            (context, msg) -> {
                                msg.reply(state.getProcessedCount());
                                return Behaviors.same();
                            })
                    .build();
        }
    }

    // Router actors
    @Component
    static class SupervisedRouterActor implements SpringActor<SupervisedRouterActor.Command> {

        public interface Command {}

        public static class ProcessCommand implements Command {
            public final String data;

            public ProcessCommand(String data) {
                this.data = data;
            }
        }

        public static class GetSuccessCount extends AskCommand<Integer> implements Command {}

        public static class GetFailureCount extends AskCommand<Integer> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringRouterBehavior.builder(Command.class, ctx)
                    .withRoutingStrategy(RoutingStrategy.roundRobin())
                    .withPoolSize(3)
                    .withWorkerActors(SupervisedWorkerActor.class)
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .build();
        }
    }

    @Component
    static class HighVolumeRouterActor implements SpringActor<HighVolumeRouterActor.Command> {

        public interface Command {}

        public static class BulkMessage implements Command {
            public final int id;

            public BulkMessage(int id) {
                this.id = id;
            }
        }

        public static class GetProcessedCount extends AskCommand<Integer> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringRouterBehavior.builder(Command.class, ctx)
                    .withRoutingStrategy(RoutingStrategy.random())
                    .withPoolSize(10)
                    .withWorkerActors(HighVolumeWorkerActor.class)
                    .build();
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {}

    @BeforeEach
    void resetState(@Autowired SupervisionState supervisionState, @Autowired HighVolumeState highVolumeState) {
        supervisionState.reset();
        highVolumeState.reset();
    }

    @Test
    void routerHandlesWorkerFailuresWithSupervision(@Autowired SpringActorSystem actorSystem)
            throws Exception {
        SpringActorRef<SupervisedRouterActor.Command> router =
                actorSystem
                        .actor(SupervisedRouterActor.class)
                        .withId("supervised-router")
                        .spawnAndWait();

        // Send mix of normal and failing messages
        router.tell(new SupervisedRouterActor.ProcessCommand("success-1"));
        router.tell(new SupervisedRouterActor.ProcessCommand("fail-1"));
        router.tell(new SupervisedRouterActor.ProcessCommand("success-2"));
        router.tell(new SupervisedRouterActor.ProcessCommand("fail-2"));
        router.tell(new SupervisedRouterActor.ProcessCommand("success-3"));

        // Wait for all successful messages to be processed
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            Integer successCount =
                                    router.ask(new SupervisedRouterActor.GetSuccessCount())
                                            .withTimeout(Duration.ofSeconds(1))
                                            .execute()
                                            .toCompletableFuture()
                                            .get();
                            return successCount == 3;
                        });

        Integer successCount =
                router.ask(new SupervisedRouterActor.GetSuccessCount())
                        .withTimeout(Duration.ofSeconds(1))
                        .execute()
                        .toCompletableFuture()
                        .get();

        Integer failureCount =
                router.ask(new SupervisedRouterActor.GetFailureCount())
                        .withTimeout(Duration.ofSeconds(1))
                        .execute()
                        .toCompletableFuture()
                        .get();

        assertThat(successCount).isEqualTo(3);
        assertThat(failureCount).isEqualTo(2);
    }

    @Test
    void routerHandlesHighMessageVolume(@Autowired SpringActorSystem actorSystem) throws Exception {
        SpringActorRef<HighVolumeRouterActor.Command> router =
                actorSystem
                        .actor(HighVolumeRouterActor.class)
                        .withId("high-volume-router")
                        .spawnAndWait();

        // Send 1000 messages
        int messageCount = 1000;
        for (int i = 0; i < messageCount; i++) {
            router.tell(new HighVolumeRouterActor.BulkMessage(i));
        }

        // Wait for all messages to be processed
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            Integer count =
                                    router.ask(new HighVolumeRouterActor.GetProcessedCount())
                                            .withTimeout(Duration.ofSeconds(2))
                                            .execute()
                                            .toCompletableFuture()
                                            .get();
                            return count == 1000;
                        });

        Integer processedCount =
                router.ask(new HighVolumeRouterActor.GetProcessedCount())
                        .withTimeout(Duration.ofSeconds(1))
                        .execute()
                        .toCompletableFuture()
                        .get();

        assertThat(processedCount).isEqualTo(1000);
    }

    @Test
    void multipleRoutersCanCoexist(@Autowired SpringActorSystem actorSystem) throws Exception {
        SpringActorRef<HighVolumeRouterActor.Command> router1 =
                actorSystem
                        .actor(HighVolumeRouterActor.class)
                        .withId("router-1")
                        .spawnAndWait();

        SpringActorRef<HighVolumeRouterActor.Command> router2 =
                actorSystem
                        .actor(HighVolumeRouterActor.class)
                        .withId("router-2")
                        .spawnAndWait();

        SpringActorRef<HighVolumeRouterActor.Command> router3 =
                actorSystem
                        .actor(HighVolumeRouterActor.class)
                        .withId("router-3")
                        .spawnAndWait();

        // Send messages to all routers
        for (int i = 0; i < 10; i++) {
            router1.tell(new HighVolumeRouterActor.BulkMessage(i));
            router2.tell(new HighVolumeRouterActor.BulkMessage(i));
            router3.tell(new HighVolumeRouterActor.BulkMessage(i));
        }

        // Wait for all messages to be processed
        // Note: All routers share the same HighVolumeState singleton,
        // so the total count accumulates across all routers
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            Integer count =
                                    router1.ask(new HighVolumeRouterActor.GetProcessedCount())
                                            .withTimeout(Duration.ofSeconds(1))
                                            .execute()
                                            .toCompletableFuture()
                                            .get();
                            return count >= 30;
                        });

        // Verify total messages processed across all routers
        // Since HighVolumeState is a shared singleton, all routers see the same global count
        Integer totalCount =
                router1.ask(new HighVolumeRouterActor.GetProcessedCount())
                        .withTimeout(Duration.ofSeconds(1))
                        .execute()
                        .toCompletableFuture()
                        .get();

        // All 3 routers coexist and collectively processed 30 messages (10 each)
        assertThat(totalCount).isEqualTo(30);
    }

    @Test
    void routerHandlesConcurrentMessageBursts(@Autowired SpringActorSystem actorSystem)
            throws Exception {
        SpringActorRef<HighVolumeRouterActor.Command> router =
                actorSystem
                        .actor(HighVolumeRouterActor.class)
                        .withId("burst-router")
                        .spawnAndWait();

        // Simulate concurrent bursts from multiple threads
        CompletableFuture<Void> burst1 =
                CompletableFuture.runAsync(
                        () -> {
                            for (int i = 0; i < 100; i++) {
                                router.tell(new HighVolumeRouterActor.BulkMessage(i));
                            }
                        });

        CompletableFuture<Void> burst2 =
                CompletableFuture.runAsync(
                        () -> {
                            for (int i = 100; i < 200; i++) {
                                router.tell(new HighVolumeRouterActor.BulkMessage(i));
                            }
                        });

        CompletableFuture<Void> burst3 =
                CompletableFuture.runAsync(
                        () -> {
                            for (int i = 200; i < 300; i++) {
                                router.tell(new HighVolumeRouterActor.BulkMessage(i));
                            }
                        });

        // Wait for all bursts to complete sending
        CompletableFuture.allOf(burst1, burst2, burst3).get(10, TimeUnit.SECONDS);

        // Wait for all 300 messages to be processed
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            Integer count =
                                    router.ask(new HighVolumeRouterActor.GetProcessedCount())
                                            .withTimeout(Duration.ofSeconds(2))
                                            .execute()
                                            .toCompletableFuture()
                                            .get();
                            return count == 300;
                        });

        Integer processedCount =
                router.ask(new HighVolumeRouterActor.GetProcessedCount())
                        .withTimeout(Duration.ofSeconds(1))
                        .execute()
                        .toCompletableFuture()
                        .get();

        assertThat(processedCount).isEqualTo(300);
    }
}
