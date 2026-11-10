package io.github.seonwkim.actor.delivery;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SupervisionStrategy;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/** Reliable delivery to sharded entities on a one-node cluster: an entity that failed gets the message again. */
class ShardedReliableDeliveryTest {

    @SpringBootApplication
    @EnableActorSupport
    static class TestApp {
        @Bean
        public OrderEntity reliableOrderEntity() {
            return new OrderEntity();
        }
    }

    /**
     * Records the orders each entity handled and throws once for ids in {@link #failOnce}. The
     * entity id's prefix picks its supervision: {@code stop-}, {@code restart-} or {@code resume-}.
     */
    static class OrderEntity implements SpringShardedActor<OrderEntity.Command> {

        static final ShardedEntityType<Command> TYPE = ShardedEntityType.of(Command.class, "ReliableOrderEntity");

        interface Command extends JsonSerializable {}

        record PlaceOrder(String id) implements Command {}

        final Set<String> failOnce = ConcurrentHashMap.newKeySet();
        final Map<String, List<String>> handled = new ConcurrentHashMap<>();

        @Override
        public ShardedEntityType<Command> entityType() {
            return TYPE;
        }

        @Override
        public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
            String entityId = ctx.getEntityId();
            SpringActorBehavior<Command> behavior = SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(PlaceOrder.class, (c, order) -> {
                        if (failOnce.remove(entityId + "/" + order.id())) {
                            throw new IllegalStateException("database down for " + order.id());
                        }
                        handled.computeIfAbsent(entityId, id -> new CopyOnWriteArrayList<>())
                                .add(order.id());
                        return SpringActorBehavior.same();
                    })
                    .build();
            if (entityId.startsWith("restart-")) {
                return ReliableDelivery.consumer(behavior, SupervisionStrategy.restart());
            }
            if (entityId.startsWith("resume-")) {
                return ReliableDelivery.consumer(behavior, SupervisionStrategy.resume());
            }
            return ReliableDelivery.consumer(behavior);
        }
    }

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void startOneNodeCluster() {
        context = new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.actor.provider=cluster",
                        "spring.actor.remote.port=0",
                        "spring.actor.cluster.join-self=true",
                        "spring.actor.pekko.management.enabled=false",
                        // Wake a failed entity after 1s instead of 10s.
                        "spring.actor.pekko.reliable-delivery.sharding.producer-controller"
                                + ".resend-first-unconfirmed-idle-timeout=1s")
                .run();
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"stop-1", "restart-1", "resume-1"})
    void entityWhoseHandlerThrewGetsTheMessageAgain(String entityId) throws Exception {
        OrderEntity entity = context.getBean(OrderEntity.class);
        entity.failOnce.add(entityId + "/o-2");

        try (ShardedReliableSender<OrderEntity.Command> sender = context.getBean(SpringReliableDelivery.class)
                .toSharded(OrderEntity.class)
                .start()) {
            CompletableFuture.allOf(Stream.of("o-1", "o-2", "o-3")
                            .map(id -> sender.send(entityId, new OrderEntity.PlaceOrder(id))
                                    .toCompletableFuture())
                            .toArray(CompletableFuture[]::new))
                    .get(30, SECONDS);
        }

        assertThat(entity.handled.get(entityId)).containsExactly("o-1", "o-2", "o-3");
    }
}
