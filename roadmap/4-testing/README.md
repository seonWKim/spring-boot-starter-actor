# 4. Testing Utilities

Extract common testing patterns from existing tests and provide Spring Boot-friendly test utilities for comprehensive actor testing.

> **✅ GOOD APPROACH - Minor Adjustment**
> **Recommendation:** Build thin wrapper over Pekko TestKit (don't reinvent)
> **Priority:** HIGH
> **Focus:** Spring Boot integration + Mock support

---

## Overview

Based on analysis of existing test patterns in the codebase, provide reusable test utilities that make actor testing intuitive for Spring Boot developers.

**KEY PRINCIPLE:** Don't reinvent Pekko TestKit - wrap it with Spring Boot-friendly API.

### Common Test Patterns Identified

1. **Actor Spawning and Lifecycle**: Tests frequently spawn actors, send messages, wait for responses
2. **Message Verification**: Tests verify correct messages sent/received
3. **State Verification**: Tests check actor internal state
4. **Supervision Testing**: Tests verify failure handling and supervision
5. **Timeout Testing**: Tests verify timeout behavior
6. **Concurrent Testing**: Tests verify behavior under concurrent load

---

## 4.1 Spring Boot ActorTestKit

**Priority:** HIGH  
**Complexity:** Medium

### Overview

Spring Boot-friendly testing utilities that integrate with JUnit 5, Spring Test, and familiar assertion libraries.

### Implementation

```java
// Enable actor testing
@SpringBootTest
@EnableActorTesting  // Custom annotation
public class OrderActorTest {
    
    @Autowired
    private SpringActorTestKit testKit;  // Auto-configured
    
    @Test
    public void testOrderCreation() {
        // Fluent test API
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
    
    @Test
    public void testOrderProcessingFlow() {
        // Test complete message flow
        testKit.forActor(OrderActor.class)
            .withId("order-flow")
            .spawn()
            .send(new CreateOrder("order-1", 100.0))
            .expectReply(OrderCreated.class, Duration.ofSeconds(5))
            .send(new ConfirmOrder("order-1"))
            .expectReply(OrderConfirmed.class)
            .send(new GetOrderStatus("order-1"))
            .expectReply(OrderStatus.class)
            .assertThat(status -> assertEquals("CONFIRMED", status.status()));
    }
}
```

### Common Test Patterns Extracted

```java
// Pattern 1: Test probe for async responses
@Test
public void testAsyncResponse() {
    ActorTestProbe<Response> probe = testKit.createProbe();
    
    SpringActorRef<Command> actor = testKit.spawn(OrderActor.class, "async-test");
    actor.tell(new ProcessAsync(probe.ref()));
    
    Response response = probe.expectMessage(Duration.ofSeconds(5));
    assertEquals("SUCCESS", response.status());
}

// Pattern 2: Test behavior changes
@Test
public void testBehaviorChange() {
    testKit.forActor(StatefulActor.class)
        .withId("stateful")
        .spawn()
        .send(new Initialize())
        .expectNoReply()
        .assertState(state -> assertEquals("INITIALIZED", state.getStatus()))
        .send(new Activate())
        .assertState(state -> assertEquals("ACTIVE", state.getStatus()));
}

// Pattern 3: Test supervision
@Test
public void testSupervisionRestart() {
    testKit.forActor(FailingActor.class)
        .withId("supervised")
        .withSupervision(SupervisorStrategy.restart())
        .spawn()
        .send(new CauseFailure())
        .expectNoReply()
        .waitFor(Duration.ofSeconds(1))  // Wait for restart
        .assertRestarted()
        .send(new GetState())
        .expectReply(State.class)
        .assertThat(state -> assertTrue(state.isReset()));
}

// Pattern 4: Test timeouts
@Test
public void testTimeout() {
    testKit.forActor(SlowActor.class)
        .withId("slow")
        .spawn()
        .send(new SlowOperation())
        .expectTimeout(Duration.ofSeconds(2));
}

// Pattern 5: Test concurrent operations
@Test
public void testConcurrentMessages() {
    SpringActorRef<Command> actor = testKit.spawn(ConcurrentActor.class, "concurrent");
    
    testKit.sendConcurrently(actor, 100, i -> new Process("item-" + i));
    
    testKit.awaitCompletion(Duration.ofSeconds(10));
    
    testKit.assertState(actor, state -> 
        assertEquals(100, state.getProcessedCount())
    );
}
```

---

## 4.2 Mock Actor Support

**Priority:** HIGH  
**Complexity:** Low

### Overview

Easy mocking of actors for unit testing services without spawning real actors.

### Implementation

```java
@SpringBootTest
public class OrderServiceTest {
    
    @MockActor  // Custom annotation
    private SpringActorRef<OrderActor.Command> orderActor;
    
    @Autowired
    private OrderService orderService;
    
    @Test
    public void testServiceUsesActor() {
        // Configure mock behavior
        when(orderActor.ask(any(CreateOrder.class)))
            .thenReturn(CompletableFuture.completedFuture(
                new OrderCreated("order-1", 100.0)
            ));
        
        // Test service
        OrderResult result = orderService.createOrder("order-1", 100.0);
        
        // Verify
        assertEquals("SUCCESS", result.status());
        verify(orderActor).tell(argThat(cmd -> 
            cmd instanceof CreateOrder create && 
            create.orderId().equals("order-1")
        ));
    }
}

// Alternative: Programmatic mocking
@Test
public void testWithMockActorSystem() {
    MockSpringActorSystem mockSystem = MockSpringActorSystem.create();
    
    MockSpringActorRef<Command> mockActor = mockSystem
        .mock(OrderActor.class)
        .replyTo(CreateOrder.class, cmd -> new OrderCreated(cmd.orderId(), cmd.amount()))
        .replyTo(GetOrder.class, cmd -> new OrderDetails(cmd.orderId()))
        .build();
    
    // Use in tests
    OrderService service = new OrderService(mockSystem);
    service.processOrder("order-1");
    
    // Verify interactions
    mockActor.verifyReceived(CreateOrder.class, times(1));
}
```

---

## 4.3 Test Utilities Extracted from Existing Tests

### Pattern: Actor State Verification

```java
public class ActorStateAssert {
    
    public static <S> void assertActorState(
            SpringActorRef<?> actor,
            Class<S> stateClass,
            Consumer<S> assertions) {
        
        actor.ask(new GetInternalState())
            .withTimeout(Duration.ofSeconds(5))
            .execute()
            .thenAccept(state -> assertions.accept(stateClass.cast(state)));
    }
}

// Usage in tests
@Test
public void testStateUpdate() {
    SpringActorRef<Command> actor = testKit.spawn(StatefulActor.class, "test");
    
    actor.tell(new UpdateValue(42));
    
    assertActorState(actor, ActorState.class, state -> {
        assertEquals(42, state.getValue());
        assertTrue(state.isModified());
    });
}
```

### Pattern: Message Flow Testing

```java
public class MessageFlowTester {
    
    public static MessageFlowBuilder testFlow(SpringActorRef<?> actor) {
        return new MessageFlowBuilder(actor);
    }
}

@Test
public void testCompleteFlow() {
    SpringActorRef<Command> actor = testKit.spawn(FlowActor.class, "flow");
    
    MessageFlowTester.testFlow(actor)
        .send(new Start())
        .expectState("STARTED")
        .send(new Process())
        .expectState("PROCESSING")
        .send(new Complete())
        .expectState("COMPLETED")
        .verify();
}
```

### Pattern: Performance Testing

```java
@Test
public void benchmarkActorThroughput() {
    ActorPerformanceTester tester = new ActorPerformanceTester(testKit);
    
    PerformanceResult result = tester
        .forActor(OrderActor.class)
        .withConcurrency(100)
        .withDuration(Duration.ofSeconds(30))
        .withMessageGenerator(() -> new CreateOrder(UUID.randomUUID().toString(), 100.0))
        .run();
    
    System.out.println("Throughput: " + result.getMessagesPerSecond());
    System.out.println("P50 Latency: " + result.getP50Latency());
    System.out.println("P95 Latency: " + result.getP95Latency());
    System.out.println("P99 Latency: " + result.getP99Latency());
    
    assertTrue(result.getMessagesPerSecond() > 1000);
    assertTrue(result.getP95Latency().toMillis() < 100);
}
```

---

## Summary

Extracted common patterns from existing tests and provided reusable utilities:

1. **Fluent Test API**: Chainable testing DSL
2. **Mock Actors**: Easy mocking for unit tests  
3. **State Verification**: Assert actor internal state
4. **Message Flow Testing**: Test complete workflows
5. **Performance Testing**: Benchmark actor throughput
6. **Spring Boot Integration**: Works with @SpringBootTest

All utilities follow Spring Boot conventions and integrate with existing test infrastructure.
