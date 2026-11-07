# AI Agents

This guide shows how the starter's pieces fit together for AI agents: one sharded actor per agent,
a supervised child that keeps the conversation, `pipeToSelf` for model calls, a per-node rate
limiter, a human approval step with ask and a timeout, and snapshots that survive a restart.

All code on this page is included from the runnable example in
[`example/agent`](https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/agent).
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

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/llm/LlmClient.java:llm-client"
```

The fake model used by the example:

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/llm/FakeLlmClient.java:fake-llm"
```

Tools are beans too. A tool that changes the world asks for a human approval first:

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/tool/Tool.java:tool"
```

## One sharded actor per agent

Each agent id is a sharded entity. The entity is a thin shell: it spawns the agent logic as a
supervised child and forwards every command to it.

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentActor.java:agent-actor"
```

Callers address agents by id and never need to know which node hosts them:

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentService.java:service"
```

!!! info "Single node by default"
    The example sets `agent.self-join: true`, so one node forms its own cluster. For more nodes,
    list `spring.actor.pekko.cluster.seed-nodes` as in the [Sharded Actors](sharded-actors.md)
    guide and set `agent.self-join: false`.

## The agent loop without blocking

A turn is: user message, rate-limit permit, model call, then either a final answer or a tool
call followed by another model call. The model call returns a `CompletionStage`;
`pipeToSelf` turns its result into a message, so the actor never waits on it.

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentBrain.java:llm-call"
```

While a call is pending the agent still answers `GetConversation` at once, and a new `Chat`
waits in a queue until the current turn ends.

## A rate limiter per node

Model providers limit calls per second. Each node spawns one rate limiter actor and every agent
on that node asks it for a permit before each call. It keeps a sliding one-second window, so no
one-second span ever holds more than `agent.calls-per-second` calls.

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/ratelimit/RateLimiterActor.java:rate-limiter"
```

The node-local actors are spawned once, when Spring starts:

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentRuntime.java:runtime"
```

!!! note "Per node, not per cluster"
    With three nodes the cluster can make three times `agent.calls-per-second` calls. For a
    cluster-wide limit, run the limiter as a cluster singleton (`asClusterSingleton()`) instead.

## A human approval step

When the model asks for a tool that needs approval, the agent asks the node's approval gateway
with a timeout. The gateway holds the request until a human answers; if nobody answers in time,
the ask fails and the agent tells the model the tool was not run.

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentBrain.java:approval"
```

The human side, for a controller, a chat bot or a test:

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/approval/ApprovalService.java:approval-service"
```

## Supervision and restarts

The agent's logic runs under a restart supervisor. `Behaviors.setup` sits inside
`Behaviors.supervise`, so a restart runs the setup again, and the setup loads the last snapshot:

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentBrain.java:supervision"
```

!!! warning "Where the supervisor goes"
    `SpringActorBehavior.builder(...).withSupervisionStrategy(...)` puts the supervisor inside its
    own setup, so a restart keeps the state object made by `withState`. To reload state on
    restart, wrap `Behaviors.setup` in `Behaviors.supervise` yourself, as above.

Every change to the conversation is saved before the agent moves on, so a new actor system
finds the agent where it left off:

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentBrain.java:snapshot"
```

The store is an interface. The example writes one JSON file per agent; for a database, implement
it with the `ActorSnapshot` entity and `ActorSnapshotRepository` from
[Persistence with Spring Boot](persistence-spring-boot.md).

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/persistence/AgentSnapshotStore.java:snapshot-store"
```

## Configuration

| Property | Default | What |
|---|---|---|
| `agent.calls-per-second` | `20` | Model calls one node may start per second |
| `agent.approval-timeout` | `30s` | How long an agent waits for a human |
| `agent.max-tool-steps` | `5` | Tool calls per turn before the agent stops |
| `agent.snapshot-dir` | `${java.io.tmpdir}/agent-snapshots` | Where snapshots are written |
| `agent.fake-llm-latency` | `0` (`200ms` in application.yml) | Delay of each fake model reply |
| `agent.self-join` | `true` | Form a one-node cluster without seed nodes |
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

- **Timers.** There is no timer wrapper; the rate limiter and the approval gateway use Pekko's
  `Behaviors.withTimers` through `SpringActorBehavior.wrap`.
- **Rate limiting.** There is no rate limiter in the starter; the example's is an actor.
- **Supervision of sharded entities.** `SpringShardedActorBehavior` has no supervision or signal option, so
  the agent puts its logic in a supervised child.
