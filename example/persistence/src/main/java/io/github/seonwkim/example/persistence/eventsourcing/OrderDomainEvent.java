package io.github.seonwkim.example.persistence.eventsourcing;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderCreatedEvent.class, name = "ORDER_CREATED"),
    @JsonSubTypes.Type(value = OrderApprovedEvent.class, name = "ORDER_APPROVED"),
    @JsonSubTypes.Type(value = OrderItemAddedEvent.class, name = "ORDER_ITEM_ADDED"),
    @JsonSubTypes.Type(value = OrderCancelledEvent.class, name = "ORDER_CANCELLED")
})
public interface OrderDomainEvent {
    String getEventType();
}
