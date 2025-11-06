package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.TagsConfig;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Service
public class OrderService {

    private final SpringActorRef<OrderProcessorActor.Command> orderProcessor;

    public OrderService(SpringActorSystem actorSystem) {
        this.orderProcessor = actorSystem
            .actor(OrderProcessorActor.class)
            .withId("order-processor")
            .withTags(TagsConfig.of("order-service", "critical", "cpu-intensive"))
            .withTimeout(Duration.ofSeconds(5))
            .spawnAndWait();
    }

    public Mono<OrderProcessorActor.OrderProcessed> processOrder(String userId, double amount) {
        String requestId = MDC.get("requestId");
        return processOrder(userId, amount, requestId);
    }

    public Mono<OrderProcessorActor.OrderProcessed> processOrder(String userId, double amount, String requestId) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        return Mono.fromCompletionStage(
            orderProcessor.ask(
                replyTo -> new OrderProcessorActor.ProcessOrder(orderId, userId, amount, requestId, replyTo),
                Duration.ofSeconds(10)
            )
        );
    }
}
