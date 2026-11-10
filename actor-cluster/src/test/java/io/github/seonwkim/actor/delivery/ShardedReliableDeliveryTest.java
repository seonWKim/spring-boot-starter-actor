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
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Reliable delivery to sharded entities on a one-node cluster. Every message is serialized, as
 * between nodes, so the envelope's serializer is part of what is tested.
 */
class ShardedReliableDeliveryTest {

    @SpringBootApplication
    @EnableActorSupport
    static class TestApp {
        @Bean
        public OrderEntity reliableOrderEntity() {
            return new OrderEntity();
        }
    }

    /** Counts handler runs per entity; throws once for each {@code entityId/orderId} in {@link #failOnce}. */
    static class OrderEntity implements SpringShardedActor<OrderEntity.Command> {

        static final ShardedEntityType<Command> TYPE = ShardedEntityType.of(Command.class, "ReliableOrderEntity");

        interface Command extends JsonSerializable {}

        record PlaceOrder(String id) implements Command {}

        final Set<String> failOnce = ConcurrentHashMap.newKeySet();
        final Map<String, AtomicInteger> runs = new ConcurrentHashMap<>();

        @Override
        public ShardedEntityType<Command> entityType() {
            return TYPE;
        }

        @Override
        public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
            String entityId = ctx.getEntityId();
            return ReliableDelivery.consumer(SpringActorBehavior.builder(Command.class, ctx)
                    .withSupervisionStrategy(SupervisionStrategy.restart())
                    .onMessage(PlaceOrder.class, (c, order) -> {
                        runs.computeIfAbsent(entityId, id -> new AtomicInteger())
                                .incrementAndGet();
                        if (failOnce.remove(entityId + "/" + order.id())) {
                            throw new IllegalStateException("database down for " + order.id());
                        }
                        return SpringActorBehavior.same();
                    })
                    .build());
        }
    }

    private static ConfigurableApplicationContext context;
    private static OrderEntity entity;
    private static ShardedReliableSender<OrderEntity.Command> sender;

    @BeforeAll
    static void startOneNodeCluster() {
        context = new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.actor.provider=cluster",
                        "spring.actor.remote.port=0",
                        "spring.actor.cluster.join-self=true",
                        "spring.actor.pekko.management.enabled=false",
                        "spring.actor.pekko.actor.serialize-messages=on")
                .run();
        entity = context.getBean(OrderEntity.class);
        sender = context.getBean(SpringReliableDelivery.class)
                .toSharded(OrderEntity.class)
                .withRetry(RetryPolicy.defaults().withBackoff(Duration.ofMillis(50), Duration.ofMillis(200)))
                .build();
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void throwingEntityHandlerIsRetriedAndSucceedsLater() throws Exception {
        entity.failOnce.add("retry-1/o-1");

        sender.send("retry-1", new OrderEntity.PlaceOrder("o-1"))
                .toCompletableFuture()
                .get(30, SECONDS);

        assertThat(entity.runs.get("retry-1")).hasValue(2);
    }

    @Test
    void sameKeyToAnEntityRunsOnce() throws Exception {
        sender.send("dedupe-1", new OrderEntity.PlaceOrder("o-1"), "order-1")
                .toCompletableFuture()
                .get(30, SECONDS);
        sender.send("dedupe-1", new OrderEntity.PlaceOrder("o-1"), "order-1")
                .toCompletableFuture()
                .get(30, SECONDS);

        assertThat(entity.runs.get("dedupe-1")).hasValue(1);
    }
}
