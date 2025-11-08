# Task 1.2: Fluent Test API

**Priority:** HIGH  
**Estimated Effort:** 1 week  
**Phase:** 1 - Spring Boot TestKit Wrapper

---

## Overview

Build a fluent test API that wraps Pekko TestKit functionality, providing a chainable, Spring Boot-friendly DSL for actor testing.

**CRITICAL: Wrap Pekko TestKit, DON'T reimplement core functionality**

## Goals

1. Provide fluent, chainable API for common test patterns
2. Support actor spawning, message sending, and response expectations
3. Integrate with standard assertions (JUnit, AssertJ)
4. Maintain access to underlying Pekko TestKit when needed

## Implementation Details

### 1. ActorTestBuilder

Create: `core/src/test/java/io/github/seonwkim/core/test/ActorTestBuilder.java`

```java
public class ActorTestBuilder<T> {
    
    private final SpringActorTestKit testKit;
    private final Class<? extends SpringActor<T>> actorClass;
    private String actorId;
    private Duration timeout = Duration.ofSeconds(3);
    
    public ActorTestBuilder(SpringActorTestKit testKit, Class<? extends SpringActor<T>> actorClass) {
        this.testKit = testKit;
        this.actorClass = actorClass;
    }
    
    public ActorTestBuilder<T> withId(String id) {
        this.actorId = id;
        return this;
    }
    
    public ActorTestBuilder<T> withTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }
    
    public SpawnedActorContext<T> spawn() {
        // Spawn using SpringActor mechanism
        ActorRef<T> actorRef = spawnActor();
        return new SpawnedActorContext<>(testKit, actorRef, timeout);
    }
    
    private ActorRef<T> spawnActor() {
        // Use existing SpringActor spawning logic
        // Wrap with Pekko TestKit spawn
    }
}
```

### 2. SpawnedActorContext

Create: `core/src/test/java/io/github/seonwkim/core/test/SpawnedActorContext.java`

```java
public class SpawnedActorContext<T> {
    
    private final SpringActorTestKit testKit;
    private final ActorRef<T> actorRef;
    private final Duration defaultTimeout;
    private final TestProbe<Object> probe;
    
    public SpawnedActorContext(SpringActorTestKit testKit, ActorRef<T> actorRef, Duration timeout) {
        this.testKit = testKit;
        this.actorRef = actorRef;
        this.defaultTimeout = timeout;
        // Use Pekko TestProbe
        this.probe = testKit.getPekkoTestKit().createTestProbe();
    }
    
    public SpawnedActorContext<T> send(Object message) {
        actorRef.tell((T) message);
        return this;
    }
    
    public <R> ReplyContext<T, R> expectReply(Class<R> replyClass) {
        return expectReply(replyClass, defaultTimeout);
    }
    
    public <R> ReplyContext<T, R> expectReply(Class<R> replyClass, Duration timeout) {
        // Use Pekko TestProbe expectMessageClass
        R reply = probe.expectMessageClass(replyClass, timeout);
        return new ReplyContext<>(this, reply);
    }
    
    public SpawnedActorContext<T> expectNoReply() {
        return expectNoReply(Duration.ofMillis(100));
    }
    
    public SpawnedActorContext<T> expectNoReply(Duration duration) {
        probe.expectNoMessage(duration);
        return this;
    }
    
    public ActorRef<T> getActorRef() {
        return actorRef;
    }
}
```

### 3. ReplyContext

Create: `core/src/test/java/io/github/seonwkim/core/test/ReplyContext.java`

```java
public class ReplyContext<T, R> {
    
    private final SpawnedActorContext<T> actorContext;
    private final R reply;
    
    public ReplyContext(SpawnedActorContext<T> actorContext, R reply) {
        this.actorContext = actorContext;
        this.reply = reply;
    }
    
    public ReplyContext<T, R> assertThat(Consumer<R> assertion) {
        assertion.accept(reply);
        return this;
    }
    
    public SpawnedActorContext<T> send(Object message) {
        return actorContext.send(message);
    }
    
    public R getReply() {
        return reply;
    }
}
```

## Usage Examples

### Example 1: Simple Request-Reply

```java
@Test
public void testSimpleRequestReply() {
    testKit.forActor(OrderActor.class)
        .withId("test-order")
        .spawn()
        .send(new CreateOrder("order-1", 100.0))
        .expectReply(OrderCreated.class)
        .assertThat(created -> {
            assertEquals("order-1", created.orderId());
            assertEquals(100.0, created.amount());
        });
}
```

### Example 2: Multi-step Flow

```java
@Test
public void testOrderFlow() {
    testKit.forActor(OrderActor.class)
        .withId("order-flow")
        .spawn()
        .send(new CreateOrder("order-1", 100.0))
        .expectReply(OrderCreated.class)
        .send(new ConfirmOrder("order-1"))
        .expectReply(OrderConfirmed.class)
        .send(new GetOrderStatus("order-1"))
        .expectReply(OrderStatus.class)
        .assertThat(status -> assertEquals("CONFIRMED", status.status()));
}
```

### Example 3: No Reply Expected

```java
@Test
public void testFireAndForget() {
    testKit.forActor(LoggerActor.class)
        .withId("logger")
        .spawn()
        .send(new LogMessage("test"))
        .expectNoReply();
}
```

## Testing

Create comprehensive tests in: `core/src/test/java/io/github/seonwkim/core/test/FluentTestApiTest.java`

Test:
1. Actor spawning with and without ID
2. Sending messages and expecting replies
3. Multi-step message flows
4. Timeout handling
5. Assertion chaining
6. No-reply scenarios

## Dependencies

No new dependencies. Uses:
- Pekko ActorTestKit (wrapping, not reimplementing)
- JUnit 5 for tests
- Standard Java assertions

## Success Criteria

- [ ] ActorTestBuilder implemented with fluent API
- [ ] SpawnedActorContext supports message sending and reply expectations
- [ ] ReplyContext allows assertions on replies
- [ ] Chainable API for multi-step flows
- [ ] Proper timeout handling
- [ ] Works with existing SpringActor classes
- [ ] Tests demonstrate all usage patterns
- [ ] No reimplementation of Pekko TestKit core

## Notes

- Always wrap Pekko TestKit functionality
- Provide escape hatch to underlying Pekko TestKit when needed
- Keep API simple and intuitive
- Follow builder pattern conventions
- Support method chaining for readability
