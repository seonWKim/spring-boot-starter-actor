package io.github.seonwkim.example.persistence.eventsourcing;

public record OrderCreatedEvent(String orderId, String customerId, double amount) implements OrderDomainEvent {
    @Override
    public String getEventType() {
        return "ORDER_CREATED";
    }
}
