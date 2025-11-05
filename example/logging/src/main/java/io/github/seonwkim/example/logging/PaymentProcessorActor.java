package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * PaymentProcessorActor demonstrates both static and dynamic MDC.
 * Static MDC (service, region) is set at spawn time.
 * Dynamic MDC (paymentId, orderId) is computed per message.
 */
@Component
public class PaymentProcessorActor implements SpringActor<PaymentProcessorActor.Command> {

    public interface Command {}

    public static class ProcessPayment implements Command {
        public final String paymentId;
        public final String orderId;
        public final String customerId;
        public final double amount;
        public final String paymentMethod;
        public final ActorRef<PaymentProcessed> replyTo;

        public ProcessPayment(String paymentId, String orderId, String customerId,
                            double amount, String paymentMethod, ActorRef<PaymentProcessed> replyTo) {
            this.paymentId = paymentId;
            this.orderId = orderId;
            this.customerId = customerId;
            this.amount = amount;
            this.paymentMethod = paymentMethod;
            this.replyTo = replyTo;
        }
    }

    public static class PaymentProcessed {
        public final String paymentId;
        public final String status;
        public final String transactionId;
        public final String message;

        public PaymentProcessed(String paymentId, String status, String transactionId, String message) {
            this.paymentId = paymentId;
            this.status = status;
            this.transactionId = transactionId;
            this.message = message;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            // Dynamic MDC: computed per message
            .withMdc(msg -> {
                if (msg instanceof ProcessPayment) {
                    ProcessPayment payment = (ProcessPayment) msg;
                    return Map.of(
                        "paymentId", payment.paymentId,
                        "orderId", payment.orderId,
                        "customerId", payment.customerId,
                        "amount", String.valueOf(payment.amount),
                        "paymentMethod", payment.paymentMethod
                    );
                }
                return Map.of();
            })
            .onMessage(ProcessPayment.class, (ctx, msg) -> {
                // All log entries include both static and dynamic MDC
                ctx.getLog().info("Starting payment processing");

                try {
                    // Simulate payment processing
                    ctx.getLog().debug("Validating payment method");
                    Thread.sleep(80);

                    ctx.getLog().debug("Contacting payment gateway");
                    Thread.sleep(150);

                    String transactionId = "txn-" + System.currentTimeMillis();
                    ctx.getLog().info("Payment processed successfully with transaction ID: {}", transactionId);

                    msg.replyTo.tell(new PaymentProcessed(
                        msg.paymentId,
                        "SUCCESS",
                        transactionId,
                        "Payment processed successfully"
                    ));

                } catch (Exception e) {
                    ctx.getLog().error("Payment processing failed", e);
                    msg.replyTo.tell(new PaymentProcessed(
                        msg.paymentId,
                        "ERROR",
                        null,
                        "Payment failed: " + e.getMessage()
                    ));
                }

                return Behaviors.same();
            })
            .build();
    }
}
