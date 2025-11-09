package io.github.seonwkim.example.persistence.eventsourcing;

public record OrderCancelledEvent(String orderId, String reason) implements OrderDomainEvent {
    @Override
    public String getEventType() {
        return "ORDER_CANCELLED";
    }
}
