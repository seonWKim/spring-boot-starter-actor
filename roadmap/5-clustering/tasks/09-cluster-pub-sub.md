# Task 4.1: Cluster Pub-Sub Implementation

**Priority:** MEDIUM  
**Estimated Effort:** 2 weeks  
**Status:** TODO

## Objective

Implement a Spring Boot-friendly cluster pub-sub system using Pekko's distributed pub-sub.

## ClusterEventBus Service

```java
package io.github.seonwkim.core.cluster;

@Service
public class ClusterEventBus {
    
    private final SpringActorSystem actorSystem;
    
    /**
     * Subscribe an actor to a topic
     */
    public <T> void subscribe(String topic, SpringActorRef<T> subscriber) {
        ActorRef<Receptionist.Command> receptionist = 
            actorSystem.getRaw().receptionist();
        
        ServiceKey<T> key = ServiceKey.create(
            (Class<T>) subscriber.getMessageClass(), 
            topic
        );
        
        receptionist.tell(Receptionist.register(key, subscriber.getRaw()));
    }
    
    /**
     * Unsubscribe from a topic
     */
    public <T> void unsubscribe(String topic, SpringActorRef<T> subscriber) {
        // Deregister from receptionist
    }
    
    /**
     * Publish event to all subscribers
     */
    public <T> CompletionStage<Integer> publish(String topic, T event, Class<T> eventClass) {
        ActorRef<Receptionist.Command> receptionist = 
            actorSystem.getRaw().receptionist();
        
        ServiceKey<T> key = ServiceKey.create(eventClass, topic);
        
        return receptionist.ask(
            replyTo -> Receptionist.find(key, replyTo),
            Duration.ofSeconds(3)
        ).thenApply(listing -> {
            Set<ActorRef<T>> subscribers = listing.getServiceInstances(key);
            subscribers.forEach(actor -> actor.tell(event));
            return subscribers.size();
        });
    }
}
```

## Event Types

```java
public interface ClusterEvent extends JsonSerializable {
    String getTopic();
}

public class OrderCreated implements ClusterEvent {
    public final String orderId;
    public final String topic = "orders";
    // Constructor, getters
}
```

## Subscriber Actor

```java
@Component
public class OrderSubscriberActor implements SpringActor<OrderCreated> {
    
    @Override
    public SpringActorBehavior<OrderCreated> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(OrderCreated.class, ctx)
            .onMessage(OrderCreated.class, this::handleOrderCreated)
            .build();
    }
    
    private Behavior<OrderCreated> handleOrderCreated(
            ActorContext<OrderCreated> ctx, 
            OrderCreated event) {
        
        ctx.getLog().info("Received order created: {}", event.orderId);
        // Process event
        
        return Behaviors.same();
    }
}
```

## Usage Example

```java
@Service
public class OrderService {
    
    private final ClusterEventBus eventBus;
    private final SpringActorSystem actorSystem;
    
    @PostConstruct
    public void subscribeToOrders() {
        SpringActorRef<OrderCreated> subscriber = 
            actorSystem.actor(OrderSubscriberActor.class)
                .withId("order-subscriber")
                .spawnAndWait();
        
        eventBus.subscribe("orders", subscriber);
    }
    
    public void createOrder(Order order) {
        // Create order...
        
        // Publish event to all subscribers across cluster
        eventBus.publish("orders", 
            new OrderCreated(order.getId()), 
            OrderCreated.class);
    }
}
```

## Deliverables

1. `core/src/main/java/io/github/seonwkim/core/cluster/ClusterEventBus.java`
2. Example event types and subscribers in `example/cluster/`
3. Tests in `core/src/test/java/io/github/seonwkim/core/cluster/ClusterEventBusTest.java`
4. Documentation: `docs/clustering/cluster-pub-sub.md`

## Success Criteria

- ✅ Subscribe/publish works across cluster nodes
- ✅ Events delivered to all subscribers
- ✅ At-most-once delivery semantics
- ✅ Topic-based routing works correctly
- ✅ Tests validate pub-sub behavior
