# Actor Spring Boot Starter

The actor model for Spring Boot, on [Apache Pekko](https://pekko.apache.org/) (the open-source fork of Akka).

Actors are Spring beans. You spawn them, send them messages, and scale them across a cluster, and the API
you compile against has no Pekko types.

## Why actors in a Spring application

Most Spring services are stateless: every request reads its state from a database. Some problems are
stateful by nature, and an actor fits them better:

- **A chat room or a game session** that many users talk to at once
- **A user session, a cart or a device** that receives a stream of events
- **A workflow or a long-running AI agent** that waits for timers, tools, model replies and approvals

An actor owns its state and handles one message at a time, so you write plain code with no locks.
A failed actor is restarted by its supervisor. Sharding spreads thousands of actors over a cluster,
and event sourcing brings an actor's state back after a restart.

## What you get

| Area | What's included | Start here |
|---|---|---|
| Core | Actors as beans, tell and ask, supervision, timers, routers, pub/sub topics, receptionist, dispatchers, logging with actor context | [Actors](guides/core/actors.md) |
| Cluster | Sharding, singletons, replicated data, split brain resolver and leases; join through seed nodes, Kubernetes, a database or Redis | [Sharded actors](guides/cluster/sharded-actors.md) |
| Data | Actor state in your database, event-sourced actors, projections into Spring Data or Kafka | [State in a database](guides/data/state-in-a-database.md) |
| Streaming | Reactor `Mono`/`Flux`, SSE, WebSocket and gRPC | [Reactor streams](guides/streaming/reactor-streams.md) |
| Operations | Graceful shutdown, Actuator health, metrics, tracing, dashboards, rolling/canary/blue-green deployments | [Running in production](guides/operations/production.md) |
| Kotlin | `suspend` ask and spawn, reified helpers | [Kotlin](guides/kotlin.md) |
| Testing | `@ActorTest` slice, multi-node cluster helpers | [Testing](guides/testing.md) |
| AI agents | Agents as sharded actors with tools, streaming and approvals | [AI agents](guides/ai-agents.md) |

## Modules

Start with `actor-spring-boot-starter`, and add the others when you need them. All are published as
`io.github.seonwkim:<module>:1.0.0` and need Java 21+ and Spring Boot 3.5+.

| Module | What it adds |
|---|---|
| `actor-spring-boot-starter` | The actor system, actors as beans, messaging, routers, topics, receptionist |
| `actor-spring-boot-starter-cluster` | Cluster mode: sharding, singletons, replicated data, split brain resolver (use it instead of the starter above) |
| `actor-jdbc` | Cluster join and leases through your `DataSource` |
| `actor-redis` | Cluster join and leases through Redis |
| `actor-persistence` | Event-sourced actors, with journal and snapshots on your `DataSource` |
| `actor-projection` | Projections of persisted events into Spring Data or Kafka |
| `actor-streams` | Reactor bridges, and SSE, WebSocket and gRPC streaming |
| `actor-metrics` | Micrometer meters for actors, mailboxes, dispatchers, cluster and sharding |
| `actor-kotlin` | Kotlin coroutines and reified helpers |
| `actor-test` | `@ActorTest`, cluster test helpers and store contract tests (test scope) |

Using several? Import `actor-bom` to set the version once (see [Getting started](getting-started.md#several-modules-import-the-bom)).

## Next steps

- [Getting started](getting-started.md): an actor answering HTTP requests in a few minutes
- [Examples](examples/index.md): runnable applications, from a single node to Kubernetes
- [Migrating from 0.x](migrating-from-0x.md): if you used `spring-boot-starter-actor`
- [llms.txt](llms.txt): the index of these docs for AI assistants
