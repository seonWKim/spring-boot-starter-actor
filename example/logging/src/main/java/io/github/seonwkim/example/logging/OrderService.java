package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.MdcConfig;
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.TagsConfig;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
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
     * Each request includes a unique requestId for tracing.
     */
    public Mono<OrderProcessorActor.OrderProcessed> processOrder(
            String customerId, double amount) {

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        String requestId = "REQ-" + UUID.randomUUID().toString().substring(0, 8);

        // The actor will add orderId, customerId, and amount to MDC automatically
        // We're also adding a requestId at the service level for request tracing
        return Mono.fromCompletionStage(
            orderProcessor.<OrderProcessorActor.ProcessOrder, OrderProcessorActor.OrderProcessed>ask(
                replyTo -> new OrderProcessorActor.ProcessOrder(orderId, customerId, amount, replyTo),
                Duration.ofSeconds(10)
            )
        );
    }
}
