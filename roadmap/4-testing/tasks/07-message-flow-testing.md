# Task 3.2: Message Flow Testing

**Priority:** MEDIUM  
**Estimated Effort:** 2-3 days  
**Phase:** 3 - Common Test Utilities

---

## Overview

Create `MessageFlowTester` utility for testing complete message flows and actor interactions in a structured, readable way.

## Goals

1. Provide DSL for testing multi-step message flows
2. Support verification of message sequences
3. Enable testing of actor-to-actor communication patterns
4. Make complex flow tests readable and maintainable

## Implementation Details

### 1. MessageFlowTester

Create: `core/src/test/java/io/github/seonwkim/core/test/MessageFlowTester.java`

```java
public class MessageFlowTester {
    
    private final SpringActorTestKit testKit;
    
    public MessageFlowTester(SpringActorTestKit testKit) {
        this.testKit = testKit;
    }
    
    /**
     * Start a flow test for a single actor.
     */
    public <T> SingleActorFlowBuilder<T> testFlow(SpringActorRef<T> actor) {
        return new SingleActorFlowBuilder<>(testKit, actor);
    }
    
    /**
     * Start a flow test for multiple actors.
     */
    public MultiActorFlowBuilder testFlow() {
        return new MultiActorFlowBuilder(testKit);
    }
}
```

### 2. SingleActorFlowBuilder

Create: `core/src/test/java/io/github/seonwkim/core/test/SingleActorFlowBuilder.java`

```java
public class SingleActorFlowBuilder<T> {
    
    private final SpringActorTestKit testKit;
    private final SpringActorRef<T> actor;
    private final List<FlowStep> steps = new ArrayList<>();
    
    public SingleActorFlowBuilder(SpringActorTestKit testKit, SpringActorRef<T> actor) {
        this.testKit = testKit;
        this.actor = actor;
    }
    
    /**
     * Send a message.
     */
    public SingleActorFlowBuilder<T> send(Object message) {
        steps.add(new SendStep(actor, message));
        return this;
    }
    
    /**
     * Expect a specific reply type.
     */
    public <R> SingleActorFlowBuilder<T> expectReply(Class<R> replyClass) {
        steps.add(new ExpectReplyStep<>(replyClass));
        return this;
    }
    
    /**
     * Expect state to match predicate.
     */
    public <S> SingleActorFlowBuilder<T> expectState(
            Class<S> stateClass,
            Predicate<S> predicate) {
        steps.add(new ExpectStateStep<>(actor, stateClass, predicate));
        return this;
    }
    
    /**
     * Wait for a duration.
     */
    public SingleActorFlowBuilder<T> waitFor(Duration duration) {
        steps.add(new WaitStep(duration));
        return this;
    }
    
    /**
     * Execute the flow.
     */
    public void verify() {
        for (FlowStep step : steps) {
            step.execute(testKit);
        }
    }
}
```

### 3. MultiActorFlowBuilder

Create: `core/src/test/java/io/github/seonwkim/core/test/MultiActorFlowBuilder.java`

```java
public class MultiActorFlowBuilder {
    
    private final SpringActorTestKit testKit;
    private final List<FlowStep> steps = new ArrayList<>();
    
    public MultiActorFlowBuilder(SpringActorTestKit testKit) {
        this.testKit = testKit;
    }
    
    /**
     * Send a message to a specific actor.
     */
    public <T> MultiActorFlowBuilder send(SpringActorRef<T> actor, Object message) {
        steps.add(new SendStep(actor, message));
        return this;
    }
    
    /**
     * Expect a message on a probe.
     */
    public <T> MultiActorFlowBuilder expect(
            ActorTestProbe<T> probe,
            Class<T> messageClass) {
        steps.add(new ExpectProbeMessageStep<>(probe, messageClass));
        return this;
    }
    
    /**
     * Wait for a duration.
     */
    public MultiActorFlowBuilder waitFor(Duration duration) {
        steps.add(new WaitStep(duration));
        return this;
    }
    
    /**
     * Execute the flow.
     */
    public void verify() {
        for (FlowStep step : steps) {
            step.execute(testKit);
        }
    }
}
```

### 4. Flow Steps

Create: `core/src/test/java/io/github/seonwkim/core/test/FlowStep.java`

```java
interface FlowStep {
    void execute(SpringActorTestKit testKit);
}

class SendStep implements FlowStep {
    private final SpringActorRef<?> actor;
    private final Object message;
    
    SendStep(SpringActorRef<?> actor, Object message) {
        this.actor = actor;
        this.message = message;
    }
    
    @Override
    public void execute(SpringActorTestKit testKit) {
        actor.tell(message);
    }
}

class ExpectReplyStep<R> implements FlowStep {
    private final Class<R> replyClass;
    
    ExpectReplyStep(Class<R> replyClass) {
        this.replyClass = replyClass;
    }
    
    @Override
    public void execute(SpringActorTestKit testKit) {
        // Create probe and expect message
        ActorTestProbe<R> probe = testKit.createProbe();
        probe.expectMessageClass(replyClass);
    }
}

class ExpectStateStep<S> implements FlowStep {
    private final SpringActorRef<?> actor;
    private final Class<S> stateClass;
    private final Predicate<S> predicate;
    
    ExpectStateStep(SpringActorRef<?> actor, Class<S> stateClass, Predicate<S> predicate) {
        this.actor = actor;
        this.stateClass = stateClass;
        this.predicate = predicate;
    }
    
    @Override
    public void execute(SpringActorTestKit testKit) {
        ActorStateVerifier verifier = new ActorStateVerifier();
        verifier.assertState(actor, stateClass, predicate);
    }
}

class WaitStep implements FlowStep {
    private final Duration duration;
    
    WaitStep(Duration duration) {
        this.duration = duration;
    }
    
    @Override
    public void execute(SpringActorTestKit testKit) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Wait interrupted", e);
        }
    }
}

class ExpectProbeMessageStep<T> implements FlowStep {
    private final ActorTestProbe<T> probe;
    private final Class<T> messageClass;
    
    ExpectProbeMessageStep(ActorTestProbe<T> probe, Class<T> messageClass) {
        this.probe = probe;
        this.messageClass = messageClass;
    }
    
    @Override
    public void execute(SpringActorTestKit testKit) {
        probe.expectMessageClass(messageClass);
    }
}
```

## Usage Examples

### Example 1: Simple Flow Test

```java
@Test
public void testOrderProcessingFlow() {
    SpringActorRef<OrderActor.Command> actor = 
        testKit.spawn(OrderActor.class, "order");
    
    MessageFlowTester flowTester = new MessageFlowTester(testKit);
    
    flowTester.testFlow(actor)
        .send(new CreateOrder("order-1", 100.0))
        .expectReply(OrderCreated.class)
        .send(new ConfirmOrder("order-1"))
        .expectReply(OrderConfirmed.class)
        .expectState(OrderState.class, state -> 
            state.getStatus().equals("CONFIRMED"))
        .verify();
}
```

### Example 2: Multi-Actor Flow

```java
@Test
public void testMultiActorFlow() {
    SpringActorRef<OrderActor.Command> orderActor = 
        testKit.spawn(OrderActor.class, "order");
    SpringActorRef<InventoryActor.Command> inventoryActor = 
        testKit.spawn(InventoryActor.class, "inventory");
    
    ActorTestProbe<Event> eventProbe = testKit.createProbe();
    
    MessageFlowTester flowTester = new MessageFlowTester(testKit);
    
    flowTester.testFlow()
        .send(orderActor, new CreateOrder("order-1", 100.0))
        .send(inventoryActor, new ReserveInventory("item-1", 5))
        .expect(eventProbe, InventoryReserved.class)
        .send(orderActor, new ConfirmOrder("order-1"))
        .expect(eventProbe, OrderConfirmed.class)
        .verify();
}
```

### Example 3: Flow with Waits

```java
@Test
public void testFlowWithTiming() {
    SpringActorRef<ProcessorActor.Command> actor = 
        testKit.spawn(ProcessorActor.class, "processor");
    
    MessageFlowTester flowTester = new MessageFlowTester(testKit);
    
    flowTester.testFlow(actor)
        .send(new StartProcessing("task-1"))
        .waitFor(Duration.ofSeconds(1))
        .expectState(ProcessorState.class, state -> 
            state.getStatus().equals("PROCESSING"))
        .waitFor(Duration.ofSeconds(2))
        .expectState(ProcessorState.class, state -> 
            state.getStatus().equals("COMPLETED"))
        .verify();
}
```

### Example 4: Complex Workflow

```java
@Test
public void testComplexWorkflow() {
    SpringActorRef<WorkflowActor.Command> workflow = 
        testKit.spawn(WorkflowActor.class, "workflow");
    
    MessageFlowTester flowTester = new MessageFlowTester(testKit);
    
    flowTester.testFlow(workflow)
        .send(new InitializeWorkflow("wf-1"))
        .expectReply(WorkflowInitialized.class)
        .send(new AddStep("step-1"))
        .expectReply(StepAdded.class)
        .send(new AddStep("step-2"))
        .expectReply(StepAdded.class)
        .send(new ExecuteWorkflow())
        .waitFor(Duration.ofSeconds(3))
        .expectState(WorkflowState.class, state -> 
            state.isCompleted() && state.getStepCount() == 2)
        .verify();
}
```

## Testing

Create comprehensive tests in: `core/src/test/java/io/github/seonwkim/core/test/MessageFlowTesterTest.java`

Test:
1. Single actor flow execution
2. Multi-actor flow coordination
3. Message sequence verification
4. State expectations in flows
5. Wait/timing in flows
6. Complex multi-step workflows
7. Error handling in flows

## Dependencies

No new dependencies required.

## Success Criteria

- [ ] MessageFlowTester implemented
- [ ] SingleActorFlowBuilder supports flow DSL
- [ ] MultiActorFlowBuilder supports multi-actor flows
- [ ] Flow steps execute in order
- [ ] State verification integrated
- [ ] Wait/timing support
- [ ] Tests demonstrate all usage patterns

## Notes

- Keep DSL simple and readable
- Support both single and multi-actor flows
- Make error messages clear when flows fail
- Consider adding more step types as needed
- Integrate with existing test utilities (probes, state verification)
- Make flows easy to debug when they fail
