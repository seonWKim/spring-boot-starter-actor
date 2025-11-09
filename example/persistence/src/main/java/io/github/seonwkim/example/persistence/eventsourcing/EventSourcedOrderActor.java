package io.github.seonwkim.example.persistence.eventsourcing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.example.persistence.OrderStatus;
import java.util.ArrayList;
import java.util.List;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Example of event-sourced actor that persists all changes as events.
 * State is rebuilt from event history on actor startup.
 */
@Component
public class EventSourcedOrderActor implements SpringActor<EventSourcedOrderActor.Command> {

    private final OrderEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public EventSourcedOrderActor(OrderEventRepository eventRepository, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
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

    public static class ApproveOrder extends AskCommand<OrderResponse> implements Command {
        private final String approvedBy;

        public ApproveOrder(String approvedBy) {
            this.approvedBy = approvedBy;
        }

        public String getApprovedBy() {
            return approvedBy;
        }
    }

    public static class GetOrder extends AskCommand<OrderResponse> implements Command {}

    public static class GetHistory extends AskCommand<HistoryResponse> implements Command {}

    public record OrderResponse(boolean success, OrderState state, String message) {}

    public record HistoryResponse(List<OrderEvent> events) {}

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> {
                    // Rebuild state from events
                    OrderState state = rebuildState(actorContext.actorId());
                    return new EventSourcedBehavior(ctx, actorContext, eventRepository, objectMapper, state);
                })
                .onMessage(CreateOrder.class, EventSourcedBehavior::handleCreateOrder)
                .onMessage(AddItem.class, EventSourcedBehavior::handleAddItem)
                .onMessage(ApproveOrder.class, EventSourcedBehavior::handleApproveOrder)
                .onMessage(GetOrder.class, EventSourcedBehavior::handleGetOrder)
                .onMessage(GetHistory.class, EventSourcedBehavior::handleGetHistory)
                .build();
    }

    private OrderState rebuildState(String orderId) {
        List<OrderEvent> events = eventRepository.findByOrderIdOrderBySequenceNumberAsc(orderId);
        OrderState state = new OrderState(orderId);

        for (OrderEvent event : events) {
            try {
                OrderDomainEvent domainEvent = deserializeEvent(event);
                state.apply(domainEvent);
            } catch (Exception e) {
                throw new RuntimeException("Failed to rebuild state from events", e);
            }
        }

        return state;
    }

    private OrderDomainEvent deserializeEvent(OrderEvent event) throws Exception {
        return objectMapper.readValue(event.getEventData(), OrderDomainEvent.class);
    }

    // State class
    public static class OrderState {
        private final String orderId;
        private String customerId;
        private double amount;
        private OrderStatus status;
        private List<OrderItem> items;
        private long sequenceNumber;

        public OrderState(String orderId) {
            this.orderId = orderId;
            this.items = new ArrayList<>();
            this.status = OrderStatus.PENDING;
            this.sequenceNumber = 0;
        }

        public void apply(OrderDomainEvent event) {
            if (event instanceof OrderCreatedEvent e) {
                this.customerId = e.customerId();
                this.amount = e.amount();
                this.status = OrderStatus.PENDING;
            } else if (event instanceof OrderItemAddedEvent e) {
                this.items.add(new OrderItem(e.productId(), e.quantity(), e.price()));
            } else if (event instanceof OrderApprovedEvent e) {
                this.status = OrderStatus.APPROVED;
            } else if (event instanceof OrderCancelledEvent e) {
                this.status = OrderStatus.CANCELLED;
            } else {
                throw new IllegalArgumentException("Unknown event type: " + event);
            }
            this.sequenceNumber++;
        }

        // Getters
        public String getOrderId() {
            return orderId;
        }

        public String getCustomerId() {
            return customerId;
        }

        public double getAmount() {
            return amount;
        }

        public OrderStatus getStatus() {
            return status;
        }

        public List<OrderItem> getItems() {
            return items;
        }

        public long getSequenceNumber() {
            return sequenceNumber;
        }
    }

    public record OrderItem(String productId, int quantity, double price) {}

    // Behavior class
    private static class EventSourcedBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;
        private final OrderEventRepository eventRepository;
        private final ObjectMapper objectMapper;
        private final OrderState state;

        EventSourcedBehavior(
                ActorContext<Command> ctx,
                SpringActorContext actorContext,
                OrderEventRepository eventRepository,
                ObjectMapper objectMapper,
                OrderState state) {
            this.ctx = ctx;
            this.actorContext = actorContext;
            this.eventRepository = eventRepository;
            this.objectMapper = objectMapper;
            this.state = state;
        }

        private Behavior<Command> handleCreateOrder(CreateOrder cmd) {
            if (state.getSequenceNumber() > 0) {
                cmd.reply(new OrderResponse(false, null, "Order already exists"));
                return Behaviors.same();
            }

            try {
                // Create event
                OrderCreatedEvent domainEvent =
                        new OrderCreatedEvent(actorContext.actorId(), cmd.getCustomerId(), cmd.getAmount());

                // Persist event
                persistEvent(domainEvent, state.getSequenceNumber() + 1);

                // Apply to state
                state.apply(domainEvent);

                ctx.getLog().info("Order created: {}", actorContext.actorId());
                cmd.reply(new OrderResponse(true, state, "Order created"));

            } catch (Exception e) {
                ctx.getLog().error("Failed to create order", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }

            return Behaviors.same();
        }

        private Behavior<Command> handleAddItem(AddItem cmd) {
            try {
                OrderItemAddedEvent domainEvent = new OrderItemAddedEvent(
                        actorContext.actorId(), cmd.getProductId(), cmd.getQuantity(), cmd.getPrice());

                persistEvent(domainEvent, state.getSequenceNumber() + 1);
                state.apply(domainEvent);

                ctx.getLog().info("Item added to order: {}", actorContext.actorId());
                cmd.reply(new OrderResponse(true, state, "Item added"));

            } catch (Exception e) {
                ctx.getLog().error("Failed to add item", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }

            return Behaviors.same();
        }

        private Behavior<Command> handleApproveOrder(ApproveOrder cmd) {
            try {
                OrderApprovedEvent domainEvent = new OrderApprovedEvent(actorContext.actorId(), cmd.getApprovedBy());

                persistEvent(domainEvent, state.getSequenceNumber() + 1);
                state.apply(domainEvent);

                ctx.getLog().info("Order approved: {}", actorContext.actorId());
                cmd.reply(new OrderResponse(true, state, "Order approved"));

            } catch (Exception e) {
                ctx.getLog().error("Failed to approve order", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }

            return Behaviors.same();
        }

        private Behavior<Command> handleGetOrder(GetOrder cmd) {
            cmd.reply(new OrderResponse(true, state, "Order retrieved"));
            return Behaviors.same();
        }

        private Behavior<Command> handleGetHistory(GetHistory cmd) {
            List<OrderEvent> events = eventRepository.findByOrderIdOrderBySequenceNumberAsc(actorContext.actorId());
            cmd.reply(new HistoryResponse(events));
            return Behaviors.same();
        }

        private OrderEvent persistEvent(OrderDomainEvent domainEvent, long sequenceNumber) throws Exception {
            String eventData = objectMapper.writeValueAsString(domainEvent);
            OrderEvent event =
                    new OrderEvent(actorContext.actorId(), domainEvent.getEventType(), eventData, sequenceNumber);
            return eventRepository.save(event);
        }
    }
}
