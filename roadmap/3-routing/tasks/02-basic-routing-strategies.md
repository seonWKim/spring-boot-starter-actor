# Task 1.2: Basic Routing Strategies

**Priority:** HIGH  
**Estimated Effort:** 3-4 days  
**Prerequisites:** Task 1.1 (Router Base Infrastructure)

## Objective

Implement Round Robin and Random routing strategies by wrapping Pekko's built-in router implementations.

## Requirements

### 1. Round Robin Strategy

Messages are distributed evenly to workers in a circular fashion.

```java
package io.github.seonwkim.core.router.strategy;

/**
 * Round Robin routing strategy distributes messages evenly across all workers
 * in a circular fashion. This is the default and most commonly used strategy.
 *
 * Best for: Equal distribution, predictable load balancing
 */
public final class RoundRobinRoutingStrategy implements RoutingStrategy {
    
    @Override
    public String getName() {
        return "RoundRobin";
    }
    
    @Override
    public org.apache.pekko.routing.RouterConfig toPekkoRouter(int poolSize) {
        return new org.apache.pekko.routing.RoundRobinPool(poolSize);
    }
}
```

### 2. Random Strategy

Messages are distributed randomly to workers.

```java
package io.github.seonwkim.core.router.strategy;

/**
 * Random routing strategy distributes messages randomly across all workers.
 * No state tracking required, making it very lightweight.
 *
 * Best for: Simple distribution without state tracking, non-critical workloads
 */
public final class RandomRoutingStrategy implements RoutingStrategy {
    
    @Override
    public String getName() {
        return "Random";
    }
    
    @Override
    public org.apache.pekko.routing.RouterConfig toPekkoRouter(int poolSize) {
        return new org.apache.pekko.routing.RandomPool(poolSize);
    }
}
```

### 3. Update RoutingStrategy Factory Methods

```java
public interface RoutingStrategy {
    // ... existing methods ...
    
    /**
     * Round Robin routing - distributes messages evenly in circular fashion.
     */
    static RoutingStrategy roundRobin() {
        return new RoundRobinRoutingStrategy();
    }
    
    /**
     * Random routing - distributes messages randomly.
     */
    static RoutingStrategy random() {
        return new RandomRoutingStrategy();
    }
}
```

## Implementation Steps

1. **Create strategy implementations:**
   - `RoundRobinRoutingStrategy`
   - `RandomRoutingStrategy`

2. **Add factory methods to `RoutingStrategy` interface**

3. **Create comprehensive tests for each strategy**

## Testing Requirements

### Unit Tests

Test each strategy in isolation:

```java
@Test
void roundRobinDistributesEvenly() {
    // Verify Round Robin creates correct Pekko router config
    RoutingStrategy strategy = RoutingStrategy.roundRobin();
    RouterConfig config = strategy.toPekkoRouter(5);
    
    assertThat(config).isInstanceOf(RoundRobinPool.class);
    assertThat(((RoundRobinPool) config).nrOfInstances()).isEqualTo(5);
}
```

### Integration Tests

Test distribution pattern with actual routers:

```java
@SpringBootTest
@ActorTest
class RoundRobinRoutingTest {
    
    @Autowired
    private SpringActorSystem actorSystem;
    
    @Test
    void testRoundRobinDistribution() {
        // Spawn router with Round Robin strategy
        var router = actorSystem.actor(TestRouter.class)
            .withId("round-robin-router")
            .spawn()
            .toCompletableFuture().join();
        
        // Send 9 messages
        for (int i = 0; i < 9; i++) {
            router.tell(new ProcessTask("task-" + i));
        }
        
        // Verify each of 3 workers received 3 messages
        await().atMost(5, SECONDS).untilAsserted(() -> {
            List<Integer> messageCounts = getWorkerMessageCounts(router);
            assertThat(messageCounts).containsExactly(3, 3, 3);
        });
    }
}

@Component
class TestRouter implements SpringRouterActor<TestRouter.Command> {
    
    @Override
    public SpringRouterBehavior<Command> create(SpringActorContext ctx) {
        return SpringRouterBehavior.<Command>builder()
            .withRoutingStrategy(RoutingStrategy.roundRobin())
            .withPoolSize(3)
            .withWorkerClass(TestWorker.class)
            .build();
    }
    
    interface Command {}
    record ProcessTask(String taskId) implements Command {}
}

@Component
class TestWorker implements SpringActor<TestRouter.Command> {
    
    private final MessageTracker tracker;
    
    public TestWorker(MessageTracker tracker) {
        this.tracker = tracker;
    }
    
    @Override
    public SpringActorBehavior<TestRouter.Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(TestRouter.Command.class, ctx)
            .onMessage(TestRouter.ProcessTask.class, (context, msg) -> {
                tracker.recordMessage(context.getSelf(), msg);
                return Behaviors.same();
            })
            .build();
    }
}
```

### Test Distribution Patterns

1. **Round Robin tests:**
   - Even distribution with equal number of messages
   - Distribution with uneven number of messages
   - Order verification (message 1 → worker 1, message 2 → worker 2, etc.)

2. **Random tests:**
   - All workers receive messages (eventually)
   - Distribution is not predictable
   - No worker is starved over large sample size

## Success Criteria

- ✅ Round Robin strategy implemented
- ✅ Random strategy implemented
- ✅ Unit tests verify correct Pekko router creation
- ✅ Integration tests verify message distribution patterns
- ✅ Round Robin distributes evenly across workers
- ✅ Random distributes unpredictably but fairly
- ✅ All tests pass

## Files to Create

- `core/src/main/java/io/github/seonwkim/core/router/strategy/RoundRobinRoutingStrategy.java`
- `core/src/main/java/io/github/seonwkim/core/router/strategy/RandomRoutingStrategy.java`
- `core/src/test/java/io/github/seonwkim/core/router/strategy/RoundRobinRoutingStrategyTest.java`
- `core/src/test/java/io/github/seonwkim/core/router/strategy/RandomRoutingStrategyTest.java`
- `core/src/test/java/io/github/seonwkim/core/router/RoundRobinIntegrationTest.java`
- `core/src/test/java/io/github/seonwkim/core/router/RandomIntegrationTest.java`

## Dependencies

- Task 1.1: Router Base Infrastructure
- Pekko: `org.apache.pekko.routing.RoundRobinPool`
- Pekko: `org.apache.pekko.routing.RandomPool`

## Notes

- These are the simplest and most commonly used routing strategies
- Both strategies are stateless and very efficient
- Round Robin is generally preferred for predictable workloads
- Random is useful when you want to avoid any ordering effects
