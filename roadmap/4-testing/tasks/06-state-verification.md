# Task 3.1: State Verification Helpers

**Priority:** MEDIUM  
**Estimated Effort:** 2-3 days  
**Phase:** 3 - Common Test Utilities

---

## Overview

Create helper utilities for verifying actor internal state in tests, making state assertions convenient and readable.

## Goals

1. Provide utilities for actor state verification
2. Support both synchronous and asynchronous state checks
3. Integrate with standard assertion libraries
4. Handle common state verification patterns

## Implementation Details

### 1. ActorStateVerifier

Create: `core/src/test/java/io/github/seonwkim/core/test/ActorStateVerifier.java`

```java
public class ActorStateVerifier {
    
    private final Duration defaultTimeout;
    
    public ActorStateVerifier() {
        this(Duration.ofSeconds(3));
    }
    
    public ActorStateVerifier(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }
    
    /**
     * Assert actor state matches the given predicate.
     */
    public <S> void assertState(
            SpringActorRef<?> actor,
            Class<S> stateClass,
            Predicate<S> predicate) {
        
        assertState(actor, stateClass, predicate, defaultTimeout);
    }
    
    /**
     * Assert actor state matches the given predicate with timeout.
     */
    public <S> void assertState(
            SpringActorRef<?> actor,
            Class<S> stateClass,
            Predicate<S> predicate,
            Duration timeout) {
        
        S state = getState(actor, stateClass, timeout);
        if (!predicate.test(state)) {
            throw new AssertionError("Actor state does not match predicate: " + state);
        }
    }
    
    /**
     * Assert actor state with custom assertions.
     */
    public <S> void assertStateThat(
            SpringActorRef<?> actor,
            Class<S> stateClass,
            Consumer<S> assertions) {
        
        S state = getState(actor, stateClass, defaultTimeout);
        assertions.accept(state);
    }
    
    /**
     * Get actor state.
     */
    private <S> S getState(
            SpringActorRef<?> actor,
            Class<S> stateClass,
            Duration timeout) {
        
        // This assumes actors support a GetState command
        // The implementation should use a standard pattern
        // or framework command to retrieve state
        
        try {
            return actor.ask(new FrameworkCommands.GetState())
                .withTimeout(timeout)
                .execute()
                .thenApply(stateClass::cast)
                .toCompletableFuture()
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get actor state", e);
        }
    }
}
```

### 2. Integration with SpawnedActorContext

Update `SpawnedActorContext` to include state verification:

```java
public class SpawnedActorContext<T> {
    
    // ... existing code ...
    
    private final ActorStateVerifier stateVerifier;
    
    public SpawnedActorContext(
            SpringActorTestKit testKit,
            ActorRef<T> actorRef,
            Duration timeout) {
        // ... existing initialization ...
        this.stateVerifier = new ActorStateVerifier(timeout);
    }
    
    /**
     * Assert actor state.
     */
    public <S> SpawnedActorContext<T> assertState(
            Class<S> stateClass,
            Predicate<S> predicate) {
        
        SpringActorRef<T> springRef = wrapAsSpringRef(actorRef);
        stateVerifier.assertState(springRef, stateClass, predicate);
        return this;
    }
    
    /**
     * Assert actor state with custom assertions.
     */
    public <S> SpawnedActorContext<T> assertStateThat(
            Class<S> stateClass,
            Consumer<S> assertions) {
        
        SpringActorRef<T> springRef = wrapAsSpringRef(actorRef);
        stateVerifier.assertStateThat(springRef, stateClass, assertions);
        return this;
    }
}
```

### 3. Awaitility Integration

Create: `core/src/test/java/io/github/seonwkim/core/test/ActorStateAwaiter.java`

```java
public class ActorStateAwaiter {
    
    /**
     * Wait until actor state matches the given predicate.
     */
    public static <S> void awaitState(
            SpringActorRef<?> actor,
            Class<S> stateClass,
            Predicate<S> predicate) {
        
        awaitState(actor, stateClass, predicate, Duration.ofSeconds(10));
    }
    
    /**
     * Wait until actor state matches the given predicate with timeout.
     */
    public static <S> void awaitState(
            SpringActorRef<?> actor,
            Class<S> stateClass,
            Predicate<S> predicate,
            Duration timeout) {
        
        await()
            .atMost(timeout)
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> {
                ActorStateVerifier verifier = new ActorStateVerifier();
                verifier.assertState(actor, stateClass, predicate);
            });
    }
    
    /**
     * Wait until actor state satisfies the given assertions.
     */
    public static <S> void awaitStateThat(
            SpringActorRef<?> actor,
            Class<S> stateClass,
            Consumer<S> assertions,
            Duration timeout) {
        
        await()
            .atMost(timeout)
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> {
                ActorStateVerifier verifier = new ActorStateVerifier();
                verifier.assertStateThat(actor, stateClass, assertions);
            });
    }
}
```

## Usage Examples

### Example 1: Simple State Assertion

```java
@Test
public void testStateUpdate() {
    testKit.forActor(CounterActor.class)
        .withId("counter")
        .spawn()
        .send(new Increment())
        .assertStateThat(CounterState.class, state -> {
            assertEquals(1, state.getValue());
        });
}
```

### Example 2: State After Multiple Operations

```java
@Test
public void testMultipleOperations() {
    testKit.forActor(OrderActor.class)
        .withId("order")
        .spawn()
        .send(new CreateOrder("order-1", 100.0))
        .expectReply(OrderCreated.class)
        .send(new ConfirmOrder("order-1"))
        .assertStateThat(OrderState.class, state -> {
            assertEquals("CONFIRMED", state.getStatus());
            assertEquals(100.0, state.getAmount());
            assertTrue(state.isConfirmed());
        });
}
```

### Example 3: Await State Change

```java
@Test
public void testAsyncStateChange() {
    SpringActorRef<ProcessorActor.Command> actor = 
        testKit.spawn(ProcessorActor.class, "processor");
    
    actor.tell(new StartProcessing("task-1"));
    
    // Wait for state to change
    ActorStateAwaiter.awaitState(
        actor,
        ProcessorState.class,
        state -> state.getStatus().equals("COMPLETED"),
        Duration.ofSeconds(5)
    );
}
```

### Example 4: State Predicate

```java
@Test
public void testStateCondition() {
    SpringActorRef<InventoryActor.Command> actor = 
        testKit.spawn(InventoryActor.class, "inventory");
    
    actor.tell(new AddItem("item-1", 10));
    
    ActorStateVerifier verifier = new ActorStateVerifier();
    verifier.assertState(
        actor,
        InventoryState.class,
        state -> state.getQuantity("item-1") == 10
    );
}
```

## Testing

Create comprehensive tests in: `core/src/test/java/io/github/seonwkim/core/test/ActorStateVerifierTest.java`

Test:
1. State verification with assertions
2. State verification with predicates
3. Timeout handling
4. Integration with fluent API
5. Awaitility integration for async state changes
6. Error handling for invalid state access

## Dependencies

Awaitility is already included in test dependencies.

## Success Criteria

- [ ] ActorStateVerifier implemented
- [ ] Integration with SpawnedActorContext
- [ ] Awaitility integration for async state
- [ ] Support for predicates and assertions
- [ ] Proper timeout handling
- [ ] Tests demonstrate all usage patterns

## Notes

- Assume actors expose state via a standard command pattern
- Use existing FrameworkCommands if available
- Integrate with Awaitility for async state verification
- Keep API simple and intuitive
- Support both synchronous and asynchronous verification
- Provide clear error messages for assertion failures
