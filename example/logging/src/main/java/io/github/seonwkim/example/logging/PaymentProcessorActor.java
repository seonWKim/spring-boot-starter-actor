package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import java.util.Map;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

@Component
public class PaymentProcessorActor implements SpringActor<PaymentProcessorActor.Command> {

    public interface Command {}

    public static class ProcessPayment extends AskCommand<PaymentProcessed> implements Command {
        public final String paymentId;
        public final String orderId;
        public final String userId;
        public final double amount;
        public final String paymentMethod;
        public final String requestId;

        public ProcessPayment(
                String paymentId,
                String orderId,
                String userId,
                double amount,
                String paymentMethod,
                String requestId) {
            this.paymentId = paymentId;
            this.orderId = orderId;
            this.userId = userId;
            this.amount = amount;
            this.paymentMethod = paymentMethod;
            this.requestId = requestId;
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
                .withMdc(msg -> {
                    if (msg instanceof ProcessPayment payment) {
                        Map<String, String> mdc = new java.util.HashMap<>();
                        mdc.put("paymentId", payment.paymentId);
                        mdc.put("orderId", payment.orderId);
                        mdc.put("userId", payment.userId);
                        mdc.put("amount", String.valueOf(payment.amount));
                        mdc.put("paymentMethod", payment.paymentMethod);
                        if (payment.requestId != null) {
                            mdc.put("requestId", payment.requestId);
                        }
                        return mdc;
                    }
                    return Map.of();
                })
                .onMessage(ProcessPayment.class, (ctx, msg) -> {
                    ctx.getLog().info("Starting payment processing");

                    try {
                        ctx.getLog().debug("Validating payment method");
                        Thread.sleep(80);

                        ctx.getLog().debug("Contacting payment gateway");
                        Thread.sleep(150);

                        String transactionId = "txn-" + System.currentTimeMillis();
                        ctx.getLog().info("Payment processed successfully with transaction ID: {}", transactionId);

                        msg.reply(new PaymentProcessed(
                                msg.paymentId, "SUCCESS", transactionId, "Payment processed successfully"));

                    } catch (Exception e) {
                        ctx.getLog().error("Payment processing failed", e);
                        msg.reply(new PaymentProcessed(
                                msg.paymentId, "ERROR", null, "Payment failed: " + e.getMessage()));
                    }

                    return Behaviors.same();
                })
                .build();
    }
}
