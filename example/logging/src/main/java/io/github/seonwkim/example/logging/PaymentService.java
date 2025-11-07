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

@Service
public class PaymentService {

    private final SpringActorRef<PaymentProcessorActor.Command> paymentProcessor;

    public PaymentService(SpringActorSystem actorSystem) {
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
            .withBlockingDispatcher()
            .withTimeout(Duration.ofSeconds(5))
            .spawnAndWait();
    }

    public Mono<PaymentProcessorActor.PaymentProcessed> processPayment(
            String orderId, String userId, double amount, String paymentMethod) {
        String requestId = MDC.get("requestId");
        return processPayment(orderId, userId, amount, paymentMethod, requestId);
    }

    public Mono<PaymentProcessorActor.PaymentProcessed> processPayment(
            String orderId, String userId, double amount, String paymentMethod, String requestId) {
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        return Mono.fromCompletionStage(
            paymentProcessor.ask(
                replyTo -> new PaymentProcessorActor.ProcessPayment(
                    paymentId, orderId, userId, amount, paymentMethod, requestId, replyTo),
                Duration.ofSeconds(15)
            )
        );
    }
}
