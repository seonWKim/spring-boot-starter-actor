package io.github.seonwkim.example.persistence.eventlog;

public record OrderApprovedEvent(String orderId, String approvedBy) implements OrderDomainEvent {
    @Override
    public String getEventType() {
        return "ORDER_APPROVED";
    }
}
