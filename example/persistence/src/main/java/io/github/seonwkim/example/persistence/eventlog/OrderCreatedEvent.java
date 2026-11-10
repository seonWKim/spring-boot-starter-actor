package io.github.seonwkim.example.persistence.eventlog;

public record OrderCreatedEvent(String orderId, String customerId, double amount) implements OrderDomainEvent {
    @Override
    public String getEventType() {
        return "ORDER_CREATED";
    }
}
