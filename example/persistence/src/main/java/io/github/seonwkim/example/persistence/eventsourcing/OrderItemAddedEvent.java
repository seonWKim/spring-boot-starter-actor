package io.github.seonwkim.example.persistence.eventsourcing;

public record OrderItemAddedEvent(String orderId, String productId, int quantity, double price)
        implements OrderDomainEvent {
    @Override
    public String getEventType() {
        return "ORDER_ITEM_ADDED";
    }
}
