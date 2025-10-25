# Developer Experience Enhancements for spring-boot-starter-actor

## Overview

This document outlines concrete developer experience (DX) improvements identified through comprehensive analysis of the actor and entity creation patterns in the codebase. These enhancements aim to reduce boilerplate, improve type safety, and align with Spring Boot conventions.

---

** Annotation-based Configuration (Future)**
```java
@ShardedEntity(
    name = "UserActor",  // Defaults to class simple name
    shards = 100         // Defaults to 100
)
@Component
public class UserActor implements ShardedActor<Command> {
    // TYPE_KEY auto-generated via annotation processor or registry
    // extractor() uses annotation parameters

    @Override
    public Behavior<Command> create(EntityContext<Command> ctx) {
        // Only implement business logic
    }
}
```
---

## Priority 2: API Ergonomics

### 2.1 Blocking Actor Spawns in Services

**Status:** HIGH PRIORITY - Common pattern blocks application startup

**Current Pattern (Found in examples):**
```java
@Service
public class HelloService {
    private final SpringActorRef<Command> helloActor;

    public HelloService(SpringActorSystem springActorSystem) {
        // BLOCKS constructor! Bad for Spring Boot startup
        this.helloActor = springActorSystem
            .spawn(HelloActor.class)
            .withId("default")
            .startAndWait();  // <-- Blocking call in constructor
    }
}
```

**Issues:**
- Blocks Spring application startup
- Makes startup time unpredictable
- Violates Spring best practices
- Cannot use `@Lazy` effectively

**Proposed Solutions:**

**Solution A: Lazy Reference Pattern**
```java
@Service
public class HelloService {
    private final SpringActorSystem springActorSystem;
    private SpringActorRef<Command> helloActor;

    public HelloService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    private SpringActorRef<Command> getActor() {
        if (helloActor == null) {
            helloActor = springActorSystem
                .spawn(HelloActor.class)
                .withId("default")
                .startAndWait();
        }
        return helloActor;
    }

    public void greet(String message) {
        getActor().tell(new HelloActor.SayHello(message));
    }
}
```

**Solution B: SpringActorProvider (Recommended)**
```java
// New class to add
@Component
public class SpringActorProvider implements ApplicationContextAware {
    private final SpringActorSystem actorSystem;
    private final Map<ActorKey, SpringActorRef<?>> singletons = new ConcurrentHashMap<>();

    // Auto-spawn singleton actors during startup
    @EventListener(ApplicationReadyEvent.class)
    public void initializeSingletonActors() {
        // Find all @Actor(singleton=true) and spawn them
    }

    public <T> SpringActorRef<T> get(Class<? extends SpringActor<?, T>> actorClass) {
        // Return pre-spawned actor or spawn on-demand
    }
}

// Usage
@Service
public class HelloService {
    private final SpringActorRef<Command> helloActor;

    public HelloService(SpringActorProvider provider) {
        // Non-blocking: actor already spawned or spawned async
        this.helloActor = provider.get(HelloActor.class);
    }
}
```

**Solution C: CompletionStage-based Pattern**
```java
@Service
public class HelloService {
    private final CompletionStage<SpringActorRef<Command>> helloActorFuture;

    public HelloService(SpringActorSystem springActorSystem) {
        // Non-blocking: returns immediately
        this.helloActorFuture = springActorSystem
            .spawn(HelloActor.class)
            .withId("default")
            .start();  // Async, not startAndWait()
    }

    public CompletionStage<Void> greet(String message) {
        return helloActorFuture.thenAccept(actor ->
            actor.tell(new HelloActor.SayHello(message))
        );
    }
}
```

**Recommendation:** Solution C (document pattern) + Solution B (future enhancement)

---

### 2.2 Command Boilerplate (Jackson Annotations)

**Status:** MEDIUM PRIORITY - Verbose but standard pattern

**Current Pattern:**
```java
public interface Command extends JsonSerializable {}

public static class UpdateProfile implements Command {
    public final String name;

    @JsonCreator
    public UpdateProfile(@JsonProperty("name") String name) {
        this.name = name;
    }
}
```

**Modern Alternative (Java 17+ Records):**
```java
public sealed interface Command extends JsonSerializable {
    record UpdateProfile(String name) implements Command {}
    record DeleteProfile() implements Command {}
    record GetProfile(ActorRef<ProfileData> replyTo) implements Command {}
}
```

**Benefits:**
- No `@JsonCreator` needed (Jackson auto-detects record constructors in recent versions)
- Immutable by default
- Compact syntax
- Pattern matching support

**Action Items:**
1. Update examples to use records where appropriate
2. Document record-based pattern in README
3. Note Java 17+ requirement for records

---

### 3.2 Actor Discovery API

**Status:** LOW PRIORITY - Feature gap for monitoring/debugging

**Current State:**
- No way to check if actor exists
- No way to get reference to existing actor
- No way to list running actors

**Proposed Addition:**
```java
// Add to SpringActorSystem
public <A extends SpringActor<A, C>, C> Optional<SpringActorRef<C>>
    getActor(Class<A> actorClass, String actorId) {
    // Query RootGuardian for existing actor
    // Return Optional.empty() if not found
}

public <A extends SpringActor<A, C>, C> Set<String>
    listActorIds(Class<A> actorClass) {
    // Return set of all actor IDs for given class
}

// Usage
Optional<SpringActorRef<Command>> existing =
    actorSystem.getActor(HelloActor.class, "actor-123");

if (existing.isPresent()) {
    existing.get().tell(new SayHello("message"));
} else {
    // Spawn new actor
}
```

**Use Cases:**
- Monitoring dashboards
- Debugging tools
- Conditional actor spawning
- Health checks

---

### 3.3 Fluent Ask Pattern Enhancements

**Status:** LOW PRIORITY - Nice-to-have syntax sugar

**Current Pattern:**
```java
CompletionStage<String> result = actor.ask(
    replyTo -> new GetValue(replyTo),
    Duration.ofSeconds(3)
);
```

**Proposed Enhancements:**

**Option A: Simplified Ask (no timeout)**
```java
// Add to SpringActorRef
public <REQ extends T, RES> CompletionStage<RES> query(
    Function<ActorRef<RES>, REQ> messageFactory
) {
    return ask(messageFactory, defaultTimeout);
}

// Usage
CompletionStage<String> result = actor.query(GetValue::new);
```

**Option B: Builder Pattern**
```java
// More complex but very flexible
CompletionStage<String> result = actor
    .askBuilder(GetValue.class)
    .withTimeout(Duration.ofSeconds(5))
    .onTimeout(() -> "default-value")
    .execute();
```

**Recommendation:** Option A (simple alias) - provides most value with minimal complexity

---

## Implementation Roadmap

### Phase 1: Quick Wins (1-2 weeks)
- [X] Fix README documentation (1.1)
- [X] Add default `extractor()` implementation (1.3)
- [X] Document shard count guidelines (2.3)
- [ ] Add `sharded(Class, String)` convenience method (3.1)

### Phase 2: Core Improvements (2-4 weeks)
- X ] Fix `SpringShardedActorBuilder` reflection (1.2)
- [X] Implement `SpringActorProvider` pattern (2.1)

---

# API Enhancement Recommendations

## Executive Summary
After analyzing the example modules (simple, chat, cluster, synchronization), several DX (Developer Experience) issues have been identified in the public APIs. These issues create unnecessary complexity and verbosity for developers using the framework.

## Identified DX Issues

### 2. Inconsistent Actor Context Handling
**Issue**: Different actors require different context implementations with no clear pattern.

**Examples**:
- `HelloActor` uses `SpringActorContext` directly
- `UserActor` requires custom `UserActorContext` with WebSocket session
- No clear guidance on when to use custom contexts vs default

**Problem**:
- Confusing for developers when to create custom contexts
- Type safety issues with runtime casting (UserActor.java:138-140)
- No compile-time guarantees for context compatibility

### 4. Nested Class Command Pattern Verbosity
**Issue**: All examples use nested classes for commands, creating verbose definitions.

**Example** (HelloActor.java):
```java
public interface Command {}
public static class SayHello implements Command {
    public final ActorRef<String> replyTo;
    public SayHello(ActorRef<String> replyTo) {
        this.replyTo = replyTo;
    }
}
```

**Problem**:
- Boilerplate constructors for every command
- Manual field declarations and assignments
- No built-in support for common patterns (request-response)

### 5. Blocking Operations in Constructors
**Issue**: Examples show blocking operations in service constructors.

**Example** (HelloService.java:36):
```java
this.helloActor = springActorSystem.spawn(spawnContext).toCompletableFuture().join();
```

**Problem**:
- Blocks application startup
- Poor practice for Spring applications
- No clear async initialization pattern


## Recommended Enhancements

### 2. Annotation-Based Actor Configuration
```java
@Actor(
    singleton = true,
    timeout = "3s",
    mailbox = "priority"
)
@Component
public class HelloActor implements SpringActor<HelloActor.Command> {
    // Actor implementation
}
```

### 3. Record-Based Commands with Builder Support
```java
// Using Java records for commands
public sealed interface Command {
    record SayHello(ActorRef<String> replyTo) implements Command {}
    record Shutdown() implements Command {}
}

// Or with annotation processor for builders
@ActorCommand
public class SayHello {
    ActorRef<String> replyTo;
}
// Generates: SayHello.builder().replyTo(ref).build()
```

### 4. Simplified Sharded Actor API
```java
// Proposed API
@ShardedActor(shards = 10)
@Component
public class CounterActor {
    // Framework handles TYPE_KEY and extractor
}

// Usage
var counter = actorSystem.sharded(CounterActor.class, "counterId");
counter.tell(new Increment());
```

### 5. Async Actor Initialization Pattern
```java
@Service
public class HelloService {
    private final Mono<SpringActorRef<Command>> helloActor;
    
    @Autowired
    public HelloService(SpringActorSystem system) {
        this.helloActor = system
            .spawnAsync(HelloActor.class, "default")
            .cache(); // Cache the result
    }
    
    public Mono<String> hello() {
        return helloActor.flatMap(actor -> 
            Mono.fromCompletionStage(
                actor.ask(SayHello::new, Duration.ofSeconds(3))
            )
        );
    }
}
```

### 6. Context Factory Pattern
```java
// Define context factory
@Component
public class UserActorContextFactory implements ActorContextFactory<UserActor> {
    @Override
    public SpringActorContext create(Map<String, Object> params) {
        return new UserActorContext(
            params.get("userId"),
            params.get("session")
        );
    }
}

// Usage
actorSystem.spawn(UserActor.class)
    .withContextParams(Map.of(
        "userId", userId,
        "session", session
    ))
    .start();
```

### 7. Fluent Ask Pattern
```java
// Current
actor.ask(replyTo -> new GetValue(replyTo), timeout);

// Proposed
actor.ask(GetValue.class)
    .withTimeout(Duration.ofSeconds(3))
    .execute();

// Or for simple cases
actor.query(GetValue::new);  // Uses default timeout
```

### 8. Actor Lifecycle Hooks
```java
@Component
public class HelloActor implements SpringActor<Command> {
    
    @PostSpawn
    public void onSpawn(SpringActorContext context) {
        // Initialization logic
    }
    
    @PreStop
    public void onStop() {
        // Cleanup logic
    }
}
```

## Implementation Priority

### High Priority (Breaking DX Issues)
1. Simplified spawning API
2. Async initialization patterns
3. Simplified sharded actor API

### Medium Priority (Quality of Life)
1. Record-based commands
2. Context factory pattern
3. Fluent ask pattern

### Low Priority (Nice to Have)
1. Annotation-based configuration
2. Actor lifecycle hooks
3. Command builder generation

## Migration Strategy

1. **Phase 1**: Introduce new APIs alongside existing ones
2. **Phase 2**: Update documentation and examples
3. **Phase 3**: Deprecate old APIs
4. **Phase 4**: Remove deprecated APIs in major version

## Conclusion

These enhancements would significantly improve the developer experience by:
- Reducing boilerplate code by ~60%
- Improving type safety and compile-time checks
- Following Spring Boot conventions and patterns
- Making common use cases simple while keeping complex cases possible

The proposed changes maintain backward compatibility initially while providing a clear migration path to the improved APIs.
