package io.github.seonwkim.actor.internal.delivery;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.SupervisionStrategy;
import io.github.seonwkim.actor.delivery.ReliableDelivery;
import io.github.seonwkim.actor.delivery.ReliableDeliveryException;
import io.github.seonwkim.actor.delivery.ReliableDeliveryException.Reason;
import io.github.seonwkim.actor.delivery.ReliableSender;
import io.github.seonwkim.actor.delivery.RetryPolicy;
import io.github.seonwkim.actor.delivery.SpringReliableDelivery;
import io.github.seonwkim.actor.delivery.StaleGenerationException;
import io.github.seonwkim.actor.internal.PekkoActorRef;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

/**
 * Reliable delivery to a local actor: retries, deduplication by idempotency key, payload conflicts,
 * stale generations, giving up and why.
 */
@SpringBootTest(
        properties = {"spring.actor.pekko.name=reliable-delivery-test", "spring.actor.pekko.actor.provider=local"})
class ReliableDeliveryTest {

    /** Short enough that a test waits little for a retry. */
    private static final RetryPolicy FAST = RetryPolicy.defaults()
            .withAttemptTimeout(Duration.ofMillis(500))
            .withBackoff(Duration.ofMillis(50), Duration.ofMillis(200));

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.actor")
    static class TestApp {
        @Bean
        public OrderActor reliableOrderActor() {
            return new OrderActor();
        }
    }

    /**
     * Counts handler runs and records the orders it handled; can throw once per id, or always, and
     * can be stale (not yet active) for its first runs.
     */
    static class OrderActor implements SpringActorWithContext<OrderActor.Command, OrderActor.Ctx> {

        interface Command {}

        record PlaceOrder(String id) implements Command {}

        static class Ctx extends SpringActorContext {
            final String actorId = UUID.randomUUID().toString();
            final Set<String> failOnce = ConcurrentHashMap.newKeySet();
            final AtomicBoolean failAlways = new AtomicBoolean();
            /** Runs left before the actor is active; until then it throws StaleGenerationException. */
            final AtomicInteger staleRuns = new AtomicInteger();

            final AtomicInteger runs = new AtomicInteger();
            final List<String> handled = new CopyOnWriteArrayList<>();

            @Nullable Duration ttl;

            @Override
            public String actorId() {
                return actorId;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(Ctx ctx) {
            SpringActorBehavior<Command> behavior = SpringActorBehavior.builder(Command.class, ctx)
                    .withSupervisionStrategy(SupervisionStrategy.restart())
                    .onMessage(PlaceOrder.class, (c, order) -> {
                        ctx.runs.incrementAndGet();
                        if (ctx.staleRuns.getAndDecrement() > 0) {
                            throw new StaleGenerationException("not active yet");
                        }
                        if (ctx.failAlways.get() || ctx.failOnce.remove(order.id())) {
                            throw new IllegalStateException("database down for " + order.id());
                        }
                        ctx.handled.add(order.id());
                        return SpringActorBehavior.same();
                    })
                    .build();
            return ctx.ttl == null ? ReliableDelivery.consumer(behavior) : ReliableDelivery.consumer(behavior, ctx.ttl);
        }
    }

    @Autowired
    private SpringActorSystem actorSystem;

    @Autowired
    private SpringReliableDelivery reliableDelivery;

    private SpringActorHandle<OrderActor.Command> spawn(OrderActor.Ctx ctx) {
        return actorSystem.actor(OrderActor.class).withContext(ctx).spawnAndWait();
    }

    private ReliableSender<OrderActor.Command> senderTo(OrderActor.Ctx ctx) {
        return reliableDelivery.to(spawn(ctx)).withRetry(FAST).build();
    }

    /** Records every envelope it passes on to {@code actor}. */
    private ActorRef<ReliableEnvelope> recording(ActorRef<Object> actor, List<ReliableEnvelope> seen, String name) {
        return PekkoActorSystem.raw(actorSystem)
                .systemActorOf(
                        Behaviors.receiveMessage(envelope -> {
                            seen.add(envelope);
                            actor.tell(envelope);
                            return Behaviors.same();
                        }),
                        name,
                        Props.empty());
    }

    private static ReliableDeliveryException failureOf(CompletionStage<Void> send) {
        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> send.toCompletableFuture().get(20, SECONDS));
        assertThat(failure.getCause()).isInstanceOf(ReliableDeliveryException.class);
        return (ReliableDeliveryException) failure.getCause();
    }

    @Test
    void lostAckIsRetriedAndTheHandlerRunsOnce() throws Exception {
        OrderActor.Ctx ctx = new OrderActor.Ctx();
        ActorRef<Object> actor = PekkoActorRef.toPekko(spawn(ctx)).unsafeUpcast();
        // Passes messages on, but the first one's ack goes nowhere.
        ActorRef<ReliableEnvelope> losesFirstAck = PekkoActorSystem.raw(actorSystem)
                .systemActorOf(
                        Behaviors.setup(setup -> {
                            ActorRef<ReliableAck> nowhere = setup.spawnAnonymous(Behaviors.ignore());
                            AtomicBoolean lost = new AtomicBoolean();
                            return Behaviors.receiveMessage(envelope -> {
                                actor.tell(
                                        lost.compareAndSet(false, true)
                                                ? new ReliableEnvelope(
                                                        envelope.idempotencyKey(), envelope.message(), nowhere)
                                                : envelope);
                                return Behaviors.same();
                            });
                        }),
                        "loses-first-ack",
                        Props.empty());
        ReliableSender<OrderActor.Command> sender = reliableDelivery
                .to(new PekkoActorRef<OrderActor.Command>(losesFirstAck.unsafeUpcast()))
                .withRetry(FAST)
                .build();

        sender.send(new OrderActor.PlaceOrder("o-1")).toCompletableFuture().get(10, SECONDS);

        assertThat(ctx.runs).hasValue(1);
    }

    @Test
    void sameKeyRunsOnceWithinTheTtlAndAgainAfterIt() throws Exception {
        OrderActor.Ctx ctx = new OrderActor.Ctx();
        ctx.ttl = Duration.ofSeconds(1);
        ReliableSender<OrderActor.Command> sender = senderTo(ctx);

        sender.send(new OrderActor.PlaceOrder("o-1"), "order-1")
                .toCompletableFuture()
                .get(5, SECONDS);
        sender.send(new OrderActor.PlaceOrder("o-1"), "order-1")
                .toCompletableFuture()
                .get(5, SECONDS);
        assertThat(ctx.runs).hasValue(1);

        await().atMost(10, SECONDS).pollInterval(Duration.ofMillis(200)).until(() -> {
            sender.send(new OrderActor.PlaceOrder("o-1"), "order-1")
                    .toCompletableFuture()
                    .get(5, SECONDS);
            return ctx.runs.get() == 2;
        });
    }

    @Test
    void throwingHandlerIsRetriedAndSucceedsLater() throws Exception {
        // The actor restarts on the failure, under its own (builder) supervision.
        OrderActor.Ctx ctx = new OrderActor.Ctx();
        ctx.failOnce.add("o-1");

        senderTo(ctx)
                .send(new OrderActor.PlaceOrder("o-1"))
                .toCompletableFuture()
                .get(10, SECONDS);

        assertThat(ctx.runs).hasValue(2);
        assertThat(ctx.handled).containsExactly("o-1");
    }

    @Test
    void givesUpAfterMaxAttemptsAsATimeout() {
        OrderActor.Ctx ctx = new OrderActor.Ctx();
        ctx.failAlways.set(true);
        ReliableSender<OrderActor.Command> sender = reliableDelivery
                .to(spawn(ctx))
                .withRetry(FAST.withMaxAttempts(3).withTimeout(Duration.ofSeconds(30)))
                .build();

        ReliableDeliveryException failure = failureOf(sender.send(new OrderActor.PlaceOrder("o-1")));

        assertThat(failure.attempts()).isEqualTo(3);
        assertThat(failure.reason()).isEqualTo(Reason.TIMEOUT);
        assertThat(ctx.runs).hasValue(3);
    }

    @Test
    void staleGenerationIsRetriedAndSucceedsAfterActivation() throws Exception {
        OrderActor.Ctx ctx = new OrderActor.Ctx();
        ctx.staleRuns.set(2);

        senderTo(ctx)
                .send(new OrderActor.PlaceOrder("o-1"), "order-1")
                .toCompletableFuture()
                .get(10, SECONDS);

        assertThat(ctx.runs).hasValue(3);
        assertThat(ctx.handled).containsExactly("o-1");
    }

    @Test
    void anActorThatStaysStaleGivesUpAsStaleGeneration() {
        OrderActor.Ctx ctx = new OrderActor.Ctx();
        ctx.staleRuns.set(Integer.MAX_VALUE);
        ReliableSender<OrderActor.Command> sender = reliableDelivery
                .to(spawn(ctx))
                .withRetry(FAST.withMaxAttempts(3))
                .build();

        ReliableDeliveryException failure = failureOf(sender.send(new OrderActor.PlaceOrder("o-1")));

        assertThat(failure.reason()).isEqualTo(Reason.STALE_GENERATION);
        assertThat(failure.attempts()).isEqualTo(3);
    }

    @Test
    void retriesKeepTheSameKeyAndPayload() throws Exception {
        OrderActor.Ctx ctx = new OrderActor.Ctx();
        ctx.staleRuns.set(2);
        List<ReliableEnvelope> seen = new CopyOnWriteArrayList<>();
        ActorRef<ReliableEnvelope> relay =
                recording(PekkoActorRef.toPekko(spawn(ctx)).unsafeUpcast(), seen, "records-attempts");
        OrderActor.PlaceOrder order = new OrderActor.PlaceOrder("o-1");

        reliableDelivery
                .to(new PekkoActorRef<OrderActor.Command>(relay.unsafeUpcast()))
                .withRetry(FAST)
                .build()
                .send(order, "order-1")
                .toCompletableFuture()
                .get(10, SECONDS);

        assertThat(seen).hasSize(3).allSatisfy(envelope -> {
            assertThat(envelope.idempotencyKey()).isEqualTo("order-1");
            assertThat(envelope.message()).isSameAs(order);
        });
    }

    @Test
    void sameKeyWithAnotherPayloadIsAConflictAndNotRetried() throws Exception {
        OrderActor.Ctx ctx = new OrderActor.Ctx();
        ReliableSender<OrderActor.Command> sender = senderTo(ctx);
        sender.send(new OrderActor.PlaceOrder("o-1"), "order-1")
                .toCompletableFuture()
                .get(5, SECONDS);

        ReliableDeliveryException failure = failureOf(sender.send(new OrderActor.PlaceOrder("o-2"), "order-1"));

        assertThat(failure.reason()).isEqualTo(Reason.PAYLOAD_CONFLICT);
        assertThat(failure.attempts()).isEqualTo(1);
        assertThat(ctx.handled).containsExactly("o-1");
    }

    @Test
    void aStoppedActorIsUnavailable() {
        OrderActor.Ctx ctx = new OrderActor.Ctx();
        SpringActorHandle<OrderActor.Command> actor = spawn(ctx);
        ReliableSender<OrderActor.Command> sender =
                reliableDelivery.to(actor).withRetry(FAST.withMaxAttempts(2)).build();
        actor.stop();

        // Stopping is asynchronous: until the actor is gone, an attempt times out instead.
        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(
                        failureOf(sender.send(new OrderActor.PlaceOrder("o-1"))).reason())
                .isEqualTo(Reason.UNAVAILABLE));
    }

    @Test
    void plainTellStillReachesAReliableConsumer() {
        OrderActor.Ctx ctx = new OrderActor.Ctx();
        spawn(ctx).tell(new OrderActor.PlaceOrder("told"));
        await().atMost(5, SECONDS).until(() -> ctx.handled.contains("told"));
    }
}
