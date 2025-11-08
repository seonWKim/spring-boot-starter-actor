# Task 1.1: Router Base Infrastructure

**Priority:** HIGH  
**Estimated Effort:** 1 week

## Objective

Create the foundational infrastructure for router support by defining the core interfaces and builders that wrap Pekko's router functionality with a Spring Boot-friendly API.

## Requirements

### 1. SpringRouterActor Interface

Create a new interface similar to `SpringActor` but specifically for routers:

```java
package io.github.seonwkim.core.router;

/**
 * Interface for Spring-managed router actors. Classes implementing this interface
 * will be automatically registered with the actor system and can spawn pools of
 * worker actors with various routing strategies.
 *
 * @param <C> The command type that worker actors handle
 */
public interface SpringRouterActor<C> extends SpringActorWithContext<C, SpringActorContext> {
    
    /**
     * Create the router behavior configuration.
     * 
     * @param context The Spring actor context
     * @return SpringRouterBehavior configuration
     */
    @Override
    SpringRouterBehavior<C> create(SpringActorContext context);
}
```

### 2. SpringRouterBehavior Class

Create a builder for router behavior that wraps Pekko's router functionality:

```java
package io.github.seonwkim.core.router;

/**
 * A behavior wrapper for router actors that provides Spring-friendly
 * configuration for Pekko routers.
 */
public final class SpringRouterBehavior<C> {
    
    private final RoutingStrategy routingStrategy;
    private final int poolSize;
    private final Class<? extends SpringActor<C>> workerClass;
    private final SupervisorStrategy supervisionStrategy;
    
    private SpringRouterBehavior(Builder<C> builder) {
        this.routingStrategy = builder.routingStrategy;
        this.poolSize = builder.poolSize;
        this.workerClass = builder.workerClass;
        this.supervisionStrategy = builder.supervisionStrategy;
    }
    
    public static <C> Builder<C> builder() {
        return new Builder<>();
    }
    
    public static class Builder<C> {
        private RoutingStrategy routingStrategy;
        private int poolSize = 5;
        private Class<? extends SpringActor<C>> workerClass;
        private SupervisorStrategy supervisionStrategy;
        
        public Builder<C> withRoutingStrategy(RoutingStrategy strategy) {
            this.routingStrategy = Objects.requireNonNull(strategy);
            return this;
        }
        
        public Builder<C> withPoolSize(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("Pool size must be positive");
            }
            this.poolSize = size;
            return this;
        }
        
        public Builder<C> withWorkerClass(Class<? extends SpringActor<C>> workerClass) {
            this.workerClass = Objects.requireNonNull(workerClass);
            return this;
        }
        
        public Builder<C> withSupervisionStrategy(SupervisorStrategy strategy) {
            this.supervisionStrategy = strategy;
            return this;
        }
        
        public SpringRouterBehavior<C> build() {
            Objects.requireNonNull(routingStrategy, "Routing strategy is required");
            Objects.requireNonNull(workerClass, "Worker class is required");
            return new SpringRouterBehavior<>(this);
        }
    }
}
```

### 3. RoutingStrategy Interface

Define routing strategies as an abstraction over Pekko's routing logic:

```java
package io.github.seonwkim.core.router;

/**
 * Defines the routing strategy for distributing messages across worker actors.
 */
public interface RoutingStrategy {
    
    /**
     * Get the name of this routing strategy.
     */
    String getName();
    
    /**
     * Convert this strategy to Pekko's router logic.
     */
    org.apache.pekko.routing.RouterConfig toPekkoRouter(int poolSize);
    
    // Factory methods for common strategies
    static RoutingStrategy roundRobin() {
        return new RoundRobinRoutingStrategy();
    }
    
    static RoutingStrategy random() {
        return new RandomRoutingStrategy();
    }
}
```

### 4. Pekko Router Integration

Create internal implementation that wraps Pekko's pool routers:

```java
package io.github.seonwkim.core.router.internal;

/**
 * Internal class that converts SpringRouterBehavior to Pekko Behavior.
 * This class wraps Pekko's pool router with Spring-managed worker spawning.
 */
class PekkoRouterAdapter<C> {
    
    public static <C> Behavior<C> createRouter(
            SpringRouterBehavior<C> routerBehavior,
            SpringActorContext actorContext,
            ActorTypeRegistry registry) {
        
        // Convert routing strategy to Pekko router config
        RouterConfig routerConfig = routerBehavior.getRoutingStrategy()
            .toPekkoRouter(routerBehavior.getPoolSize());
        
        // Create props for worker actors
        Props workerProps = createWorkerProps(
            routerBehavior.getWorkerClass(),
            actorContext,
            registry
        );
        
        // Apply supervision strategy if specified
        if (routerBehavior.getSupervisionStrategy() != null) {
            workerProps = workerProps.withDispatcherFromConfig(...)
                .withSupervisionStrategy(routerBehavior.getSupervisionStrategy());
        }
        
        // Create Pekko pool router
        return Routers.pool(routerBehavior.getPoolSize(), workerProps)
            .withRouter(routerConfig);
    }
}
```

## Implementation Steps

1. **Create package structure:**
   - `io.github.seonwkim.core.router` - public API
   - `io.github.seonwkim.core.router.internal` - internal implementation

2. **Implement core interfaces:**
   - `SpringRouterActor`
   - `SpringRouterBehavior` with builder
   - `RoutingStrategy` interface

3. **Create Pekko integration layer:**
   - `PekkoRouterAdapter` for converting to Pekko routers
   - Utilities for creating worker props with Spring DI

4. **Update RootGuardian:**
   - Add support for spawning router actors
   - Handle router-specific spawn commands

5. **Update ActorTypeRegistry:**
   - Register router actor types
   - Track router instances separately if needed

## Testing Requirements

- Unit tests for builder validation
- Unit tests for routing strategy factory methods
- Integration test: spawn router and verify worker pool creation
- Integration test: send messages through router
- Integration test: verify workers receive messages

## Success Criteria

- ✅ Can spawn a router actor through SpringActorSystem
- ✅ Router creates worker pool on spawn
- ✅ Messages sent to router are distributed to workers
- ✅ Worker actors are managed by Spring DI
- ✅ Supervision strategy applies to workers
- ✅ All tests pass

## Files to Create

- `core/src/main/java/io/github/seonwkim/core/router/SpringRouterActor.java`
- `core/src/main/java/io/github/seonwkim/core/router/SpringRouterBehavior.java`
- `core/src/main/java/io/github/seonwkim/core/router/RoutingStrategy.java`
- `core/src/main/java/io/github/seonwkim/core/router/internal/PekkoRouterAdapter.java`
- `core/src/test/java/io/github/seonwkim/core/router/SpringRouterBehaviorTest.java`
- `core/src/test/java/io/github/seonwkim/core/router/RouterIntegrationTest.java`

## Dependencies

- Existing: `SpringActor`, `SpringActorSystem`, `RootGuardian`, `ActorTypeRegistry`
- Pekko: `org.apache.pekko.routing.*` (already in dependencies)

## Notes

- DO NOT reimplement routing algorithms - wrap Pekko's proven implementations
- Follow existing patterns from `SpringActor` and `SpringShardedActor`
- Keep API simple and Spring Boot-friendly
- Ensure zero breaking changes to existing actor system
