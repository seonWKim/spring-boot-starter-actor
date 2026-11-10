package io.github.seonwkim.actor.delivery;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.SupervisionStrategy;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

/** Reliable delivery to a local actor: redelivery after a failed handler, and its failure modes. */
@SpringBootTest(
        properties = {"spring.actor.pekko.name=reliable-delivery-test", "spring.actor.pekko.actor.provider=local"})
class ReliableDeliveryTest {

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.actor")
    static class TestApp {
        @Bean
        public OrderActor reliableOrderActor() {
            return new OrderActor();
        }
    }

    /** Records the orders it handled; throws once for each id in {@code failOnce}. */
    static class OrderActor implements SpringActorWithContext<OrderActor.Command, OrderActor.Ctx> {

        interface Command {}

        record PlaceOrder(String id) implements Command {}

        static class Ctx extends SpringActorContext {
            final String actorId = UUID.randomUUID().toString();
            final Set<String> failOnce = ConcurrentHashMap.newKeySet();
            final List<String> handled = new CopyOnWriteArrayList<>();

            @Nullable final SupervisionStrategy supervision;

            final boolean builderSupervision;

            Ctx(@Nullable SupervisionStrategy supervision, boolean builderSupervision, String... failOnce) {
                this.supervision = supervision;
                this.builderSupervision = builderSupervision;
                this.failOnce.addAll(List.of(failOnce));
            }

            @Override
            public String actorId() {
                return actorId;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(Ctx ctx) {
            SpringActorBehavior.Builder<Command, ?> builder = SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(PlaceOrder.class, (c, order) -> {
                        if (ctx.failOnce.remove(order.id())) {
                            throw new IllegalStateException("database down for " + order.id());
                        }
                        ctx.handled.add(order.id());
                        return SpringActorBehavior.same();
                    });
            if (ctx.builderSupervision) {
                builder.withSupervisionStrategy(SupervisionStrategy.restart());
            }
            return ctx.supervision == null
                    ? ReliableDelivery.consumer(builder.build())
                    : ReliableDelivery.consumer(builder.build(), ctx.supervision);
        }
    }

    @Autowired
    private SpringActorSystem actorSystem;

    @Autowired
    private SpringReliableDelivery reliableDelivery;

    private SpringActorHandle<OrderActor.Command> spawn(OrderActor.Ctx ctx) {
        return actorSystem.actor(OrderActor.class).withContext(ctx).spawnAndWait();
    }

    private static CompletableFuture<Void> sendAll(ReliableSender<OrderActor.Command> sender, String... ids) {
        return CompletableFuture.allOf(Stream.of(ids)
                .map(id -> sender.send(new OrderActor.PlaceOrder(id)).toCompletableFuture())
                .toArray(CompletableFuture[]::new));
    }

    @Test
    void messageWhoseHandlerThrewIsDeliveredAgainAfterRestart() throws Exception {
        OrderActor.Ctx ctx = new OrderActor.Ctx(SupervisionStrategy.restart(), false, "o-2");
        try (ReliableSender<OrderActor.Command> sender =
                reliableDelivery.to(spawn(ctx)).start()) {
            sendAll(sender, "o-1", "o-2", "o-3").get(10, SECONDS);
        }
        assertThat(ctx.handled).containsExactly("o-1", "o-2", "o-3");
    }

    @Test
    void messageWhoseHandlerThrewIsDeliveredAgainAfterResume() throws Exception {
        OrderActor.Ctx ctx = new OrderActor.Ctx(SupervisionStrategy.resume(), false, "o-2");
        try (ReliableSender<OrderActor.Command> sender =
                reliableDelivery.to(spawn(ctx)).start()) {
            sendAll(sender, "o-1", "o-2", "o-3").get(10, SECONDS);
        }
        assertThat(ctx.handled).containsExactly("o-1", "o-2", "o-3");
    }

    @Test
    void plainTellStillReachesAReliableConsumer() {
        OrderActor.Ctx ctx = new OrderActor.Ctx(null, false);
        spawn(ctx).tell(new OrderActor.PlaceOrder("told"));
        await().atMost(5, SECONDS).until(() -> ctx.handled.contains("told"));
    }

    @Test
    void sendFailsWhenTheConsumerStops() {
        // No supervision: the failed handler stops the actor, so the message can't be confirmed.
        OrderActor.Ctx ctx = new OrderActor.Ctx(null, false, "o-1");
        try (ReliableSender<OrderActor.Command> sender =
                reliableDelivery.to(spawn(ctx)).start()) {
            CompletableFuture<Void> sent =
                    sender.send(new OrderActor.PlaceOrder("o-1")).toCompletableFuture();
            assertThatThrownBy(() -> sent.get(10, SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("stopped");
        }
    }

    @Test
    void builderSupervisionIsRejectedBecauseItWouldConfirmFailedDeliveries() {
        assertThatThrownBy(() -> spawn(new OrderActor.Ctx(null, true)))
                .rootCause()
                .hasMessageContaining("ReliableDelivery.consumer(behavior, strategy)");
    }
}
