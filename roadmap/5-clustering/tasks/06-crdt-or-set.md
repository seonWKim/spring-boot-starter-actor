# Task 3.2: ORSet CRDT Actor Wrapper

**Priority:** MEDIUM  
**Estimated Effort:** 1 week  
**Status:** TODO

## Objective

Wrap Pekko's Observed-Removed Set (ORSet) CRDT in actor commands for distributed set operations.

## Background

ORSet provides a distributed set that handles concurrent add/remove operations correctly. It's useful for membership lists, tags, collections, etc.

## Actor Commands

```java
@Component
public class ORSetActor<E> implements SpringActorWithContext<ORSetActor.Command<E>, SpringActorContext> {
    
    public interface Command<E> extends JsonSerializable {}
    
    public static class Add<E> extends AskCommand<Done> implements Command<E> {
        public final E element;
    }
    
    public static class Remove<E> extends AskCommand<Done> implements Command<E> {
        public final E element;
    }
    
    public static class Contains<E> extends AskCommand<Boolean> implements Command<E> {
        public final E element;
    }
    
    public static class GetAll<E> extends AskCommand<Set<E>> implements Command<E> {}
    
    public static class Size<E> extends AskCommand<Integer> implements Command<E> {}
    
    public static class Clear<E> extends AskCommand<Done> implements Command<E> {}
}
```

## Service Wrapper

```java
@Service
public class DistributedSetService<E> {
    public CompletionStage<Done> add(E element);
    public CompletionStage<Done> remove(E element);
    public CompletionStage<Boolean> contains(E element);
    public CompletionStage<Set<E>> getAll();
    public CompletionStage<Integer> size();
}
```

## Deliverables

1. `core/src/main/java/io/github/seonwkim/core/crdt/ORSetActor.java`
2. `core/src/main/java/io/github/seonwkim/core/crdt/DistributedSetService.java`
3. Tests for all operations
4. Documentation: `docs/clustering/crdt-orset.md`

## Success Criteria

- ✅ Set operations work via actor commands
- ✅ Concurrent add/remove handled correctly
- ✅ Data replicates across cluster
- ✅ Tests validate all operations
