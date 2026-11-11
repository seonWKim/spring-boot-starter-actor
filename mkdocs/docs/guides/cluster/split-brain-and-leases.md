# Split Brain and Leases

When nodes stop hearing from each other (a network split, a long GC pause, a crashed machine), each side
sees the other as unreachable. Without a decision, both sides keep running: a cluster singleton and a
sharded entity can then run twice, once on each side. A **downing strategy** (Pekko's split brain resolver)
decides which side stays and which side stops. A **lease** adds a lock in a store both sides can reach, so
only one side can hold it.

## Downing strategy

Set `spring.actor.cluster.downing.strategy`:

| `strategy` | Which side stays |
|---|---|
| `keep-majority` | The side with more nodes; on a tie, the side with the lowest address. |
| `lease-majority` | The side that takes the lease in `spring.actor.cluster.lease.store`. The bigger side tries first. |
| `static-quorum` | A side with at least `downing.quorum-size` nodes. Set it above half the cluster's size. |
| `keep-oldest` | The side with the oldest node. |
| `down-all` | No side: all nodes stop. |
| `none` | No downing. Unreachable nodes stay members until removed by hand. |

The default is `keep-majority` with `join: discovery`, and `none` otherwise. A cluster with no downing logs a
warning at startup. A node that is downed leaves the cluster and its actor system stops; with the default
`spring.actor.shutdown.exit-process: true`, the process exits too, and your platform (Kubernetes, systemd)
starts it again as a fresh node.

```yaml
spring:
  actor:
    provider: cluster
    cluster:
      downing:
        strategy: keep-majority
```

`keep-majority` needs no store, but on an even split (2 and 2) only the lowest address decides. When the
store is reachable from both sides, `lease-majority` decides by who takes the lease instead.

## Leases

A lease is a lock with an owner and an expiry, kept in a store. The holder renews it; when it stops renewing,
another node can take it after the lease's ttl. Three things can use one:

| Setting | Lease | Effect |
|---|---|---|
| `downing.strategy: lease-majority` | one per cluster | Only the side that takes it stays up. |
| `singleton.use-lease: true` | one per cluster singleton | A singleton starts only after it takes its lease, so it never runs on two nodes. See [Cluster Singletons](singletons.md#leases-never-two-at-once). |
| `sharding.use-lease: true` | one per shard | A shard starts its entities only after it takes its lease. |

Each needs `spring.actor.cluster.lease.store`:

| `store` | Where leases live | Module |
|---|---|---|
| `jdbc` | The `actor_lease` table on the application's `DataSource` | `actor-jdbc` |
| `redis` | Keys with a TTL on the application's `RedisConnectionFactory` | `actor-redis` |
| `custom` | Your `LeaseStore` bean | none |

Whoever can write the lease store can take a lease, so the store is part of the cluster's trust boundary. See
[Securing the discovery store](cluster-formation.md#securing-the-discovery-store) for the Redis ACL and the
database grants.

```yaml
spring:
  actor:
    provider: cluster
    jdbc:
      initialize-schema: always   # creates actor_peer and actor_lease
    cluster:
      downing:
        strategy: lease-majority
      lease:
        store: jdbc
      singleton:
        use-lease: true
      sharding:
        use-lease: true
```

Timings:

| Key | Default | Meaning |
|---|---|---|
| `lease.ttl` | `120s` | How long a lease stays held after its last renewal before another node may take it. |
| `lease.renew-interval` | `12s` | How often the holder renews it. Keep it well under the ttl. |
| `lease.expiry-margin` | `10s` | The holder treats the lease as lost this long before the ttl runs out, so it stops before another node can take over. Must be less than `ttl`. |

Times come from the store's clock (the database's `CURRENT_TIMESTAMP`, Redis `TIME`). The holder also counts
the ttl on its own clock from the last successful renewal: if the store is unreachable until then (minus the
margin), it gives the lease up. A singleton or shard whose lease is lost stops.

Lease names: `<name>-pekko-sbr` for the downing lease, `<name>-singleton-<path>` for a singleton and
`<name>-shard-<type>-<shard id>` for a shard, where `<name>` is `spring.actor.name`. The owner is
`<name>@<host>:<port>` of the holding node.

### JDBC

`actor_lease` is created together with `actor_peer` when `spring.actor.jdbc.initialize-schema` is `always`
(or `embedded` with an embedded database). With `never`, create it from the schema file for your database in
the `actor-jdbc` jar: `io/github/seonwkim/actor/jdbc/schema-{h2,postgresql,mysql,mariadb}.sql`.

```sql
CREATE TABLE IF NOT EXISTS actor_lease (
    lease_name VARCHAR(500) NOT NULL,
    owner VARCHAR(500) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (lease_name)
);
```

Taking a lease is one conditional `UPDATE` (free, expired or already ours), then an `INSERT` if no row exists.
The primary key makes only one of two concurrent inserts succeed.

### Redis

A lease is a key `actor:lease:<name>` set with `SET NX PX`; renew and release check the owner first. Redis
replicates to replicas asynchronously: if the primary fails right after a node took a lease, the new primary
may not have the key, and a second node can take it. Use the JDBC store, or a Redis without failover, when a
singleton must never run twice.

### Your own store

Implement `io.github.seonwkim.actor.spi.LeaseStore` (in `actor-api`, no Pekko types), declare it as a bean
and set `lease.store: custom`. A `LeaseStore` bean of your own also replaces the `jdbc` and `redis` stores.

The interface:

```java
public interface LeaseStore {
    boolean tryAcquire(String lease, String owner, Duration ttl); // free, expired or already ours
    boolean renew(String lease, String owner, Duration ttl);      // only while we hold it
    void release(String lease, String owner);                     // only if we hold it
    Optional<String> holder(String lease);                        // the current, unexpired owner
}
```

An implementation, as a bean:

=== "Java"

    ```java
    @Component
    public class MyLeaseStore implements LeaseStore {

        private final MyLeaseTable table; // your own storage code

        public MyLeaseStore(MyLeaseTable table) {
            this.table = table;
        }

        @Override
        public boolean tryAcquire(String lease, String owner, Duration ttl) {
            return table.insertOrTakeOver(lease, owner, ttl); // one atomic statement
        }

        @Override
        public boolean renew(String lease, String owner, Duration ttl) {
            return table.extendIfOwner(lease, owner, ttl);
        }

        @Override
        public void release(String lease, String owner) {
            table.deleteIfOwner(lease, owner);
        }

        @Override
        public Optional<String> holder(String lease) {
            return table.unexpiredOwner(lease);
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class MyLeaseStore(private val table: MyLeaseTable) : LeaseStore { // MyLeaseTable: your own storage code

        override fun tryAcquire(lease: String, owner: String, ttl: Duration): Boolean =
            table.insertOrTakeOver(lease, owner, ttl) // one atomic statement

        override fun renew(lease: String, owner: String, ttl: Duration): Boolean =
            table.extendIfOwner(lease, owner, ttl)

        override fun release(lease: String, owner: String) =
            table.deleteIfOwner(lease, owner)

        override fun holder(lease: String): Optional<String> =
            table.unexpiredOwner(lease)
    }
    ```

`Duration` is `java.time.Duration` in both.

Each method must be atomic in the store. Methods may block: the starter calls them on the blocking
dispatcher, and owns the renewal timers. Run `LeaseStoreContract` from `actor-test` against your store to
check it (see [Testing](../testing.md#contract-suites-for-your-own-store)).

## Related

- [Cluster Singletons](singletons.md): what a singleton lease protects
- [Sharded Actors](sharded-actors.md): what a shard lease protects
- [Cluster Formation](cluster-formation.md): `actor-jdbc` and `actor-redis` also find peers
- [Production](../operations/production.md): graceful shutdown, so a leaving node is never downed
