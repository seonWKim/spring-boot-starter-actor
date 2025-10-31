# Enhancement Recommendations for spring-boot-starter-actor

This document analyzes the current implementation against the best practices outlined in [RESEARCH.md](RESEARCH.md) and
identifies features to add or fix.

## Table of Contents

1. [Current State Analysis](#current-state-analysis)
2. [Critical Gaps](#critical-gaps)
3. [Priority Matrix](#priority-matrix)
4. [Detailed Enhancement Recommendations](#detailed-enhancement-recommendations)

---

## Current State Analysis

### Strengths

The current implementation has several solid foundations:

| Feature               | Status    | Implementation                                    |
|-----------------------|-----------|---------------------------------------------------|
| Actor Model           | ✅ Strong  | SpringActor interface with Pekko backing          |
| Spring Integration    | ✅ Strong  | Full DI support, auto-configuration               |
| Async Messaging       | ✅ Strong  | tell() and ask() patterns implemented             |
| Location Transparency | ✅ Strong  | ActorRef-based communication                      |
| Cluster Support       | ✅ Strong  | Sharded actors, cluster events                    |
| Basic Supervision     | ✅ Partial | Per-actor supervision via Behaviors.supervise()   |
| Metrics               | ✅ Strong  | Java agent instrumentation for lifecycle/messages |
| Spring Boot 2/3       | ✅ Strong  | Dual support with separate artifacts              |

### Weaknesses

Compared to Erlang/Elixir best practices, these areas need improvement:

| Category               | Current State         | Erlang/Elixir Standard                   | Gap          |
|------------------------|-----------------------|------------------------------------------|--------------|
| Supervision Trees      | ❌ Flat structure      | ✅ Hierarchical trees                     | **Critical** |
| Supervision Strategies | ❌ Only per-actor      | ✅ one-for-one, one-for-all, rest-for-one | **High**     |
| Restart Policies       | ❌ Not exposed         | ✅ permanent, temporary, transient        | **High**     |
| Backpressure           | ❌ No built-in support | ✅ Mailbox limits, flow control           | **High**     |
| State Recovery         | ❌ Manual only         | ✅ Event sourcing, snapshots              | **Medium**   |
| Error Kernel Pattern   | ❌ Not documented      | ✅ Core pattern                           | **Medium**   |
| Testing Tools          | ❌ Basic only          | ✅ TestKit, chaos testing                 | **High**     |
| Message Validation     | ❌ None                | ✅ Pattern matching, guards               | **Medium**   |
| Configuration          | ⚠️ Limited            | ✅ Extensive HOCON                        | **Medium**   |

---

## Critical Gaps

### 1. No Hierarchical Supervision Trees ❌ CRITICAL

**Current Implementation:**

```java
// All actors are spawned flat under RootGuardian
core/src/main/java/io/github/seonwkim/core/impl/DefaultRootGuardian.java:66-73
```

All actors are children of a single `RootGuardian`. There's no way to create parent-child supervision hierarchies.

**Erlang/Elixir Pattern:**

```erlang
% Supervisor with children that can themselves be supervisors
Supervisor.start_link([
  {CriticalWorker, []},
  {WorkerSupervisor, [    % Child supervisor
    {Worker1, []},
    {Worker2, []}
  ]}
], strategy: :one_for_one)
```

**Impact:**

- Cannot isolate failure domains properly
- All actors fail at the same level
- No error escalation hierarchy
- Violates "error kernel" pattern

**Recommendation:** See [Enhancement #1](#enhancement-1-hierarchical-supervision-trees)

---

### 2. No Supervision Strategy Options ❌ HIGH

**Current Implementation:**

```java
// example/simple/src/main/java/io/github/seonwkim/example/HelloActor.java:93-95
return Behaviors.supervise(behavior)
    .

onFailure(SupervisorStrategy.restart()
        .

withLimit(10,Duration.ofMinutes(1)));
```

Only per-actor supervision with restart strategy. No way to define supervision strategies for groups of actors.

**Erlang/Elixir Strategies:**

1. `:one_for_one` - Restart only failed child
2. `:one_for_all` - Restart all children when one fails
3. `:rest_for_one` - Restart failed child and all started after it

**Impact:**

- Cannot model interdependent actor groups
- Cannot implement pipeline patterns properly
- Manual coordination required for related actors

**Recommendation:** See [Enhancement #2](#enhancement-2-supervision-strategies)

---

### 3. No Backpressure Mechanism ❌ HIGH

**Current Implementation:**

```java
// No mailbox limits or flow control
core/src/main/java/io/github/seonwkim/core/SpringActorSpawnBuilder.java
```

Actors have unbounded mailboxes. Fast producers can overwhelm slow consumers.

**Erlang/Elixir Pattern:**

```erlang
# Bounded mailbox
spawn_opt(fun() -> worker() end, [{message_queue_data, off_heap},
                                   {max_heap_size, 1000000}])
```

**Real-World Scenario:**

- Chat application: Broadcast to 10,000 users
- One slow consumer (network issue)
- Mailbox grows unbounded → OOM

**Recommendation:** See [Enhancement #3](#enhancement-3-backpressure-support)

---

### 4. Missing Testing Infrastructure ❌ HIGH

**Current Implementation:**
No specialized testing utilities for actors. Users must write integration tests manually.

**Erlang/Elixir Tooling:**

- `TestKit` for actor testing
- Chaos testing utilities
- Property-based testing (QuickCheck)
- Supervision testing helpers

**Impact:**

- Difficult to test actor behaviors
- No way to verify supervision strategies
- Cannot test failure scenarios easily
- Hinders adoption (developers need confidence)

**Recommendation:** See [Enhancement #8](#enhancement-8-testing-infrastructure)

---

## Priority Matrix

| Priority | Enhancement                    | Complexity | Impact   | Erlang/Elixir Alignment |
|----------|--------------------------------|------------|----------|-------------------------|
| **P0**   | Hierarchical Supervision Trees | High       | Critical | Essential               |
| **P0**   | Backpressure Support           | Medium     | High     | Essential               |
| **P0**   | Testing Infrastructure         | Medium     | High     | Essential               |
| **P1**   | Supervision Strategies         | High       | High     | Core Pattern            |
| **P1**   | Restart Policies               | Low        | High     | Core Pattern            |
| **P1**   | Configuration API              | Medium     | Medium   | Best Practice           |
| **P2**   | State Recovery Patterns        | High       | Medium   | Advanced                |
| **P2**   | Error Kernel Pattern           | Low        | Medium   | Best Practice           |
| **P2**   | Message Validation             | Medium     | Medium   | Best Practice           |
| **P3**   | Event Sourcing Support         | High       | Medium   | Advanced                |
| **P3**   | Partition Handling             | Medium     | Low      | Advanced                |

**Priority Definitions:**

- **P0**: Critical gaps that limit production usage
- **P1**: Core patterns missing from Erlang/Elixir model
- **P2**: Best practices that improve robustness
- **P3**: Advanced features for specific use cases

---

## Detailed Enhancement Recommendations

### Enhancement #1: Hierarchical Supervision Trees

**Status:** ❌ Missing
**Priority:** P0 - Critical
**Complexity:** High
**Reference:** RESEARCH.md Section 2 - Supervision Trees

#### Problem

Current architecture forces flat actor hierarchy under RootGuardian:

```
RootGuardian
├── HelloActor:instance1
├── HelloActor:instance2
├── ChatRoomActor:room1
└── UserActor:user1
```

All actors are siblings. No parent-child supervision relationships.

#### Erlang/Elixir Pattern

```erlang
# Hierarchical tree with error escalation
Supervisor.start_link([
  # Error kernel - minimal, bulletproof code
  {ConfigManager, []},

  # Application supervisors
  {ChatSupervisor, [
    {RoomSupervisor, []},    # Can restart all rooms
    {UserSupervisor, []}     # Independent from rooms
  ]},

  {WorkerSupervisor, [
    {Worker1, []},
    {Worker2, []}
  ]}
], strategy: :one_for_one)
```

#### Proposed Implementation

**1. New API for Supervisor Actors**

```java

@Component
public class ChatSupervisor implements SpringSupervisor<ChatSupervisor.Command> {

    @Override
    public Behavior<Command> create(SpringActorContext context) {
        return Behaviors.setup(ctx -> {
            // Spawn supervised children
            ActorRef<RoomSupervisor.Command> roomSupervisor = ctx.spawn(
                    createRoomSupervisor(),
                    "room-supervisor"
            );

            ActorRef<UserSupervisor.Command> userSupervisor = ctx.spawn(
                    createUserSupervisor(),
                    "user-supervisor"
            );

            return supervisorBehavior(roomSupervisor, userSupervisor);
        });
    }

    @Override
    public SupervisionStrategy supervisionStrategy() {
        return SupervisionStrategy.oneForOne()
                .withMaxRestarts(5, Duration.ofMinutes(1));
    }
}
```

**2. SpringActorSystem API Extension**

```java
public class SpringActorSystem {

    /**
     * Spawn an actor under a specific parent supervisor
     */
    public <A extends SpringActor<A, C>, C> CompletionStage<SpringActorRef<C>>
    spawnUnder(ActorRef<?> parent, Class<A> actorClass, String actorId) {
        // Implementation
    }

    /**
     * Create a supervisor hierarchy
     */
    public SupervisorBuilder supervisor(String name) {
        return new SupervisorBuilder(this, name);
    }
}
```

**3. Configuration Support**

```yaml
spring:
  actor:
    supervision:
      trees:
        - name: chat-system
          strategy: one-for-one
          children:
            - name: room-supervisor
              class: io.github.seonwkim.example.RoomSupervisor
              strategy: one-for-all
            - name: user-supervisor
              class: io.github.seonwkim.example.UserSupervisor
              strategy: one-for-one
```

#### Benefits

- Proper error isolation and escalation
- Implements Erlang's error kernel pattern
- Easier to reason about failure domains
- Aligns with OTP supervision principles

#### Implementation Files

- New: `core/src/main/java/io/github/seonwkim/core/SpringSupervisor.java`
- New: `core/src/main/java/io/github/seonwkim/core/supervision/SupervisionStrategy.java`
- New: `core/src/main/java/io/github/seonwkim/core/supervision/SupervisorBuilder.java`
- Modify: `core/src/main/java/io/github/seonwkim/core/SpringActorSystem.java`
- Modify: `core/src/main/java/io/github/seonwkim/core/impl/DefaultRootGuardian.java`

---

### Enhancement #2: Supervision Strategies

**Status:** ❌ Missing
**Priority:** P1 - High
**Complexity:** High
**Reference:** RESEARCH.md Section 2 - Supervision Strategies

#### Problem

Only per-actor supervision exists. No group-level strategies:

```java
// Current: Each actor supervises itself
Behaviors.supervise(behavior)
    .

onFailure(SupervisorStrategy.restart());
```

Cannot express: "If actor A fails, restart A, B, and C together"

#### Erlang/Elixir Strategies

**One-for-One:**

```erlang
# Only restart failed child
Supervisor.start_link(children, strategy: :one_for_one)
```

Use when: Children are independent

**One-for-All:**

```erlang
# Restart all children when any fails
Supervisor.start_link(children, strategy: :one_for_all)
```

Use when: Children are interdependent (e.g., connection pool)

**Rest-for-One:**

```erlang
# Restart failed child and all started after it
Supervisor.start_link(children, strategy: :rest_for_one)
```

Use when: Later children depend on earlier ones (e.g., DB → Query → Cache)

#### Proposed Implementation

**1. Strategy API**

```java
public interface SupervisionStrategy {

    static OneForOne oneForOne() {
        return new OneForOne();
    }

    static OneForAll oneForAll() {
        return new OneForAll();
    }

    static RestForOne restForOne() {
        return new RestForOne();
    }

    /**
     * Maximum restarts within time window
     */
    SupervisionStrategy withMaxRestarts(int maxRestarts, Duration window);

    /**
     * What to do when max restarts exceeded
     */
    SupervisionStrategy onMaxRestartsExceeded(EscalationStrategy escalation);
}

public enum EscalationStrategy {
    STOP,           // Stop all children
    ESCALATE_UP,    // Fail to parent supervisor
    IGNORE          // Keep trying
}
```

**2. Supervisor Declaration**

```java

@Component
public class WorkerPoolSupervisor implements SpringSupervisor<Command> {

    @Override
    public SupervisionConfig supervisionConfig() {
        return SupervisionConfig.builder()
                .strategy(SupervisionStrategy.oneForAll()) // All restart together
                .maxRestarts(5, Duration.ofMinutes(1))
                .onMaxExceeded(EscalationStrategy.ESCALATE_UP)
                .build();
    }

    @Override
    public List<ChildSpec> children() {
        return List.of(
                ChildSpec.of(Worker1.class, "worker-1"),
                ChildSpec.of(Worker2.class, "worker-2"),
                ChildSpec.of(Worker3.class, "worker-3")
        );
    }
}
```

**3. Real-World Example: Database Connection Pool**

```java
// All workers share a connection pool
// If connection pool fails, all workers must restart
@Component
public class DatabaseWorkerSupervisor implements SpringSupervisor<Command> {

    @Override
    public SupervisionConfig supervisionConfig() {
        return SupervisionConfig.builder()
                .strategy(SupervisionStrategy.restForOne()) // Sequential dependency
                .build();
    }

    @Override
    public List<ChildSpec> children() {
        return List.of(
                ChildSpec.of(ConnectionPoolActor.class, "db-pool"),     // 1st
                ChildSpec.of(QueryExecutorActor.class, "query-exec"),   // 2nd - depends on pool
                ChildSpec.of(ResultCacheActor.class, "result-cache")    // 3rd - depends on executor
        );
        // If db-pool fails: restart db-pool, query-exec, result-cache
        // If query-exec fails: restart query-exec, result-cache
        // If result-cache fails: restart only result-cache
    }
}
```

#### Benefits

- Models real-world dependencies accurately
- Prevents inconsistent state across related actors
- Matches Erlang/OTP supervision model exactly
- Simplifies complex restart logic

#### Implementation Files

- New: `core/src/main/java/io/github/seonwkim/core/supervision/SupervisionStrategy.java`
- New: `core/src/main/java/io/github/seonwkim/core/supervision/OneForOne.java`
- New: `core/src/main/java/io/github/seonwkim/core/supervision/OneForAll.java`
- New: `core/src/main/java/io/github/seonwkim/core/supervision/RestForOne.java`
- New: `core/src/main/java/io/github/seonwkim/core/supervision/SupervisionConfig.java`

---

### Enhancement #3: Backpressure Support

**Status:** ❌ Missing
**Priority:** P0 - Critical
**Complexity:** Medium
**Reference:** RESEARCH.md Section 8 - Backpressure

#### Problem

Unbounded mailboxes can cause memory issues:

```java
// Current: No mailbox limits
// example/chat/src/main/java/io/github/seonwkim/example/ChatRoomActor.java:130
private void broadcastCommand(...) {
    connectedUsers.values().forEach(userRef -> userRef.tell(command));
    // What if a user's mailbox is full? → OOM
}
```

**Scenario:**

- 10,000 users in chat room
- One user has slow network
- Room broadcasts 100 messages/sec
- User's mailbox grows: 100 * 60 * 5 = 30,000 messages in 5 minutes
- Multiply by message size → Memory leak

#### Erlang/Elixir Pattern

```erlang
# Bounded mailbox
spawn_opt(Fun, [{message_queue_data, off_heap},
                {max_heap_size, #{size => 1000000}}])

# Process dies if mailbox exceeds limit
# Supervisor restarts with empty mailbox
```

Also provides:

- Selective receive (priority messages)
- Credit-based flow control
- Process groups with backpressure

#### Proposed Implementation

**1. Bounded Mailbox API**

```java
public class SpringActorSpawnBuilder<A, C> {

    /**
     * Set bounded mailbox with capacity
     */
    public SpringActorSpawnBuilder<A, C> withBoundedMailbox(int capacity) {
        return withBoundedMailbox(capacity, OverflowStrategy.DROP_NEWEST);
    }

    /**
     * Set bounded mailbox with overflow strategy
     */
    public SpringActorSpawnBuilder<A, C> withBoundedMailbox(
            int capacity,
            OverflowStrategy strategy
    ) {
        this.mailboxSelector = MailboxSelector.bounded(capacity);
        this.overflowStrategy = strategy;
        return this;
    }
}

public enum OverflowStrategy {
    DROP_NEWEST,    // Drop new messages when full
    DROP_OLDEST,    // Drop old messages when full
    FAIL,           // Fail the sender
    BACKPRESSURE    // Block the sender (use carefully!)
}
```

**2. Priority Mailbox**

```java
public class SpringActorSpawnBuilder<A, C> {

    /**
     * Use priority mailbox for control messages
     */
    public SpringActorSpawnBuilder<A, C> withPriorityMailbox(
            Comparator<C> priorityComparator
    ) {
        // Control messages processed before data messages
        return this;
    }
}

// Example: Always process shutdown messages first
actorSystem.

actor(WorkerActor .class)
    .

withId("worker-1")
    .

withPriorityMailbox((msg1, msg2) ->{
        if(msg1 instanceof Shutdown)return-1;
        if(msg2 instanceof Shutdown)return 1;
        return 0;
        })
        .

start();
```

**3. Flow Control Pattern**

```java
/**
 * Credit-based flow control for producer-consumer
 */
@Component
public class ConsumerActor implements SpringActor<ConsumerActor, Command> {

    private int credits = 100;

    @Override
    public Behavior<Command> create(SpringActorContext context) {
        return Behaviors.setup(ctx -> {
            // Request initial credits from producer
            producer.tell(new RequestCredits(100, ctx.getSelf()));

            return Behaviors.receive(Command.class)
                    .onMessage(DataMessage.class, msg -> {
                        credits--;
                        process(msg.data);

                        // Request more credits when low
                        if (credits < 20) {
                            producer.tell(new RequestCredits(80, ctx.getSelf()));
                            credits += 80;
                        }

                        return Behaviors.same();
                    })
                    .build();
        });
    }
}
```

**4. Configuration**

```yaml
spring:
  actor:
    default-mailbox:
      type: bounded
      capacity: 1000
      overflow-strategy: drop-newest

    # Per-actor configuration
    actors:
      UserActor:
        mailbox:
          type: bounded
          capacity: 500
          overflow-strategy: drop-oldest

      ChatRoomActor:
        mailbox:
          type: priority
          capacity: 1000
```

#### Benefits

- Prevents OOM from message buildup
- Provides natural backpressure mechanism
- Priority handling for control messages
- Matches Erlang's mailbox semantics

#### Implementation Files

- Modify: `core/src/main/java/io/github/seonwkim/core/SpringActorSpawnBuilder.java`
- New: `core/src/main/java/io/github/seonwkim/core/mailbox/OverflowStrategy.java`
- New: `core/src/main/java/io/github/seonwkim/core/mailbox/MailboxConfig.java`
- Modify: `core/src/main/java/io/github/seonwkim/core/ActorProperties.java`

---

### Enhancement #4: Restart Policies

**Status:** ❌ Missing
**Priority:** P1 - High
**Complexity:** Low
**Reference:** RESEARCH.md Section 3 - Restart Policies

#### Problem

All actors restart on failure. No way to specify "don't restart" or "restart only on abnormal exit".

#### Erlang/Elixir Policies

```erlang
# Permanent: Always restart (default)
child_spec(Worker, restart: :permanent)

# Temporary: Never restart (one-shot tasks)
child_spec(OneTimeTask, restart: :temporary)

# Transient: Restart only on abnormal exit (ignore normal shutdown)
child_spec(Worker, restart: :transient)
```

#### Proposed Implementation

```java
public enum RestartPolicy {
    PERMANENT,   // Always restart on failure
    TEMPORARY,   // Never restart (for one-shot tasks)
    TRANSIENT    // Restart only on abnormal termination
}

@Component
public class OneTimeTaskActor implements SpringActor<OneTimeTaskActor, Command> {

    @Override
    public RestartPolicy restartPolicy() {
        return RestartPolicy.TEMPORARY; // Don't restart when done
    }

    @Override
    public Behavior<Command> create(SpringActorContext context) {
        return Behaviors.receive(Command.class)
                .onMessage(ProcessTask.class, msg -> {
                    processTask(msg);
                    return Behaviors.stopped(); // Exit normally, no restart
                })
                .build();
    }
}
```

#### Configuration

```yaml
spring:
  actor:
    default-restart-policy: permanent
    actors:
      OneTimeTaskActor:
        restart-policy: temporary
      WorkerActor:
        restart-policy: transient
```

#### Implementation Files

- New: `core/src/main/java/io/github/seonwkim/core/supervision/RestartPolicy.java`
- Modify: `core/src/main/java/io/github/seonwkim/core/SpringActor.java`
- Modify: `core/src/main/java/io/github/seonwkim/core/impl/DefaultRootGuardian.java`

---

### Enhancement #5: State Recovery Patterns

**Status:** ⚠️ Manual Only
**Priority:** P2 - Medium
**Complexity:** High
**Reference:** RESEARCH.md Section 5 - State Recovery Patterns

#### Problem

Actors lose state on restart. No built-in support for:

- Event sourcing
- Snapshots
- State persistence

Users must implement recovery manually.

#### Erlang/Elixir Pattern

Uses Pekko Persistence (same backing technology):

```scala
class PersistentActor extends EventSourcedBehavior[Command, Event, State] {

  override def persistenceId: String = "user-" + userId

  override def commandHandler: CommandHandler = {
    case (state, cmd: CreateOrder) =>
      Effect.persist(OrderCreated(cmd.id))
  }

  override def eventHandler: EventHandler = {
    case (state, OrderCreated(id)) =>
      state.withOrder(id)
  }
}
```

#### Proposed Implementation

**1. Event Sourcing Support**

```java

@Component
public class OrderActor implements SpringEventSourcedActor<
        OrderActor.Command,
        OrderActor.Event,
        OrderActor.State> {

    @Override
    public String persistenceId(SpringActorContext context) {
        return "order-" + context.actorId();
    }

    @Override
    public State emptyState() {
        return new State(List.of());
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(CreateOrder.class, (state, cmd) ->
                        Effect().persist(new OrderCreated(cmd.orderId, cmd.items))
                )
                .onCommand(CancelOrder.class, (state, cmd) ->
                        Effect().persist(new OrderCancelled(cmd.orderId))
                )
                .build();
    }

    @Override
    public EventHandler<Event, State> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(OrderCreated.class, (state, event) ->
                        state.addOrder(event.orderId, event.items)
                )
                .onEvent(OrderCancelled.class, (state, event) ->
                        state.cancelOrder(event.orderId)
                )
                .build();
    }

    // Snapshots
    @Override
    public SnapshotPolicy snapshotPolicy() {
        return SnapshotPolicy.every(100); // Snapshot every 100 events
    }
}
```

**2. Configuration**

```yaml
spring:
  actor:
    persistence:
      journal:
        plugin: jdbc-journal
      snapshot-store:
        plugin: jdbc-snapshot-store

      jdbc:
        url: jdbc:postgresql://localhost:5432/actor_db
        user: actor_user
        password: secret
```

**3. State Recovery Strategies**

```java
public interface StateRecoveryStrategy {

    // Ephemeral: No persistence (default)
    static Ephemeral ephemeral() { ...}

    // Event Sourcing: Replay events
    static EventSourced eventSourced(String journalId) { ...}

    // Snapshots: Periodic checkpoints
    static Snapshots snapshots(Duration interval) { ...}

    // External: Store in database
    static External external(StateStore store) { ...}
}
```

#### Benefits

- CQRS/Event Sourcing support out of the box
- State survives restarts and node failures
- Audit trail for free (event log)
- Time travel debugging

#### Implementation Files

- New: `core/src/main/java/io/github/seonwkim/core/persistence/SpringEventSourcedActor.java`
- New: `core/src/main/java/io/github/seonwkim/core/persistence/CommandHandler.java`
- New: `core/src/main/java/io/github/seonwkim/core/persistence/EventHandler.java`
- New: `core/src/main/java/io/github/seonwkim/core/persistence/SnapshotPolicy.java`
- New: Module `persistence/` with JDBC/Cassandra support

---

### Enhancement #6: Error Kernel Pattern

**Status:** ❌ Not Documented
**Priority:** P2 - Medium
**Complexity:** Low
**Reference:** RESEARCH.md Section 6 - Error Kernel Pattern

#### Problem

No clear guidance on structuring supervision trees. Users don't know:

- Which actors should be supervisors
- What code should go in error kernel
- How to structure for fault tolerance

#### Erlang/Elixir Pattern

```
Application Root
├── Error Kernel (Never crashes)
│   ├── ConfigStore (permanent, simple)
│   └── Supervisor (minimal logic)
│
└── Worker Layer (Can crash freely)
    ├── BusinessLogicActor
    ├── ExternalAPIActor
    └── ComputationActor
```

**Error Kernel Rules:**

1. Minimal code
2. No complex logic
3. No external dependencies
4. Well-tested
5. Supervises all workers

#### Proposed Implementation

**1. Documentation & Example**

```java
/**
 * Example: Error Kernel Pattern
 *
 * Structure your actors in two layers:
 *
 * 1. Error Kernel: Critical, bulletproof actors
 *    - Configuration
 *    - Supervisors
 *    - Essential state
 *
 * 2. Worker Layer: Business logic actors (can fail)
 *    - HTTP clients
 *    - Database access
 *    - Computations
 */
@Component
public class ApplicationSupervisor implements SpringSupervisor<Command> {

    @Override
    public List<ChildSpec> children() {
        return List.of(
                // Error Kernel
                ChildSpec.of(ConfigActor.class, "config")
                        .withRestartPolicy(PERMANENT)
                        .withSupervision(oneForOne()),

                // Worker Supervisors
                ChildSpec.of(WorkerSupervisor.class, "workers")
                        .withRestartPolicy(PERMANENT)
                        .withSupervision(oneForAll())
        );
    }
}
```

**2. Validation & Warnings**

```java
// Warn if error kernel has external dependencies
@Component
public class ConfigActor implements SpringActor<ConfigActor, Command> {

    // ⚠️ WARNING: Error kernel actor with @Autowired external dependency
    @Autowired
    private ExternalService externalService; // DON'T DO THIS

    // ✅ GOOD: Only core dependencies
    @Autowired
    private ApplicationContext context;
}
```

#### Implementation Files

- New: `mkdocs/docs/guides/error-kernel-pattern.md`
- New: `example/error-kernel/` with reference implementation
- New: Validation in `ActorConfiguration.java`

---

### Enhancement #7: Configuration DSL

**Status:** ⚠️ Limited
**Priority:** P1 - Medium
**Complexity:** Medium
**Reference:** RESEARCH.md Section 2-3

#### Problem

Supervision configuration is code-only. Cannot:

- Configure supervision externally
- Change restart strategies without recompile
- Environment-specific supervision (dev vs prod)

#### Erlang/Elixir Pattern

```elixir
# config/config.exs
config :my_app, MyApp.Supervisor,
  strategy: :one_for_one,
  max_restarts: 5,
  max_seconds: 60,
  children: [
    {Worker1, restart: :permanent},
    {Worker2, restart: :transient}
  ]
```

#### Proposed Implementation

```yaml
spring:
  actor:
    supervision:
      default-strategy: one-for-one
      default-max-restarts: 5
      default-max-seconds: 60

      supervisors:
        application-root:
          strategy: one-for-one
          children:
            - class: io.github.seonwkim.example.ConfigActor
              name: config
              restart-policy: permanent

            - class: io.github.seonwkim.example.WorkerSupervisor
              name: workers
              strategy: one-for-all
              max-restarts: 10
              max-seconds: 60
              children:
                - class: io.github.seonwkim.example.Worker1
                  restart-policy: transient
                - class: io.github.seonwkim.example.Worker2
                  restart-policy: transient
```

#### Benefits

- Environment-specific supervision (strict in prod, lenient in dev)
- No recompile for strategy changes
- Easier operations management
- Matches Erlang/Elixir HOCON configuration

---

### Enhancement #8: Testing Infrastructure

**Status:** ❌ Missing
**Priority:** P0 - Critical
**Complexity:** Medium
**Reference:** RESEARCH.md Section 9 - Testing Strategies

#### Problem

No specialized testing tools. Current testing requires:

```java
// Manual integration testing
@Test
void testActor() throws Exception {
    CompletionStage<SpringActorRef<Command>> futureActor =
            actorSystem.getOrSpawn(MyActor.class, "test");
    SpringActorRef<Command> actor = futureActor.toCompletableFuture().get();

    // How to assert responses? No TestKit!
    actor.tell(new MyCommand());
    Thread.sleep(1000); // ❌ Terrible!
}
```

#### Erlang/Elixir Pattern

```elixir
# Proper actor testing
test "actor responds to messages" do
  {:ok, actor} = start_supervised(MyActor)

  # Send message
  send(actor, {:request, "data"})

  # Assert response
  assert_receive {:response, "result"}, 1000
end

# Supervision testing
test "actor restarts on failure" do
  {:ok, sup} = start_supervised(MySupervisor)

  # Kill child
  child = get_child(sup, :worker)
  Process.exit(child, :kill)

  # Assert restart
  assert get_child(sup, :worker) != child
end
```

#### Proposed Implementation

**1. SpringActorTestKit**

```java

@SpringActorTest
class MyActorTest {

    @Autowired
    private SpringActorTestKit testKit;

    @Test
    void testActorBehavior() {
        // Create test probe
        TestProbe<String> probe = testKit.createTestProbe();

        // Spawn actor
        ActorRef<Command> actor = testKit.spawn(MyActor.class, "test-actor");

        // Send message
        actor.tell(new GetValue(probe.ref()));

        // Assert response
        String response = probe.expectMessage(Duration.ofSeconds(1));
        assertEquals("expected-value", response);
    }

    @Test
    void testActorFailure() {
        TestProbe<String> probe = testKit.createTestProbe();
        ActorRef<Command> actor = testKit.spawn(MyActor.class, "test-actor");

        // Trigger failure
        actor.tell(new CauseError());

        // Verify actor restarted
        testKit.expectTerminated(actor, Duration.ofSeconds(1));

        // Actor should still respond after restart
        actor.tell(new GetValue(probe.ref()));
        probe.expectMessage(Duration.ofSeconds(1));
    }
}
```

**2. Supervision Testing**

```java

@Test
void testSupervisionStrategy() {
    // Spawn supervisor with children
    ActorRef<Command> supervisor = testKit.spawn(
            MySupervisor.class,
            "test-supervisor"
    );

    // Get child references
    ActorRef<Command> child1 = testKit.child(supervisor, "child-1");
    ActorRef<Command> child2 = testKit.child(supervisor, "child-2");

    // Kill child1
    testKit.kill(child1);

    // With one-for-all strategy, both should restart
    testKit.expectTerminated(child1);
    testKit.expectTerminated(child2);

    // Verify new instances
    ActorRef<Command> newChild1 = testKit.child(supervisor, "child-1");
    assertNotEquals(child1, newChild1);
}
```

**3. Chaos Testing**

```java

@SpringActorTest
class ChaosTest {

    @Test
    void testRandomActorFailures() {
        ChaosTestKit chaos = testKit.chaosTestKit();

        // Spawn system
        ActorRef<Command> system = testKit.spawn(MySystem.class, "system");

        // Random failures for 30 seconds
        chaos.randomKills(system,
                Duration.ofSeconds(30),
                killProbability = 0.1
        );

        // System should still be responsive
        TestProbe<String> probe = testKit.createTestProbe();
        system.tell(new HealthCheck(probe.ref()));
        assertEquals("healthy", probe.expectMessage());
    }
}
```

**4. Property-Based Testing**

```java

@SpringActorTest
class PropertyTest {

    @Property
    void actorShouldNeverLoseMessages(
            @ForAll List<Command> commands
    ) {
        TestProbe<Integer> probe = testKit.createTestProbe();
        ActorRef<Command> actor = testKit.spawn(CounterActor.class, "counter");

        // Send all commands
        commands.forEach(actor::tell);

        // Query final count
        actor.tell(new GetCount(probe.ref()));
        int count = probe.expectMessage();

        // Should equal number of commands
        assertEquals(commands.size(), count);
    }
}
```

#### Benefits

- Proper actor testing without hacks
- Supervision strategy verification
- Chaos testing for resilience
- Matches Pekko/Akka TestKit API
- Lowers barrier to adoption (developers need testing!)

#### Implementation Files

- New: `test/src/main/java/io/github/seonwkim/test/SpringActorTestKit.java`
- New: `test/src/main/java/io/github/seonwkim/test/TestProbe.java`
- New: `test/src/main/java/io/github/seonwkim/test/ChaosTestKit.java`
- New: `test/src/main/java/io/github/seonwkim/test/SpringActorTest.java` (annotation)

---

### Enhancement #9: Message Validation & Versioning

**Status:** ❌ Missing
**Priority:** P2 - Medium
**Complexity:** Medium
**Reference:** RESEARCH.md Section 4 - Message Design

#### Problem

No validation for messages:

```java
// What if userId is null? Empty? Invalid format?
public record CreateUser(String userId, String email) implements Command {
}
```

No message versioning strategy:

```java
// How to evolve messages over time?
// Old nodes with CreateUser(userId, email)
// New nodes with CreateUser(userId, email, phoneNumber)
```

#### Erlang/Elixir Pattern

```elixir
# Guards for validation
def handle_call({:create_user, user_id, email}, _from, state)
    when is_binary(user_id) and byte_size(user_id) > 0 do
  # Process...
end

# Message versioning with pattern matching
def handle_call({:create_user, v1, user_id, email}, ...) do
  # V1 message
end

def handle_call({:create_user, v2, user_id, email, phone}, ...) do
  # V2 message
end
```

#### Proposed Implementation

**1. Message Validation**

```java

@Component
public class UserActor implements SpringActor<UserActor, Command> {

    @Validated
    public sealed interface Command permits CreateUser, UpdateUser {
    }

    public record CreateUser(
            @NotNull @Pattern(regexp = "[a-z0-9-]+") String userId,
            @NotNull @Email String email
    ) implements Command {
    }

    @Override
    public Behavior<Command> create(SpringActorContext context) {
        return Behaviors.receive(Command.class)
                .onMessage(CreateUser.class, msg -> {
                    // Validation already performed by framework
                    createUser(msg.userId, msg.email);
                    return Behaviors.same();
                })
                .build();
    }
}

// Framework validates before delivering to actor
// Invalid messages rejected with ValidationException
```

**2. Message Versioning**

```java
public sealed interface Command {
    int version(); // All commands must declare version
}

// V1
public record CreateUserV1(
        String userId,
        String email
) implements Command {
    @Override
    public int version() {
        return 1;
    }
}

// V2 - added phone
public record CreateUserV2(
        String userId,
        String email,
        String phoneNumber
) implements Command {
    @Override
    public int version() {
        return 2;
    }
}

// Actor handles both versions
@Override
public Behavior<Command> create(SpringActorContext context) {
    return Behaviors.receive(Command.class)
            .onMessage(CreateUserV1.class, msg -> {
                createUser(msg.userId, msg.email, null); // no phone
                return Behaviors.same();
            })
            .onMessage(CreateUserV2.class, msg -> {
                createUser(msg.userId, msg.email, msg.phoneNumber);
                return Behaviors.same();
            })
            .build();
}
```

**3. Configuration**

```yaml
spring:
  actor:
    message-validation:
      enabled: true
      fail-fast: true  # Reject invalid messages immediately

    message-versioning:
      compatibility-mode: backward  # Support old message versions
      max-version-skew: 2           # Support up to 2 versions behind
```

#### Implementation Files

- New: `core/src/main/java/io/github/seonwkim/core/validation/MessageValidator.java`
- New: `core/src/main/java/io/github/seonwkim/core/validation/Validated.java`
- New: `core/src/main/java/io/github/seonwkim/core/versioning/VersionedMessage.java`

---

### Enhancement #10: Network Partition Handling

**Status:** ⚠️ Basic (Uses Pekko's SBR)
**Priority:** P3 - Low
**Complexity:** Medium
**Reference:** RESEARCH.md Section 7 - Network Partition Handling

#### Current State

Uses Pekko's Split Brain Resolver:

```yaml
# example/cluster/src/main/resources/application.yml:22
cluster:
  downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider
```

No documented patterns for:

- Detecting partitions in application code
- Reconciling state after partition heals
- Choosing consistency vs availability

#### Erlang/Elixir Pattern

```elixir
# Global name registration with partition handling
:global.register_name(:leader, self(),
  resolve: fn name, pid1, pid2 ->
    # Called when partition heals
    # Choose which process to keep
    if Node.self() < node(pid1), do: pid1, else: pid2
  end
)

# Mnesia transactions with majority quorum
:mnesia.transaction(fn ->
  # Requires majority of nodes to commit
  :mnesia.write({:user, id, data})
end,
  majority: true
)
```

#### Proposed Implementation

**1. Partition Detection**

```java

@Component
public class PartitionAwareActor implements SpringActor<Command> {

    @Override
    public Behavior<Command> create(SpringActorContext context) {
        return Behaviors.setup(ctx -> {
            // Subscribe to partition events
            ctx.getSystem().eventStream().subscribe(
                    ctx.getSelf(),
                    PartitionEvent.class
            );

            return behavior();
        });
    }

    private Behavior<Command> behavior() {
        return Behaviors.receive(Command.class)
                .onMessage(PartitionDetected.class, msg -> {
                    // Enter degraded mode
                    logger.warn("Partition detected: {}", msg.unreachableNodes());
                    return degradedBehavior();
                })
                .onMessage(PartitionHealed.class, msg -> {
                    // Reconcile state
                    reconcileState(msg.rejoiningNodes());
                    return normalBehavior();
                })
                .build();
    }
}
```

**2. State Reconciliation Strategies**

```java
public interface ReconciliationStrategy<T> {

    // Last Write Wins
    static <T extends Timestamped> LWW<T> lastWriteWins() { ...}

    // CRDTs
    static <T> CRDT<T> crdt(CRDTType type) { ...}

    // Custom merge logic
    static <T> Custom<T> custom(BiFunction<T, T, T> merger) { ...}

    // Prefer local (availability over consistency)
    static <T> PreferLocal<T> preferLocal() { ...}

    // Prefer majority (consistency over availability)
    static <T> PreferMajority<T> preferMajority() { ...}
}

// Usage
@Component
public class DistributedCacheActor implements SpringActor<Command> {

    @Override
    public ReconciliationStrategy<CacheEntry> reconciliationStrategy() {
        return ReconciliationStrategy.lastWriteWins();
    }
}
```

**3. Split-Brain Testing**

```java

@SpringActorTest
@EnableCluster(nodes = 5)
class PartitionTest {

    @Test
    void testSplitBrainRecovery() {
        // Create 2-3 partition
        testKit.partition(
                List.of("node1", "node2"),
                List.of("node3", "node4", "node5")
        );

        // Write to both sides
        node1Actor.tell(new Write("key", "value1"));
        node3Actor.tell(new Write("key", "value2"));

        // Heal partition
        testKit.healPartition();

        // Verify reconciliation
        String value = testKit.readConsistent("key");
        assertEquals("value2", value); // Majority side wins
    }
}
```

#### Implementation Files

- New: `core/src/main/java/io/github/seonwkim/core/partition/PartitionEvent.java`
- New: `core/src/main/java/io/github/seonwkim/core/partition/ReconciliationStrategy.java`
- New: `test/src/main/java/io/github/seonwkim/test/PartitionTestKit.java`
- New: `mkdocs/docs/guides/partition-handling.md`

---

## Implementation Roadmap

### Phase 1: Foundation (P0 - Critical for Production)

**Timeline:** 2-3 months

1. **Testing Infrastructure** (Enhancement #8)
    - Essential for validating all other enhancements
    - Enables TDD for actor development
    - Builds developer confidence

2. **Backpressure Support** (Enhancement #3)
    - Critical for production stability
    - Prevents OOM issues
    - Relatively contained implementation

3. **Hierarchical Supervision Trees** (Enhancement #1)
    - Core architectural improvement
    - Unlocks proper error handling
    - Required before supervision strategies

### Phase 2: Core Patterns (P1 - Erlang/Elixir Parity)

**Timeline:** 2-3 months

4. **Supervision Strategies** (Enhancement #2)
    - Depends on hierarchical trees
    - Core OTP pattern
    - High user demand

5. **Restart Policies** (Enhancement #4)
    - Simple implementation
    - Big impact on flexibility
    - Completes supervision model

6. **Configuration DSL** (Enhancement #7)
    - Makes supervision manageable
    - Operations-friendly
    - Enables environment-specific behavior

### Phase 3: Advanced Features (P2-P3)

**Timeline:** 3-4 months

7. **State Recovery Patterns** (Enhancement #5)
    - Event sourcing support
    - Persistence integration
    - Advanced use cases

8. **Error Kernel Pattern** (Enhancement #6)
    - Documentation and examples
    - Validation tooling
    - Best practices guide

9. **Message Validation** (Enhancement #9)
    - Safety and robustness
    - Version management
    - Production hygiene

10. **Partition Handling** (Enhancement #10)
    - Advanced clustering
    - State reconciliation
    - Split-brain testing

---

## Metrics for Success

### Adoption Metrics

- ⬆️ GitHub stars increase by 2x
- ⬆️ Production deployments (track via issues/discussions)
- ⬆️ Questions/answers on StackOverflow

### Technical Metrics

- ✅ 100% Erlang/OTP supervision pattern coverage
- ✅ Test coverage >85% for new features
- ✅ Zero P0 bugs in production use cases

### Community Metrics

- ⬆️ Contributors increase by 3x
- ⬆️ Documentation page views increase by 5x
- ⬆️ Example projects using the library

---

## Appendix: Comparison Matrix

| Feature                  | Erlang/OTP | Akka | Pekko | spring-boot-starter-actor (Current) | spring-boot-starter-actor (After Enhancements) |
|--------------------------|------------|------|-------|-------------------------------------|------------------------------------------------|
| Hierarchical Supervision | ✅          | ✅    | ✅     | ❌                                   | ✅                                              |
| Supervision Strategies   | ✅          | ✅    | ✅     | ❌                                   | ✅                                              |
| Restart Policies         | ✅          | ✅    | ✅     | ❌                                   | ✅                                              |
| Backpressure             | ✅          | ✅    | ✅     | ❌                                   | ✅                                              |
| Event Sourcing           | ✅          | ✅    | ✅     | ❌                                   | ✅                                              |
| Testing Tools            | ✅          | ✅    | ✅     | ⚠️                                  | ✅                                              |
| Spring Integration       | ❌          | ⚠️   | ⚠️    | ✅                                   | ✅                                              |
| Auto-Configuration       | ❌          | ❌    | ❌     | ✅                                   | ✅                                              |
| Type Safety              | ✅          | ✅    | ✅     | ✅                                   | ✅                                              |
| Documentation            | ✅          | ✅    | ✅     | ⚠️                                  | ✅                                              |

**Legend:**

- ✅ Full support
- ⚠️ Partial support
- ❌ Not supported

---

## References

1. **RESEARCH.md** - Best practices from Erlang/Elixir
2. **IDEAS.md** - Use case ideas for the library
3. **Erlang OTP Design Principles**: https://www.erlang.org/doc/design_principles/
4. **Designing for Scalability with Erlang/OTP** by Francesco Cesarini
5. **Akka Documentation**: https://doc.akka.io/
6. **Pekko Documentation**: https://pekko.apache.org/docs/

---

## Conclusion

The spring-boot-starter-actor project has a solid foundation with Spring integration and basic actor support. However,
to truly embrace the Erlang/Elixir philosophy and provide production-grade actor systems, **10 critical enhancements**
are needed.

**Immediate Action Items:**

1. **Testing Infrastructure** (P0) - Enables everything else
2. **Backpressure** (P0) - Prevents production issues
3. **Hierarchical Supervision** (P0) - Core architectural improvement

These three enhancements will move the library from "interesting experiment" to "production-ready alternative" to
traditional Spring patterns.

The long-term goal is **100% parity with Erlang/OTP supervision patterns** while maintaining the ease-of-use that Spring
developers expect.
