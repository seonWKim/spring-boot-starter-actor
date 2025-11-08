# Testing Utilities for Spring Boot Actor

This directory contains testing utilities for the spring-boot-starter-actor project.

## Overview

The testing utilities provide a Spring Boot-friendly wrapper around Pekko TestKit, making it easy to write tests for actor-based applications.

**KEY PRINCIPLE:** These utilities wrap Pekko TestKit - they do NOT reimplement core testing functionality.

## Features Implemented

### Phase 1: Basic Testing Infrastructure ✅

#### @EnableActorTesting Annotation

Enable actor testing in your Spring Boot tests with a simple annotation:

```java
@SpringBootTest
@EnableActorTesting
public class MyActorTest {
    
    @Autowired
    private SpringActorTestKit testKit;
    
    @Test
    public void testMyActor() {
        // Test code here
    }
}
```

#### SpringActorTestKit

A wrapper around Pekko's `ActorTestKit` that provides:

- **Actor Spawning**: Spawn actors for testing
- **Test Probes**: Create test probes to verify actor behavior
- **Spring Integration**: Works seamlessly with Spring Boot test context
- **Auto-Cleanup**: Automatically shuts down when the test context is destroyed

Example usage:

```java
@Test
public void testActorBehavior() {
    // Spawn an actor
    ActorRef<Command> actor = testKit.spawn(createBehavior(), "test-actor");
    
    // Create a test probe
    ActorTestProbe<String> probe = testKit.createProbe();
    
    // Send message and verify response
    actor.tell(new Ping(probe.ref(), "hello"));
    String response = probe.receiveMessage(Duration.ofSeconds(3));
    assertEquals("pong:hello", response);
}
```

#### ActorTestProbe

A wrapper around Pekko's `TestProbe` that provides:

- **Message Reception**: `receiveMessage()` to receive messages with timeout
- **Type-safe Expectations**: `expectMessageClass()` for type-safe message expectations
- **Assertion Helpers**: `expectMessageThat()` for inline assertions
- **No-Message Verification**: `expectNoMessage()` to verify no message was sent

Example usage:

```java
@Test
public void testProbeWithAssertions() {
    ActorTestProbe<OrderEvent> probe = testKit.createProbe();
    SpringActorRef<Command> actor = actorSystem.actor(OrderActor.class)
        .withId("order")
        .spawnAndWait();
    
    actor.tell(new CreateOrder("order-1", 100.0, probe.ref()));
    
    probe.expectMessageThat(OrderCreated.class, created -> {
        assertEquals("order-1", created.orderId());
        assertEquals(100.0, created.amount());
    });
}
```

## Configuration

The testing infrastructure is auto-configured when you use `@EnableActorTesting`. The configuration:

1. Creates a `SpringActorTestKit` bean
2. Manages its lifecycle (automatic shutdown)
3. Integrates with Spring's test context

## Best Practices

### 1. Use Specific Test Configuration

To avoid conflicts with multiple `@SpringBootConfiguration` classes in your test classpath:

```java
@SpringBootTest(classes = MyActorTest.TestConfig.class)
public class MyActorTest {
    
    @SpringBootConfiguration
    @EnableActorSupport
    @EnableActorTesting
    static class TestConfig {}
    
    // Test methods...
}
```

### 2. Delegate to Pekko TestKit When Needed

If you need Pekko TestKit functionality not wrapped by our utilities, access it directly:

```java
ActorTestKit pekkoTestKit = testKit.getPekkoTestKit();
// Use Pekko TestKit directly
```

### 3. Prefer Test Probes for Verification

Use test probes to verify actor-to-actor communication:

```java
@Test
public void testActorCommunication() {
    ActorTestProbe<Event> eventProbe = testKit.createProbe();
    
    ActorRef<Command> actor = testKit.spawn(behavior, "actor");
    actor.tell(new Subscribe(eventProbe.ref()));
    actor.tell(new TriggerEvent());
    
    eventProbe.expectMessageClass(EventTriggered.class);
}
```

## Architecture

```
@EnableActorTesting
    ↓
ActorTestConfiguration
    ↓
    ├─→ SpringActorTestKit (wraps Pekko ActorTestKit)
    │       ↓
    │       ├─→ spawn() - spawn actors
    │       └─→ createProbe() - create test probes
    │
    └─→ ActorTestKitLifecycle (manages cleanup)
```

## Future Enhancements

The following features are planned but not yet implemented:

### Phase 1 (Remaining)
- **Fluent Test API**: Chainable DSL for testing (`testKit.forActor().spawn().send().expectReply()`)
- **SpringActor Integration**: Better integration with SpringActor spawning patterns

### Phase 2
- **@MockActor Annotation**: Easy mocking of actors with Mockito
- **MockSpringActorSystem**: Programmatic mock creation and verification

### Phase 3
- **State Verification**: Utilities for asserting actor internal state
- **Message Flow Testing**: DSL for testing multi-step message flows

### Phase 4
- **Performance Testing**: Utilities for throughput and latency measurement (deferred)

## Contributing

When adding new testing utilities:

1. **Wrap, don't reimplement**: Always wrap Pekko TestKit functionality
2. **Follow Spring conventions**: Use Spring Boot patterns and annotations
3. **Add tests**: Ensure new utilities have comprehensive tests
4. **Document**: Update this README with examples

## See Also

- [Task Specifications](../../roadmap/4-testing/tasks/) - Detailed specifications for planned features
- [Pekko TestKit Documentation](https://pekko.apache.org/docs/pekko/current/typed/testing.html) - Underlying Pekko testing framework
