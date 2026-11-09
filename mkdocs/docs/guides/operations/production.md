# Running in Production

This guide covers what matters when you run an actor application in production, for example on
Kubernetes: how it shuts down without losing work, how health checks report the actor system and the
cluster, and a checklist to go through before the first release.

For metrics, tracing and alerts, see [Monitoring](monitoring.md). For rolling, canary and blue-green
releases, see [Deployments](deployments.md).

## Graceful shutdown

When the Spring application context closes (on `SIGTERM`, or when you call `context.close()`), the starter
shuts the actor system down through Pekko **coordinated shutdown**. In cluster mode the node leaves the
cluster on purpose: other nodes see it go `Leaving` → `Exiting` → `Removed`, sharded entities and cluster
singletons move to the remaining nodes, and the node is never reported as unreachable or downed by the
split brain resolver.

### Shutdown order

The starter registers a Spring `SmartLifecycle` bean, `ActorSystemShutdownLifecycle`, at phase
`ActorSystemShutdownLifecycle.PHASE` (`Integer.MAX_VALUE - 4096`). Spring stops lifecycle beans from the
highest phase to the lowest before it destroys any bean, so on shutdown:

1. With `server.shutdown=graceful`, the web server stops accepting requests and drains in-flight ones
   (Spring Boot's web server lifecycles run at higher phases).
2. The actor system runs coordinated shutdown and the starter waits for it to terminate, at most the
   configured timeout.
3. Spring destroys the remaining beans, including the services, repositories and connection pools your
   actors use. Actors are already stopped, so they never call a destroyed bean.

The wait is bounded: if an actor blocks shutdown (for example a handler stuck on blocking I/O), closing the
context returns after the timeout with a warning in the log, and the process can exit instead of hanging.

The shutdown runs once. If the actor system has already terminated, for example because the node was downed
and Pekko shut itself down, closing the context does nothing more and does not fail.

### Shutdown timeout

```yaml
spring:
  actor:
    shutdown:
      timeout: 20s   # default
```

`spring.actor.shutdown.timeout` is how long closing the context waits for the actor system to terminate. It
takes any Spring Boot duration (`15s`, `500ms`, `PT20S`). The default is `20s`.

Keep Pekko's own coordinated shutdown phase timeouts (`pekko.coordinated-shutdown.phases.<phase>.timeout`)
shorter than this setting, so the phases that matter for a graceful leave (`cluster-leave`,
`cluster-sharding-shutdown-region`, `cluster-exiting`) finish inside the wait.

### When the actor system stops by itself

If the actor system terminates while the application runs (for example after the split brain resolver
downed this node), the Spring context closes and the process exits with status 1, so Kubernetes or systemd
restarts it. Set `spring.actor.shutdown.exit-process=false` to only close the context. Your code can wait for
this with `SpringActorSystem.whenTerminated()`.

### Kubernetes: `terminationGracePeriodSeconds` and the `preStop` hook

When Kubernetes stops a pod it runs the `preStop` hook, then sends `SIGTERM`, and sends `SIGKILL` once
`terminationGracePeriodSeconds` has passed since the start, whatever is still running. A pod killed with
`SIGKILL` cannot leave the cluster and is seen as unreachable by the other nodes. So the grace period must
cover all three steps:

```
terminationGracePeriodSeconds >= preStop sleep
                                 + web server drain (spring.lifecycle.timeout-per-shutdown-phase)
                                 + spring.actor.shutdown.timeout
                                 + a few seconds margin
```

A `preStop` sleep gives the readiness probe time to fail and the load balancer time to stop sending traffic
before the application begins to shut down:

```yaml
spec:
  terminationGracePeriodSeconds: 45
  containers:
    - name: app
      lifecycle:
        preStop:
          exec:
            command: ["/bin/sh", "-c", "sleep 10"]
```

With matching application settings:

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 10s   # web server drain
  actor:
    shutdown:
      timeout: 20s                    # actor system coordinated shutdown
    pekko:
      coordinated-shutdown:
        phases:
          cluster-leave:
            timeout: 10s
          cluster-sharding-shutdown-region:
            timeout: 10s
```

Here the worst case is 10s (`preStop`) + 10s (web drain) + 20s (actor system) = 40s, inside the 45s grace
period. If you must keep `terminationGracePeriodSeconds: 30` with a 10s `preStop` sleep, lower
`spring.actor.shutdown.timeout` (for example to `15s`) and the web drain timeout so the sum stays under 30s.

## Health checks

With Spring Boot Actuator on the classpath, `/actuator/health` gets two components:

| Component | When | Status |
|---|---|---|
| `actorSystem` | always | `UP` while the actor system runs, `DOWN` once it has terminated. Details: the system's `name`. |
| `actorCluster` | `spring.actor.provider=cluster` | `UP` when this node is an `Up` member; `OUT_OF_SERVICE` while it has not joined yet, is joining, weakly up, leaving or shutting down; `DOWN` when it was downed or removed, or other members report it unreachable |

`actorCluster` details hold this node's status (`selfStatus`, `NOT_JOINED` before it joins), whether it is
unreachable (`selfUnreachable`), the member count (`members`) and the unreachable count (`unreachable`),
never addresses. An unreachable peer shows only in the count: one partitioned peer does not make every node
unhealthy. `management.health.actor.enabled=false` turns both components off.

Neither is in the Kubernetes probe groups by default. Add `actorCluster` to readiness, so a pod gets traffic
only once it is a cluster member and stops getting it as soon as it leaves:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState,actorCluster
```

Keep it out of liveness: a node that is still joining is not broken, and restarting it would only delay the
join. Cluster Bootstrap reaches pods through Pekko Management, not through the readiness-gated Service, so a
cluster still forms while no pod is ready yet.

## Production checklist

Go through this list before the first release of a clustered application.

**Cluster formation and downing**

- [ ] Every node uses the same `spring.actor.name`.
- [ ] Nodes find each other through `seed-nodes` or `join: discovery`, not `join-self`
      ([Cluster Formation](../cluster/cluster-formation.md)).
- [ ] A downing strategy is set. With `join: discovery` the default is `keep-majority`; with seed nodes the
      default is `none` and the starter logs a warning
      ([Split Brain and Leases](../cluster/split-brain-and-leases.md)).
- [ ] If a singleton or a shard must never run on two nodes at once, it uses a lease
      (`singleton.use-lease`, `sharding.use-lease`).
- [ ] The network lets pods reach each other on the remoting port (`25520` by default) and the Pekko
      Management port. The [Kubernetes example](../../examples/kubernetes.md) lists the rules to ask for.

**Messages**

- [ ] Every message that crosses nodes (sharded commands, replies, topic messages) implements
      `JsonSerializable` or `CborSerializable`. The Kubernetes example also sets
      `spring.actor.pekko.actor.allow-java-serialization: off`.
- [ ] Message changes stay readable by the previous version during a rolling or canary release
      ([Message compatibility](deployments.md#message-compatibility-across-versions)).

**Shutdown**

- [ ] `server.shutdown: graceful` is set.
- [ ] `terminationGracePeriodSeconds` covers the `preStop` sleep, the web drain and
      `spring.actor.shutdown.timeout` ([above](#kubernetes-terminationgraceperiodseconds-and-the-prestop-hook)).
- [ ] `spring.actor.shutdown.exit-process` stays `true`, so a downed node restarts.

**Health and monitoring**

- [ ] The readiness group includes `actorCluster`; liveness does not.
- [ ] `actor-metrics` is on the classpath and Prometheus scrapes `/actuator/prometheus`.
- [ ] The dashboard and alert rules from `monitoring/` are installed ([Monitoring](monitoring.md#dashboard-and-alerts)).
- [ ] `spring.actor.metrics.path-tag-limit` stays `0`, or is small, so the series count does not grow with
      the number of actors.

**Data**

- [ ] With `actor-jdbc` or `actor-persistence`, `initialize-schema` is `never` and the tables come from your
      Flyway or Liquibase migrations ([Configuration Reference](configuration.md#database-tables)).
- [ ] State that must survive a restart or a blue-green switch lives in a database or event journal, not
      only in actor memory.

**Blocking work**

- [ ] Actors that call blocking I/O (JDBC, blocking HTTP clients) run on `withBlockingDispatcher()` or a
      dispatcher of their own ([Dispatchers](../core/dispatchers.md)).

## Related

- [Monitoring](monitoring.md): meters, the `actors` endpoint, tracing, dashboard and alerts
- [Deployments](deployments.md): rolling, canary and blue-green releases
- [Configuration Reference](configuration.md): every key on this page
- [Kubernetes example](../../examples/kubernetes.md): manifests that apply these settings
