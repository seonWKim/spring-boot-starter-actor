# Behaviors

An actor's behavior says how it handles each message. You build it in `create` with
`SpringActorBehavior.builder(...)` (or `actorBehavior<C>(...)` in Kotlin). This page covers what you can
do inside a behavior: keep state, choose the next behavior, react to lifecycle signals, schedule
messages with timers, watch other actors, and turn futures into messages.

For defining and spawning actors see [Actors](actors.md); for sending messages see
[Messaging](messaging.md).

## The builder

| Builder method | What it does |
|---|---|
| `withState(ctx -> state)` | Creates the actor's state object when the actor starts (and again on each restart). Handlers then receive the state instead of the context. |
| `onMessage(Type.class, (state, msg) -> next)` | Handles messages of `Type`. Handlers are tried in the order they were added; the first whose type matches runs. |
| `onSignal(Signal.class, (state, signal) -> next)` | Handles a lifecycle signal: `PreRestart`, `PostStop` or `ActorTerminated`. |
| `withSupervisionStrategy(strategy)` | What happens when a handler throws. See [Supervision](supervision.md). |
| `withMdc(msg -> map)` | Log context computed per message. See [Logging](logging.md). |
| `build()` | Returns the behavior. |

A message with no matching handler is *unhandled*: it is dropped (and published as unhandled), and the
actor keeps going.

## State with withState

Without `withState`, every handler receives the behavior context (`ctx`). With it, the handlers receive
your own state object, which you usually give a reference to the context. Put the actor's fields and its
handler methods in that object:

=== "Java"

    ```java
    @Component
    public class CounterActor implements SpringActor<CounterActor.Command> {

        public interface Command {}

        public record Add(long amount) implements Command {}

        public static class Get extends AskCommand<Long> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new Counter(ctx, actorContext.actorId()))
                    .onMessage(Add.class, Counter::onAdd)
                    .onMessage(Get.class, Counter::onGet)
                    .build();
        }

        private static class Counter {
            private final SpringBehaviorContext<Command> ctx;
            private final String id;
            private long value;   // only this actor's thread touches it

            Counter(SpringBehaviorContext<Command> ctx, String id) {
                this.ctx = ctx;
                this.id = id;
                ctx.getLog().info("Counter {} started", id);
            }

            SpringActorBehavior<Command> onAdd(Add msg) {
                value += msg.amount();
                return SpringActorBehavior.same();
            }

            SpringActorBehavior<Command> onGet(Get msg) {
                msg.reply(value);
                return SpringActorBehavior.same();
            }
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class CounterActor : SpringActor<CounterActor.Command> {

        sealed interface Command

        data class Add(val amount: Long) : Command

        class Get : AskCommand<Long>(), Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .withState { ctx -> Counter(ctx, actorContext.actorId()) }
                .onMessage(Add::class.java) { counter, msg -> counter.onAdd(msg) }
                .onMessage(Get::class.java) { counter, msg -> counter.onGet(msg) }
                .build()

        private class Counter(private val ctx: SpringBehaviorContext<Command>, private val id: String) {
            private var value = 0L   // only this actor's thread touches it

            init {
                ctx.log.info("Counter {} started", id)
            }

            fun onAdd(msg: Add): SpringActorBehavior<Command> {
                value += msg.amount
                return SpringActorBehavior.same()
            }

            fun onGet(msg: Get): SpringActorBehavior<Command> {
                msg.reply(value)
                return SpringActorBehavior.same()
            }
        }
    }
    ```

The state object belongs to one actor and is only touched while that actor handles a message, so plain
fields need no `volatile` or locks. Don't hand the object (or its mutable collections) to other threads.

The `withState` factory runs inside the actor when it starts, before the first message. It is the place
to load initial data, start timers, register with the [receptionist](receptionist.md) or spawn children.
On a [restart](supervision.md#what-a-restart-keeps-and-what-it-loses) it runs again and builds a fresh
object.

## What a handler returns

Each handler returns the behavior for the next message:

| Return | Meaning |
|---|---|
| `SpringActorBehavior.same()` | Keep handling messages the same way. The usual return. |
| `SpringActorBehavior.stopped()` | Stop the actor after this message. Its `PostStop` handler runs. |
| `SpringActorBehavior.unhandled()` | This handler didn't handle the message after all. The message is published as unhandled; the behavior stays. |
| A new behavior from `SpringActorBehavior.builder(...).build()` | Handle the next messages with this behavior instead. |

Returning a new behavior lets an actor change how it reacts, for example a door that ignores `Open` while
locked. Each built behavior has its own handlers and its own state:

=== "Java"

    ```java
    @Component
    public class DoorActor implements SpringActor<DoorActor.Command> {

        public interface Command {}
        public record Lock() implements Command {}
        public record Unlock() implements Command {}
        public static class IsLocked extends AskCommand<Boolean> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return unlocked(actorContext);
        }

        private static SpringActorBehavior<Command> unlocked(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(Lock.class, (ctx, msg) -> locked(actorContext))
                    .onMessage(IsLocked.class, (ctx, msg) -> {
                        msg.reply(false);
                        return SpringActorBehavior.same();
                    })
                    .build();
        }

        private static SpringActorBehavior<Command> locked(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(Unlock.class, (ctx, msg) -> unlocked(actorContext))
                    .onMessage(IsLocked.class, (ctx, msg) -> {
                        msg.reply(true);
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class DoorActor : SpringActor<DoorActor.Command> {

        sealed interface Command
        class Lock : Command
        class Unlock : Command
        class IsLocked : AskCommand<Boolean>(), Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            unlocked(actorContext)

        private fun unlocked(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .onMessage(Lock::class.java) { _, _ -> locked(actorContext) }
                .onMessage(IsLocked::class.java) { _, msg ->
                    msg.reply(false)
                    SpringActorBehavior.same()
                }
                .build()

        private fun locked(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .onMessage(Unlock::class.java) { _, _ -> unlocked(actorContext) }
                .onMessage(IsLocked::class.java) { _, msg ->
                    msg.reply(true)
                    SpringActorBehavior.same()
                }
                .build()
    }
    ```

In the locked behavior, `Lock` has no handler, so a second `Lock` is unhandled and dropped. For many
actors a status field in one state object is simpler; switch behaviors when the states really accept
different messages.

## Lifecycle signals

Signals are events about the actor's life, not messages from others. Handle them with `onSignal`:

| Signal | When it arrives | Typical use |
|---|---|---|
| `PreRestart` | Just before a supervised restart | Release resources the old state holds; log what is lost |
| `PostStop` | After the actor stopped (for any reason) | Close resources, deregister, log final state |
| `ActorTerminated` | An actor this one [watches](#watching-other-actors) has stopped; `signal.ref()` is that actor | Replace a worker, clean up a session |

An example with `PreRestart` and `PostStop` is in [Supervision](supervision.md#reacting-to-restarts-and-stops).
A signal handler returns the next behavior like a message handler; return `same()`.

## Timers

A timer sends a message to the actor itself, once or repeatedly. Use timers for timeouts, retries,
polling and periodic work. Each timer has a key (any object); starting a timer with a key that is
already active replaces it.

| Method on the context | What it does |
|---|---|
| `startSingleTimer(key, message, delay)` | Sends `message` once after `delay` |
| `startTimerAtFixedRate(key, message, interval)` | Sends `message` every `interval` until cancelled |
| `cancelTimer(key)` | Cancels the timer; a message it already sent is not delivered |

=== "Java"

    ```java
    @Component
    public class SessionActor implements SpringActor<SessionActor.Command> {

        public interface Command {}
        public record Touch() implements Command {}
        private enum IdleTimeout implements Command { INSTANCE }
        private enum Flush implements Command { INSTANCE }

        private static final Duration IDLE = Duration.ofMinutes(30);

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> {
                        ctx.startTimerAtFixedRate("flush", Flush.INSTANCE, Duration.ofSeconds(10));
                        ctx.startSingleTimer("idle", IdleTimeout.INSTANCE, IDLE);
                        return ctx;
                    })
                    .onMessage(Touch.class, (ctx, msg) -> {
                        ctx.startSingleTimer("idle", IdleTimeout.INSTANCE, IDLE);   // restart the idle clock
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Flush.class, (ctx, msg) -> {
                        ctx.getLog().debug("Flushing");
                        return SpringActorBehavior.same();
                    })
                    .onMessage(IdleTimeout.class, (ctx, msg) -> SpringActorBehavior.stopped())
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class SessionActor : SpringActor<SessionActor.Command> {

        sealed interface Command
        class Touch : Command
        private data object IdleTimeout : Command
        private data object Flush : Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .withState { ctx ->
                    ctx.startTimerAtFixedRate("flush", Flush, 10.seconds.toJavaDuration())
                    ctx.startSingleTimer("idle", IdleTimeout, IDLE.toJavaDuration())
                    ctx
                }
                .onMessage(Touch::class.java) { ctx, _ ->
                    ctx.startSingleTimer("idle", IdleTimeout, IDLE.toJavaDuration())   // restart the idle clock
                    SpringActorBehavior.same()
                }
                .onMessage(Flush::class.java) { ctx, _ ->
                    ctx.log.debug("Flushing")
                    SpringActorBehavior.same()
                }
                .onMessage(IdleTimeout::class.java) { _, _ -> SpringActorBehavior.stopped() }
                .build()

        private companion object {
            val IDLE = 30.minutes
        }
    }
    ```

Timers belong to the actor: they stop when it stops and are cancelled when it restarts. Timer messages
go through the mailbox like any other message, so they run one at a time with the rest.

## Watching other actors

`ctx.watch(ref)` asks to be told when another actor stops. When it does, this actor receives the
`ActorTerminated` signal with the stopped actor's ref:

=== "Java"

    ```java
    .onMessage(Track.class, (ctx, msg) -> {
        ctx.watch(msg.worker());
        return SpringActorBehavior.same();
    })
    .onSignal(ActorTerminated.class, (ctx, signal) -> {
        ctx.getLog().info("{} stopped", signal.ref().path());
        return SpringActorBehavior.same();
    })
    ```

=== "Kotlin"

    ```kotlin
    .onMessage(Track::class.java) { ctx, msg ->
        ctx.watch(msg.worker)
        SpringActorBehavior.same()
    }
    .onSignal(ActorTerminated::class.java) { ctx, signal ->
        ctx.log.info("{} stopped", signal.ref.path())
        SpringActorBehavior.same()
    }
    ```

!!! warning "Always handle ActorTerminated when you watch"
    An actor that watches another and has no `onSignal(ActorTerminated.class, ...)` handler **fails**
    when the watched actor stops, and its supervision strategy applies (stop, by default).

The watched actor can be anywhere: a child, an unrelated actor, or (in cluster mode) an actor on another
node; it then also counts as stopped when its node leaves the cluster. The chat example's `UserActor`
watches the actor that represents the WebSocket client so it can clean up when the client goes away.

## Futures: pipeToSelf

Handlers must not block, and callbacks on a `CompletionStage` run on another thread, where they must not
touch the actor's state. `ctx.pipeToSelf(stage, (value, error) -> message)` solves both: when the stage
completes, the function builds a message, and the actor handles it like any other.

=== "Java"

    ```java
    .onMessage(Load.class, (state, msg) -> {
        state.ctx().pipeToSelf(
                profileClient.fetch(msg.userId()),        // CompletionStage<Profile>
                (profile, error) -> new Loaded(profile, error));
        return SpringActorBehavior.same();
    })
    .onMessage(Loaded.class, (state, msg) -> state.onLoaded(msg))   // update state here
    ```

=== "Kotlin"

    ```kotlin
    .onMessage(Load::class.java) { state, msg ->
        state.ctx.pipeToSelf(profileClient.fetch(msg.userId)) { profile, error ->   // CompletionStage<Profile>
            Loaded(profile, error)
        }
        SpringActorBehavior.same()
    }
    .onMessage(Loaded::class.java) { state, msg -> state.onLoaded(msg) }   // update state here
    ```

Exactly one of `value` and `error` is non-null. More examples, including asking another actor, are in
[Messaging](messaging.md#asking-from-inside-an-actor).

## The behavior context

The context (`SpringBehaviorContext<C>`) is what an actor can do from its handlers. You get it in the
`withState` factory, and as the first handler argument when there is no `withState`.

| Method | What it does |
|---|---|
| `getSelf()` | A handle to this actor: pass it as a reply address, or use it for [child operations](actors.md#child-actors) |
| `getLog()` | The actor's logger; its lines carry the actor's path and MDC (see [Logging](logging.md)) |
| `path()`, `nodeAddress()` | The actor's path and the address of the node it runs on |
| `getChildren()`, `getChild(name)` | The running children (`getChild` takes the internal name `<ClassName>:<id>`; prefer `getSelf().child(Class, id)`) |
| `stop(child)` | Stops one of this actor's children |
| `startSingleTimer`, `startTimerAtFixedRate`, `cancelTimer` | [Timers](#timers) |
| `watch(ref)` | [Watching](#watching-other-actors) |
| `pipeToSelf(stage, fn)` | [Futures](#futures-pipetoself) |
| `messageAdapter(Type.class, fn)` | A ref that accepts another actor's reply type and delivers it as one of this actor's commands. See [Messaging](messaging.md#replying-to-an-actor-without-ask). |
| `registerWithReceptionist(name)` | Registers this actor under a service name. See [Receptionist](receptionist.md). |
| `unwrap(Class)` | The underlying Pekko `ActorContext`, for features the starter doesn't wrap |

Use the context only on the actor's own thread: inside handlers and the `withState` factory, never in a
future callback.

## Stashing and other Pekko features

The starter has no stash (holding messages back until the actor is ready). The usual replacement is a
queue in the state object, as the `AgentBrain` of the [agent example](../../examples/agent.md) does: while
it is busy it adds new `Chat` messages to a `Deque` and takes the next one when the turn ends.

For a Pekko feature the starter doesn't wrap, there are two escape hatches. Code that uses them depends
on Pekko directly and gets no compatibility promise from the starter.

- `ctx.unwrap(org.apache.pekko.actor.typed.javadsl.ActorContext.class)` returns Pekko's context.
- `SpringActorBehavior.wrap(pekkoBehavior)` runs a behavior written against Pekko's API. Such a behavior
  gets none of the starter's features (child operations, MDC, metrics hooks).

## Pitfalls

- **Blocking in a handler.** Use `pipeToSelf` for futures, and the blocking dispatcher for blocking I/O.
- **Touching state from a callback.** `thenAccept` on a future runs on another thread. Send a message
  with `pipeToSelf` instead.
- **Watching without handling `ActorTerminated`.** The watcher fails when the watched actor stops.
- **Heavy work in `withState`.** The actor handles no message until the factory returns. Load large data asynchronously with `pipeToSelf`.
- **Handler order.** The first handler whose type matches wins. Register specific types before their
  supertypes.

## Related

- [Actors](actors.md): defining, spawning and stopping actors
- [Messaging](messaging.md): tell, ask and message adapters
- [Supervision](supervision.md): restarts and what they reset
- [Logging](logging.md): per-message MDC with `withMdc`
- [Testing](../testing.md): testing behaviors
