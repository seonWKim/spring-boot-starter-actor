package io.github.seonwkim.example.persistence;

import io.github.seonwkim.core.*;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Example actor demonstrating JPA-based persistence.
 * This actor manages order state using Spring Data JPA repositories.
 */
@Component
public class OrderActor implements SpringActor<OrderActor.Command> {

    private final OrderRepository orderRepository;

    public OrderActor(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // Commands
    public interface Command {}

    public static class CreateOrder extends AskCommand<OrderResponse> implements Command {
        private final String customerId;
        private final double amount;

        public CreateOrder(String customerId, double amount) {
            this.customerId = customerId;
            this.amount = amount;
        }

        public String getCustomerId() {
            return customerId;
        }

        public double getAmount() {
            return amount;
        }
    }

    public static class AddItem extends AskCommand<OrderResponse> implements Command {
        private final String productId;
        private final int quantity;
        private final double price;

        public AddItem(String productId, int quantity, double price) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
        }

        public String getProductId() {
            return productId;
        }

        public int getQuantity() {
            return quantity;
        }

        public double getPrice() {
            return price;
        }
    }

    public static class GetOrder extends AskCommand<OrderResponse> implements Command {}

    public static class ApproveOrder extends AskCommand<OrderResponse> implements Command {}

    public static class RejectOrder extends AskCommand<OrderResponse> implements Command {}

    public static class UpdateAmount extends AskCommand<OrderResponse> implements Command {
        private final double newAmount;

        public UpdateAmount(double newAmount) {
            this.newAmount = newAmount;
        }

        public double getNewAmount() {
            return newAmount;
        }
    }

    public record OrderResponse(boolean success, Order order, String message) {}

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> {
                    // Load existing order on startup
                    Order order = orderRepository
                            .findByOrderId(actorContext.actorId())
                            .orElse(null);
                    return new OrderActorBehavior(ctx, actorContext, orderRepository, order);
                })
                .onMessage(CreateOrder.class, OrderActorBehavior::handleCreateOrder)
                .onMessage(AddItem.class, OrderActorBehavior::handleAddItem)
                .onMessage(GetOrder.class, OrderActorBehavior::handleGetOrder)
                .onMessage(ApproveOrder.class, OrderActorBehavior::handleApproveOrder)
                .onMessage(RejectOrder.class, OrderActorBehavior::handleRejectOrder)
                .onMessage(UpdateAmount.class, OrderActorBehavior::handleUpdateAmount)
                .build();
    }

    private static class OrderActorBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final SpringActorContext actorContext;
        private final OrderRepository orderRepository;
        private Order currentOrder;

        OrderActorBehavior(
                SpringBehaviorContext<Command> ctx,
                SpringActorContext actorContext,
                OrderRepository orderRepository,
                Order currentOrder) {
            this.ctx = ctx;
            this.actorContext = actorContext;
            this.orderRepository = orderRepository;
            this.currentOrder = currentOrder;
        }

        private Behavior<Command> handleCreateOrder(CreateOrder cmd) {
            if (currentOrder != null) {
                cmd.reply(new OrderResponse(false, null, "Order already exists"));
                return Behaviors.same();
            }

            try {
                currentOrder = new Order(actorContext.actorId(), cmd.getCustomerId(), cmd.getAmount());

                currentOrder = orderRepository.save(currentOrder);

                ctx.getLog().info("Order created: {}", currentOrder.getOrderId());
                cmd.reply(new OrderResponse(true, currentOrder, "Order created successfully"));

            } catch (Exception e) {
                ctx.getLog().error("Failed to create order", e);
                cmd.reply(new OrderResponse(false, null, "Failed to create order: " + e.getMessage()));
            }

            return Behaviors.same();
        }

        private Behavior<Command> handleAddItem(AddItem cmd) {
            if (currentOrder == null) {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
                return Behaviors.same();
            }

            try {
                OrderItem item = new OrderItem(cmd.getProductId(), cmd.getQuantity(), cmd.getPrice());

                currentOrder.addItem(item);
                currentOrder = orderRepository.save(currentOrder);

                ctx.getLog().info("Item added to order: {}", currentOrder.getOrderId());
                cmd.reply(new OrderResponse(true, currentOrder, "Item added successfully"));

            } catch (Exception e) {
                ctx.getLog().error("Failed to add item", e);
                cmd.reply(new OrderResponse(false, null, "Failed to add item: " + e.getMessage()));
            }

            return Behaviors.same();
        }

        private Behavior<Command> handleGetOrder(GetOrder cmd) {
            if (currentOrder == null) {
                // Try to reload from database
                currentOrder =
                        orderRepository.findByOrderId(actorContext.actorId()).orElse(null);
            }

            if (currentOrder != null) {
                cmd.reply(new OrderResponse(true, currentOrder, "Order retrieved"));
            } else {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
            }

            return Behaviors.same();
        }

        private Behavior<Command> handleApproveOrder(ApproveOrder cmd) {
            return updateOrderStatus(cmd, OrderStatus.APPROVED, "approved");
        }

        private Behavior<Command> handleRejectOrder(RejectOrder cmd) {
            return updateOrderStatus(cmd, OrderStatus.REJECTED, "rejected");
        }

        private Behavior<Command> handleUpdateAmount(UpdateAmount cmd) {
            if (currentOrder == null) {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
                return Behaviors.same();
            }

            try {
                currentOrder.setAmount(cmd.getNewAmount());
                currentOrder = orderRepository.save(currentOrder);

                ctx.getLog().info("Order amount updated: {}", currentOrder.getOrderId());
                cmd.reply(new OrderResponse(true, currentOrder, "Amount updated"));

            } catch (OptimisticLockingFailureException e) {
                ctx.getLog().warn("Optimistic locking failure, reloading order");
                currentOrder =
                        orderRepository.findByOrderId(currentOrder.getOrderId()).orElse(null);
                cmd.reply(
                        new OrderResponse(false, currentOrder, "Order was modified by another process, please retry"));

            } catch (Exception e) {
                ctx.getLog().error("Failed to update amount", e);
                cmd.reply(new OrderResponse(false, null, "Failed to update: " + e.getMessage()));
            }

            return Behaviors.same();
        }

        private Behavior<Command> updateOrderStatus(
                AskCommand<OrderResponse> cmd, OrderStatus newStatus, String action) {

            if (currentOrder == null) {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
                return Behaviors.same();
            }

            try {
                currentOrder.setStatus(newStatus);
                currentOrder = orderRepository.save(currentOrder);

                ctx.getLog().info("Order {} {}", currentOrder.getOrderId(), action);
                cmd.reply(new OrderResponse(true, currentOrder, "Order " + action));

            } catch (OptimisticLockingFailureException e) {
                ctx.getLog().warn("Optimistic locking failure for order {}", currentOrder.getOrderId());
                currentOrder =
                        orderRepository.findByOrderId(currentOrder.getOrderId()).orElse(null);
                cmd.reply(
                        new OrderResponse(false, currentOrder, "Order was modified by another process, please retry"));

            } catch (Exception e) {
                ctx.getLog().error("Failed to update order", e);
                cmd.reply(new OrderResponse(false, null, "Failed to update: " + e.getMessage()));
            }

            return Behaviors.same();
        }
    }
}
