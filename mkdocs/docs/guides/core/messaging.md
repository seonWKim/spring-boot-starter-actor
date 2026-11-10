# Messaging

Actors talk only through messages. There are two ways to send one:

- **tell**: send and move on. Nothing comes back. Use it for commands and events.
- **ask**: send and get a `CompletionStage` that completes with the actor's reply. Use it when the
  caller needs an answer, for example to build an HTTP response.

This page covers both, how replies work, what happens on timeouts and errors, how actors reply to each
other without ask, and how to design messages. Defining and spawning actors is in [Actors](actors.md).

## Message rules

- **One message at a time.** An actor handles its messages one after another, in the order they arrive
  in its mailbox. Messages from one sender to one actor arrive in the order they were sent.
- **At most once.** A message is delivered once or not at all. Messages to an actor that has stopped are
  dropped (they go to "dead letters"). If a message must not be lost, have the receiver confirm it (ask)
  and retry, or store it first (see [Event sourcing](../data/event-sourcing.md)).
- **Immutable messages.** The sender and the actor may run on different threads. Use records,
  `final` fields or Kotlin `val`s, and immutable collections (`List.copyOf`).
- **Serializable in a cluster.** A message that can cross nodes must implement `JsonSerializable` or
  `CborSerializable` (package `io.github.seonwkim.actor.serialization`). In local mode messages are
  passed as objects and need neither. See [Cluster formation](../cluster/cluster-formation.md).

## Tell

`tell` puts the message in the actor's mailbox and returns at once:

=== "Java"

    ```java
    actorSystem.getOrSpawn(GreeterActor.class, "greeter")
            .thenAccept(greeter -> greeter.tell(new GreeterActor.LogVisit("Kim")));
    ```

=== "Kotlin"

    ```kotlin
    // In a suspend function
    actorSystem.awaitGetOrSpawn<GreeterActor, _>("greeter")
        .tell(GreeterActor.LogVisit("Kim"))
    ```

`tell` never fails and never waits. The caller doesn't learn whether the message was handled. If it
needs to know, use ask.

## Ask: request and reply

A message that expects an answer extends `AskCommand<R>`, where `R` is the answer's type. The handler
answers with `msg.reply(value)`:

=== "Java"

    ```java
    public static class GetBalance extends AskCommand<Long> implements Command {
        public final String accountId;

        public GetBalance(String accountId) {
            this.accountId = accountId;
        }
    }

    // In create():
    .onMessage(GetBalance.class, (state, msg) -> {
        msg.reply(state.balance());
        return SpringActorBehavior.same();
    })
    ```

=== "Kotlin"

    ```kotlin
    class GetBalance(val accountId: String) : AskCommand<Long>(), Command

    // In create():
    .onMessage(GetBalance::class.java) { state, msg ->
        msg.reply(state.balance())
        SpringActorBehavior.same()
    }
    ```

The caller sends it with `ask`, sets a timeout, and calls `execute()`:

=== "Java"

    ```java
    CompletionStage<Long> balance = account
            .ask(new AccountActor.GetBalance("acc-1"))
            .withTimeout(Duration.ofSeconds(2))
            .execute();
    ```

=== "Kotlin"

    ```kotlin
    // In a suspend function: suspends until the reply arrives
    val balance: Long = account.awaitAsk(AccountActor.GetBalance("acc-1"), timeout = 2.seconds)
    ```

In a WebFlux controller, return `Mono.fromCompletionStage(balance)` (Java) or make the handler a
`suspend fun` (Kotlin).

How ask works: the starter creates a short-lived reply address, stores it in the command
(`getReplyTo()`), and sends the command with `tell`. `reply(value)` sends the value to that address, and
the `CompletionStage` completes.

### Timeouts

Every ask has a timeout. When no reply arrives in time, the stage fails with
`java.util.concurrent.TimeoutException`.

| Where the handle came from | Default ask timeout |
|---|---|
| `spawn`, `getOrSpawn`, `find`, `exists`, a child builder, `ctx.getSelf()` | 3 s |

Set your own with `withTimeout(Duration)`. Pick a timeout that fits the caller (an HTTP request's
budget), not the actor.

To return a fallback value instead of failing, add `onTimeout`:

=== "Java"

    ```java
    CompletionStage<Long> balance = account
            .ask(new AccountActor.GetBalance("acc-1"))
            .withTimeout(Duration.ofSeconds(2))
            .onTimeout(() -> -1L)
            .execute();
    ```

=== "Kotlin"

    ```kotlin
    // In a suspend function
    val balance: Long = account
        .ask(AccountActor.GetBalance("acc-1"))
        .withTimeout(2.seconds)
        .onTimeout { -1L }
        .await()
    ```

`onTimeout` only handles timeouts. Any other failure still fails the stage.

When you block on the stage (`.toCompletableFuture().get()`), the `TimeoutException` arrives wrapped in an
`ExecutionException`; inside `thenCompose`/`exceptionally` chains it can arrive wrapped in a
`CompletionException`. Check `getCause()`. In Kotlin, `awaitAsk` and `await()` throw the
`TimeoutException` itself.

### Errors in the handler

If the handler throws, no reply is sent. The asker only sees a timeout, and the actor is handled by its
[supervision strategy](supervision.md) (stopped, by default). To report an error to the caller, make it
part of the reply:

=== "Java"

    ```java
    public sealed interface WithdrawResult {
        record Done(long balance) implements WithdrawResult {}
        record Rejected(String reason) implements WithdrawResult {}
    }

    public static class Withdraw extends AskCommand<WithdrawResult> implements Command {
        public final long amount;

        public Withdraw(long amount) {
            this.amount = amount;
        }
    }

    // In create():
    .onMessage(Withdraw.class, (state, msg) -> {
        if (msg.amount > state.balance()) {
            msg.reply(new WithdrawResult.Rejected("insufficient funds"));
        } else {
            msg.reply(new WithdrawResult.Done(state.withdraw(msg.amount)));
        }
        return SpringActorBehavior.same();
    })
    ```

=== "Kotlin"

    ```kotlin
    sealed interface WithdrawResult {
        data class Done(val balance: Long) : WithdrawResult
        data class Rejected(val reason: String) : WithdrawResult
    }

    class Withdraw(val amount: Long) : AskCommand<WithdrawResult>(), Command

    // In create():
    .onMessage(Withdraw::class.java) { state, msg ->
        if (msg.amount > state.balance()) {
            msg.reply(WithdrawResult.Rejected("insufficient funds"))
        } else {
            msg.reply(WithdrawResult.Done(state.withdraw(msg.amount)))
        }
        SpringActorBehavior.same()
    }
    ```

### Replying later

`reply` is an ordinary send, so the actor doesn't have to answer inside the handler. It can keep the
command and answer when it knows the answer, for example when a human approves something or another
actor answers. The `ApprovalActor` of the [agent example](../../examples/agent.md) keeps pending requests
in a map and replies when a decision arrives (simplified):

=== "Java"

    ```java
    SpringActorBehavior<Command> onRequest(RequestApproval request) {
        pending.put(request.id(), request);          // keep the command
        return SpringActorBehavior.same();
    }

    SpringActorBehavior<Command> onDecide(Decide decide) {
        RequestApproval request = pending.remove(decide.requestId());
        if (request != null) {
            request.reply(new ApprovalDecision(decide.approved()));   // answer the original asker
        }
        return SpringActorBehavior.same();
    }
    ```

=== "Kotlin"

    ```kotlin
    fun onRequest(request: RequestApproval): SpringActorBehavior<Command> {
        pending[request.id] = request                 // keep the command
        return SpringActorBehavior.same()
    }

    fun onDecide(decide: Decide): SpringActorBehavior<Command> {
        pending.remove(decide.requestId)
            ?.reply(ApprovalDecision(decide.approved)) // answer the original asker
        return SpringActorBehavior.same()
    }
    ```

The asker's timeout still applies: a reply after the timeout is dropped.

### Ask rules

- **One command object per ask.** `ask` stores the reply address in the command object. Create a new
  command for every ask; don't reuse or share one instance.
- **`reply` needs an ask.** Calling `reply` on a command that was sent with `tell` throws
  `IllegalStateException`. If a command can arrive both ways, check `msg.hasReplyTo()` first.
- **Reply once.** Only the first reply reaches the asker.

## Asking from inside an actor

Inside a handler, never wait for a reply (`join()`, `get()`): the actor would hold its thread and handle
nothing else until the reply comes. Send the ask and turn the result into a message to yourself with
`ctx.pipeToSelf`. The `AgentBrain` of the [agent example](../../examples/agent.md) asks the approval actor
this way (simplified):

=== "Java"

    ```java
    // A message this actor sends itself when the ask completes.
    record ApprovalResolved(boolean approved, String note) implements Command {}

    private SpringActorBehavior<Command> askForApproval(PendingApproval request) {
        ctx.pipeToSelf(
                approvals.ask(new ApprovalActor.RequestApproval(request))
                        .withTimeout(Duration.ofMinutes(5))
                        .execute(),
                (decision, error) -> error != null
                        ? new ApprovalResolved(false, "no answer: " + error.getMessage())
                        : new ApprovalResolved(decision.approved(), "answered"));
        return SpringActorBehavior.same();
    }
    ```

=== "Kotlin"

    ```kotlin
    // A message this actor sends itself when the ask completes.
    data class ApprovalResolved(val approved: Boolean, val note: String) : Command

    private fun askForApproval(request: PendingApproval): SpringActorBehavior<Command> {
        ctx.pipeToSelf(
            approvals.ask(ApprovalActor.RequestApproval(request))
                .withTimeout(5.minutes)
                .execute(),
        ) { decision, error ->
            if (error != null || decision == null) ApprovalResolved(false, "no answer: ${error?.message}")
            else ApprovalResolved(decision.approved, "answered")
        }
        return SpringActorBehavior.same()
    }
    ```

The function you pass to `pipeToSelf` gets the value (or `null`) and the error (or `null`). It runs when
the stage completes, so it must not touch the actor's state; it only builds the message. The actor
changes its state when it handles that message. The same pattern works for any `CompletionStage`: an
HTTP client call, a repository call on another thread, an LLM call.

## Replying to an actor without ask

Ask creates a temporary reply address. Between actors, you can pass a real address instead: put a
`SpringActorRef` in the message, and the receiver tells its answer to that ref. `ctx.getSelf()` is the
current actor's ref.

When the receiver answers with a type that is not one of your commands, use a **message adapter**:
`ctx.messageAdapter(Type.class, fn)` returns a `SpringActorRef<Type>` that turns each value it receives
into one of your commands. The agent example asks a rate limiter for permits this way:

=== "Java"

    ```java
    // The rate limiter answers with a RateLimiterActor.Permit.
    // PermitGranted is one of this actor's commands: record PermitGranted(Permit permit) implements Command {}
    private final SpringActorRef<RateLimiterActor.Permit> permits;

    AgentBrain(SpringBehaviorContext<Command> ctx) {
        this.permits = ctx.messageAdapter(RateLimiterActor.Permit.class, PermitGranted::new);
    }

    private void requestPermit() {
        rateLimiter.tell(RateLimiterActor.Acquire.replyingTo(permits));
    }
    ```

=== "Kotlin"

    ```kotlin
    // The rate limiter answers with a RateLimiterActor.Permit.
    // PermitGranted is one of this actor's commands: data class PermitGranted(val permit: Permit) : Command
    private val permits: SpringActorRef<RateLimiterActor.Permit> =
        ctx.messageAdapter(RateLimiterActor.Permit::class.java) { PermitGranted(it) }

    private fun requestPermit() {
        rateLimiter.tell(RateLimiterActor.Acquire.replyingTo(permits))
    }
    ```

Create adapters once, when the actor starts (in the `withState` factory), not per message.

Refs in messages work across nodes: a `SpringActorRef` field in a `JsonSerializable` message is
serialized by the starter.

## Calling actors from Spring code

Typical shapes, from a service or controller:

=== "Java"

    ```java
    // Fire and forget
    public void visit(String name) {
        actorSystem.getOrSpawn(GreeterActor.class, "greeter")
                .thenAccept(greeter -> greeter.tell(new GreeterActor.LogVisit(name)));
    }

    // Request and reply, as a Mono for WebFlux
    public Mono<String> greet(String name) {
        return Mono.fromCompletionStage(actorSystem.getOrSpawn(GreeterActor.class, "greeter")
                .thenCompose(greeter -> greeter.ask(new GreeterActor.Greet(name))
                        .withTimeout(Duration.ofSeconds(3))
                        .execute()));
    }
    ```

=== "Kotlin"

    ```kotlin
    // Fire and forget
    suspend fun visit(name: String) {
        actorSystem.awaitGetOrSpawn<GreeterActor, _>("greeter").tell(GreeterActor.LogVisit(name))
    }

    // Request and reply: a suspend function, usable from a suspend WebFlux handler
    suspend fun greet(name: String): String =
        actorSystem.awaitGetOrSpawn<GreeterActor, _>("greeter")
            .awaitAsk(GreeterActor.Greet(name), timeout = 3.seconds)
    ```

For a stream of values instead of one reply, see [Reactor streams](../streaming/reactor-streams.md).
For one message to many actors, see [Pub/sub topics](pub-sub-topics.md) and [Routers](routers.md).

## Pitfalls

- **Ask timeouts that hide errors.** A handler that throws sends no reply. Put errors in the reply type.
- **Blocking on ask inside an actor.** Use `pipeToSelf`.
- **Reusing a command object across asks.** Create one per ask.
- **Mutable messages.** A message changed after sending can be seen half-changed by the actor.
- **Forgetting serialization in cluster mode.** A message without `JsonSerializable` or
  `CborSerializable` fails when it crosses nodes.

## Related

- [Actors](actors.md): defining, spawning and finding actors
- [Behaviors](behaviors.md): `pipeToSelf`, message adapters and the rest of the behavior context
- [Supervision](supervision.md): what happens when a handler throws
- [Pub/sub topics](pub-sub-topics.md) and [Receptionist](receptionist.md): reaching actors without their id
- [Testing](../testing.md): testing asks and replies
