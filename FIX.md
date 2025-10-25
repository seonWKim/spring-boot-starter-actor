# Developer Experience Enhancements for spring-boot-starter-actor

## Overview

This document outlines concrete developer experience (DX) improvements identified through comprehensive analysis of the actor and entity creation patterns in the codebase. These enhancements aim to reduce boilerplate, improve type safety, and align with Spring Boot conventions.

---

## Priority 1: Critical Issues (Breaking DX)

### 1.2 Unsafe Reflection in `SpringShardedActorBuilder`

**Status:** CRITICAL - Instantiates Spring beans outside container

**Location:** `core/src/main/java/io/github/seonwkim/core/SpringShardedActorBuilder.java:104-122`

**Current Implementation (Problematic):**
```java
private EntityTypeKey<T> resolveTypeKey(Class<? extends ShardedActor<T>> actorClass) {
    try {
        // DANGEROUS: Instantiates Spring bean outside Spring context!
        ShardedActor<T> instance = actorClass.getDeclaredConstructor().newInstance();
        return instance.typeKey();
    } catch (Exception e) {
        // Fallback to static field reflection
        try {
            java.lang.reflect.Field typeKeyField = actorClass.getField("TYPE_KEY");
            return (EntityTypeKey<T>) typeKeyField.get(null);
        } catch (Exception ex) {
            // Returns null
        }
    }
    return null;
}
```

**Issues:**
- Creates actor instances outside Spring context (no dependencies injected)
- Violates Spring lifecycle management
- TODO comment at line 102 acknowledges this: "Use ShardedActorRegistry"

**Proposed Solution:**

1. Use existing `ShardedActorRegistry` (already exists but underutilized)
2. Populate registry during Spring startup
3. Query registry instead of reflection

**Implementation:**
```java
// In SpringShardedActorBuilder.java
private EntityTypeKey<T> resolveTypeKey(Class<? extends ShardedActor<T>> actorClass) {
    ShardedActorRegistry registry = actorSystem.getShardedActorRegistry();
    EntityTypeKey<T> typeKey = registry.getTypeKey(actorClass);

    if (typeKey == null) {
        throw new IllegalStateException(
            "ShardedActor " + actorClass.getName() + " not registered. " +
            "Ensure the actor is annotated with @Component and implements ShardedActor."
        );
    }

    return typeKey;
}
```

**Files to modify:**
- `core/src/main/java/io/github/seonwkim/core/SpringShardedActorBuilder.java`
- `core/src/main/java/io/github/seonwkim/core/shard/ShardedActorRegistry.java`

---

### 1.3 Excessive Boilerplate in ShardedActor

**Status:** HIGH PRIORITY - Every sharded actor requires repetitive code

**Current Pattern (Required Boilerplate):**
```java
@Component
public class UserActor implements ShardedActor<Command> {
    // Boilerplate 1: Static TYPE_KEY
    public static final EntityTypeKey<Command> TYPE_KEY =
        EntityTypeKey.create(Command.class, "UserActor");

    // Boilerplate 2: typeKey() method
    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    // Boilerplate 3: extractor() method
    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(100);
    }

    // Actual business logic
    @Override
    public Behavior<Command> create(EntityContext<Command> ctx) {
        // ... implementation
    }
}
```

**Proposed Enhancement:**

**Phase 1: Default Implementations**
```java
public interface ShardedActor<T> {
    EntityTypeKey<T> typeKey();
    Behavior<T> create(EntityContext<T> ctx);

    // Provide default implementation
    default ShardingMessageExtractor<ShardEnvelope<T>, T> extractor() {
        return new DefaultShardingMessageExtractor<>(100); // Sensible default
    }

    // Helper method to reduce TYPE_KEY boilerplate
    static <T> EntityTypeKey<T> createTypeKey(Class<T> commandClass, String name) {
        return EntityTypeKey.create(commandClass, name);
    }
}
```

**Phase 2: Annotation-based Configuration (Future)**
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

**Implementation Steps:**
1. Add `default extractor()` implementation to `ShardedActor` interface
2. Update all examples to remove extractor() overrides
3. Consider adding `@ShardedEntity` annotation in future release

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

### 2.3 Inconsistent Shard Counts

**Status:** MEDIUM PRIORITY - No guidance on choosing shard count

**Current State:**
- `HelloActor` (example/chat): 30 shards
- `ChatRoomActor` (example/chat): 3 shards
- `CounterActor` (example/sync): 3 shards

**Issues:**
- No documentation on choosing shard count
- Examples use inconsistent values
- Default value varies per actor

**Proposed Solution:**

1. **Provide sensible default:** 100 shards (good for most use cases)
2. **Document guidelines:**
   ```
   Shard Count Guidelines:
   - Low traffic (<100 entities): 10-30 shards
   - Medium traffic (100-10k entities): 50-100 shards
   - High traffic (10k+ entities): 100-300 shards
   - Very high traffic: 300-1000 shards

   Rule of thumb: Aim for 10-100 entities per shard
   Note: Cannot change shard count after deployment without data migration
   ```

3. **Make default explicit in `DefaultShardingMessageExtractor`:**
   ```java
   public class DefaultShardingMessageExtractor<T> {
       public static final int DEFAULT_SHARDS = 100;

       public DefaultShardingMessageExtractor() {
           this(DEFAULT_SHARDS);
       }

       public DefaultShardingMessageExtractor(int numberOfShards) {
           // ... existing implementation
       }
   }
   ```

---

### 2.4 Type-Unsafe Custom Context Handling

**Status:** MEDIUM PRIORITY - Runtime checks instead of compile-time safety

**Current Pattern (UserActor.java:130-132):**
```java
@Override
public Behavior<Command> create(SpringActorContext actorContext) {
    if (!(actorContext instanceof UserActorContext userActorContext)) {
        throw new IllegalStateException("Must be UserActorContext");
    }
    // Use userActorContext...
}
```

**Issues:**
- Runtime type checking
- No compile-time guarantees
- Easy to pass wrong context type
- Poor IDE support

**Proposed Enhancement:**

Add generic context parameter to `SpringActor`:

```java
// Modified interface
public interface SpringActor<A extends SpringActor<A, C, CTX>, C, CTX extends SpringActorContext> {
    Behavior<C> create(CTX actorContext);
}

// Simple actors use default context
public class HelloActor
    implements SpringActor<HelloActor, Command, SpringActorContext> {

    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        // Type-safe, no casting
    }
}

// Complex actors use custom context
public class UserActor
    implements SpringActor<UserActor, Command, UserActorContext> {

    @Override
    public Behavior<Command> create(UserActorContext actorContext) {
        // Type-safe, no casting needed!
        WebSocketSession session = actorContext.getSession();
    }
}
```

**Benefits:**
- Compile-time type safety
- Better IDE autocomplete
- Impossible to pass wrong context type
- Self-documenting code

**Migration Path:**
1. Keep current signature for backward compatibility
2. Add new signature with default generic: `CTX extends SpringActorContext = SpringActorContext`
3. Update examples to use new signature
4. Deprecate old signature in next major version

---

## Priority 3: Quality of Life Improvements

### 3.1 Simplified Sharded Actor Access

**Status:** LOW PRIORITY - Minor convenience improvement

**Current Pattern:**
```java
SpringShardedActorRef<Command> roomActor = actorSystem
    .sharded(ChatRoomActor.class)
    .withId(currentRoomId)
    .get();
roomActor.tell(new JoinRoom(...));
```

**Proposed Convenience Method:**
```java
// Add overload to SpringActorSystem
public <T> SpringShardedActorRef<T> sharded(
    Class<? extends ShardedActor<T>> actorClass,
    String entityId
) {
    return sharded(actorClass).withId(entityId).get();
}

// Usage
actorSystem
    .sharded(ChatRoomActor.class, currentRoomId)
    .tell(new JoinRoom(...));
```

**Benefits:**
- Reduces two-step process to one-liner
- Common case (one-time reference) becomes simpler
- Builder still available for advanced cases

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
- [ ] Fix README documentation (1.1)
- [ ] Add default `extractor()` implementation (1.3)
- [ ] Document shard count guidelines (2.3)
- [ ] Update examples to use Java records (2.2)
- [ ] Add `sharded(Class, String)` convenience method (3.1)

### Phase 2: Core Improvements (2-4 weeks)
- [ ] Fix `SpringShardedActorBuilder` reflection (1.2)
- [ ] Implement `SpringActorProvider` pattern (2.1)
- [ ] Add generic context parameter (2.4)
- [ ] Add `query()` method alias (3.3)

### Phase 3: Advanced Features (Future)
- [ ] Add `@ShardedEntity` annotation
- [ ] Implement actor discovery API (3.2)
- [ ] Add lifecycle hook annotations
- [ ] Create annotation processor for TYPE_KEY generation

---

## Breaking Changes Assessment

### Non-Breaking (Safe to implement now)
- README documentation fix (1.1)
- Default `extractor()` implementation (1.3)
- Convenience methods (3.1, 3.3)
- Documentation improvements (2.3)

### Potentially Breaking (Require deprecation cycle)
- Generic context parameter (2.4)
- SpringActorProvider pattern (2.1) - changes recommended usage pattern

### Major Version Only
- Remove deprecated constructors
- Remove old type resolution mechanism
- Make records mandatory for commands

---

## Testing Strategy

For each enhancement:
1. Add unit tests for new functionality
2. Update integration tests in example modules
3. Add migration guide in documentation
4. Validate backward compatibility

---

## Documentation Updates Required

- [ ] README: Fix lambda tell() examples
- [ ] README: Add section on choosing shard counts
- [ ] README: Show record-based commands
- [ ] README: Document async spawning patterns
- [ ] Javadocs: Add context type parameter examples
- [ ] Migration guide: Creating from 0.0.38 to 0.1.0
- [ ] Best practices guide: When to use custom contexts

---

## Questions for Maintainers

1. **Java Version Target:** Can we require Java 17+ for record support?
2. **Breaking Changes:** What version strategy? Semantic versioning?
3. **Annotation Processing:** Worth the complexity for TYPE_KEY generation?
4. **Akka/Pekko Evolution:** Any upcoming Pekko changes that affect this?

---

## Metrics for Success

After implementing these enhancements, developers should see:
- **60% reduction** in boilerplate for ShardedActor implementations
- **Zero runtime type errors** for custom contexts (compile-time safety)
- **Faster startup times** with async actor initialization patterns
- **Higher adoption** due to improved Spring Boot alignment

---

## Related Issues

- ENHANCEMENT.md (existing) - Contains some overlapping suggestions
- ShardedActorRegistry TODO comment (SpringShardedActorBuilder.java:102)
- Deprecated SpringActorRef constructors (SpringActorRef.java:46-68)

---

## Appendix: Code Examples

### Full Example: Before vs After

**Before (Current):**
```java
@Component
public class CounterActor implements ShardedActor<CounterActor.Command> {
    public static final EntityTypeKey<Command> TYPE_KEY =
        EntityTypeKey.create(Command.class, "CounterActor");

    public interface Command extends JsonSerializable {}

    public static class Increment implements Command {
        @JsonCreator
        public Increment() {}
    }

    public static class GetValue implements Command {
        public final ActorRef<Long> replyTo;

        @JsonCreator
        public GetValue(@JsonProperty("replyTo") ActorRef<Long> replyTo) {
            this.replyTo = replyTo;
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public Behavior<Command> create(EntityContext<Command> ctx) {
        return Behaviors.receive(Command.class)
            .onMessage(Increment.class, msg -> { /* ... */ })
            .onMessage(GetValue.class, msg -> { /* ... */ })
            .build();
    }

    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(100);
    }
}
```

**After (With Enhancements):**
```java
@ShardedEntity(shards = 100)  // Optional: defaults to 100
@Component
public class CounterActor implements ShardedActor<CounterActor.Command> {

    // Commands using records (Java 17+)
    public sealed interface Command extends JsonSerializable {
        record Increment() implements Command {}
        record GetValue(ActorRef<Long> replyTo) implements Command {}
    }

    // No TYPE_KEY needed - auto-generated from @ShardedEntity
    // No extractor() needed - uses annotation parameter

    @Override
    public Behavior<Command> create(EntityContext<Command> ctx) {
        return Behaviors.receive(Command.class)
            .onMessage(Command.Increment.class, msg -> { /* ... */ })
            .onMessage(Command.GetValue.class, msg -> { /* ... */ })
            .build();
    }
}
```

**Lines of Code:**
- Before: ~35 lines
- After: ~15 lines
- Reduction: ~57%


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
