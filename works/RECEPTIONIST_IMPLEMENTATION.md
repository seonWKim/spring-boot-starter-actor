# Pekko Receptionist Implementation Summary

## Overview

This implementation adds support for Pekko's **Receptionist** feature to the Spring Boot Starter Actor library, enabling dynamic actor discovery and service registration.

## Problem Statement

The current spring-boot-starter-actor implementation has a gap in actor discovery:

**Current Limitations:**
- Actors must be discovered by exact name/ID (`getOrSpawn("actor-name")`)
- No way to find "all actors of type X"
- No built-in mechanism for actor pools
- No subscription to actor availability changes
- Manual tracking required for multiple similar actors

**What Receptionist Solves:**
- Dynamic service discovery without knowing exact names
- Multiple actors registered under the same service key (worker pools)
- Subscribe to actor availability changes
- Automatic deregistration when actors stop
- Better support for microservices patterns

## Implementation

### Core Components

#### 1. ServiceKey (`core/src/main/java/io/github/seonwkim/core/receptionist/ServiceKey.java`)
```java
// Type-safe key for identifying services
ServiceKey<WorkerActor.Command> workerKey = 
    ServiceKey.create(WorkerActor.Command.class, "worker-pool");
```

#### 2. Listing (`core/src/main/java/io/github/seonwkim/core/receptionist/Listing.java`)
```java
// Represents actors registered under a service key
Listing<Command> listing = receptionist.find(workerKey);
Set<SpringActorRef<Command>> actors = listing.getServiceInstances();
```

#### 3. SpringReceptionistService (`core/src/main/java/io/github/seonwkim/core/receptionist/SpringReceptionistService.java`)
```java
// Main API for registration, discovery, and subscription
receptionist.register(serviceKey, actorRef);
receptionist.find(serviceKey);
receptionist.subscribe(serviceKey, callback);
```

#### 4. Integration with SpringActorSystem
```java
// Access via SpringActorSystem
SpringReceptionistService receptionist = actorSystem.receptionist();
```

### API Design Principles

1. **Spring-friendly**: Follows existing library patterns
2. **Type-safe**: Leverages Java generics for compile-time safety
3. **Reactive**: Returns CompletionStage for async operations
4. **Simple**: Minimal ceremony, clear intent
5. **Familiar**: Mirrors existing SpringActorSystem API style

## Testing

### Test Coverage (11 comprehensive tests)

**Registration and Discovery Tests:**
- ✅ Register and find single actor
- ✅ Register and find multiple actors
- ✅ Find returns empty listing when no actors registered
- ✅ Deregister removes actor
- ✅ Actor automatically deregistered when stopped

**Subscription Tests:**
- ✅ Subscribe receives initial listing
- ✅ Subscribe receives updates when actor registered
- ✅ Subscribe receives updates when actor deregistered

**Load Balancing Tests:**
- ✅ Round-robin distribution across workers

**ServiceKey Tests:**
- ✅ ServiceKey equality
- ✅ ServiceKey getId

**Test Results:** 159 tests passing (11 new + 148 existing)

## Example Application

Created a comprehensive example demonstrating the Receptionist feature with a worker pool pattern:

### Location
`example/receptionist/`

### Features Demonstrated
- Dynamic worker pool management
- Service registration and discovery
- Round-robin load balancing
- Subscription to pool changes
- REST API for interaction
- Concurrent batch processing

### Architecture
```
WorkerPoolController (REST API)
    ↓
WorkerPoolService (manages pool)
    ↓
SpringReceptionistService (register/find/subscribe)
    ↓
DataProcessorWorker (worker actors)
```

### Key Files
- `ReceptionistExampleApplication.java` - Main application
- `DataProcessorWorker.java` - Worker actor implementation
- `WorkerPoolService.java` - Pool management with Receptionist
- `WorkerPoolController.java` - REST API endpoints
- `README.md` - Comprehensive documentation with examples

### Running the Example
```bash
cd example/receptionist
../../gradlew bootRun

# Access at http://localhost:8080
```

## Use Cases

This feature enables several important patterns:

1. **Worker Pools**
   - Register multiple workers under same key
   - Distribute tasks across available workers
   - Dynamic scaling by adding/removing workers

2. **Service Discovery**
   - Find services without hard-coded names
   - Microservices-style service registry
   - Plugin/extension discovery

3. **Load Balancing**
   - Distribute load across service instances
   - Round-robin, random, or custom strategies
   - Automatic failover when services fail

4. **Availability Monitoring**
   - Subscribe to service availability changes
   - React to services joining/leaving
   - Health monitoring and alerting

5. **Cluster Pub-Sub**
   - Foundation for pub-sub patterns (roadmap item 5.4)
   - Event broadcasting to subscribers
   - Topic-based messaging

## Benefits

### For Developers
- **Easier to use**: No need to track actor names manually
- **More flexible**: Dynamic discovery vs static names
- **Better patterns**: Enables worker pools, load balancing
- **Reactive**: Built-in subscriptions for availability changes
- **Familiar**: Matches existing SpringActorSystem API style

### For Applications
- **Scalable**: Easy to add/remove service instances
- **Resilient**: Automatic deregistration on failure
- **Distributed**: Works in both local and cluster modes
- **Dynamic**: Services can come and go at runtime

## Comparison with Existing Approaches

| Feature | Before (getOrSpawn) | After (Receptionist) |
|---------|-------------------|---------------------|
| Discovery | By exact name/ID | By service type |
| Multiple instances | Manual tracking | Automatic via ServiceKey |
| Availability monitoring | Custom implementation | Built-in subscriptions |
| Load balancing | Manual | Easy with listing |
| Deregistration | Manual | Automatic on stop |
| Worker pools | Complex | Natural pattern |

## Integration with Roadmap

This implementation aligns with roadmap items:

### 5.4 Cluster Pub-Sub (Roadmap Item)
The Receptionist is mentioned in the roadmap as the foundation for pub-sub:
```java
// From roadmap/5-clustering/README.md
actorSystem.receptionist()
    .register(ServiceKey.create(Event.class, topic), subscriber);
```

Our implementation provides this foundation.

### 8. Developer Experience
Follows the DX principles:
- Clear, actionable API
- Spring Boot friendly
- Comprehensive examples
- Excellent documentation

## Files Changed

### Core Implementation
- `core/src/main/java/io/github/seonwkim/core/SpringActorSystem.java`
- `core/src/main/java/io/github/seonwkim/core/receptionist/ServiceKey.java`
- `core/src/main/java/io/github/seonwkim/core/receptionist/Listing.java`
- `core/src/main/java/io/github/seonwkim/core/receptionist/SpringReceptionistService.java`

### Tests
- `core/src/test/java/io/github/seonwkim/core/receptionist/SpringReceptionistServiceTest.java`

### Example Application
- `example/receptionist/README.md`
- `example/receptionist/build.gradle.kts`
- `example/receptionist/src/main/java/io/github/seonwkim/example/receptionist/ReceptionistExampleApplication.java`
- `example/receptionist/src/main/java/io/github/seonwkim/example/receptionist/DataProcessorWorker.java`
- `example/receptionist/src/main/java/io/github/seonwkim/example/receptionist/WorkerPoolService.java`
- `example/receptionist/src/main/java/io/github/seonwkim/example/receptionist/WorkerPoolController.java`
- `example/receptionist/src/main/resources/application.yml`

### Configuration
- `settings.gradle.kts`

## Code Quality

- ✅ All tests passing (159 tests)
- ✅ Code formatted with spotless
- ✅ Follows existing code patterns
- ✅ Comprehensive documentation
- ✅ Type-safe API
- ✅ Null-safe with proper annotations
- ✅ Minimal changes to existing code

## Future Enhancements

Potential improvements for future PRs:

1. **Documentation**: Add to main documentation site
2. **Cluster Support**: Add cluster-specific examples
3. **Pub-Sub Pattern**: Build on this for pub-sub (roadmap 5.4)
4. **Metrics**: Track registration/deregistration events
5. **Health Checks**: Integrate with actuator health checks
6. **Advanced Routing**: Custom routing strategies
7. **Priority Queues**: Priority-based task distribution

## Conclusion

This MVP implementation successfully adds Pekko's Receptionist feature to the library with:
- ✅ Clean, Spring-friendly API
- ✅ Comprehensive test coverage
- ✅ Working example application
- ✅ Excellent documentation
- ✅ Zero breaking changes
- ✅ Aligns with roadmap goals

The implementation provides a solid foundation for dynamic actor discovery and enables important patterns like worker pools, service discovery, and load balancing. It maintains the library's philosophy of making Pekko accessible to Spring Boot developers while preserving the power of the underlying actor model.
