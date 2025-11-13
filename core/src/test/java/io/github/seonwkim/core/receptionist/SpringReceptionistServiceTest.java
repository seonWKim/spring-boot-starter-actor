package io.github.seonwkim.core.receptionist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for SpringReceptionistService - Pekko's Receptionist feature integration.
 *
 * <p>This test suite validates the receptionist functionality for actor service discovery including:
 *
 * <ul>
 *   <li>Actor registration under service keys
 *   <li>Finding actors by service key
 *   <li>Subscribing to actor availability changes
 *   <li>Multiple actors under the same service key
 *   <li>Automatic deregistration when actors stop
 * </ul>
 */
@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.actor.pekko.loglevel=DEBUG",
            "spring.actor.pekko.actor.provider=local",
            "spring.actor.pekko.log-dead-letters=off"
        })
class SpringReceptionistServiceTest {

    @Autowired private SpringActorSystem actorSystem;

    @SpringBootApplication
    @io.github.seonwkim.core.EnableActorSupport
    static class TestApplication {}

    /**
     * Test actor that performs simple work tasks.
     */
    @Component
    static class WorkerActor implements SpringActor<WorkerActor.Command> {

        public interface Command {}

        public static class DoWork extends AskCommand<String> implements Command {
            public final String taskId;

            public DoWork(String taskId) {
                this.taskId = taskId;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(DoWork.class, (ctx, msg) -> {
                        msg.reply("Worker " + actorContext.actorId() + " completed task: " + msg.taskId);
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    /**
     * Test actor for service discovery.
     */
    @Component
    static class ServiceActor implements SpringActor<ServiceActor.Command> {

        public interface Command {}

        public static class GetServiceId extends AskCommand<String> implements Command {
            public GetServiceId() {}
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(GetServiceId.class, (ctx, msg) -> {
                        msg.reply(actorContext.actorId());
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @Nested
    class RegistrationAndDiscoveryTests {

        @Test
        void testRegisterAndFindSingleActor() throws Exception {
            // Arrange
            ServiceKey<WorkerActor.Command> workerKey =
                    ServiceKey.create(WorkerActor.Command.class, "test-worker-single");
            SpringReceptionistService receptionist = actorSystem.receptionist();

            SpringActorRef<WorkerActor.Command> worker = actorSystem
                    .actor(WorkerActor.class)
                    .withId("worker-1")
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Act
            receptionist.register(workerKey, worker);

            // Wait for registration to propagate
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Listing<WorkerActor.Command> listing =
                        receptionist.find(workerKey).toCompletableFuture().get(5, TimeUnit.SECONDS);

                // Assert
                assertThat(listing.isEmpty()).isFalse();
                assertThat(listing.size()).isEqualTo(1);

                Set<SpringActorRef<WorkerActor.Command>> workers = listing.getServiceInstances();
                assertThat(workers).hasSize(1);

                // Verify the worker can do work
                SpringActorRef<WorkerActor.Command> foundWorker = workers.iterator().next();
                String result = foundWorker
                        .ask(new WorkerActor.DoWork("task-1"))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute()
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                assertThat(result).contains("completed task: task-1");
            });
        }

        @Test
        void testRegisterAndFindMultipleActors() throws Exception {
            // Arrange
            ServiceKey<WorkerActor.Command> workerKey =
                    ServiceKey.create(WorkerActor.Command.class, "test-worker-pool");
            SpringReceptionistService receptionist = actorSystem.receptionist();

            List<SpringActorRef<WorkerActor.Command>> workers = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                SpringActorRef<WorkerActor.Command> worker = actorSystem
                        .actor(WorkerActor.class)
                        .withId("pool-worker-" + i)
                        .spawn()
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                workers.add(worker);

                // Act
                receptionist.register(workerKey, worker);
            }

            // Wait for all registrations to propagate
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                Listing<WorkerActor.Command> listing =
                        receptionist.find(workerKey).toCompletableFuture().get(5, TimeUnit.SECONDS);

                // Assert
                assertThat(listing.isEmpty()).isFalse();
                assertThat(listing.size()).isEqualTo(3);

                Set<SpringActorRef<WorkerActor.Command>> foundWorkers = listing.getServiceInstances();
                assertThat(foundWorkers).hasSize(3);
            });
        }

        @Test
        void testFindReturnsEmptyListingWhenNoActorsRegistered() throws Exception {
            // Arrange
            ServiceKey<WorkerActor.Command> workerKey =
                    ServiceKey.create(WorkerActor.Command.class, "test-empty-service");
            SpringReceptionistService receptionist = actorSystem.receptionist();

            // Act
            Listing<WorkerActor.Command> listing =
                    receptionist.find(workerKey).toCompletableFuture().get(5, TimeUnit.SECONDS);

            // Assert
            assertThat(listing.isEmpty()).isTrue();
            assertThat(listing.size()).isEqualTo(0);
            assertThat(listing.getServiceInstances()).isEmpty();
        }

        @Test
        void testDeregisterRemovesActor() throws Exception {
            // Arrange
            ServiceKey<WorkerActor.Command> workerKey =
                    ServiceKey.create(WorkerActor.Command.class, "test-deregister");
            SpringReceptionistService receptionist = actorSystem.receptionist();

            SpringActorRef<WorkerActor.Command> worker = actorSystem
                    .actor(WorkerActor.class)
                    .withId("worker-dereg")
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            receptionist.register(workerKey, worker);

            // Wait for registration
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Listing<WorkerActor.Command> listing =
                        receptionist.find(workerKey).toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(listing.size()).isEqualTo(1);
            });

            // Act - Deregister the actor
            receptionist.deregister(workerKey, worker);

            // Assert - Actor should be removed
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Listing<WorkerActor.Command> listing =
                        receptionist.find(workerKey).toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(listing.isEmpty()).isTrue();
            });
        }

        @Test
        void testActorAutomaticallyDeregisteredWhenStopped() throws Exception {
            // Arrange
            ServiceKey<WorkerActor.Command> workerKey =
                    ServiceKey.create(WorkerActor.Command.class, "test-auto-dereg");
            SpringReceptionistService receptionist = actorSystem.receptionist();

            SpringActorRef<WorkerActor.Command> worker = actorSystem
                    .actor(WorkerActor.class)
                    .withId("worker-auto-dereg")
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            receptionist.register(workerKey, worker);

            // Wait for registration
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Listing<WorkerActor.Command> listing =
                        receptionist.find(workerKey).toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(listing.size()).isEqualTo(1);
            });

            // Act - Stop the actor
            worker.stop();

            // Assert - Actor should be automatically deregistered
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Listing<WorkerActor.Command> listing =
                        receptionist.find(workerKey).toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(listing.isEmpty()).isTrue();
            });
        }
    }

    @Nested
    class SubscriptionTests {

        @Test
        void testSubscribeReceivesInitialListing() throws Exception {
            // Arrange
            ServiceKey<WorkerActor.Command> workerKey =
                    ServiceKey.create(WorkerActor.Command.class, "test-subscribe-initial");
            SpringReceptionistService receptionist = actorSystem.receptionist();

            List<Listing<WorkerActor.Command>> receivedListings = new CopyOnWriteArrayList<>();

            // Register a worker before subscribing
            SpringActorRef<WorkerActor.Command> worker = actorSystem
                    .actor(WorkerActor.class)
                    .withId("worker-sub-initial")
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            receptionist.register(workerKey, worker);

            // Wait for registration
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Listing<WorkerActor.Command> listing =
                        receptionist.find(workerKey).toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(listing.size()).isEqualTo(1);
            });

            // Act - Subscribe
            receptionist.subscribe(workerKey, receivedListings::add);

            // Assert - Should receive initial listing
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(receivedListings).isNotEmpty();
                assertThat(receivedListings.get(0).size()).isEqualTo(1);
            });
        }

        @Test
        void testSubscribeReceivesUpdatesWhenActorRegistered() throws Exception {
            // Arrange
            ServiceKey<WorkerActor.Command> workerKey =
                    ServiceKey.create(WorkerActor.Command.class, "test-subscribe-register");
            SpringReceptionistService receptionist = actorSystem.receptionist();

            List<Listing<WorkerActor.Command>> receivedListings = new CopyOnWriteArrayList<>();

            // Act - Subscribe before any registration
            receptionist.subscribe(workerKey, receivedListings::add);

            // Should receive initial empty listing
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(receivedListings).isNotEmpty();
            });

            int initialSize = receivedListings.size();

            // Register a worker
            SpringActorRef<WorkerActor.Command> worker = actorSystem
                    .actor(WorkerActor.class)
                    .withId("worker-sub-reg")
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            receptionist.register(workerKey, worker);

            // Assert - Should receive update with the new actor
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(receivedListings.size()).isGreaterThan(initialSize);

                // Find the listing with one actor
                boolean foundNonEmptyListing = receivedListings.stream()
                        .anyMatch(listing -> listing.size() == 1);
                assertThat(foundNonEmptyListing).isTrue();
            });
        }

        @Test
        void testSubscribeReceivesUpdatesWhenActorDeregistered() throws Exception {
            // Arrange
            ServiceKey<WorkerActor.Command> workerKey =
                    ServiceKey.create(WorkerActor.Command.class, "test-subscribe-deregister");
            SpringReceptionistService receptionist = actorSystem.receptionist();

            List<Listing<WorkerActor.Command>> receivedListings = new CopyOnWriteArrayList<>();

            // Register a worker first
            SpringActorRef<WorkerActor.Command> worker = actorSystem
                    .actor(WorkerActor.class)
                    .withId("worker-sub-dereg")
                    .spawn()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            receptionist.register(workerKey, worker);

            // Wait for registration
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Listing<WorkerActor.Command> listing =
                        receptionist.find(workerKey).toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(listing.size()).isEqualTo(1);
            });

            // Subscribe
            receptionist.subscribe(workerKey, receivedListings::add);

            // Wait for initial listing
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(receivedListings).isNotEmpty();
            });

            int initialSize = receivedListings.size();

            // Act - Stop the worker (should trigger deregistration)
            worker.stop();

            // Assert - Should receive update showing empty listing
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(receivedListings.size()).isGreaterThan(initialSize);

                // Find the empty listing
                boolean foundEmptyListing =
                        receivedListings.stream().anyMatch(listing -> listing.isEmpty());
                assertThat(foundEmptyListing).isTrue();
            });
        }
    }

    @Nested
    class LoadBalancingPatternTests {

        @Test
        void testRoundRobinDistributionAcrossWorkers() throws Exception {
            // Arrange
            ServiceKey<WorkerActor.Command> workerKey =
                    ServiceKey.create(WorkerActor.Command.class, "test-load-balance");
            SpringReceptionistService receptionist = actorSystem.receptionist();

            // Register 3 workers
            for (int i = 1; i <= 3; i++) {
                SpringActorRef<WorkerActor.Command> worker = actorSystem
                        .actor(WorkerActor.class)
                        .withId("lb-worker-" + i)
                        .spawn()
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                receptionist.register(workerKey, worker);
            }

            // Wait for all registrations
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                Listing<WorkerActor.Command> listing =
                        receptionist.find(workerKey).toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(listing.size()).isEqualTo(3);
            });

            // Act - Send work to workers
            Listing<WorkerActor.Command> listing =
                    receptionist.find(workerKey).toCompletableFuture().get(5, TimeUnit.SECONDS);

            List<SpringActorRef<WorkerActor.Command>> workers =
                    new ArrayList<>(listing.getServiceInstances());
            List<CompletionStage<String>> results = new ArrayList<>();

            // Simple round-robin distribution
            for (int i = 0; i < 6; i++) {
                SpringActorRef<WorkerActor.Command> worker = workers.get(i % workers.size());
                CompletionStage<String> result = worker.ask(new WorkerActor.DoWork("task-" + i))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute();
                results.add(result);
            }

            // Assert - All tasks should complete successfully
            for (CompletionStage<String> result : results) {
                String taskResult = result.toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(taskResult).contains("completed task:");
            }
        }
    }

    @Nested
    class ServiceKeyTests {

        @Test
        void testServiceKeyEquality() {
            // Arrange
            ServiceKey<WorkerActor.Command> key1 =
                    ServiceKey.create(WorkerActor.Command.class, "test-key");
            ServiceKey<WorkerActor.Command> key2 =
                    ServiceKey.create(WorkerActor.Command.class, "test-key");
            ServiceKey<WorkerActor.Command> key3 =
                    ServiceKey.create(WorkerActor.Command.class, "different-key");

            // Assert
            assertThat(key1).isEqualTo(key2);
            assertThat(key1).isNotEqualTo(key3);
            assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
        }

        @Test
        void testServiceKeyGetId() {
            // Arrange
            ServiceKey<WorkerActor.Command> key =
                    ServiceKey.create(WorkerActor.Command.class, "my-service");

            // Assert
            assertThat(key.getId()).isEqualTo("my-service");
        }
    }
}
