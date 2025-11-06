package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.MdcConfig;
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.TagsConfig;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * PaymentService demonstrates spawning actors with both static MDC and tags.
 * Static MDC provides service-level context, while dynamic MDC adds payment-specific details.
 */
@Service
public class PaymentService {

    private final SpringActorSystem actorSystem;
    private final SpringActorRef<PaymentProcessorActor.Command> paymentProcessor;

    public PaymentService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;

        // Spawn PaymentProcessorActor with:
        // 1. Static MDC: service and region appear in all logs from this actor
        // 2. Tags: for categorization and filtering
        Map<String, String> staticMdc = Map.of(
            "service", "payment-service",
            "region", "us-east-1",
            "version", "2.0"
        );

        this.paymentProcessor = actorSystem
            .actor(PaymentProcessorActor.class)
            .withId("payment-processor")
            .withMdc(MdcConfig.of(staticMdc))
            .withTags(TagsConfig.of("payment-service", "critical", "io-bound"))
            .withBlockingDispatcher() // Use blocking dispatcher for I/O operations
            .withTimeout(Duration.ofSeconds(5))
            .spawnAndWait();
    }

    /**
     * Process a payment with full MDC context.
     * Combines static MDC (service, region) with dynamic MDC (paymentId, orderId, etc.)
     */
    public Mono<PaymentProcessorActor.PaymentProcessed> processPayment(
            String orderId, String userId, double amount, String paymentMethod) {

        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        String requestId = MDC.get("requestId");

        return processPayment(orderId, userId, amount, paymentMethod, requestId);
    }

    /**
     * Process a payment with explicit requestId (for reactive chain calls).
     * Use this when calling from within reactive chains where MDC may not be available.
     */
    public Mono<PaymentProcessorActor.PaymentProcessed> processPayment(
            String orderId, String userId, double amount, String paymentMethod, String requestId) {

        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);

        // Pass requestId to actor so it appears in logs via withMdc()
        return Mono.fromCompletionStage(
            paymentProcessor.ask(
                replyTo -> new PaymentProcessorActor.ProcessPayment(
                    paymentId, orderId, userId, amount, paymentMethod, requestId, replyTo),
                Duration.ofSeconds(15)
            )
        );
    }
}
