# AI Agents

This guide shows how the starter's pieces fit together for AI agents: one supervised sharded actor
per agent that keeps the conversation, `pipeToSelf` for model calls, a per-node rate
limiter, a human approval step with ask and a timeout, and snapshots that survive a restart.

All Java code on this page is included from the runnable example in
[`example/agent`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/agent).
The Kotlin tabs show the same code in Kotlin, written with the [`actor-kotlin`](kotlin.md) module; they
are not part of the example.
The example uses a fake model, so it needs no API key and no network:

```bash
./gradlew :example:agent:bootRun   # runs one demo conversation and logs it
./gradlew :example:agent:test      # the tests named at the end of this page
```

## Why actors fit agents

An agent is a long conversation with state, slow external calls, and side effects. Actors give you:

- **One owner per conversation.** An agent's actor handles one message at a time, so its
  history needs no locks, even with thousands of agents on one node.
- **Location transparency.** With sharding, the cluster keeps exactly one instance of each agent
  id and routes messages to it, wherever it lives.
- **No blocked threads.** A model call can take seconds. The actor sends the call, keeps answering
  other messages, and handles the reply as a new message.
- **Supervision.** If the agent's code throws, a supervisor restarts it from its last snapshot.

## The model behind an interface

The agent only knows this interface. The example ships a scripted fake; a real provider is
another bean that implements it.

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/llm/LlmClient.java:llm-client"
    ```

=== "Kotlin"

    ```kotlin
    /**
     * The only thing an agent knows about the model. Swap the fake for a real provider by adding
     * another bean that implements this interface; the agent code does not change.
     */
    interface LlmClient {
        /**
         * Sends the conversation so far and returns the model's next step. Must not block the
         * caller: the agent pipes the result back to itself.
         */
        fun complete(conversation: List<ChatMessage>): CompletionStage<LlmReply>
    }
    ```

The fake model used by the example:

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/llm/FakeLlmClient.java:fake-llm"
    ```

=== "Kotlin"

    ```kotlin
    /**
     * A scripted model, so the example runs without an API key or network.
     *
     * - "weather in <city>" asks for the `weather` tool;
     * - "refund <order>" asks for the `refund` tool, which needs a human approval;
     * - "my name is <name>" is remembered, and "what is my name?" answers from the conversation;
     * - a tool result is turned into the final answer;
     * - anything else is echoed.
     */
    @Component
    class FakeLlmClient(private val properties: AgentProperties) : LlmClient {

        override fun complete(conversation: List<ChatMessage>): CompletionStage<LlmReply> =
            CompletableFuture.supplyAsync(
                { reply(conversation) },
                CompletableFuture.delayedExecutor(properties.fakeLlmLatency.toMillis(), TimeUnit.MILLISECONDS),
            )

        companion object {
            fun reply(conversation: List<ChatMessage>): LlmReply {
                val last = conversation.last()
                if (last.role == ChatMessage.Role.TOOL_RESULT) {
                    return LlmReply.answer("Tool ${last.toolName} says: ${last.content}")
                }
                val text = last.content.trim()
                val lower = text.lowercase()
                val weather = lower.indexOf("weather in ")
                return when {
                    weather >= 0 -> {
                        val city = text.substring(weather + "weather in ".length).replace(Regex("[?.!]+$"), "")
                        LlmReply.callTool("weather", city)
                    }
                    lower.startsWith("refund ") -> LlmReply.callTool("refund", text.substring("refund ".length))
                    lower.startsWith("what is my name") -> {
                        val name = conversation.lastOrNull {
                            it.role == ChatMessage.Role.USER && it.content.lowercase().startsWith("my name is ")
                        }
                        if (name != null) LlmReply.answer("Your name is " + name.content.substring("my name is ".length))
                        else LlmReply.answer("You did not tell me your name.")
                    }
                    lower.startsWith("my name is ") -> LlmReply.answer("Nice to meet you, " + text.substring("my name is ".length))
                    else -> LlmReply.answer("You said: $text")
                }
            }
        }
    }
    ```

Tools are beans too. A tool that changes the world asks for a human approval first:

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/tool/Tool.java:tool"
    ```

=== "Kotlin"

    ```kotlin
    /** A function the model may ask the agent to run. */
    interface Tool {
        fun name(): String

        /** Tools that change the world wait for a human to approve the call. */
        fun requiresApproval(): Boolean = false

        fun invoke(argument: String): String
    }
    ```

## One sharded actor per agent

Each agent id is a sharded entity. Its behavior is the agent's logic, `AgentBrain`, under a restart
supervisor.

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentActor.java:agent-actor"
    ```

=== "Kotlin"

    ```kotlin
    /**
     * One sharded entity per agent id: the cluster keeps exactly one instance of each agent, on
     * some node, and routes every message for that id to it.
     *
     * The agent's logic is [AgentBrain], run under a restart supervisor. If it crashes, the
     * supervisor restarts it and it reloads its conversation from the snapshot store.
     */
    @Component
    class AgentActor(
        runtime: AgentRuntime,
        llm: LlmClient,
        tools: ToolRegistry,
        snapshots: AgentSnapshotStore,
        properties: AgentProperties,
    ) : SpringShardedActor<AgentActor.Command> {

        companion object {
            val TYPE_KEY: ShardedEntityType<Command> = ShardedEntityType.of(Command::class.java, "Agent")
        }

        /** Commands may cross nodes, so they are JSON serializable. */
        interface Command : JsonSerializable

        /** A user message. The reply is the agent's final answer for this turn. */
        class Chat @JsonCreator constructor(@JsonProperty("text") val text: String) : AskCommand<AgentAnswer>(), Command

        /** Reads the agent's state. Answered at once, even while an LLM call is pending. */
        class GetConversation : AskCommand<ConversationView>(), Command

        /** Makes the agent throw, to show supervision. */
        class SimulateCrash @JsonCreator constructor() : Command

        private val dependencies = AgentBrain.Dependencies(
            llm,
            tools,
            snapshots,
            runtime.rateLimiter,
            runtime.approvals,
            properties.approvalTimeout,
            properties.maxToolSteps,
        )

        override fun entityType(): ShardedEntityType<Command> = TYPE_KEY

        override fun create(ctx: SpringShardedActorContext<Command>): SpringActorBehavior<Command> =
            AgentBrain.behavior(actorBehavior<Command>(ctx), ctx.entityId, dependencies)
    }
    ```

Callers address agents by id and never need to know which node hosts them:

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentService.java:service"
    ```

=== "Kotlin"

    ```kotlin
    /** Talks to agents by id. The agent may live on any node of the cluster. */
    @Service
    class AgentService(private val actorSystem: SpringActorSystem) {

        suspend fun chat(agentId: String, text: String, timeout: Duration): AgentAnswer =
            agent(agentId).ask(AgentActor.Chat(text)).withTimeout(timeout).await()

        suspend fun conversation(agentId: String, timeout: Duration): ConversationView =
            agent(agentId).ask(AgentActor.GetConversation()).withTimeout(timeout).await()

        fun simulateCrash(agentId: String) {
            agent(agentId).tell(AgentActor.SimulateCrash())
        }

        private fun agent(agentId: String): SpringShardedActorHandle<AgentActor.Command> =
            actorSystem.sharded<AgentActor, _>().withId(agentId).get()
    }
    ```

!!! info "Single node by default"
    The example sets `spring.actor.cluster.join-self: true`, so one node forms its own cluster.
    For more nodes, remove it and list `spring.actor.cluster.seed-nodes` as in the
    [Sharded Actors](cluster/sharded-actors.md) guide.

## The agent loop without blocking

A turn is: user message, rate-limit permit, model call, then either a final answer or a tool
call followed by another model call. The model call returns a `CompletionStage`;
`pipeToSelf` turns its result into a message, so the actor never waits on it.

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentBrain.java:llm-call"
    ```

=== "Kotlin"

    ```kotlin
    private fun requestPermit() {
        status = Status.PERMIT
        deps.rateLimiter.tell(RateLimiterActor.Acquire.replyingTo(permits))
    }

    private fun onPermit(): SpringActorBehavior<AgentActor.Command> {
        if (status != Status.PERMIT) {
            return SpringActorBehavior.same()
        }
        status = Status.LLM
        ctx.log.debug("Agent {} calls the LLM", agentId)
        // The future completes on another thread; pipeToSelf turns it into a message, so
        // the actor never waits and never touches its state from that thread.
        ctx.pipeToSelf(deps.llm.complete(conversation.toList())) { reply, error -> LlmResponded(reply, error) }
        return SpringActorBehavior.same()
    }

    private fun onLlmResponded(responded: LlmResponded): SpringActorBehavior<AgentActor.Command> {
        if (status != Status.LLM) {
            return SpringActorBehavior.same() // a reply for an earlier incarnation
        }
        val reply = responded.reply
        if (responded.error != null || reply == null) {
            finishTurn("Sorry, the model call failed: ${responded.error?.message}")
            return SpringActorBehavior.same()
        }
        val toolName = reply.toolName
        val toolArgument = reply.toolArgument
        if (toolName == null || toolArgument == null) {
            finishTurn(reply.text.orEmpty())
            return SpringActorBehavior.same()
        }
        if (++toolSteps > deps.maxToolSteps) {
            finishTurn("Stopped after ${deps.maxToolSteps} tool calls.")
            return SpringActorBehavior.same()
        }
        append(ChatMessage.toolCall(toolName, toolArgument))
        val tool = deps.tools.find(toolName).orElse(null)
        when {
            tool == null -> toolResult(reply, "unknown tool")
            tool.requiresApproval() -> askForApproval(reply)
            else -> runTool(tool, reply)
        }
        return SpringActorBehavior.same()
    }
    ```

While a call is pending the agent still answers `GetConversation` at once, and a new `Chat`
waits in a queue until the current turn ends.

## A rate limiter per node

Model providers limit calls per second. Each node spawns one rate limiter actor and every agent
on that node asks it for a permit before each call. It keeps a sliding one-second window, so no
one-second span ever holds more than `agent.calls-per-second` calls.

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/ratelimit/RateLimiterActor.java:rate-limiter"
    ```

=== "Kotlin"

    ```kotlin
    /**
     * One per node: hands out permits for LLM calls, at most `agent.calls-per-second` in any
     * one-second window. Requests over the limit wait in a queue; a timer wakes the actor when the
     * oldest permit leaves the window.
     *
     * The starter has no rate limiter, so this is a plain actor with a timer from its behavior context.
     */
    @Component
    class RateLimiterActor(properties: AgentProperties) : SpringActor<RateLimiterActor.Command> {

        interface Command

        /** Ask for one permit. The reply comes when the call may start. */
        class Acquire private constructor(private val adapter: SpringActorRef<Permit>?) : AskCommand<Permit>(), Command {
            constructor() : this(null)

            internal fun grant(permit: Permit) {
                if (adapter != null) adapter.tell(permit) else reply(permit)
            }

            companion object {
                /** For actors that want the permit as a message instead of a future. */
                fun replyingTo(replyTo: SpringActorRef<Permit>) = Acquire(replyTo)
            }
        }

        /** @param grantedAtNanos [System.nanoTime] when the permit was granted */
        data class Permit(val grantedAtNanos: Long)

        private data object WakeUp : Command

        private val callsPerSecond = properties.callsPerSecond

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .withState { ctx -> SlidingWindow(ctx, callsPerSecond, 1.seconds.inWholeNanoseconds) }
                .onMessage(Acquire::class.java) { window, acquire -> window.onAcquire(acquire) }
                .onMessage(WakeUp::class.java) { window, _ -> window.drain() }
                .build()

        private class SlidingWindow(
            private val ctx: SpringBehaviorContext<Command>,
            private val limit: Int,
            private val windowNanos: Long,
        ) {
            private val granted = ArrayDeque<Long>()
            private val waiting = ArrayDeque<Acquire>()

            fun onAcquire(acquire: Acquire): SpringActorBehavior<Command> {
                waiting.addLast(acquire)
                return drain()
            }

            fun drain(): SpringActorBehavior<Command> {
                val now = System.nanoTime()
                while (granted.isNotEmpty() && now - granted.first() >= windowNanos) {
                    granted.removeFirst()
                }
                while (waiting.isNotEmpty() && granted.size < limit) {
                    granted.addLast(now)
                    waiting.removeFirst().grant(Permit(now))
                }
                if (waiting.isNotEmpty()) {
                    val untilFree = windowNanos - (now - granted.first())
                    ctx.startSingleTimer(WakeUp, WakeUp, Duration.ofNanos(untilFree))
                }
                return SpringActorBehavior.same()
            }
        }
    }
    ```

The node-local actors are spawned once, when Spring starts:

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentRuntime.java:runtime"
    ```

=== "Kotlin"

    ```kotlin
    /**
     * Node-local actors that every agent on this node shares: the LLM rate limiter and the approval
     * gateway. Each node spawns its own, so the rate limit is per node.
     */
    @Component
    class AgentRuntime(
        actorSystem: SpringActorSystem,
        // The actor beans are parameters so Spring registers them before they are spawned here.
        rateLimiterActor: RateLimiterActor,
        approvalActor: ApprovalActor,
    ) {
        val rateLimiter: SpringActorHandle<RateLimiterActor.Command> =
            actorSystem.actor<RateLimiterActor, _>().withId("llm-rate-limiter").spawnAndWait()
        val approvals: SpringActorHandle<ApprovalActor.Command> =
            actorSystem.actor<ApprovalActor, _>().withId("approvals").spawnAndWait()
    }
    ```

!!! note "Per node, not per cluster"
    With three nodes the cluster can make three times `agent.calls-per-second` calls. For a
    cluster-wide limit, run the limiter as a [cluster singleton](cluster/singletons.md) (`asClusterSingleton()`) instead.

## A human approval step

When the model asks for a tool that needs approval, the agent asks the node's approval gateway
with a timeout. The gateway holds the request until a human answers; if nobody answers in time,
the ask fails and the agent tells the model the tool was not run.

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentBrain.java:approval"
    ```

=== "Kotlin"

    ```kotlin
    private fun askForApproval(call: LlmReply) {
        status = Status.APPROVAL
        val request = PendingApproval("$agentId-${UUID.randomUUID()}", agentId, call.toolName!!, call.toolArgument!!)
        val timeout = deps.approvalTimeout
        ctx.pipeToSelf(
            deps.approvals
                .ask(ApprovalActor.RequestApproval(request, timeout))
                .withTimeout(timeout)
                .execute(),
        ) { decision, error ->
            if (error != null || decision == null) {
                ApprovalResolved(call, false, "no human answer within $timeout")
            } else {
                ApprovalResolved(call, decision.approved, if (decision.approved) "approved" else "rejected by a human")
            }
        }
    }

    private fun onApprovalResolved(resolved: ApprovalResolved): SpringActorBehavior<AgentActor.Command> {
        if (status != Status.APPROVAL) {
            return SpringActorBehavior.same()
        }
        val call = resolved.call
        val tool = deps.tools.find(call.toolName!!).orElse(null)
        if (resolved.approved && tool != null) {
            runTool(tool, call)
        } else {
            toolResult(call, "not run: ${resolved.reason}")
        }
        return SpringActorBehavior.same()
    }
    ```

The human side, for a controller, a chat bot or a test:

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/approval/ApprovalService.java:approval-service"
    ```

=== "Kotlin"

    ```kotlin
    /** The human side of the approval step, for a controller, a Slack bot or a test. */
    @Service
    class ApprovalService(private val runtime: AgentRuntime) {

        suspend fun pending(): List<PendingApproval> =
            runtime.approvals.ask(ApprovalActor.ListPending()).withTimeout(TIMEOUT).await()

        /** Returns false if the request is unknown or its timeout has passed. */
        suspend fun decide(requestId: String, approved: Boolean): Boolean =
            runtime.approvals.ask(ApprovalActor.Decide(requestId, approved)).withTimeout(TIMEOUT).await()

        private companion object {
            val TIMEOUT: Duration = Duration.ofSeconds(3)
        }
    }
    ```

## Supervision and restarts

The agent's logic runs under a restart supervisor (`withSupervisionStrategy`). A restart creates the
state again with `withState`, and the state loads the last snapshot:

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentBrain.java:supervision"
    ```

=== "Kotlin"

    ```kotlin
    companion object {
        /**
         * Restart on failure, at most 10 times a minute. A restart creates the state again, so the
         * brain reloads its conversation from the last snapshot. After the limit the entity stops,
         * and sharding starts a fresh one on the next message.
         */
        fun behavior(
            builder: SpringActorBehavior.Builder<AgentActor.Command, SpringBehaviorContext<AgentActor.Command>>,
            agentId: String,
            dependencies: Dependencies,
        ): SpringActorBehavior<AgentActor.Command> =
            builder.withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, 1.minutes.toJavaDuration()))
                .withState { ctx -> AgentBrain(ctx, agentId, dependencies) }
                .onMessage(AgentActor.Chat::class.java) { brain, msg -> brain.onChat(msg) }
                .onMessage(AgentActor.GetConversation::class.java) { brain, msg -> brain.onGetConversation(msg) }
                .onMessage(AgentActor.SimulateCrash::class.java) { brain, _ -> brain.onSimulateCrash() }
                .onMessage(PermitGranted::class.java) { brain, _ -> brain.onPermit() }
                .onMessage(LlmResponded::class.java) { brain, msg -> brain.onLlmResponded(msg) }
                .onMessage(ApprovalResolved::class.java) { brain, msg -> brain.onApprovalResolved(msg) }
                .build()
    }

    private val permits: SpringActorRef<RateLimiterActor.Permit> =
        ctx.messageAdapter(RateLimiterActor.Permit::class.java) { PermitGranted(it) }
    private val conversation = mutableListOf<ChatMessage>()

    init {
        deps.snapshots.load(agentId).ifPresent { conversation.addAll(it.conversation) }
    }
    ```

Every change to the conversation is saved before the agent moves on, so a new actor system
finds the agent where it left off:

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentBrain.java:snapshot"
    ```

=== "Kotlin"

    ```kotlin
    /** Every change to the conversation is written before the agent moves on. */
    private fun append(message: ChatMessage) {
        conversation.add(message)
        deps.snapshots.save(AgentSnapshot(agentId, conversation.toList()))
    }
    ```

The store is an interface. The example writes one JSON file per agent; for a database, implement
it with a JPA entity and repository like `ActorSnapshot` and `ActorSnapshotRepository` in the
[persistence example](../examples/persistence.md) (see also
[State in a Database](data/state-in-a-database.md)).

=== "Java"

    ```java
    --8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/persistence/AgentSnapshotStore.java:snapshot-store"
    ```

=== "Kotlin"

    ```kotlin
    /**
     * Saves and loads agent snapshots. The example writes JSON files; a JPA implementation with an
     * `ActorSnapshot` entity and repository, as in example/persistence, plugs in the same way.
     */
    interface AgentSnapshotStore {
        fun load(agentId: String): Optional<AgentSnapshot>

        fun save(snapshot: AgentSnapshot)
    }
    ```

## Configuration

| Property | Default | What |
|---|---|---|
| `agent.calls-per-second` | `20` | Model calls one node may start per second |
| `agent.approval-timeout` | `30s` | How long an agent waits for a human |
| `agent.max-tool-steps` | `5` | Tool calls per turn before the agent stops |
| `agent.snapshot-dir` | `${java.io.tmpdir}/agent-snapshots` | Where snapshots are written |
| `agent.fake-llm-latency` | `0` (`200ms` in application.yml) | Delay of each fake model reply |
| `agent.demo` | `true` | Run one demo conversation on start-up |

## What the tests show

| Test | Shows |
|---|---|
| `AgentConversationTest` | user message, model reply, tool call, tool result, final answer |
| `ConcurrentAgentsTest` | 100 agents at once, each with its own id and its own history |
| `RateLimiterTest` | no one-second window holds more permits than the limit |
| `SlowLlmTest` | the agent answers other messages while a model call is pending |
| `SupervisionTest` | a crashed agent restarts and reloads its conversation |
| `ActorSystemRestartTest` | the conversation survives a restart of the whole actor system |
| `HumanApprovalTest` | approve, reject, and a timeout when nobody answers |

## What the starter does not provide yet

The example builds these itself:

- **Rate limiting.** There is no rate limiter in the starter; the example's is an actor.
