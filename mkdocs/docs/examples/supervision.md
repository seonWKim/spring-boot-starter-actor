# Supervision and Restart Strategies

This guide demonstrates how to use supervision and restart strategies in Spring Boot Starter Actor to build
fault-tolerant, self-healing applications.

## Overview

Supervision is a core concept in the actor model where parent actors monitor and respond to failures in their child
actors. Spring Boot Starter Actor supports multiple supervision strategies that determine how the system handles
failures.

The supervision example provides:

- **Interactive visualization** of actor hierarchies and supervision behavior
- **Real-time failure tracking** with visual indicators
- **Hands-on testing** of different supervision strategies
- **Multi-level hierarchies** demonstrating supervision at arbitrary depth

## Source Code

Complete source
code: [https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/supervision](https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/supervision)

## Supervision Strategies

Spring Boot Starter Actor provides four supervision strategies for handling child actor failures:

### 1. Restart (Unlimited)

**Strategy:**

```java
SupervisorStrategy.restart()
```

**Behavior:**

- Child actor is stopped when it fails
- New instance is immediately created with fresh state
- No limit on number of restarts
- Failure continues to be tracked across restarts

**When to use:**

- Transient failures that are likely to succeed on retry
- Stateless actors where losing state is acceptable
- Services that connect to external resources (databases, APIs)
- Worker actors processing independent tasks

**Example scenario:** A worker actor fetching data from an external API fails due to network timeout. Restarting gives
it another chance to succeed.

### 2. Restart (Limited)

**Strategy:**

```java
SupervisorStrategy.restart().withLimit(maxRetries, duration)
```

**Common configurations:**

```java
// Restart up to 3 times within 1 minute
SupervisorStrategy.restart().withLimit(3,Duration.ofMinutes(1))

// Restart up to 10 times within 1 hour
SupervisorStrategy.restart().withLimit(10,Duration.ofHours(1))

// Restart up to 5 times within 30 seconds
SupervisorStrategy.restart().withLimit(5,Duration.ofSeconds(30))
```

**Behavior:**

- Child actor restarts after failure, but only up to the specified limit
- If limit is exceeded within the time window, the child is stopped permanently
- Counter resets after the time window expires
- Prevents infinite restart loops

**When to use:**

- Preventing resource exhaustion from failing actors
- Distinguishing between transient and persistent failures
- Production systems where infinite retries could cause problems
- Critical actors where repeated failures indicate serious issues

**Example scenario:** A payment processing actor should retry a few times for network issues, but stop permanently if
consistently failing (indicating a configuration or system problem).

### 3. Stop

**Strategy:**

```java
SupervisorStrategy.stop()
```

**Behavior:**

- Child actor is stopped immediately on failure
- No restart attempted
- Parent is notified of termination
- Resources are cleaned up

**When to use:**

- Fatal errors that cannot be recovered by restart
- Validation failures indicating bad configuration
- Actors that should fail fast
- Development/testing to catch errors early

**Example scenario:** An actor receives invalid configuration at startup. There's no point restarting - the
configuration must be fixed first.

### 4. Resume

**Strategy:**

```java
SupervisorStrategy.resume()
```

**Behavior:**

- Exception is logged but ignored
- Actor continues processing next message
- Current state is preserved
- No restart occurs

**When to use:**

- Non-critical failures that shouldn't disrupt operation
- Actors processing independent messages where one failure shouldn't stop others
- Logging/monitoring actors
- Best-effort processing scenarios

**Example scenario:** A logging actor fails to write one log entry to a remote service. It should continue processing
other log entries rather than stopping.

## Choosing the Right Strategy

Use this decision tree to select an appropriate strategy:

```
Is the failure recoverable?
├─ NO → Stop
└─ YES
   └─ Should we retry?
      ├─ NO → Resume (log and continue)
      └─ YES
         └─ Could infinite retries cause problems?
            ├─ YES → Restart (Limited)
            └─ NO → Restart (Unlimited)
```

## Strategy Comparison

| Strategy                | State After Failure | Continues Processing | Automatic Retry | Use Case                              |
|-------------------------|---------------------|----------------------|-----------------|---------------------------------------|
| **Restart (Unlimited)** | Reset               | ✅ (new instance)     | ✅ Infinite      | Transient failures, stateless workers |
| **Restart (Limited)**   | Reset               | ✅ (until limit)      | ✅ Limited       | Production systems, prevent loops     |
| **Stop**                | Lost                | ❌                    | ❌               | Fatal errors, fail fast               |
| **Resume**              | Preserved           | ✅ (same instance)    | ❌               | Non-critical failures, best effort    |

## Interactive Demo

### Starting the Application

```bash
cd example/supervision
./gradlew bootRun
```

Open your browser to: **http://localhost:8080**

### Using the Web Interface

The interactive UI provides:

1. **Visual Hierarchy Tree**
    - Green circles represent actors
    - Darker green = supervisors (manage children only)
    - Lighter green = workers (can process tasks and supervise)

2. **Failure Count Badges**
    - Red badges show how many times each worker has failed
    - Updated in real-time
    - Persists across restarts

3. **Interactive Actions**
    - **Add Child**: Create a child actor with chosen strategy
    - **Send Work**: Test the actor processes messages correctly
    - **Trigger Failure**: Deliberately fail the actor to test supervision
    - **Stop**: Manually terminate an actor

4. **Live Event Log**
    - Real-time stream of all actor events
    - Shows spawns, failures, restarts, and stops
    - Helps understand supervision behavior

## State Management

Understanding how state behaves across supervision actions:

| Strategy    | Actor Instance       | Actor State             | Message Mailbox                         |
|-------------|----------------------|-------------------------|-----------------------------------------|
| **Restart** | New instance created | Lost (reset to initial) | Preserved (old messages reprocessed)    |
| **Stop**    | Terminated           | Lost                    | Lost                                    |
| **Resume**  | Same instance        | Preserved               | Preserved (continues from next message) |

**Important:** If you need to preserve state across restarts, consider:

- Persisting state to external storage (database, etc.)
- Using event sourcing patterns
- Implementing custom snapshot/recovery logic

## Architecture Benefits

Supervision provides:

✅ **Fault Isolation** - Failures don't cascade to unrelated actors
✅ **Self-Healing** - Automatic recovery without manual intervention
✅ **Graceful Degradation** - System continues operating despite component failures
✅ **Clear Error Boundaries** - Supervision hierarchy defines failure domains
✅ **Observability** - Real-time visibility into system health

## Best Practices

1. **Choose strategies per actor type**, not globally
2. **Use limited restart in production** to prevent resource exhaustion
3. **Monitor restart rates** to detect systemic issues
4. **Design for restart** - assume actors can restart anytime
5. **Keep critical state external** to survive restarts
6. **Use hierarchy for isolation** - group related actors under supervisors
7. **Test failure scenarios** before production deployment

## Common Pitfalls

❌ **Using unlimited restart everywhere** - Can hide persistent problems
❌ **Using resume for critical failures** - May continue with corrupted state
❌ **Not monitoring failure rates** - Miss signs of systemic issues
❌ **Storing critical state in-memory only** - Lost on restart
❌ **Flat hierarchies** - No fault isolation
