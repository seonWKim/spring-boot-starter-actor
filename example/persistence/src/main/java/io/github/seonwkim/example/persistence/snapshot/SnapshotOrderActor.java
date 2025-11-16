package io.github.seonwkim.example.persistence.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.seonwkim.core.*;
import io.github.seonwkim.example.persistence.Order;
import io.github.seonwkim.example.persistence.OrderRepository;
import io.github.seonwkim.example.persistence.OrderStatus;
import java.time.Instant;
import java.util.Optional;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Example actor with snapshot support for fast recovery.
 * Snapshots are created periodically based on the strategy.
 */
@Component
public class SnapshotOrderActor implements SpringActor<SnapshotOrderActor.Command> {

    private final OrderRepository orderRepository;
    private final ActorSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public SnapshotOrderActor(
            OrderRepository orderRepository, ActorSnapshotRepository snapshotRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

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

    public static class UpdateOrder extends AskCommand<OrderResponse> implements Command {
        private final double newAmount;

        public UpdateOrder(double newAmount) {
            this.newAmount = newAmount;
        }

        public double getNewAmount() {
            return newAmount;
        }
    }

    public static class ApproveOrder extends AskCommand<OrderResponse> implements Command {}

    public static class GetOrder extends AskCommand<OrderResponse> implements Command {}

    public static class SaveSnapshot implements Command {}

    public record OrderResponse(boolean success, Order order, String message) {}

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> {
                    // Try to load from snapshot first
                    Order order = loadFromSnapshot(actorContext.actorId()).orElseGet(() -> orderRepository
                            .findByOrderId(actorContext.actorId())
                            .orElse(null));

                    SnapshotStrategy strategy = new HybridSnapshotStrategy(
                            10, // Every 10 operations
                            60000 // Or every 60 seconds
                            );

                    return new SnapshotOrderBehavior(
                            ctx, actorContext, orderRepository, snapshotRepository, objectMapper, order, strategy);
                })
                .onMessage(CreateOrder.class, SnapshotOrderBehavior::handleCreateOrder)
                .onMessage(UpdateOrder.class, SnapshotOrderBehavior::handleUpdateOrder)
                .onMessage(ApproveOrder.class, SnapshotOrderBehavior::handleApproveOrder)
                .onMessage(GetOrder.class, SnapshotOrderBehavior::handleGetOrder)
                .onMessage(SaveSnapshot.class, SnapshotOrderBehavior::handleSaveSnapshot)
                .build();
    }

    private Optional<Order> loadFromSnapshot(String actorId) {
        try {
            return snapshotRepository
                    .findTopByActorIdAndActorTypeOrderByCreatedAtDesc(actorId, "OrderActor")
                    .map(snapshot -> {
                        try {
                            return objectMapper.readValue(snapshot.getStateData(), Order.class);
                        } catch (Exception e) {
                            return null;
                        }
                    });
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static class SnapshotOrderBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final SpringActorContext actorContext;
        private final OrderRepository orderRepository;
        private final ActorSnapshotRepository snapshotRepository;
        private final ObjectMapper objectMapper;
        private final SnapshotStrategy strategy;

        private Order currentOrder;
        private long operationCount;
        private Instant lastSnapshotTime;

        SnapshotOrderBehavior(
                SpringBehaviorContext<Command> ctx,
                SpringActorContext actorContext,
                OrderRepository orderRepository,
                ActorSnapshotRepository snapshotRepository,
                ObjectMapper objectMapper,
                Order currentOrder,
                SnapshotStrategy strategy) {
            this.ctx = ctx;
            this.actorContext = actorContext;
            this.orderRepository = orderRepository;
            this.snapshotRepository = snapshotRepository;
            this.objectMapper = objectMapper;
            this.currentOrder = currentOrder;
            this.strategy = strategy;
            this.operationCount = 0;
            this.lastSnapshotTime = Instant.now();
        }

        private Behavior<Command> handleCreateOrder(CreateOrder cmd) {
            if (currentOrder != null) {
                cmd.reply(new OrderResponse(false, null, "Order already exists"));
                return Behaviors.same();
            }

            try {
                currentOrder = new Order(actorContext.actorId(), cmd.getCustomerId(), cmd.getAmount());
                currentOrder = orderRepository.save(currentOrder);
                operationCount++;

                saveSnapshotIfNeeded();

                ctx.getLog().info("Order created with snapshot support: {}", currentOrder.getOrderId());
                cmd.reply(new OrderResponse(true, currentOrder, "Order created"));

            } catch (Exception e) {
                ctx.getLog().error("Failed to create order", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }

            return Behaviors.same();
        }

        private Behavior<Command> handleUpdateOrder(UpdateOrder cmd) {
            if (currentOrder == null) {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
                return Behaviors.same();
            }

            try {
                currentOrder.setAmount(cmd.getNewAmount());
                currentOrder = orderRepository.save(currentOrder);
                operationCount++;

                saveSnapshotIfNeeded();

                cmd.reply(new OrderResponse(true, currentOrder, "Order updated"));

            } catch (Exception e) {
                ctx.getLog().error("Failed to update order", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }

            return Behaviors.same();
        }

        private Behavior<Command> handleApproveOrder(ApproveOrder cmd) {
            if (currentOrder == null) {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
                return Behaviors.same();
            }

            try {
                currentOrder.setStatus(OrderStatus.APPROVED);
                currentOrder = orderRepository.save(currentOrder);
                operationCount++;

                saveSnapshotIfNeeded();

                cmd.reply(new OrderResponse(true, currentOrder, "Order approved"));

            } catch (Exception e) {
                ctx.getLog().error("Failed to approve order", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }

            return Behaviors.same();
        }

        private Behavior<Command> handleGetOrder(GetOrder cmd) {
            if (currentOrder == null) {
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

        private Behavior<Command> handleSaveSnapshot(SaveSnapshot cmd) {
            saveSnapshot();
            return Behaviors.same();
        }

        private void saveSnapshotIfNeeded() {
            long timeSinceLastSnapshot = Instant.now().toEpochMilli() - lastSnapshotTime.toEpochMilli();

            if (strategy.shouldCreateSnapshot(operationCount, timeSinceLastSnapshot)) {
                saveSnapshot();
                operationCount = 0;
                lastSnapshotTime = Instant.now();
            }
        }

        private void saveSnapshot() {
            if (currentOrder == null) {
                return;
            }

            try {
                String stateData = objectMapper.writeValueAsString(currentOrder);

                ActorSnapshot snapshot = new ActorSnapshot(actorContext.actorId(), "OrderActor", stateData);

                snapshotRepository.save(snapshot);

                ctx.getLog().info("Snapshot saved for order {}", actorContext.actorId());

                // Clean up old snapshots (keep only last 5)
                cleanupOldSnapshots();

            } catch (Exception e) {
                ctx.getLog().error("Failed to save snapshot", e);
            }
        }

        private void cleanupOldSnapshots() {
            try {
                long count = snapshotRepository.countSnapshots(actorContext.actorId(), "OrderActor");

                if (count > 5) {
                    Instant cutoff = Instant.now().minusSeconds(3600); // Keep last hour
                    snapshotRepository.deleteOldSnapshots(actorContext.actorId(), "OrderActor", cutoff);
                }
            } catch (Exception e) {
                ctx.getLog().warn("Failed to cleanup old snapshots", e);
            }
        }
    }
}
