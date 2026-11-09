# Replicated Data

Replicated data is a map, a counter or a set that every node of the cluster keeps a copy of. Any node
reads and writes its own copy without a round trip, and the copies converge: changes made on different
nodes at the same time are merged, never lost. These are Pekko Distributed Data's CRDTs (conflict-free
replicated data types) behind a small API with no Pekko types.

Use it for small, shared, often-read state: feature flags, who is online, rate-limit counters, a cache of
settings. It is not a database: the data lives in memory and is gone when every node stops.

## Setup

Replicated data needs the cluster starter and cluster mode:

```yaml
spring:
  actor:
    provider: cluster
```

The starter then provides a `ReplicatedData` bean. Each node starts Pekko's replicator when its actor
system starts.

## Maps, counters and sets

`ReplicatedData` hands out a handle per name. Declare the handles you use as beans and inject them where
you need them:

```java
@Configuration
class ReplicatedDataConfig {

    @Bean
    ReplicatedMap<String, Cart> carts(ReplicatedData data) {
        return data.map("carts");
    }

    @Bean
    ReplicatedCounter visits(ReplicatedData data) {
        return data.counter("visits");
    }

    @Bean
    ReplicatedSet<String> onlineUsers(ReplicatedData data) {
        return data.set("online-users");
    }
}
```

Or inject `ReplicatedData` and ask for a handle when the name is only known at run time, such as one
counter per tenant: `data.counter("requests-" + tenantId)`. A handle is a cheap view: two handles with the
same name and kind read and write the same data. Use each name for one kind only.

Every read and write returns a `CompletionStage`:

```java
@Service
class CartService {
    private final ReplicatedMap<String, Cart> carts;

    CartService(ReplicatedMap<String, Cart> carts) {
        this.carts = carts;
    }

    CompletionStage<Void> save(String user, Cart cart) {
        return carts.put(user, cart);
    }

    CompletionStage<Optional<Cart>> find(String user) {
        return carts.get(user);
    }
}
```

| Type | Reads | Writes | When two nodes write at once |
|---|---|---|---|
| `ReplicatedMap<K, V>` | `entries()`, `get(key)` | `put(key, value)`, `remove(key)` | The later write to a key wins, by wall clock |
| `ReplicatedCounter` | `value()` | `increment(delta)`, `decrement(delta)` | Both changes count |
| `ReplicatedSet<E>` | `elements()` | `add(element)`, `remove(element)` | An add wins over a concurrent remove |

A map, counter or set nobody has written yet reads as empty (or zero).

## Consistency

Each read and write takes an optional `Consistency`: how many nodes to wait for.

| `Consistency` | Waits for | Use it when |
|---|---|---|
| `local()` (default) | This node only | Fast reads and writes; other nodes see the change shortly after, in the background |
| `majority(timeout)` | More than half of the nodes | A write followed by a read on another node must see it |
| `all(timeout)` | Every node | Rarely: one unreachable node makes it fail |

```java
Consistency majority = Consistency.majority(Duration.ofSeconds(3));

carts.put("alice", cart, majority);   // on node A
carts.get("alice", majority);         // on node B: sees the write
```

A majority write followed by a majority read always sees the write, because the two majorities share at
least one node. If the nodes don't answer within the timeout, the stage completes with
`ReplicationTimeoutException`. A write that times out is still applied on this node and spreads to the
others later; it is not rolled back.

## What can be stored

Keys, values and set elements travel between nodes, so they must be serializable: `String`, `Integer`,
`Long`, or a type that implements `JsonSerializable` or `CborSerializable`, the same as actor messages.
Values that implement `JsonSerializable` use your application's `ObjectMapper`.

```java
public record Cart(List<String> items, int quantity) implements JsonSerializable {}
```

## Limits

- **Small data.** Each node holds every entry, a new node receives all of them, and a change can send a
  whole map or set to the other nodes. Keep each one to thousands of entries, not millions; split big
  data over several names.
- **In memory.** The data survives a node restart while other nodes keep it, and is lost when all nodes
  stop. Keep the source of truth in a database.
- **Clock skew.** A map decides concurrent writes to the same key by wall clock, so keep the nodes' clocks
  in sync (NTP).

## Tuning

Pekko's settings under `pekko.cluster.distributed-data` apply through the raw override, for example a
shorter gossip interval:

```yaml
spring:
  actor:
    pekko:
      cluster:
        distributed-data:
          gossip-interval: 1s
```
