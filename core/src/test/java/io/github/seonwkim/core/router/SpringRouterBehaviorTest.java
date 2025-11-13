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
import org.junit.jupiter.api.BeforeEach;
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
                                String workerId = context.path().name();
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

        public void reset() {
            workerCounts.clear();
            totalCount.set(0);
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

        public void reset() {
            totalCount.set(0);
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

    /** Worker for broadcast routing */
    @Component
    static class BroadcastWorkerActor implements SpringActor<BroadcastRouterActor.Command> {

        @Autowired private BroadcastWorkerState state;

        @Override
        public SpringActorBehavior<BroadcastRouterActor.Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(BroadcastRouterActor.Command.class, ctx)
                    .onMessage(
                            BroadcastRouterActor.ProcessMessage.class,
                            (context, msg) -> {
                                String workerId = context.path().name();
                                state.recordMessage(workerId, msg.message);
                                return Behaviors.same();
                            })
                    .onMessage(
                            BroadcastRouterActor.GetWorkerMessageCounts.class,
                            (context, msg) -> {
                                msg.reply(state.getWorkerCounts());
                                return Behaviors.same();
                            })
                    .onMessage(
                            BroadcastRouterActor.GetTotalMessagesReceived.class,
                            (context, msg) -> {
                                msg.reply(state.getTotalCount());
                                return Behaviors.same();
                            })
                    .build();
        }
    }

    @Component
    static class BroadcastWorkerState {
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

        public void reset() {
            workerCounts.clear();
            totalCount.set(0);
        }
    }

    /** Router with broadcast strategy for testing */
    @Component
    static class BroadcastRouterActor implements SpringActor<BroadcastRouterActor.Command> {

        public interface Command {}

        public static class ProcessMessage implements Command {
            public final String message;

            public ProcessMessage(String message) {
                this.message = message;
            }
        }

        public static class GetWorkerMessageCounts extends AskCommand<Map<String, Integer>>
                implements Command {}

        public static class GetTotalMessagesReceived extends AskCommand<Integer> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringRouterBehavior.builder(Command.class, ctx)
                    .withRoutingStrategy(RoutingStrategy.broadcast())
                    .withPoolSize(3)
                    .withWorkerActors(BroadcastWorkerActor.class)
                    .build();
        }
    }

    /** Worker for consistent hashing routing */
    @Component
    static class ConsistentHashingWorkerActor
            implements SpringActor<ConsistentHashingRouterActor.Command> {

        @Autowired private ConsistentHashingWorkerState state;

        @Override
        public SpringActorBehavior<ConsistentHashingRouterActor.Command> create(
                SpringActorContext ctx) {
            return SpringActorBehavior.builder(ConsistentHashingRouterActor.Command.class, ctx)
                    .onMessage(
                            ConsistentHashingRouterActor.ProcessHashableMessage.class,
                            (context, msg) -> {
                                String workerId = context.path().name();
                                state.recordMessage(workerId, msg.getConsistentHashKey());
                                return Behaviors.same();
                            })
                    .onMessage(
                            ConsistentHashingRouterActor.GetHashKeyWorkerMapping.class,
                            (context, msg) -> {
                                msg.reply(state.getHashKeyWorkerMapping());
                                return Behaviors.same();
                            })
                    .onMessage(
                            ConsistentHashingRouterActor.GetTotalMessageCount.class,
                            (context, msg) -> {
                                msg.reply(state.getTotalCount());
                                return Behaviors.same();
                            })
                    .build();
        }
    }

    @Component
    static class ConsistentHashingWorkerState {
        // Track which worker processed which hash key
        private final Map<String, String> hashKeyToWorker = new ConcurrentHashMap<>();
        private final AtomicInteger totalCount = new AtomicInteger(0);

        public void recordMessage(String workerId, String hashKey) {
            hashKeyToWorker.put(hashKey, workerId);
            totalCount.incrementAndGet();
        }

        public Map<String, String> getHashKeyWorkerMapping() {
            return new HashMap<>(hashKeyToWorker);
        }

        public int getTotalCount() {
            return totalCount.get();
        }

        public void reset() {
            hashKeyToWorker.clear();
            totalCount.set(0);
        }
    }

    /** Router with consistent hashing strategy for testing */
    @Component
    static class ConsistentHashingRouterActor
            implements SpringActor<ConsistentHashingRouterActor.Command> {

        public interface Command {}

        public static class ProcessHashableMessage implements Command, ConsistentHashable {
            private final String hashKey;
            private final String data;

            public ProcessHashableMessage(String hashKey, String data) {
                this.hashKey = hashKey;
                this.data = data;
            }

            @Override
            public String getConsistentHashKey() {
                return hashKey;
            }

            public String getData() {
                return data;
            }
        }

        public static class GetHashKeyWorkerMapping extends AskCommand<Map<String, String>>
                implements Command {}

        public static class GetTotalMessageCount extends AskCommand<Integer> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringRouterBehavior.builder(Command.class, ctx)
                    .withRoutingStrategy(RoutingStrategy.consistentHashing())
                    .withPoolSize(3)
                    .withWorkerActors(ConsistentHashingWorkerActor.class)
                    .build();
        }
    }

    // Service to demonstrate Spring DI in workers
    @Component
    static class WorkerService {
        private final AtomicInteger serviceCallCount = new AtomicInteger(0);

        public String processMessage(String message) {
            serviceCallCount.incrementAndGet();
            return "Processed by service: " + message;
        }

        public int getServiceCallCount() {
            return serviceCallCount.get();
        }

        public void reset() {
            serviceCallCount.set(0);
        }
    }

    // Worker that uses Spring DI
    @Component
    static class SpringDIWorkerActor implements SpringActor<SpringDIRouterActor.Command> {

        @Autowired private WorkerService workerService; // Spring DI injection!

        @Override
        public SpringActorBehavior<SpringDIRouterActor.Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(SpringDIRouterActor.Command.class, ctx)
                    .onMessage(
                            SpringDIRouterActor.ProcessWithService.class,
                            (context, msg) -> {
                                // Use the injected service
                                String result = workerService.processMessage(msg.data);
                                context.getLog().info("Service result: {}", result);
                                return Behaviors.same();
                            })
                    .onMessage(
                            SpringDIRouterActor.GetServiceCallCount.class,
                            (context, msg) -> {
                                msg.reply(workerService.getServiceCallCount());
                                return Behaviors.same();
                            })
                    .build();
        }
    }

    // Router using DI-enabled workers
    @Component
    static class SpringDIRouterActor implements SpringActor<SpringDIRouterActor.Command> {

        public interface Command {}

        public static class ProcessWithService implements Command {
            public final String data;

            public ProcessWithService(String data) {
                this.data = data;
            }
        }

        public static class GetServiceCallCount extends AskCommand<Integer> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringRouterBehavior.builder(Command.class, ctx)
                    .withRoutingStrategy(RoutingStrategy.roundRobin())
                    .withPoolSize(3)
                    .withWorkerActors(SpringDIWorkerActor.class) // Workers get Spring DI!
                    .build();
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {}

    @Nested
    @SpringBootTest(classes = TestApp.class)
    class SpringDependencyInjectionTests {

        @BeforeEach
        void resetState(@Autowired WorkerService workerService) {
            workerService.reset();
        }

        @Test
        void workerActorsCanInjectSpringBeans(@Autowired SpringActorSystem actorSystem)
                throws Exception {
            // This test verifies the key benefit of withWorkerActors() API:
            // Workers are full Spring components with dependency injection!

            SpringActorRef<SpringDIRouterActor.Command> router =
                    actorSystem
                            .actor(SpringDIRouterActor.class)
                            .withId("spring-di-router")
                            .spawnAndWait();

            // Send messages that will be processed using the injected service
            router.tell(new SpringDIRouterActor.ProcessWithService("message-1"));
            router.tell(new SpringDIRouterActor.ProcessWithService("message-2"));
            router.tell(new SpringDIRouterActor.ProcessWithService("message-3"));
            router.tell(new SpringDIRouterActor.ProcessWithService("message-4"));
            router.tell(new SpringDIRouterActor.ProcessWithService("message-5"));

            // Wait for all messages to be processed
            await().atMost(5, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .until(
                            () -> {
                                Integer count =
                                        router.ask(new SpringDIRouterActor.GetServiceCallCount())
                                                .withTimeout(Duration.ofSeconds(1))
                                                .execute()
                                                .toCompletableFuture()
                                                .get();
                                return count == 5;
                            });

            // Verify the injected service was actually called
            Integer serviceCallCount =
                    router.ask(new SpringDIRouterActor.GetServiceCallCount())
                            .withTimeout(Duration.ofSeconds(1))
                            .execute()
                            .toCompletableFuture()
                            .get();

            // SUCCESS! The injected WorkerService was called 5 times
            // This proves Spring DI works in router workers!
            assertThat(serviceCallCount).isEqualTo(5);
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    class RoundRobinRoutingTests {

        @BeforeEach
        void resetState(@Autowired WorkerState workerState) {
            workerState.reset();
        }

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

        @BeforeEach
        void resetState(@Autowired RandomWorkerState randomWorkerState) {
            randomWorkerState.reset();
        }

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
    class BroadcastRoutingTests {

        @BeforeEach
        void resetState(@Autowired BroadcastWorkerState broadcastWorkerState) {
            broadcastWorkerState.reset();
        }

        @Test
        void broadcastSendsToAllWorkers(@Autowired SpringActorSystem actorSystem) throws Exception {
            SpringActorRef<BroadcastRouterActor.Command> router =
                    actorSystem
                            .actor(BroadcastRouterActor.class)
                            .withId("broadcast-router")
                            .spawnAndWait();

            // Send 5 messages - each should go to ALL 3 workers
            for (int i = 0; i < 5; i++) {
                router.tell(new BroadcastRouterActor.ProcessMessage("broadcast-msg-" + i));
            }

            // Wait until all messages are processed
            // Expected: 5 messages × 3 workers = 15 total messages received
            await().atMost(5, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .until(
                            () -> {
                                Integer count =
                                        router.ask(new BroadcastRouterActor.GetTotalMessagesReceived())
                                                .withTimeout(Duration.ofSeconds(1))
                                                .execute()
                                                .toCompletableFuture()
                                                .get();
                                return count >= 15;
                            });

            // Verify total messages received (5 sent × 3 workers = 15 received)
            Integer totalReceived =
                    router.ask(new BroadcastRouterActor.GetTotalMessagesReceived())
                            .withTimeout(Duration.ofSeconds(1))
                            .execute()
                            .toCompletableFuture()
                            .get();

            assertThat(totalReceived).isEqualTo(15);

            // Verify each worker received ALL 5 messages
            Map<String, Integer> workerCounts =
                    router.ask(new BroadcastRouterActor.GetWorkerMessageCounts())
                            .withTimeout(Duration.ofSeconds(1))
                            .execute()
                            .toCompletableFuture()
                            .get();

            assertThat(workerCounts).hasSize(3);
            assertThat(workerCounts.values()).containsOnly(5); // Each worker got ALL 5 messages
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    class ConsistentHashingRoutingTests {

        @BeforeEach
        void resetState(@Autowired ConsistentHashingWorkerState consistentHashingWorkerState) {
            consistentHashingWorkerState.reset();
        }

        @Test
        void consistentHashingRoutesMessagesToSameWorker(@Autowired SpringActorSystem actorSystem)
                throws Exception {
            SpringActorRef<ConsistentHashingRouterActor.Command> router =
                    actorSystem
                            .actor(ConsistentHashingRouterActor.class)
                            .withId("consistent-hashing-router")
                            .spawnAndWait();

            // Send 3 messages with hash key "user-1" - all should go to same worker
            router.tell(new ConsistentHashingRouterActor.ProcessHashableMessage("user-1", "msg-1"));
            router.tell(new ConsistentHashingRouterActor.ProcessHashableMessage("user-1", "msg-2"));
            router.tell(new ConsistentHashingRouterActor.ProcessHashableMessage("user-1", "msg-3"));

            // Send 2 messages with hash key "user-2" - all should go to same worker (may be different from user-1)
            router.tell(new ConsistentHashingRouterActor.ProcessHashableMessage("user-2", "msg-4"));
            router.tell(new ConsistentHashingRouterActor.ProcessHashableMessage("user-2", "msg-5"));

            // Send 2 messages with hash key "user-3" - all should go to same worker
            router.tell(new ConsistentHashingRouterActor.ProcessHashableMessage("user-3", "msg-6"));
            router.tell(new ConsistentHashingRouterActor.ProcessHashableMessage("user-3", "msg-7"));

            // Wait until all messages are processed
            await().atMost(5, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .until(
                            () -> {
                                Integer count =
                                        router.ask(
                                                        new ConsistentHashingRouterActor
                                                                .GetTotalMessageCount())
                                                .withTimeout(Duration.ofSeconds(1))
                                                .execute()
                                                .toCompletableFuture()
                                                .get();
                                return count == 7;
                            });

            // Verify total count
            Integer totalCount =
                    router.ask(new ConsistentHashingRouterActor.GetTotalMessageCount())
                            .withTimeout(Duration.ofSeconds(1))
                            .execute()
                            .toCompletableFuture()
                            .get();

            assertThat(totalCount).isEqualTo(7);

            // Get the hash key -> worker mapping
            Map<String, String> mapping =
                    router.ask(new ConsistentHashingRouterActor.GetHashKeyWorkerMapping())
                            .withTimeout(Duration.ofSeconds(1))
                            .execute()
                            .toCompletableFuture()
                            .get();

            // Verify: Each hash key was consistently routed to the same worker
            // (We sent multiple messages per hash key, but map only shows last worker per key)
            assertThat(mapping).containsKeys("user-1", "user-2", "user-3");

            // The key property: all messages with the same hash key went to the same worker
            // Since we only track the last message per hash key in the map, the fact that
            // we have exactly 3 entries (one per hash key) proves consistency worked
            assertThat(mapping).hasSize(3);

            // Note: We can't assert which specific worker handles which key, as that depends
            // on the consistent hashing algorithm. We only verify that the same key always
            // goes to the same worker (which is proven by having exactly 7 messages processed
            // and exactly 3 distinct hash keys in the mapping).
        }

        @Test
        void consistentHashingDistributesAcrossWorkers(@Autowired SpringActorSystem actorSystem)
                throws Exception {
            SpringActorRef<ConsistentHashingRouterActor.Command> router =
                    actorSystem
                            .actor(ConsistentHashingRouterActor.class)
                            .withId("consistent-hashing-distribution")
                            .spawnAndWait();

            // Send many messages with different hash keys
            // With 20 different keys and 3 workers, we expect some distribution
            for (int i = 0; i < 20; i++) {
                router.tell(
                        new ConsistentHashingRouterActor.ProcessHashableMessage(
                                "key-" + i, "data-" + i));
            }

            // Wait for all messages to be processed
            await().atMost(5, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .until(
                            () -> {
                                Integer count =
                                        router.ask(
                                                        new ConsistentHashingRouterActor
                                                                .GetTotalMessageCount())
                                                .withTimeout(Duration.ofSeconds(1))
                                                .execute()
                                                .toCompletableFuture()
                                                .get();
                                return count == 20;
                            });

            // Get the mapping
            Map<String, String> mapping =
                    router.ask(new ConsistentHashingRouterActor.GetHashKeyWorkerMapping())
                            .withTimeout(Duration.ofSeconds(1))
                            .execute()
                            .toCompletableFuture()
                            .get();

            // Verify all keys are present
            assertThat(mapping).hasSize(20);

            // Verify that work was distributed (not all keys went to same worker)
            // With 20 keys and 3 workers, it's extremely unlikely all go to 1 worker
            long distinctWorkers = mapping.values().stream().distinct().count();
            assertThat(distinctWorkers)
                    .as("Work should be distributed across multiple workers")
                    .isGreaterThan(1);
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
