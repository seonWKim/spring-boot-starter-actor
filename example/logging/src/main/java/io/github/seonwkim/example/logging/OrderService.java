package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.TagsConfig;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * OrderService demonstrates spawning actors with static MDC and tags.
 */
@Service
public class OrderService {

    private final SpringActorSystem actorSystem;
    private final SpringActorRef<OrderProcessorActor.Command> orderProcessor;

    public OrderService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;

        // Spawn OrderProcessorActor with tags for categorization
        // Tags help identify this actor in logs: order-service, critical, cpu-intensive
        this.orderProcessor = actorSystem
            .actor(OrderProcessorActor.class)
            .withId("order-processor")
            .withTags(TagsConfig.of("order-service", "critical", "cpu-intensive"))
            .withTimeout(Duration.ofSeconds(5))
            .spawnAndWait();
    }

    /**
     * Process an order with dynamic MDC context.
     * Each request includes order-specific details in the actor's logs.
     */
    public Mono<OrderProcessorActor.OrderProcessed> processOrder(
            String userId, double amount) {

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        String requestId = MDC.get("requestId");

        return processOrder(userId, amount, requestId);
    }

    /**
     * Process an order with explicit requestId (for reactive chain calls).
     * Use this when calling from within reactive chains where MDC may not be available.
     */
    public Mono<OrderProcessorActor.OrderProcessed> processOrder(
            String userId, double amount, String requestId) {

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);

        // Pass requestId to actor so it appears in logs via withMdc()
        return Mono.fromCompletionStage(
            orderProcessor.ask(
                replyTo -> new OrderProcessorActor.ProcessOrder(orderId, userId, amount, requestId, replyTo),
                Duration.ofSeconds(10)
            )
        );
    }
}
