package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderProcessorActor implements SpringActor<OrderProcessorActor.Command> {

    public interface Command {}

    public static class ProcessOrder implements Command {
        public final String orderId;
        public final String userId;
        public final double amount;
        public final String requestId;
        public final ActorRef<OrderProcessed> replyTo;

        public ProcessOrder(String orderId, String userId, double amount, String requestId, ActorRef<OrderProcessed> replyTo) {
            this.orderId = orderId;
            this.userId = userId;
            this.amount = amount;
            this.requestId = requestId;
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
            .withMdc(msg -> {
                if (msg instanceof ProcessOrder order) {
                    Map<String, String> mdc = new java.util.HashMap<>();
                    mdc.put("orderId", order.orderId);
                    mdc.put("userId", order.userId);
                    mdc.put("amount", String.valueOf(order.amount));
                    if (order.requestId != null) {
                        mdc.put("requestId", order.requestId);
                    }
                    return mdc;
                }
                return Map.of();
            })
            .onMessage(ProcessOrder.class, (ctx, msg) -> {
                ctx.getLog().info("Starting order processing");

                try {
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
