# 9. Integration Features

Seamless integration with Spring ecosystem and external systems, focusing on examples over library features.

---

## 9.1 Spring Events Bridge

**Priority:** MEDIUM  
**Approach:** Wrapped implementation

### Overview

Bridge between Spring Application Events and Actor messages in a wrapped, Spring Boot-friendly way.

### Implementation

```java
// Automatic bridge via annotations
@Component
public class SpringEventBridge {
    
    @EventListener
    @SendToActor(OrderActor.class)
    public OrderActor.CreateOrder onOrderPlaced(OrderPlacedEvent event) {
        return new OrderActor.CreateOrder(event.getOrderId(), event.getAmount());
    }
}

// Actor publishing Spring events
@Component
public class OrderActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(OrderCreated.class, (context, msg) -> {
                // Publish Spring event
                context.publishApplicationEvent(
                    new OrderCreatedEvent(msg.orderId())
                );
                return Behaviors.same();
            })
            .build();
    }
}
```

**Design:** Wrapped implementation that hides complexity from users while leveraging Spring's event infrastructure.

---

## 9.2 WebSocket Integration

**Priority:** MEDIUM  
**Complexity:** Medium

### Overview

Direct WebSocket-to-Actor message routing for real-time applications.

### Implementation

```java
@Configuration
@EnableWebSocket
public class WebSocketConfiguration {
    
    @Bean
    public WebSocketHandler chatWebSocketHandler(SpringActorSystem actorSystem) {
        return new ActorWebSocketHandler<ChatMessage>(
            actorSystem,
            ChatRoomActor.class,
            session -> "chat-room-" + session.getUri().getQuery()
        );
    }
}
```

---

## 9.3 Kafka Integration

**Priority:** MEDIUM  
**Approach:** Example module only

### Overview

Provide comprehensive example module showing Kafka-to-Actor integration patterns rather than library-level support.

### Example Module Structure

```
example/kafka/
  ├── README.md
  ├── build.gradle.kts
  ├── src/main/java/
  │   ├── KafkaConsumerActor.java
  │   ├── KafkaProducerActor.java
  │   └── OrderProcessingPipeline.java
  └── src/main/resources/
      └── application.yml
```

### Example Implementation

```java
// Example: Kafka consumer actor
@Component
public class KafkaConsumerActor implements SpringActor<Command> {
    
    private final KafkaTemplate<String, Order> kafkaTemplate;
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(ConsumeFromKafka.class, this::handleConsume)
            .build();
    }
}
```

**Rationale:** Kafka integration patterns vary greatly by use case. An example module provides flexibility while showing best practices.

---

## 9.4 gRPC Integration

**Priority:** LOW  
**Approach:** Example module only

### Overview

Example module showing how to expose actors as gRPC services.

### Example Module Structure

```
example/grpc/
  ├── README.md
  ├── build.gradle.kts
  ├── src/main/proto/
  │   └── order_service.proto
  └── src/main/java/
      └── OrderActorGrpcService.java
```

### Example Implementation

```java
@GrpcService
public class OrderActorService extends OrderServiceGrpc.OrderServiceImplBase {
    
    @Autowired
    private SpringActorSystem actorSystem;
    
    @Override
    public void createOrder(CreateOrderRequest request, 
                           StreamObserver<CreateOrderResponse> responseObserver) {
        actorSystem.getOrSpawn(OrderActor.class, request.getOrderId())
            .thenCompose(actor -> actor.ask(
                new CreateOrder(request.getOrderId(), request.getAmount())
            ).withTimeout(Duration.ofSeconds(5)).execute())
            .whenComplete((result, error) -> {
                if (error != null) {
                    responseObserver.onError(error);
                } else {
                    responseObserver.onNext(toResponse(result));
                    responseObserver.onCompleted();
                }
            });
    }
}
```

**Rationale:** gRPC integration is straightforward with examples. Library support would add complexity without significant value.

---

## Summary

1. **Spring Events**: Wrapped implementation for seamless integration
2. **WebSocket**: Library support for real-time applications  
3. **Kafka**: Example module approach (not library feature)
4. **gRPC**: Example module approach (not library feature)

Focus on **pragmatic integration** - library support where it adds value, examples where flexibility is needed.
