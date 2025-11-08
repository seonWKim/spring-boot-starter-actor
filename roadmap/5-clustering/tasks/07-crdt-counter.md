# Task 3.3: Counter CRDT Actor Wrapper

**Priority:** MEDIUM  
**Estimated Effort:** 1 week  
**Status:** TODO

## Objective

Wrap Pekko's Counter CRDTs (PNCounter, GCounter) in actor commands for distributed counting.

## Background

Counters are useful for metrics, votes, likes, etc. PNCounter supports increment and decrement, GCounter only increment.

## Actor Commands

```java
@Component
public class PNCounterActor implements SpringActorWithContext<PNCounterActor.Command, SpringActorContext> {
    
    public interface Command extends JsonSerializable {}
    
    public static class Increment extends AskCommand<Done> implements Command {
        public final long delta;
    }
    
    public static class Decrement extends AskCommand<Done> implements Command {
        public final long delta;
    }
    
    public static class GetValue extends AskCommand<Long> implements Command {}
}
```

## Service Wrapper

```java
@Service
public class DistributedCounterService {
    public CompletionStage<Done> increment(long delta);
    public CompletionStage<Done> decrement(long delta);
    public CompletionStage<Long> getValue();
}
```

## Deliverables

1. `core/src/main/java/io/github/seonwkim/core/crdt/PNCounterActor.java`
2. `core/src/main/java/io/github/seonwkim/core/crdt/DistributedCounterService.java`
3. Tests for counter operations
4. Documentation: `docs/clustering/crdt-counter.md`

## Success Criteria

- ✅ Counter operations work via actor commands
- ✅ Concurrent increments/decrements converge correctly
- ✅ Tests validate all operations
