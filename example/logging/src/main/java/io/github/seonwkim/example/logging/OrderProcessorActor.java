package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OrderProcessorActor demonstrates dynamic MDC usage.
 * Each message includes order-specific context in the logs.
 */
@Component
public class OrderProcessorActor implements SpringActor<OrderProcessorActor.Command> {

    public interface Command {}

    public static class ProcessOrder implements Command {
        public final String orderId;
        public final String customerId;
        public final double amount;
        public final ActorRef<OrderProcessed> replyTo;

        public ProcessOrder(String orderId, String customerId, double amount, ActorRef<OrderProcessed> replyTo) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.amount = amount;
            this.replyTo = replyTo;
        }
    }

    public static class OrderProcessed {
        public final String orderId;
        public final String status;
        public final String message;

        public OrderProcessed(String orderId, String status, String message) {
            this.orderId = orderId;
            this.status = status;
            this.message = message;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            // Dynamic MDC: computed per message
            .withMdc(msg -> {
                if (msg instanceof ProcessOrder) {
                    ProcessOrder order = (ProcessOrder) msg;
                    return Map.of(
                        "orderId", order.orderId,
                        "customerId", order.customerId,
                        "amount", String.valueOf(order.amount)
                    );
                }
                return Map.of();
            })
            .onMessage(ProcessOrder.class, (ctx, msg) -> {
                // All log entries will include orderId, customerId, and amount in MDC
                ctx.getLog().info("Starting order processing");

                try {
                    // Simulate order processing
                    ctx.getLog().debug("Validating order details");
                    Thread.sleep(100);

                    ctx.getLog().debug("Checking inventory");
                    Thread.sleep(50);

                    ctx.getLog().debug("Calculating total");
                    Thread.sleep(30);

                    ctx.getLog().info("Order processed successfully");

                    msg.replyTo.tell(new OrderProcessed(
                        msg.orderId,
                        "SUCCESS",
                        "Order processed successfully"
                    ));

                } catch (Exception e) {
                    ctx.getLog().error("Failed to process order", e);
                    msg.replyTo.tell(new OrderProcessed(
                        msg.orderId,
                        "ERROR",
                        "Failed to process order: " + e.getMessage()
                    ));
                }

                return Behaviors.same();
            })
            .build();
    }
}
