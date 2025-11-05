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
            String orderId, String customerId, double amount, String paymentMethod) {

        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);

        // The actor will add payment-specific details to MDC automatically
        return Mono.fromCompletionStage(
            paymentProcessor.<PaymentProcessorActor.ProcessPayment, PaymentProcessorActor.PaymentProcessed>ask(
                replyTo -> new PaymentProcessorActor.ProcessPayment(
                    paymentId, orderId, customerId, amount, paymentMethod, replyTo),
                Duration.ofSeconds(15)
            )
        );
    }
}
