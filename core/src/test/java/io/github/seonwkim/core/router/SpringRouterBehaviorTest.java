package io.github.seonwkim.core.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;

class SpringRouterBehaviorTest {

    /** Worker actor for round-robin testing */
    @Component
    static class RoundRobinWorkerActor implements SpringActor<TestRouterActor.Command> {

        @Autowired private WorkerState workerState;

        @Override
        public SpringActorBehavior<TestRouterActor.Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(TestRouterActor.Command.class, ctx)
                    .onMessage(
                            TestRouterActor.ProcessMessage.class,
                            (context, msg) -> {
                                String workerId = context.getSelf().path().name();
                                workerState.recordMessage(workerId, msg.message);
                                context.getLog().info("Worker {} processed: {}", workerId, msg.message);
                                return Behaviors.same();
                            })
                    .onMessage(
                            TestRouterActor.GetWorkerMessageCounts.class,
                            (context, msg) -> {
                                msg.reply(workerState.getWorkerCounts());
                                return Behaviors.same();
                            })
                    .onMessage(
                            TestRouterActor.GetTotalMessageCount.class,
                            (context, msg) -> {
                                msg.reply(workerState.getTotalCount());
                                return Behaviors.same();
                            })
                    .build();
        }
    }

    /** Shared state for tracking worker messages */
    @Component
    static class WorkerState {
        private final Map<String, AtomicInteger> workerCounts = new ConcurrentHashMap<>();
        private final AtomicInteger totalCount = new AtomicInteger(0);

        public void recordMessage(String workerId, String message) {
            workerCounts.putIfAbsent(workerId, new AtomicInteger(0));
            workerCounts.get(workerId).incrementAndGet();
            totalCount.incrementAndGet();
        }

        public Map<String, Integer> getWorkerCounts() {
            Map<String, Integer> counts = new HashMap<>();
            workerCounts.forEach((k, v) -> counts.put(k, v.get()));
            return counts;
        }

        public int getTotalCount() {
            return totalCount.get();
        }
    }

    /** Test router actor with queryable state */
    @Component
    static class TestRouterActor implements SpringActor<TestRouterActor.Command> {

        public interface Command {}

        public static class ProcessMessage implements Command {
            public final String message;

            public ProcessMessage(String message) {
                this.message = message;
            }
        }

        public static class GetWorkerMessageCounts extends AskCommand<Map<String, Integer>>
                implements Command {}

        public static class GetTotalMessageCount extends AskCommand<Integer> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringRouterBehavior.builder(Command.class, ctx)
                    .withRoutingStrategy(RoutingStrategy.roundRobin())
                    .withPoolSize(3)
                    .withWorkerActors(RoundRobinWorkerActor.class)
                    .build();
        }
    }

    /** Worker for random routing */
    @Component
    static class RandomWorkerActor implements SpringActor<RandomRouterActor.Command> {

        @Autowired private RandomWorkerState state;

        @Override
        public SpringActorBehavior<RandomRouterActor.Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(RandomRouterActor.Command.class, ctx)
                    .onMessage(
                            RandomRouterActor.ProcessMessage.class,
                            (context, msg) -> {
                                state.incrementCount();
                                return Behaviors.same();
                            })
                    .onMessage(
                            RandomRouterActor.GetTotalMessageCount.class,
                            (context, msg) -> {
                                msg.reply(state.getCount());
                                return Behaviors.same();
                            })
                    .build();
        }
    }

    @Component
    static class RandomWorkerState {
        private final AtomicInteger totalCount = new AtomicInteger(0);

        public void incrementCount() {
            totalCount.incrementAndGet();
        }

        public int getCount() {
            return totalCount.get();
        }
    }

    /** Router with random strategy for testing */
    @Component
    static class RandomRouterActor implements SpringActor<RandomRouterActor.Command> {

        public interface Command {}

        public static class ProcessMessage implements Command {
            public final String message;

            public ProcessMessage(String message) {
                this.message = message;
            }
        }

        public static class GetTotalMessageCount extends AskCommand<Integer> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringRouterBehavior.builder(Command.class, ctx)
                    .withRoutingStrategy(RoutingStrategy.random())
                    .withPoolSize(3)
                    .withWorkerActors(RandomWorkerActor.class)
                    .build();
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {}

    @Nested
    @SpringBootTest(classes = TestApp.class)
    class RoundRobinRoutingTests {

        @Test
        void roundRobinDistributesEvenly(@Autowired SpringActorSystem actorSystem) throws Exception {
            SpringActorRef<TestRouterActor.Command> router =
                    actorSystem
                            .actor(TestRouterActor.class)
                            .withId("round-robin-router")
                            .spawnAndWait();

            // Send 9 messages (should distribute 3 to each of the 3 workers)
            for (int i = 0; i < 9; i++) {
                router.tell(new TestRouterActor.ProcessMessage("msg-" + i));
            }

            // Query total count until all messages are processed
            await().atMost(5, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .until(
                            () -> {
                                Integer count =
                                        router.ask(new TestRouterActor.GetTotalMessageCount())
                                                .withTimeout(Duration.ofSeconds(1))
                                                .execute()
                                                .toCompletableFuture()
                                                .get();
                                return count >= 9;
                            });

            // Verify total count
            Integer totalCount =
                    router.ask(new TestRouterActor.GetTotalMessageCount())
                            .withTimeout(Duration.ofSeconds(1))
                            .execute()
                            .toCompletableFuture()
                            .get();

            assertThat(totalCount).isEqualTo(9);

            // Verify distribution - each worker should have received exactly 3 messages
            Map<String, Integer> workerCounts =
                    router.ask(new TestRouterActor.GetWorkerMessageCounts())
                            .withTimeout(Duration.ofSeconds(1))
                            .execute()
                            .toCompletableFuture()
                            .get();

            assertThat(workerCounts).hasSize(3);
            assertThat(workerCounts.values()).containsOnly(3); // Each worker got exactly 3
        }

        @Test
        void roundRobinHandlesUnevenMessageCount(@Autowired SpringActorSystem actorSystem)
                throws Exception {
            SpringActorRef<TestRouterActor.Command> router =
                    actorSystem
                            .actor(TestRouterActor.class)
                            .withId("round-robin-uneven")
                            .spawnAndWait();

            // Send 10 messages to 3 workers (3, 3, 4 distribution expected)
            for (int i = 0; i < 10; i++) {
                router.tell(new TestRouterActor.ProcessMessage("msg-" + i));
            }

            // Wait for all messages to be processed
            await().atMost(5, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .until(
                            () -> {
                                Integer count =
                                        router.ask(new TestRouterActor.GetTotalMessageCount())
                                                .withTimeout(Duration.ofSeconds(1))
                                                .execute()
                                                .toCompletableFuture()
                                                .get();
                                return count == 10;
                            });

            // Verify total count is exactly 10
            Integer totalCount =
                    router.ask(new TestRouterActor.GetTotalMessageCount())
                            .withTimeout(Duration.ofSeconds(1))
                            .execute()
                            .toCompletableFuture()
                            .get();

            assertThat(totalCount).isEqualTo(10);

            // Verify all workers participated
            Map<String, Integer> workerCounts =
                    router.ask(new TestRouterActor.GetWorkerMessageCounts())
                            .withTimeout(Duration.ofSeconds(1))
                            .execute()
                            .toCompletableFuture()
                            .get();

            assertThat(workerCounts).hasSize(3);
            // Sum should equal total
            int sum = workerCounts.values().stream().mapToInt(Integer::intValue).sum();
            assertThat(sum).isEqualTo(10);
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    class RandomRoutingTests {

        @Test
        void randomDistributesMessages(@Autowired SpringActorSystem actorSystem) throws Exception {
            SpringActorRef<RandomRouterActor.Command> router =
                    actorSystem
                            .actor(RandomRouterActor.class)
                            .withId("random-router")
                            .spawnAndWait();

            // Send 15 messages
            for (int i = 0; i < 15; i++) {
                router.tell(new RandomRouterActor.ProcessMessage("random-msg-" + i));
            }

            // Wait until all messages are processed
            await().atMost(5, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .until(
                            () -> {
                                Integer count =
                                        router.ask(new RandomRouterActor.GetTotalMessageCount())
                                                .withTimeout(Duration.ofSeconds(1))
                                                .execute()
                                                .toCompletableFuture()
                                                .get();
                                return count == 15;
                            });

            // Verify exact count
            Integer totalCount =
                    router.ask(new RandomRouterActor.GetTotalMessageCount())
                            .withTimeout(Duration.ofSeconds(1))
                            .execute()
                            .toCompletableFuture()
                            .get();

            assertThat(totalCount).isEqualTo(15);
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    class BuilderValidationTests {

        @Test
        void builderRequiresRoutingStrategy() {
            SpringActorContext ctx = new SpringActorContext() {
                @Override
                public String actorId() {
                    return "test";
                }
            };

            assertThatThrownBy(
                            () -> SpringRouterBehavior.builder(TestRouterActor.Command.class, ctx)
                                            .withPoolSize(5)
                                            .withWorkerActors(RoundRobinWorkerActor.class)
                                            .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Routing strategy");
        }

        @Test
        void builderRequiresWorkerActors() {
            SpringActorContext ctx = new SpringActorContext() {
                @Override
                public String actorId() {
                    return "test";
                }
            };

            assertThatThrownBy(
                            () ->
                                    SpringRouterBehavior.builder(TestRouterActor.Command.class, ctx)
                                            .withRoutingStrategy(RoutingStrategy.roundRobin())
                                            .withPoolSize(5)
                                            .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Worker actor class");
        }

        @Test
        void builderRejectsInvalidPoolSize() {
            SpringActorContext ctx = new SpringActorContext() {
                @Override
                public String actorId() {
                    return "test";
                }
            };

            assertThatThrownBy(() -> SpringRouterBehavior.builder(TestRouterActor.Command.class, ctx).withPoolSize(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Pool size must be positive");

            assertThatThrownBy(() -> SpringRouterBehavior.builder(TestRouterActor.Command.class, ctx).withPoolSize(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Pool size must be positive");
        }
    }
}
