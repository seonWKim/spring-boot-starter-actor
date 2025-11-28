# Task 1.3: Test Probe Support

**Priority:** HIGH  
**Estimated Effort:** 2-3 days  
**Phase:** 1 - Spring Boot TestKit Wrapper

---

## Overview

Implement `ActorTestProbe` wrapper around Pekko TestProbe for testing asynchronous actor responses and message expectations.

**CRITICAL: Wrap Pekko TestProbe, DON'T reimplement**

## Goals

1. Wrap Pekko TestProbe with Spring-friendly API
2. Provide convenient message expectation helpers
3. Support common test patterns (expect message, expect no message, etc.)
4. Enable testing of actor-to-actor communication

## Implementation Details

### 1. ActorTestProbe

Create: `core/src/test/java/io/github/seonwkim/core/test/ActorTestProbe.java`

```java
public class ActorTestProbe<T> {
    
    private final TestProbe<T> pekkoProbe;
    private final Duration defaultTimeout;
    
    public ActorTestProbe(TestProbe<T> pekkoProbe) {
        this(pekkoProbe, Duration.ofSeconds(3));
    }
    
    public ActorTestProbe(TestProbe<T> pekkoProbe, Duration defaultTimeout) {
        this.pekkoProbe = pekkoProbe;
        this.defaultTimeout = defaultTimeout;
    }
    
    // Direct delegation to Pekko TestProbe
    public ActorRef<T> ref() {
        return pekkoProbe.ref();
    }
    
    // Message expectations
    public T expectMessage() {
        return pekkoProbe.expectMessage(defaultTimeout);
    }
    
    public T expectMessage(Duration timeout) {
        return pekkoProbe.expectMessage(timeout);
    }
    
    public <M extends T> M expectMessageClass(Class<M> messageClass) {
        return pekkoProbe.expectMessageClass(messageClass, defaultTimeout);
    }
    
    public <M extends T> M expectMessageClass(Class<M> messageClass, Duration timeout) {
        return pekkoProbe.expectMessageClass(messageClass, timeout);
    }
    
    public void expectNoMessage() {
        pekkoProbe.expectNoMessage(Duration.ofMillis(100));
    }
    
    public void expectNoMessage(Duration duration) {
        pekkoProbe.expectNoMessage(duration);
    }
    
    // Enhanced assertion methods
    public ActorTestProbe<T> expectMessageMatching(Predicate<T> predicate) {
        T message = expectMessage();
        if (!predicate.test(message)) {
            throw new AssertionError("Message did not match predicate: " + message);
        }
        return this;
    }
    
    public <M extends T> ActorTestProbe<T> expectMessageThat(
            Class<M> messageClass, 
            Consumer<M> assertion) {
        M message = expectMessageClass(messageClass);
        assertion.accept(message);
        return this;
    }
    
    // Access underlying Pekko probe if needed
    public TestProbe<T> getPekkoProbe() {
        return pekkoProbe;
    }
}
```

### 2. Integration with SpringActorTestKit

Update `SpringActorTestKit` to include probe creation:

```java
public class SpringActorTestKit implements AutoCloseable {
    
    // ... existing code ...
    
    public <T> ActorTestProbe<T> createProbe() {
        TestProbe<T> pekkoProbe = pekkoTestKit.createTestProbe();
        return new ActorTestProbe<>(pekkoProbe);
    }
    
    public <T> ActorTestProbe<T> createProbe(Class<T> messageClass) {
        TestProbe<T> pekkoProbe = pekkoTestKit.createTestProbe(messageClass);
        return new ActorTestProbe<>(pekkoProbe);
    }
    
    public <T> ActorTestProbe<T> createProbe(String name) {
        TestProbe<T> pekkoProbe = pekkoTestKit.createTestProbe(name);
        return new ActorTestProbe<>(pekkoProbe);
    }
}
```

## Usage Examples

### Example 1: Basic Probe Usage

```java
@Test
public void testAsyncResponse() {
    ActorTestProbe<Response> probe = testKit.createProbe();
    
    SpringActorRef<Command> actor = testKit.spawn(OrderActor.class, "async-test");
    actor.tell(new ProcessAsync(probe.ref()));
    
    Response response = probe.expectMessageClass(Response.class);
    assertEquals("SUCCESS", response.status());
}
```

### Example 2: Multiple Message Expectations

```java
@Test
public void testMultipleMessages() {
    ActorTestProbe<Event> probe = testKit.createProbe();
    
    SpringActorRef<Command> actor = testKit.spawn(EventActor.class, "events");
    actor.tell(new Subscribe(probe.ref()));
    actor.tell(new TriggerEvents());
    
    probe.expectMessageClass(EventStarted.class);
    probe.expectMessageClass(EventProcessing.class);
    probe.expectMessageClass(EventCompleted.class);
}
```

### Example 3: Message Assertions

```java
@Test
public void testMessageContent() {
    ActorTestProbe<OrderEvent> probe = testKit.createProbe();
    
    SpringActorRef<Command> actor = testKit.spawn(OrderActor.class, "order");
    actor.tell(new CreateOrder("order-1", 100.0, probe.ref()));
    
    probe.expectMessageThat(OrderCreated.class, created -> {
        assertEquals("order-1", created.orderId());
        assertEquals(100.0, created.amount());
        assertTrue(created.timestamp() > 0);
    });
}
```

### Example 4: Testing Actor-to-Actor Communication

```java
@Test
public void testActorCommunication() {
    ActorTestProbe<ManagerEvent> managerProbe = testKit.createProbe();
    ActorTestProbe<WorkerEvent> workerProbe = testKit.createProbe();
    
    SpringActorRef<ManagerCommand> manager = 
        testKit.spawn(ManagerActor.class, "manager");
    
    manager.tell(new RegisterWorker(workerProbe.ref()));
    manager.tell(new AssignTask("task-1", managerProbe.ref()));
    
    workerProbe.expectMessageClass(WorkAssigned.class);
    managerProbe.expectMessageClass(TaskCompleted.class);
}
```

## Testing

Create comprehensive tests in: `core/src/test/java/io/github/seonwkim/core/test/ActorTestProbeTest.java`

Test:
1. Probe creation and reference access
2. Message expectations with timeouts
3. No message expectations
4. Message class expectations
5. Custom message assertions
6. Multiple probes in single test
7. Probe with actor-to-actor communication

## Dependencies

No new dependencies. Uses:
- Pekko TestProbe (wrapping)
- JUnit 5 for tests

## Success Criteria

- [ ] ActorTestProbe wraps Pekko TestProbe
- [ ] Support for all common message expectations
- [ ] Convenient assertion methods
- [ ] Integration with SpringActorTestKit
- [ ] Multiple probes can be used simultaneously
- [ ] Tests demonstrate all usage patterns
- [ ] No reimplementation of Pekko TestProbe core

## Notes

- Always delegate to Pekko TestProbe
- Provide convenience methods that add value
- Don't hide Pekko TestProbe completely (allow access)
- Keep timeout handling consistent with fluent API
- Support both typed and generic message expectations
