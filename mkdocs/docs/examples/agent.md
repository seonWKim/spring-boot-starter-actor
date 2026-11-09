# Agent Example

LLM agents as actors. Each agent is a sharded actor that keeps its conversation, calls a model and
tools without blocking, waits for a human before risky tools, shares a per-node rate limit, and reloads
its conversation after a crash or a restart of the whole app.

The model is a scripted fake, so the example runs without an API key or network.

- **Source:** [`example/agent`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/agent)
- **Runs:** a one-node cluster (`spring.actor.cluster.join-self: true`); no HTTP server
- **Modules:** `actor-spring-boot-starter-cluster`, Jackson; no LLM SDK

The [AI agents guide](../guides/ai-agents.md) explains the design step by step with this example's code.
This page is the short tour.

## What it shows

- One sharded actor per agent id (`AgentActor`), reachable from any node
- `pipeToSelf` to turn an LLM call's future into a message, so the actor never waits
- A message adapter (`ctx.messageAdapter`) so a node-local rate limiter can reply with a message
- A human approval step: an `ask` with a timeout to an approval actor, and timers that expire requests
- Restart supervision that reloads state from a snapshot file
- Plain node-local actors (`RateLimiterActor`, `ApprovalActor`) spawned once per node with `spawnAndWait`

## Run it

```bash
./gradlew :example:agent:bootRun
```

On start, `DemoRunner` asks the agent `demo-agent` "What is the weather in Seoul?" and logs the answer and
the whole conversation (user message, tool call, tool result, final answer). Set `agent.demo: false` to
skip it.

The tests drive every feature and are the best way to explore:

```bash
./gradlew :example:agent:test
```

| Test | What it checks |
|---|---|
| `AgentConversationTest` | A turn with a tool call, and a turn without one |
| `ConcurrentAgentsTest` | 100 agents in parallel each keep their own conversation |
| `SlowLlmTest` | The agent answers `GetConversation` while an LLM call is pending |
| `RateLimiterTest` | No more than the limit of permits in any one-second window |
| `HumanApprovalTest` | Approved, rejected and unanswered (timed out) tool calls |
| `SupervisionTest` | A crashed agent restarts with its conversation |
| `ActorSystemRestartTest` | The conversation survives a restart of the whole actor system |

## Configuration

```yaml
spring:
  actor:
    name: agent-example
    provider: cluster
    remote:
      hostname: 127.0.0.1
      port: 2551
    cluster:
      join-self: true   # a one-node cluster; list seed-nodes instead for more nodes

agent:
  calls-per-second: 20   # LLM calls one node may start per second, across all its agents
  approval-timeout: 30s  # how long an agent waits for a human
  max-tool-steps: 5      # tool calls per turn before the agent gives up
  fake-llm-latency: 200ms
  demo: true
```

`agent.snapshot-dir` (default: `<java.io.tmpdir>/agent-snapshots`) is where each agent's conversation is
saved as one JSON file. The `agent.*` keys are the example's own `AgentProperties`, not starter
properties.

## Walkthrough

| Class | Role |
|---|---|
| `AgentActor` | The sharded entity: commands `Chat` (ask, replies `AgentAnswer`), `GetConversation`, `SimulateCrash` |
| `AgentBrain` | The agent loop and its state, run under a restart supervisor |
| `AgentService` | Talks to agents by id |
| `AgentRuntime` | Spawns the node-local rate limiter and approval actors |
| `RateLimiterActor` | Sliding-window permits for LLM calls, per node |
| `ApprovalActor`, `ApprovalService` | Pending tool calls, and the human side that lists and decides them |
| `FakeLlmClient` | The scripted model behind the `LlmClient` interface |
| `WeatherTool`, `RefundTool` | Two tools; `refund` requires approval |
| `FileAgentSnapshotStore` | One JSON file per agent, replaced atomically |

### Talking to an agent

```java
--8<-- "example/agent/src/main/java/io/github/seonwkim/example/agent/AgentService.java:service"
```

### The LLM call without blocking

The brain asks the rate limiter for a permit, then calls the model. `pipeToSelf` delivers the result as
an `LlmResponded` message, so the actor keeps handling other messages while the model works:

```java
ctx.pipeToSelf(deps.llm().complete(List.copyOf(conversation)), LlmResponded::new);
```

New `Chat` messages that arrive during a turn wait in a queue inside the state and start when the turn
ends.

### Approval

A tool with `requiresApproval()` sends `RequestApproval` to the approval actor with an `ask` and the
configured timeout. A human (here, a test) lists and decides through `ApprovalService`:

```java
approvals.pending();                    // CompletionStage<List<PendingApproval>>
approvals.decide(requestId, true);      // completes with false if the request is unknown or expired
```

If no one answers in time, the tool does not run and the model gets "not run: no human answer within ...".

### Crash and restart

`AgentBrain` is built with `SupervisionStrategy.restart().withLimit(10, Duration.ofMinutes(1))`. Every
change to the conversation is saved before the agent moves on, and the state constructor loads the
snapshot, so a restart (or a new actor system) continues the conversation.

## Things to keep in mind

- The rate limiter and the approval actor are per node. With N nodes, the cluster-wide limit is N times
  `calls-per-second`, and a human must decide on the node that holds the request.
- Snapshots go to a local directory. In a real cluster, use a store every node can read, because a
  sharded agent can move to another node.

## Try next

- Replace `FakeLlmClient` with a real provider behind `LlmClient`.
- Ask "refund order-42" from a test and approve it with `ApprovalService`.
- Run two nodes (remove `join-self`, list seed nodes) and check that `demo-agent` answers from either.

## Related

- [AI agents](../guides/ai-agents.md): the full design, with this example's code
- [Sharded actors](../guides/cluster/sharded-actors.md)
- [Behaviors](../guides/core/behaviors.md): timers and state
- [Messaging](../guides/core/messaging.md): message adapters and `ask`
- [Supervision](../guides/core/supervision.md)
