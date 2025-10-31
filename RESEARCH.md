# Best Practices for Building Distributed Actor Systems

This document outlines the proven design principles from Erlang/Elixir for building robust distributed actor systems.

## Table of Contents

1. [Core Philosophy](#core-philosophy)
2. [Supervision and Fault Tolerance](#supervision-and-fault-tolerance)
3. [Actor Design Principles](#actor-design-principles)
4. [Message Passing Patterns](#message-passing-patterns)
5. [State Management](#state-management)
6. [Error Handling](#error-handling)
7. [Distributed System Design](#distributed-system-design)
8. [Performance Considerations](#performance-considerations)
9. [Testing Strategies](#testing-strategies)

---

## Core Philosophy

### 1. Let It Crash

**Principle**: Don't defensively program for every possible error. Instead, let actors fail fast and rely on supervision
to restart them.

**Why It Works**:

- Simplifies code by removing defensive error handling
- Failed actors are replaced with fresh state, avoiding corrupt state propagation
- System self-heals automatically through supervisor restarts
- Errors are isolated and don't cascade

**Implementation Guidelines**:

```
- Design actors to fail when they encounter unexpected conditions
- Use supervisors to define restart strategies
- Keep initialization logic idempotent
- Avoid try-catch blocks for business logic errors
- Reserve error handling for recoverable situations only
```

**Example Pattern**:

```java
// BAD: Defensive programming
if(data !=null&&data.

isValid() &&data.

hasRequiredFields()){

process(data);
}else{
        log.

error("Invalid data");
// Try to recover...
}

// GOOD: Let it crash
process(data); // Will throw if invalid, supervisor handles restart
```

---

## Supervision and Fault Tolerance

### 2. Supervision Trees

**Principle**: Organize actors in hierarchical supervision trees where parent actors supervise children.

**Supervision Strategies**:

1. **One-for-One**: If one child fails, only restart that child
    - Use when: Children are independent
    - Example: Web request handlers, independent workers

2. **One-for-All**: If one child fails, restart all children
    - Use when: Children are interdependent
    - Example: Connected pool of workers, pipeline stages

3. **Rest-for-One**: If one child fails, restart that child and all children started after it
    - Use when: Later children depend on earlier ones
    - Example: Database connection → Query executor → Result processor

**Design Guidelines**:

```
- Supervisors should not do business logic, only supervision
- Keep supervision trees shallow (3-4 levels max)
- Group actors by failure correlation
- Use different strategies at different levels
- Critical actors should be higher in the tree
```

### 3. Restart Policies

**Erlang's Standard Policies**:

- **Permanent**: Always restart (critical services)
- **Temporary**: Never restart (one-shot tasks)
- **Transient**: Restart only on abnormal termination (workers)

**Restart Limits**:

```
- Set maximum restart frequency (e.g., 5 restarts in 60 seconds)
- Prevents cascading failures from overwhelming the system
- Supervisor itself fails up to parent if limit exceeded
- Allows intervention at higher level
```

---

## Actor Design Principles

### 4. Single Responsibility

**Principle**: Each actor should have one clear responsibility.

**Benefits**:

- Easier to reason about behavior
- Simpler testing
- Better failure isolation
- Improved reusability

**Anti-pattern**: Creating "god actors" that handle multiple concerns

### 5. Isolation and Share-Nothing

**Principle**: Actors share nothing, communicate only via messages.

**Rules**:

```
- No shared mutable state between actors
- Pass immutable messages or copies
- Each actor owns its state exclusively
- No direct method calls between actors
```

**Why Critical**:

- Prevents race conditions
- Enables true parallelism
- Allows actors to fail independently
- Makes distribution transparent

### 6. Location Transparency

**Principle**: Actor references should work regardless of physical location.

**Design Implications**:

```
- Use actor references/PIDs, not object references
- Message passing should work locally and remotely
- No assumptions about actor location in code
- Enables elastic scaling and load distribution
```

**Implementation**:

```java
// BAD: Location-dependent
UserActor user = new UserActor();
user.

updateProfile(data);

// GOOD: Location-transparent
ActorRef user = system.actorOf("user-123");
user.

tell(new UpdateProfile(data));
```

---

## Message Passing Patterns

### 7. Asynchronous by Default

**Principle**: All actor communication should be asynchronous (fire-and-forget).

**Benefits**:

- Non-blocking, better throughput
- Natural backpressure
- Prevents deadlocks
- Enables distribution

**When Synchronous is Acceptable**:

```
- Initialization sequences
- Shutdown coordination
- Testing only
```

### 8. Message Design

**Best Practices**:

1. **Immutability**: Messages should be immutable
   ```java
   // GOOD
   public record UserCreated(String userId, String email, Instant timestamp) {}
   ```

2. **Self-Contained**: Messages contain all needed data
   ```java
   // BAD: Requires additional state lookup
   ProcessOrder(orderId)

   // GOOD: Self-contained
   ProcessOrder(orderId, items, userId, totalAmount)
   ```

3. **Serializable**: Design for network transmission
   ```
   - Use simple data types
   - Avoid circular references
   - Keep messages small
   - Version your message formats
   ```

4. **Command vs Event Pattern**:
   ```
   Commands: CreateUser, UpdateOrder (intent)
   Events: UserCreated, OrderUpdated (facts)
   ```

### 9. Request-Reply Pattern

**When Needed**: Synchronous-style communication while staying async.

**Implementation**:

```
1. Sender includes return address in message
2. Receiver processes and sends reply
3. Sender handles reply asynchronously
```

**With Timeout**:

```java
Future<Response> future = actor.ask(new Request(), timeout);
future.

onComplete(response ->

handle(response));
```

---

## State Management

### 10. Stateful Actors

**Principle**: Actors can safely maintain mutable state since access is serialized.

**Guidelines**:

```
- State is private to the actor
- All state changes happen in message handlers
- One message processed at a time (no locks needed)
- State is lost on crash (by design)
```

### 11. State Recovery Patterns

**Options**:

1. **Ephemeral State**: State lost on restart
    - Use for: Caches, temporary workers
    - Recovery: Rebuild from source

2. **Event Sourcing**: Replay events to rebuild state
    - Use for: Business entities
    - Recovery: Replay event log

3. **Snapshots**: Periodic state checkpoints
    - Use for: Long-lived actors with many events
    - Recovery: Load snapshot + replay recent events

4. **External State**: Store in database
    - Use for: When state must survive node failure
    - Recovery: Load from database on restart

**Erlang Pattern**: Most state is ephemeral; critical state is in databases

---

## Error Handling

### 12. Three-Layer Error Strategy

**Layer 1: Application Logic** (Actors)

```
- Let it crash for unexpected errors
- Handle only expected, recoverable errors
- Validate inputs, fail fast on invalid data
```

**Layer 2: Supervision**

```
- Restart failed actors
- Implement backoff strategies
- Escalate persistent failures
```

**Layer 3: Monitoring & Observability**

```
- Log failures for debugging
- Track restart rates
- Alert on persistent failures
```

### 13. Error Kernel Pattern

**Principle**: Keep critical system components minimal and bulletproof.

**Structure**:

```
Error Kernel (Critical)
  ├─ Supervisor (minimal logic)
  └─ Essential state storage

Worker Layer (Allowed to fail)
  ├─ Business logic actors
  ├─ External integrations
  └─ Computations
```

**Rules**:

- Error kernel never crashes
- Keep error kernel code simple and well-tested
- Push complexity to supervised workers

---

## Distributed System Design

### 14. Network Partition Handling

**Erlang's Approach**: Assume partitions will happen.

**Strategies**:

1. **Node Monitoring**:
   ```
   - Monitor connected nodes
   - Detect node disconnections
   - Handle network splits gracefully
   ```

2. **Split-Brain Prevention**:
   ```
   - Use distributed consensus (Raft, Paxos)
   - Implement quorum requirements
   - Prefer consistency over availability when needed
   ```

3. **Eventual Consistency**:
   ```
   - Accept temporary inconsistency
   - Use CRDTs for conflict-free merging
   - Implement reconciliation logic
   ```

### 15. Service Discovery

**Patterns**:

1. **Local Registration**: Register actors by name on local node
2. **Global Registration**: Cluster-wide name registration
3. **Service Registry**: External service discovery (Consul, etcd)

**Best Practice**: Use local registration for frequent lookups, global for singletons.

### 16. Distributed Coordination

**Erlang Patterns**:

1. **Process Groups**: Multicast messages to actor groups
2. **Distributed Monitors**: Track remote actor lifecycle
3. **Global Locks**: Coordination when absolutely necessary (rare)

**Anti-pattern**: Distributed transactions (too brittle)

---

## Performance Considerations

### 17. Backpressure

**Problem**: Fast senders overwhelm slow receivers.

**Erlang Solutions**:

1. **Mailbox Limits**: Set maximum mailbox size
   ```
   - Sender blocks or fails when limit reached
   - Provides natural pushback
   ```

2. **Selective Receive**: Process priority messages first
   ```
   - Handle control messages before data
   - Allows cancellation, shutdown
   ```

3. **Flow Control**: Implement credit-based flow control
   ```
   - Receiver grants credits to sender
   - Sender only sends when credits available
   ```

### 18. Actor Granularity

**Guidelines**:

- **Fine-grained**: More actors, better isolation
  ```
  - One actor per user session
  - One actor per entity (order, account)
  - Better fault isolation
  ```

- **Coarse-grained**: Fewer actors, less overhead
  ```
  - Pool of workers
  - Stateless processors
  - Better for high throughput
  ```

**Erlang Philosophy**: "Millions of lightweight actors" - prefer fine-grained.

### 19. Message Batching

**When to Batch**:

```
- High-throughput scenarios
- Network-bound communication
- I/O operations
```

**When NOT to Batch**:

```
- Low-latency requirements
- Interactive requests
- Control messages
```

---

## Testing Strategies

### 20. Testing Actors

**Unit Testing**:

```java
// Test actor behavior with messages
@Test
void testUserActor() {
    TestKit probe = new TestKit(system);
    ActorRef user = system.actorOf(UserActor.props());

    user.tell(new CreateUser("john@example.com"), probe.getRef());

    UserCreated response = probe.expectMsgClass(UserCreated.class);
    assertEquals("john@example.com", response.email());
}
```

**Integration Testing**:

```
- Test supervision trees
- Verify restart behavior
- Test failure scenarios
```

**Chaos Testing**:

```
- Kill random actors
- Simulate network partitions
- Inject latency
- Verify system recovers
```

**Erlang Practice**: Heavy emphasis on property-based testing.

---

## Summary: Key Takeaways

1. **Let It Crash**: Embrace failure, use supervision for recovery
2. **Supervision Trees**: Organize actors hierarchically with clear restart strategies
3. **Isolation**: Share nothing, communicate via immutable messages
4. **Location Transparency**: Design for distribution from day one
5. **Async by Default**: Non-blocking message passing for scalability
6. **State Locality**: Each actor owns its state, no shared mutable state
7. **Error Kernel**: Keep critical code minimal, push complexity to workers
8. **Backpressure**: Handle flow control to prevent overload
9. **Network Awareness**: Assume partitions, design for eventual consistency
10. **Test Failures**: Verify recovery through chaos testing

---

## Further Reading

### Erlang/OTP Resources:

- "Learn You Some Erlang for Great Good!" by Fred Hébert
- "Designing for Scalability with Erlang/OTP" by Francesco Cesarini
- Erlang OTP Design Principles: https://www.erlang.org/doc/design_principles/

### Elixir Resources:

- "Programming Elixir" by Dave Thomas
- "Designing Elixir Systems with OTP" by James Edward Gray II
- Elixir Supervision Guide: https://hexdocs.pm/elixir/Supervisor.html

### Actor Model Theory:

- Carl Hewitt's original Actor Model papers
- "Reactive Messaging Patterns with the Actor Model" by Vaughn Vernon
- Joe Armstrong's PhD thesis: "Making reliable distributed systems"

---

## Applying to Java/Spring Actor Systems

When implementing these patterns in Java/Spring environments:

1. **Use Akka or similar frameworks**: Already implement Erlang's supervision model
2. **Embrace immutability**: Use Java records, immutable collections
3. **Async patterns**: CompletableFuture, reactive streams
4. **Supervision**: Configure restart strategies explicitly
5. **Serialization**: Use Avro, Protocol Buffers for messages
6. **Monitoring**: Integrate with Spring Actuator for observability
7. **Testing**: Use Akka TestKit patterns or similar testing frameworks

The key is adapting these proven Erlang/Elixir patterns to JVM idioms while preserving the core principles.
