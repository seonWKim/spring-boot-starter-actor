# Receptionist (Service Discovery)

The receptionist lets you find actors by a **service key** instead of by name or path. Actors register under a key; other code looks the key up once or subscribes to changes. It wraps Pekko's typed [Receptionist](https://pekko.apache.org/docs/pekko/current/typed/actor-discovery.html).

## Overview

**Key Features:**

- Register any actor under a typed `ServiceKey`
- One-time lookup that returns `SpringActorHandle` refs
- Subscriptions that are called on every change and can be cancelled
- Actors can register themselves from behavior setup
- Works in both local and cluster modes

**Use Cases:**

- Finding a pool of workers without knowing their ids
- Reacting when instances of a service come and go
- Discovering actors that live on other cluster nodes

## Service Keys

A key combines a message type and an id. Keys with the same type and id are equal, on every node:

```java
public static final ServiceKey<WorkerCommand> WORKERS =
        SpringReceptionist.serviceKey(WorkerCommand.class, "workers");
```

In cluster mode the messages must be serializable (`JsonSerializable` or `CborSerializable`), as for any remote message.

## Registering and Finding

Inject `SpringReceptionist`. It is created as a bean automatically (`@ConditionalOnMissingBean`, so you can provide your own).

```java
@Service
public class WorkerPool {
    private final SpringActorSystem actorSystem;
    private final SpringReceptionist receptionist;

    public WorkerPool(SpringActorSystem actorSystem, SpringReceptionist receptionist) {
        this.actorSystem = actorSystem;
        this.receptionist = receptionist;
    }

    public void startWorker(String id) {
        SpringActorHandle<WorkerCommand> worker = actorSystem
                .actor(WorkerActor.class)
                .withId(id)
                .spawnAndWait();
        receptionist.register(WORKERS, worker);
    }
}

@Service
public class JobDispatcher {
    private final SpringReceptionist receptionist;

    public JobDispatcher(SpringReceptionist receptionist) {
        this.receptionist = receptionist;
    }

    public CompletionStage<Void> dispatch(Job job) {
        return receptionist.find(WORKERS)
                .thenAccept(workers -> workers.forEach(w -> w.tell(new DoJob(job))));
    }
}
```

`find(key)` and `find(key, timeout)` return a `CompletionStage<Set<SpringActorHandle<T>>>`. The set may be empty. Each handle supports `tell` and `ask` like any other `SpringActorHandle`.

A stopped actor is removed from the listing automatically. Use `deregister(key, handle)` to remove a running actor.

## Registering from Behavior Setup

An actor can register itself with `SpringBehaviorContext.registerWithReceptionist`:

```java
@Component
public class WorkerActor implements SpringActor<WorkerCommand> {
    @Override
    public SpringActorBehavior<WorkerCommand> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(WorkerCommand.class, actorContext)
                .withState(ctx -> {
                    ctx.registerWithReceptionist(WORKERS);
                    return new WorkerState();
                })
                .onMessage(DoJob.class, WorkerState::onJob)
                .build();
    }
}
```

## Subscribing to Changes

```java
SpringReceptionistSubscription<WorkerCommand> subscription =
        receptionist.subscribe(WORKERS, workers ->
                log.info("{} workers available", workers.size()));

// later
subscription.cancel();
```

The listener is called with the current set right after subscribing, then with the full updated set each time an actor registers, deregisters or stops. It runs on an actor thread: keep it short and never block in it.

Each subscription is backed by a small helper actor. `cancel()` stops it; after `cancel()` returns the listener is not called again. `whenTerminated()` completes once the helper actor has stopped.

## Cluster Mode

In a cluster, registrations are replicated to all nodes, so `find` on node 2 can return an actor that lives on node 1, and `tell` on the returned handle is delivered over the network.

!!! warning "Eventually consistent"
    Replication is **eventually consistent**. Right after registering on one node, a lookup on another node may still return an empty set or an older listing. Do not treat an empty result as final: retry with a bounded wait, or use `subscribe` and react when the actor appears.

```java
// Wait for a worker registered on another node, with an upper bound
await().atMost(15, SECONDS).until(() ->
        !receptionist.find(WORKERS).toCompletableFuture().get().isEmpty());
```

Listings from a node that leaves the cluster are removed once the node is removed from membership.

## API Summary

| Method | Description |
|---|---|
| `SpringReceptionist.serviceKey(type, id)` | Creates a service key |
| `register(key, handle)` | Registers an actor under a key |
| `deregister(key, handle)` | Removes an actor from a key |
| `find(key)` / `find(key, timeout)` | One-time lookup, returns `SpringActorHandle` refs |
| `subscribe(key, listener)` | Calls the listener on every change; returns a cancellable subscription |
| `SpringBehaviorContext.registerWithReceptionist(key)` | Registers the current actor from behavior setup |
