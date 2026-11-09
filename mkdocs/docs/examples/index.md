# Examples

The repository's [`example/`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example)
directory holds runnable Spring Boot apps. Each one is a Gradle subproject that uses the library's
modules from source, so you can run it from a clone with one command:

```bash
git clone https://github.com/seonWKim/actor-spring-boot-starter.git
cd actor-spring-boot-starter
./gradlew :example:simple:bootRun
```

Cluster examples start several nodes with `sh cluster-start.sh <example> <main class> 8080 2551 3` from
the repository root and stop them with `sh cluster-stop.sh`. Each page gives the exact commands.

## All examples

| Example | What it shows | Runs | Modules |
|---|---|---|---|
| [Simple](simple.md) | One actor behind REST: spawn, ask, restart, stop | Local | `actor-spring-boot-starter` |
| [Kotlin](kotlin.md) | The simple example in Kotlin with `suspend` functions | Local | `actor-spring-boot-starter`, `actor-kotlin` |
| [Cluster](cluster.md) | Sharded actors and a cluster singleton on three nodes | Cluster (3 nodes) | `actor-spring-boot-starter-cluster` |
| [Synchronization](synchronization.md) | A counter under load: DB lock vs Redis lock vs sharded actor | Cluster (3 nodes) + MySQL, Redis | `actor-spring-boot-starter-cluster` |
| [Chat](chat.md) | WebSocket chat: actor per connection, sharded rooms, pub/sub topics | Cluster (3 nodes), Docker Compose | `actor-spring-boot-starter-cluster`, `actor-streams`, `actor-metrics` |
| [Metrics](metrics.md) | Actor meters in Prometheus through Actuator | Local | `actor-spring-boot-starter`, `actor-metrics` |
| [Logging](logging.md) | Static and per-message MDC, tags, request ids, JSON logs | Local | `actor-spring-boot-starter` |
| [Virtual threads](virtual-threads.md) | Dispatchers on Java 21 virtual threads | Local | `actor-spring-boot-starter` |
| [Persistence](persistence.md) | Order state in JPA: current state, events, snapshots | Local + H2 | `actor-spring-boot-starter` |
| [Agent](agent.md) | LLM agents: sharded agents, rate limit, human approval, recovery | Cluster (1 node) | `actor-spring-boot-starter-cluster` |
| [Kubernetes](kubernetes.md) | The chat app on Kubernetes: discovery, rollouts, monitoring | Kubernetes (kind) | the chat app's image |

## Suggested learning path

1. **[Simple](simple.md)** (or **[Kotlin](kotlin.md)**): the basics of defining, spawning and asking an
   actor, and what supervision does.
2. **[Logging](logging.md)** and **[Virtual threads](virtual-threads.md)**: settings you will want in
   any app, MDC and dispatchers.
3. **[Persistence](persistence.md)**: keep actor state across restarts with a database.
4. **[Cluster](cluster.md)**: the step to several nodes, with sharded actors and a singleton.
5. **[Synchronization](synchronization.md)**: why one actor per key replaces a lock.
6. **[Chat](chat.md)**: a full app with WebSockets and pub/sub on a cluster.
7. **[Metrics](metrics.md)** and **[Kubernetes](kubernetes.md)**: run it in production.
8. **[Agent](agent.md)**: a larger design that combines most of the above.

## Related

- [Getting started](../getting-started.md)
- [Actors](../guides/core/actors.md), the first guide
