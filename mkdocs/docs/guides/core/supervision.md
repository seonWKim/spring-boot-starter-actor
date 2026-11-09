# Supervision

Supervision decides what happens to an actor when one of its handlers throws an exception. Instead of
try/catch around every handler, you choose a strategy once: restart the actor with fresh state, keep it
running and skip the message, or stop it. A failure then stays inside that one actor, and the rest of
the application keeps working.

Use supervision for failures you can't handle where they happen: a bug, a corrupted in-memory state, a
dependency that throws. For expected outcomes (a rejected payment, an invalid input), reply with a
result instead (see [Errors in the handler](messaging.md#errors-in-the-handler)).

## Strategies

`SupervisionStrategy` has three strategies:

| Strategy | What happens after a failure | Use it when |
|---|---|---|
| `SupervisionStrategy.stop()` | The actor stops. **This is the default** when no strategy is set. | A failed actor should not keep going; a caller or parent will create a new one if needed |
| `SupervisionStrategy.restart()` | The actor starts again with fresh state, under the same id and ref. No limit. | The state can be rebuilt (from a database, a snapshot, or from nothing) |
| `SupervisionStrategy.restart().withLimit(n, window)` | Restarts, but stops the actor once it has restarted `n` times within `window` | Most restarts in production: it stops an actor that fails on every message |
| `SupervisionStrategy.resume()` | The actor keeps its state and goes on with the next message. The failed message is dropped. | The failure doesn't damage the state, and losing one message is fine |

!!! note "Default: stop"
    With no strategy, a failing actor stops. Earlier versions of these docs said the default was restart;
    it is stop.

The message that failed is never retried, whatever the strategy. If the sender asked, it gets no reply
and sees a timeout.

## Where to set the strategy

There are four places. Set it in one of them per actor.

=== "Java"

    ```java
    // 1. When spawning a top-level actor
    actorSystem.actor(OrderActor.class)
            .withId("order-42")
            .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(3, Duration.ofMinutes(1)))
            .spawn();

    // 2. In the actor's own behavior: applies however the actor is spawned
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, Duration.ofMinutes(1)))
                .withState(ctx -> new OrderState(ctx))
                .onMessage(Pay.class, OrderState::onPay)
                .build();
    }

    // 3. When a parent spawns a child
    ctx.getSelf().child(WorkerActor.class)
            .withId("worker-1")
            .withSupervisionStrategy(SupervisionStrategy.resume())
            .spawn();

    // 4. For the workers of a router
    SpringRouterBehavior.builder(Command.class, actorContext)
            .withRoutingStrategy(RoutingStrategy.roundRobin())
            .withWorkerActors(WorkerActor.class)
            .withSupervisionStrategy(SupervisionStrategy.restart())
            .build();
    ```

=== "Kotlin"

    ```kotlin
    // 1. When spawning a top-level actor (in a suspend function)
    actorSystem.actor<OrderActor, _>()
        .withId("order-42")
        .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(3, 1.minutes.toJavaDuration()))
        .awaitSpawn()

    // 2. In the actor's own behavior: applies however the actor is spawned
    override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
        actorBehavior<Command>(actorContext)
            .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, 1.minutes.toJavaDuration()))
            .withState { ctx -> OrderState(ctx) }
            .onMessage(Pay::class.java) { state, msg -> state.onPay(msg) }
            .build()

    // 3. When a parent spawns a child
    ctx.self.child(WorkerActor::class.java)
        .withId("worker-1")
        .withSupervisionStrategy(SupervisionStrategy.resume())
        .spawn()

    // 4. For the workers of a router
    SpringRouterBehavior.builder(Command::class.java, actorContext)
        .withRoutingStrategy(RoutingStrategy.roundRobin())
        .withWorkerActors(WorkerActor::class.java)
        .withSupervisionStrategy(SupervisionStrategy.restart())
        .build()
    ```

Which one to pick:

- Put it **in the behavior** (2) when the strategy belongs to the actor itself, for example "this actor
  reloads its state on restart, so restarting is always safe". Every spawn then gets it.
- Put it **at the spawn site** (1, 3) when the caller decides, for example a parent that treats some
  children as disposable.
- Sharded actors set it in their behavior (2); see [Sharded actors](../cluster/sharded-actors.md).

## What a restart keeps and what it loses

A restart builds the actor's behavior again from its `withState` factory. `create` is not called again.

| Kept | Lost |
|---|---|
| The actor's id, path and ref: handles held by others keep working | The state object: the `withState` factory runs again and builds a new one |
| Messages already waiting in the mailbox | The message that failed |
| The actor's dispatcher, mailbox settings, tags and MDC | Timers: they are cancelled. Timers started in the `withState` factory start again because it runs again. |
| The Spring bean and its injected dependencies | Child actors: Pekko stops them when the parent restarts |

So anything an actor must not lose on restart has to live outside the state object: in a database, a
snapshot, or an [event-sourced journal](../data/event-sourcing.md). The `AgentBrain` of the
[agent example](../../examples/agent.md) restarts up to 10 times a minute and reloads its conversation
from the last snapshot in its constructor, which the `withState` factory calls on every start:

=== "Java"

    ```java
    return builder.withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, Duration.ofMinutes(1)))
            .withState(ctx -> new AgentBrain(ctx, agentId, dependencies))   // reloads the snapshot
            .onMessage(AgentActor.Chat.class, AgentBrain::onChat)
            .build();
    ```

=== "Kotlin"

    ```kotlin
    builder.withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, 1.minutes.toJavaDuration()))
        .withState { ctx -> AgentBrain(ctx, agentId, dependencies) }   // reloads the snapshot
        .onMessage(AgentActor.Chat::class.java) { brain, chat -> brain.onChat(chat) }
        .build()
    ```

## Reacting to restarts and stops

Two signals tell an actor about its own lifecycle. Register them with `onSignal`:

- `PreRestart` arrives just before a restart. Release what the old state holds (open files,
  subscriptions) or log what is lost.
- `PostStop` arrives after the actor stopped, whether a handler returned `stopped()`, someone called
  `stop()`, or supervision stopped it.

=== "Java"

    ```java
    return SpringActorBehavior.builder(Command.class, actorContext)
            .withSupervisionStrategy(SupervisionStrategy.restart())
            .withState(ctx -> new WorkerState(ctx))
            .onMessage(DoTask.class, WorkerState::onDoTask)
            .onSignal(PreRestart.class, (state, signal) -> {
                state.ctx().getLog().warn("Restarting; {} completed tasks are lost", state.completed());
                return SpringActorBehavior.same();
            })
            .onSignal(PostStop.class, (state, signal) -> {
                state.ctx().getLog().info("Stopped after {} tasks", state.completed());
                return SpringActorBehavior.same();
            })
            .build();
    ```

=== "Kotlin"

    ```kotlin
    actorBehavior<Command>(actorContext)
        .withSupervisionStrategy(SupervisionStrategy.restart())
        .withState { ctx -> WorkerState(ctx) }
        .onMessage(DoTask::class.java) { state, msg -> state.onDoTask(msg) }
        .onSignal(PreRestart::class.java) { state, _ ->
            state.ctx.log.warn("Restarting; {} completed tasks are lost", state.completed)
            SpringActorBehavior.same()
        }
        .onSignal(PostStop::class.java) { state, _ ->
            state.ctx.log.info("Stopped after {} tasks", state.completed)
            SpringActorBehavior.same()
        }
        .build()
    ```

Keep signal handlers short and don't throw from them. More on signals in
[Behaviors](behaviors.md#lifecycle-signals).

## Supervising children

A parent chooses a strategy for each child when it spawns it (place 3 above). A child's failure does not
fail the parent: with `stop`, the child just stops. To find out, the parent
[watches](behaviors.md#watching-other-actors) the child and handles the `ActorTerminated` signal, for
example to spawn a replacement or to tell a caller:

=== "Java"

    ```java
    .onMessage(WorkerReady.class, (ctx, msg) -> {
        ctx.watch(msg.worker());              // ActorTerminated arrives when the worker stops
        return SpringActorBehavior.same();
    })
    .onSignal(ActorTerminated.class, (ctx, signal) -> {
        ctx.getLog().warn("Worker {} stopped", signal.ref().path());
        return SpringActorBehavior.same();
    })
    ```

=== "Kotlin"

    ```kotlin
    .onMessage(WorkerReady::class.java) { ctx, msg ->
        ctx.watch(msg.worker)                  // ActorTerminated arrives when the worker stops
        SpringActorBehavior.same()
    }
    .onSignal(ActorTerminated::class.java) { ctx, signal ->
        ctx.log.warn("Worker {} stopped", signal.ref.path())
        SpringActorBehavior.same()
    }
    ```

Common choices by role:

| Child role | Strategy |
|---|---|
| Stateless worker | `restart()` |
| Worker in production that might fail on every message | `restart().withLimit(3, Duration.ofMinutes(1))` |
| Validator or one-shot task that should fail fast | `stop()` |
| Logger or metrics forwarder where one lost message is fine | `resume()` |

## Pitfalls

- **Relying on the default.** With no strategy, the first exception stops the actor, and later messages
  to it are lost. Set a strategy on every long-lived actor.
- **Unlimited restarts.** `restart()` without a limit restarts an actor that fails on every message
  forever. Prefer `withLimit`.
- **State that must survive.** A restart throws the state object away. Persist what matters.
- **`resume` on damaged state.** If the exception left the state half-updated, `resume` keeps the
  half-updated state.
- **Errors meant for the caller.** The asker of a failed message only sees a timeout. Reply with an
  error result for expected failures.

## Related

- [Actors](actors.md): spawning actors and children
- [Behaviors](behaviors.md): `withState`, signals and watching
- [Messaging](messaging.md): replies, timeouts and errors
- [Event sourcing](../data/event-sourcing.md): state that survives restarts and node failures
