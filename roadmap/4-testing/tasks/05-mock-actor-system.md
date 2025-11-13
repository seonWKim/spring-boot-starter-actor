# Task 2.2: MockSpringActorSystem

**Priority:** HIGH  
**Estimated Effort:** 2-3 days  
**Phase:** 2 - Mock Actor Support

---

## Overview

Create `MockSpringActorSystem` for programmatic mocking of actors and actor system behavior in unit tests.

## Goals

1. Provide programmatic API for creating mock actors
2. Allow behavior configuration without annotations
3. Support verification of actor interactions
4. Enable testing without real actor system

## Implementation Details

### 1. MockSpringActorSystem

Create: `core/src/test/java/io/github/seonwkim/core/test/MockSpringActorSystem.java`

```java
public class MockSpringActorSystem {
    
    private final Map<String, SpringActorRef<?>> mocks = new ConcurrentHashMap<>();
    
    public static MockSpringActorSystem create() {
        return new MockSpringActorSystem();
    }
    
    /**
     * Create a mock actor builder for the given actor class.
     */
    public <T> MockActorBuilder<T> mock(Class<? extends SpringActor<T>> actorClass) {
        return new MockActorBuilder<>(this, actorClass);
    }
    
    /**
     * Get a previously created mock by name.
     */
    @SuppressWarnings("unchecked")
    public <T> SpringActorRef<T> getMock(String name) {
        return (SpringActorRef<T>) mocks.get(name);
    }
    
    /**
     * Register a mock actor.
     */
    <T> void registerMock(String name, SpringActorRef<T> mock) {
        mocks.put(name, mock);
    }
    
    /**
     * Clear all mocks.
     */
    public void reset() {
        mocks.clear();
    }
}
```

### 2. MockActorBuilder

Create: `core/src/test/java/io/github/seonwkim/core/test/MockActorBuilder.java`

```java
public class MockActorBuilder<T> {
    
    private final MockSpringActorSystem mockSystem;
    private final Class<? extends SpringActor<T>> actorClass;
    private String name;
    private final Map<Class<?>, Function<?, ?>> replyHandlers = new HashMap<>();
    
    public MockActorBuilder(MockSpringActorSystem mockSystem, 
                           Class<? extends SpringActor<T>> actorClass) {
        this.mockSystem = mockSystem;
        this.actorClass = actorClass;
        this.name = actorClass.getSimpleName() + "-mock";
    }
    
    public MockActorBuilder<T> withName(String name) {
        this.name = name;
        return this;
    }
    
    /**
     * Configure reply for a specific command type.
     */
    public <C, R> MockActorBuilder<T> replyTo(Class<C> commandClass, Function<C, R> replyFunction) {
        replyHandlers.put(commandClass, replyFunction);
        return this;
    }
    
    /**
     * Configure reply with a fixed response.
     */
    public <C, R> MockActorBuilder<T> replyTo(Class<C> commandClass, R reply) {
        return replyTo(commandClass, cmd -> reply);
    }
    
    /**
     * Build and register the mock actor.
     */
    public MockSpringActorRef<T> build() {
        SpringActorRef<T> mockRef = Mockito.mock(SpringActorRef.class, 
            Mockito.withSettings().name(name));
        
        // Configure ask() behavior for each registered handler
        for (Map.Entry<Class<?>, Function<?, ?>> entry : replyHandlers.entrySet()) {
            configureAskBehavior(mockRef, entry.getKey(), entry.getValue());
        }
        
        MockSpringActorRef<T> mockActorRef = new MockSpringActorRef<>(mockRef, name);
        mockSystem.registerMock(name, mockRef);
        
        return mockActorRef;
    }
    
    @SuppressWarnings("unchecked")
    private <C, R> void configureAskBehavior(
            SpringActorRef<T> mockRef,
            Class<?> commandClass,
            Function<?, ?> replyFunction) {
        
        when(mockRef.ask(any(commandClass)))
            .thenAnswer(invocation -> {
                C command = (C) invocation.getArgument(0);
                R reply = ((Function<C, R>) replyFunction).apply(command);
                return CompletableFuture.completedFuture(reply);
            });
    }
}
```

### 3. MockSpringActorRef

Create: `core/src/test/java/io/github/seonwkim/core/test/MockSpringActorRef.java`

```java
public class MockSpringActorRef<T> {
    
    private final SpringActorRef<T> mockRef;
    private final String name;
    
    public MockSpringActorRef(SpringActorRef<T> mockRef, String name) {
        this.mockRef = mockRef;
        this.name = name;
    }
    
    public SpringActorRef<T> getRef() {
        return mockRef;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * Verify that a message of the given type was received.
     */
    public <M> void verifyReceived(Class<M> messageClass) {
        verify(mockRef).tell(any(messageClass));
    }
    
    /**
     * Verify that a message of the given type was received a specific number of times.
     */
    public <M> void verifyReceived(Class<M> messageClass, VerificationMode mode) {
        verify(mockRef, mode).tell(any(messageClass));
    }
    
    /**
     * Verify that ask was called for a command of the given type.
     */
    public <M> void verifyAsked(Class<M> commandClass) {
        verify(mockRef).ask(any(commandClass));
    }
    
    /**
     * Verify that ask was called with a specific verification mode.
     */
    public <M> void verifyAsked(Class<M> commandClass, VerificationMode mode) {
        verify(mockRef, mode).ask(any(commandClass));
    }
    
    /**
     * Reset the mock.
     */
    public void reset() {
        Mockito.reset(mockRef);
    }
}
```

## Usage Examples

### Example 1: Basic Programmatic Mocking

```java
@Test
public void testWithMockActorSystem() {
    MockSpringActorSystem mockSystem = MockSpringActorSystem.create();
    
    MockSpringActorRef<OrderActor.Command> mockActor = mockSystem
        .mock(OrderActor.class)
        .withName("order-actor")
        .replyTo(CreateOrder.class, cmd -> 
            new OrderCreated(cmd.orderId(), cmd.amount()))
        .replyTo(GetOrder.class, cmd -> 
            new OrderDetails(cmd.orderId()))
        .build();
    
    // Use in service
    OrderService service = new OrderService(mockActor.getRef());
    OrderResult result = service.createOrder("order-1", 100.0);
    
    // Verify interactions
    mockActor.verifyReceived(CreateOrder.class);
    assertEquals("SUCCESS", result.status());
}
```

### Example 2: Dynamic Reply Logic

```java
@Test
public void testDynamicReplies() {
    MockSpringActorSystem mockSystem = MockSpringActorSystem.create();
    
    AtomicInteger counter = new AtomicInteger(0);
    
    MockSpringActorRef<CounterActor.Command> mockActor = mockSystem
        .mock(CounterActor.class)
        .replyTo(Increment.class, cmd -> 
            new CounterValue(counter.incrementAndGet()))
        .replyTo(GetValue.class, cmd -> 
            new CounterValue(counter.get()))
        .build();
    
    // Test
    CompletableFuture<CounterValue> result1 = 
        mockActor.getRef().ask(new Increment());
    assertEquals(1, result1.join().value());
    
    CompletableFuture<CounterValue> result2 = 
        mockActor.getRef().ask(new Increment());
    assertEquals(2, result2.join().value());
}
```

### Example 3: Verification

```java
@Test
public void testVerification() {
    MockSpringActorSystem mockSystem = MockSpringActorSystem.create();
    
    MockSpringActorRef<OrderActor.Command> mockActor = mockSystem
        .mock(OrderActor.class)
        .replyTo(CreateOrder.class, new OrderCreated("order-1", 100.0))
        .build();
    
    SpringActorRef<OrderActor.Command> actorRef = mockActor.getRef();
    
    // Use actor
    actorRef.tell(new CreateOrder("order-1", 100.0));
    actorRef.ask(new GetOrder("order-1"));
    
    // Verify
    mockActor.verifyReceived(CreateOrder.class, times(1));
    mockActor.verifyAsked(GetOrder.class, times(1));
}
```

### Example 4: Multiple Mock Actors

```java
@Test
public void testMultipleMocks() {
    MockSpringActorSystem mockSystem = MockSpringActorSystem.create();
    
    MockSpringActorRef<OrderActor.Command> orderMock = mockSystem
        .mock(OrderActor.class)
        .withName("order")
        .replyTo(CreateOrder.class, new OrderCreated("order-1", 100.0))
        .build();
    
    MockSpringActorRef<InventoryActor.Command> inventoryMock = mockSystem
        .mock(InventoryActor.class)
        .withName("inventory")
        .replyTo(ReserveInventory.class, new InventoryReserved("item-1"))
        .build();
    
    // Use in workflow
    WorkflowService service = new WorkflowService(
        orderMock.getRef(), 
        inventoryMock.getRef()
    );
    
    service.processOrder("order-1");
    
    // Verify both actors were used
    orderMock.verifyReceived(CreateOrder.class);
    inventoryMock.verifyReceived(ReserveInventory.class);
}
```

## Testing

Create comprehensive tests in: `core/src/test/java/io/github/seonwkim/core/test/MockSpringActorSystemTest.java`

Test:
1. Mock actor creation with name
2. Reply configuration for commands
3. Dynamic reply logic
4. Verification of tell() calls
5. Verification of ask() calls
6. Multiple mock actors
7. Mock reset functionality
8. Integration with services

## Dependencies

Uses existing Mockito dependency (already added in Task 2.1).

## Success Criteria

- [ ] MockSpringActorSystem implemented
- [ ] MockActorBuilder provides fluent API
- [ ] MockSpringActorRef supports verification
- [ ] Reply configuration works for ask() patterns
- [ ] Multiple mocks can coexist
- [ ] Dynamic reply logic supported
- [ ] Tests demonstrate all usage patterns

## Notes

- Use Mockito for all mocking
- Keep API simple and intuitive
- Support both tell() and ask() patterns
- Make verification easy and obvious
- Allow for stateful reply logic
- Don't reimplement actor system - just mock the refs
